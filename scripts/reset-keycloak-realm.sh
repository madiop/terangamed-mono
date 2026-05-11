#!/usr/bin/env bash
# ============================================================================
#  TerangaMed — Réimport idempotent du realm Keycloak
# ============================================================================
#
#  Usage :    ./scripts/reset-keycloak-realm.sh [--dry-run] [--quiet]
#
#  --dry-run : affiche les actions sans les exécuter
#  --quiet   : sortie minimale (CI-friendly)
#  -h, --help: affiche cette aide
#
#  Pourquoi ce script ?
#  --------------------
#  Keycloak persiste son état dans Postgres (KC_DB=postgres) et l'option
#  `start-dev --import-realm` n'a d'effet QU'AU PREMIER BOOT, quand le realm
#  cible n'existe pas encore. Toute modification ultérieure du fichier
#  `terangamed-backend/docker/keycloak/realm-export.json` (ajout d'un user,
#  changement de password, nouveau client...) reste sans effet sur le realm
#  en place — d'où des 401 inexplicables ("le user X n'existe pas alors qu'il
#  est dans mon JSON !").
#
#  Symptôme classique : 401 dans Swagger avec `reception` ou `dr.martin`
#  alors que le realm-export.json est correct.
#
#  Ce script résout définitivement le problème :
#    1. Récupère un token admin du realm `master`
#    2. Supprime le realm `terangamed` (ignore 404)
#    3. Recrée le realm depuis le JSON via l'API admin
#    4. Valide en récupérant un token pour `reception`
#
#  Avantages vs. `docker compose down -v` :
#    - Conserve le volume `postgres-data` → garde tous les patients dev seed
#    - Conserve les autres realms (master, autres tenants éventuels)
#    - Préserve les sessions des autres services (Eureka, Config Server)
#    - Idempotent : peut être lancé à chaud, sans arrêt de service
#
#  Pré-requis :
#    - curl + jq
#    - Keycloak up sur http://localhost:8180
#    - Variables d'env optionnelles :
#        KEYCLOAK_URL          (défaut: http://localhost:8180)
#        KEYCLOAK_ADMIN        (défaut: admin)
#        KEYCLOAK_ADMIN_PASSWORD (défaut: admin)
#        REALM_FILE            (défaut: terangamed-backend/docker/keycloak/realm-export.json)
#
#  Exit code :
#    - 0 si le realm est réimporté ET le user reception fonctionne
#    - 1 si l'auth admin échoue ou si le réimport échoue
#    - 2 si pré-requis manquants
# ============================================================================

set -euo pipefail

# ─── Couleurs (alignées sur les autres scripts du projet) ───────────────────
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly BOLD='\033[1m'
readonly RESET='\033[0m'

# ─── Args ──────────────────────────────────────────────────────────────────
DRY_RUN=false
QUIET=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run) DRY_RUN=true; shift ;;
        --quiet)   QUIET=true;   shift ;;
        -h|--help)
            sed -n '4,53p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *)
            echo -e "${RED}✗${RESET} Argument inconnu: $1" >&2
            exit 2
            ;;
    esac
done

# ─── Helpers ────────────────────────────────────────────────────────────────
banner() {
    "$QUIET" && return
    echo
    echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════════════${RESET}"
    echo -e "${BOLD}${BLUE}  $1${RESET}"
    echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════════════${RESET}"
}
info()  { "$QUIET" || echo -e "${BLUE}ℹ${RESET}  $1"; }
ok()    { "$QUIET" || echo -e "${GREEN}✓${RESET}  $1"; }
warn()  { echo -e "${YELLOW}⚠${RESET}  $1" >&2; }
err()   { echo -e "${RED}✗${RESET}  $1" >&2; }

# ─── Configuration ──────────────────────────────────────────────────────────
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KC_ADMIN="${KEYCLOAK_ADMIN:-admin}"
KC_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM_NAME="terangamed"

# Résolution du REALM_FILE par défaut : relatif au script (resilient au cwd)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
REALM_FILE="${REALM_FILE:-$REPO_ROOT/terangamed-backend/docker/keycloak/realm-export.json}"

banner "TerangaMed — Reset du realm Keycloak"

# ─── Pré-requis ─────────────────────────────────────────────────────────────
if ! command -v curl > /dev/null 2>&1; then
    err "curl introuvable"; exit 2
fi
if ! command -v jq > /dev/null 2>&1; then
    err "jq introuvable (brew install jq / apt install jq)"; exit 2
fi
if [[ ! -f "$REALM_FILE" ]]; then
    err "Fichier realm introuvable : $REALM_FILE"; exit 2
fi

info "Keycloak  : $KEYCLOAK_URL"
info "Admin     : $KC_ADMIN"
info "Realm     : $REALM_NAME"
info "Source    : $REALM_FILE"
"$DRY_RUN" && warn "Mode DRY-RUN — aucune modification ne sera appliquée"

# Sanity check : Keycloak répond
if ! curl -fsS --max-time 5 "$KEYCLOAK_URL/realms/master/.well-known/openid-configuration" \
        > /dev/null 2>&1; then
    err "Keycloak injoignable sur $KEYCLOAK_URL — démarrer la stack docker"
    exit 1
fi
ok "Keycloak joignable"

# ─── 1. Token admin master ──────────────────────────────────────────────────
info "Étape 1/4 — Authentification admin..."

if "$DRY_RUN"; then
    info "  [dry-run] POST $KEYCLOAK_URL/realms/master/protocol/openid-connect/token"
    TOKEN="dry-run-token"
else
    TOKEN_RESPONSE=$(curl -sS -X POST \
        "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password" \
        -d "client_id=admin-cli" \
        -d "username=$KC_ADMIN" \
        -d "password=$KC_PASSWORD")
    TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty')
    if [[ -z "$TOKEN" ]]; then
        err "Impossible d'obtenir un token admin"
        echo "$TOKEN_RESPONSE" | jq . >&2 || echo "$TOKEN_RESPONSE" >&2
        exit 1
    fi
fi
ok "Token admin OK"

# ─── 2. Suppression du realm existant ───────────────────────────────────────
info "Étape 2/4 — Suppression du realm $REALM_NAME (si existant)..."

if "$DRY_RUN"; then
    info "  [dry-run] DELETE $KEYCLOAK_URL/admin/realms/$REALM_NAME"
    DELETE_HTTP="dry-run"
else
    DELETE_HTTP=$(curl -sS -o /tmp/keycloak-delete.log -w "%{http_code}" \
        -X DELETE "$KEYCLOAK_URL/admin/realms/$REALM_NAME" \
        -H "Authorization: Bearer $TOKEN")
fi

case "$DELETE_HTTP" in
    204|200)   ok "Realm supprimé (HTTP $DELETE_HTTP)" ;;
    404)       ok "Realm absent (HTTP 404) — sera créé from scratch" ;;
    dry-run)   ok "Suppression simulée" ;;
    *)
        err "Suppression KO — HTTP $DELETE_HTTP"
        cat /tmp/keycloak-delete.log >&2 2>/dev/null || true
        exit 1
        ;;
esac

# ─── 3. Réimport du realm ───────────────────────────────────────────────────
info "Étape 3/4 — Réimport du realm depuis le JSON..."

if "$DRY_RUN"; then
    info "  [dry-run] POST $KEYCLOAK_URL/admin/realms (JSON $(wc -c < "$REALM_FILE") octets)"
else
    IMPORT_HTTP=$(curl -sS -o /tmp/keycloak-import.log -w "%{http_code}" \
        -X POST "$KEYCLOAK_URL/admin/realms" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        --data-binary "@$REALM_FILE")

    case "$IMPORT_HTTP" in
        201)
            ok "Realm réimporté (HTTP 201)"
            ;;
        409)
            err "Conflit (HTTP 409) — la suppression précédente a peut-être échoué"
            cat /tmp/keycloak-import.log >&2 2>/dev/null || true
            exit 1
            ;;
        *)
            err "Réimport KO — HTTP $IMPORT_HTTP"
            cat /tmp/keycloak-import.log >&2 2>/dev/null || true
            exit 1
            ;;
    esac
fi

# ─── 4. Validation : token reception ────────────────────────────────────────
info "Étape 4/4 — Validation user 'reception/reception'..."

if "$DRY_RUN"; then
    info "  [dry-run] POST .../realms/$REALM_NAME/.../token (grant=password, user=reception)"
    ok "Validation simulée"
else
    RECEPTION_RESP=$(curl -sS -X POST \
        "$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password" \
        -d "client_id=swagger-ui" \
        -d "username=reception" \
        -d "password=reception" \
        -d "scope=openid profile email")

    RECEPTION_TOKEN=$(echo "$RECEPTION_RESP" | jq -r '.access_token // empty')
    if [[ -n "$RECEPTION_TOKEN" ]]; then
        # Décode le payload JWT pour extraire les rôles (sanity)
        PAYLOAD_B64=$(echo "$RECEPTION_TOKEN" | cut -d. -f2)
        # Ajoute le padding base64 si nécessaire
        PADDING=$(( (4 - ${#PAYLOAD_B64} % 4) % 4 ))
        PAYLOAD_B64="${PAYLOAD_B64}$(printf '=%.0s' $(seq 1 $PADDING))"
        ROLES=$(echo "$PAYLOAD_B64" | base64 -d 2>/dev/null \
            | jq -r '.realm_access.roles | join(",")' 2>/dev/null || echo "?")
        AUD=$(echo "$PAYLOAD_B64" | base64 -d 2>/dev/null \
            | jq -r 'if .aud | type == "array" then .aud | join(",") else .aud end' 2>/dev/null || echo "?")
        ok "Token reception OK — roles=[$ROLES] aud=[$AUD]"
    else
        err "Le user reception ne s'authentifie toujours pas après réimport"
        echo "$RECEPTION_RESP" | jq . >&2 || echo "$RECEPTION_RESP" >&2
        exit 1
    fi
fi

# ─── Récap ─────────────────────────────────────────────────────────────────
banner "Réimport terminé"
ok "Realm $REALM_NAME prêt — Swagger doit maintenant passer en reception/dr.martin/admin"
info "Note : les tokens existants émis avant ce reset sont invalidés."
info "       Re-cliquer sur 'Authorize' dans Swagger pour récupérer un token frais."
exit 0
