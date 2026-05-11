#!/usr/bin/env bash
# ============================================================================
#  TerangaMed — Récupération d'un access_token Keycloak (mode bearer-auth)
# ============================================================================
#
#  Usage :    ./scripts/get-token.sh [<user>] [<password>]
#
#             ./scripts/get-token.sh                       # défaut: reception/reception
#             ./scripts/get-token.sh admin                 # password = admin
#             ./scripts/get-token.sh dr.martin doctor123
#             ./scripts/get-token.sh --decode              # affiche aussi le payload JWT
#             ./scripts/get-token.sh --help
#
#  Pourquoi ce script ?
#  --------------------
#  Le scheme OAuth2 Password Flow de Swagger UI 5.13 (bundlé avec
#  springdoc-openapi 2.5.0) a un bug client-side qui empêche l'attache du
#  Bearer après Authorize : la modale affiche "Authorized" mais le token
#  n'est pas stocké → toutes les requêtes partent en 401.
#
#  Workaround fiable : utiliser le scheme `bearer-auth` qui demande à
#  l'utilisateur de coller un JWT obtenu par d'autres moyens. Ce script
#  produit ce JWT en une commande.
#
#  Sortie :
#    - stdout : le access_token brut (utilisable en curl, Postman, etc.)
#    - copie dans le clipboard si pbcopy/xclip dispo (macOS/Linux)
#    - --decode : ajoute le payload JWT décodé sur stderr (lisible par humain)
#
#  Procédure d'utilisation dans Swagger UI :
#    1. ./scripts/get-token.sh reception
#    2. Le token est dans ton clipboard (ou affiché si clipboard indispo)
#    3. Dans Swagger UI → Authorize → section "bearer-auth" → coller (Cmd-V)
#    4. Cliquer Authorize → fermer la modale
#    5. Try it out + Execute → tu verras "Authorization: Bearer eyJ..." dans
#       le curl reproduit ET la requête doit retourner 200.
#
#  ⚠️  Le token expire en 5 minutes (réglage Keycloak access.token.lifespan).
#      Relance le script si tu vois 401 après quelques minutes.
#
#  Pré-requis :
#    - curl + jq
#    - Keycloak up sur http://localhost:8180
#    - Realm `terangamed` importé (sinon : ./scripts/reset-keycloak-realm.sh)
#
#  Variables d'env optionnelles :
#    KEYCLOAK_URL        (défaut: http://localhost:8180)
#    KC_REALM            (défaut: terangamed)
#    KC_CLIENT_ID        (défaut: swagger-ui)
#
#  Exit code :
#    - 0 si token obtenu
#    - 1 si auth Keycloak échoue (mauvais user/password ou realm absent)
#    - 2 si pré-requis manquants
# ============================================================================

set -euo pipefail

# ─── Couleurs ────────────────────────────────────────────────────────────────
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly RESET='\033[0m'

err()   { echo -e "${RED}✗${RESET} $1" >&2; }
ok()    { echo -e "${GREEN}✓${RESET} $1" >&2; }
info()  { echo -e "${BLUE}ℹ${RESET} $1" >&2; }
warn()  { echo -e "${YELLOW}⚠${RESET} $1" >&2; }

# ─── Args & defaults ────────────────────────────────────────────────────────
DECODE=false
USER_ARG=""
PWD_ARG=""

for arg in "$@"; do
    case "$arg" in
        --decode) DECODE=true ;;
        -h|--help)
            sed -n '4,53p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *)
            if [[ -z "$USER_ARG" ]]; then USER_ARG="$arg"
            elif [[ -z "$PWD_ARG" ]]; then PWD_ARG="$arg"
            else err "Argument inattendu: $arg"; exit 2
            fi
            ;;
    esac
done

# Mappings user → password par défaut (cohérents avec le realm seed dev)
KC_USER="${USER_ARG:-reception}"
case "$KC_USER" in
    admin)     DEFAULT_PWD="admin" ;;
    dr.martin) DEFAULT_PWD="doctor123" ;;
    reception) DEFAULT_PWD="reception" ;;
    *)         DEFAULT_PWD="" ;;
esac
KC_PWD="${PWD_ARG:-$DEFAULT_PWD}"

if [[ -z "$KC_PWD" ]]; then
    err "Password requis pour user '$KC_USER' (pas de défaut connu)"
    err "Usage : $0 $KC_USER <password>"
    exit 2
fi

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KC_REALM="${KC_REALM:-terangamed}"
KC_CLIENT_ID="${KC_CLIENT_ID:-swagger-ui}"

# ─── Pré-requis ─────────────────────────────────────────────────────────────
for cmd in curl jq; do
    if ! command -v "$cmd" > /dev/null 2>&1; then
        err "$cmd introuvable"; exit 2
    fi
done

# ─── Récup du token ─────────────────────────────────────────────────────────
info "Récupération token : user=$KC_USER, realm=$KC_REALM, client=$KC_CLIENT_ID"

RESPONSE=$(curl -sS -X POST \
    "$KEYCLOAK_URL/realms/$KC_REALM/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=$KC_CLIENT_ID" \
    -d "username=$KC_USER" \
    -d "password=$KC_PWD" \
    -d "scope=openid profile email")

TOKEN=$(echo "$RESPONSE" | jq -r '.access_token // empty')

if [[ -z "$TOKEN" ]]; then
    err "Échec — réponse Keycloak :"
    echo "$RESPONSE" | jq . >&2 2>/dev/null || echo "$RESPONSE" >&2
    exit 1
fi

ok "Token obtenu (expire en $(echo "$RESPONSE" | jq -r '.expires_in')s)"

# ─── Decode optionnel ────────────────────────────────────────────────────────
if "$DECODE"; then
    PAYLOAD_B64=$(echo "$TOKEN" | cut -d. -f2)
    PADDING=$(( (4 - ${#PAYLOAD_B64} % 4) % 4 ))
    PAYLOAD_B64="${PAYLOAD_B64}$(printf '=%.0s' $(seq 1 $PADDING))"
    PAYLOAD=$(echo "$PAYLOAD_B64" | base64 -d 2>/dev/null)
    info "Payload JWT décodé :"
    echo "$PAYLOAD" | jq '{sub: .sub, preferred_username: .preferred_username, aud: .aud, iss: .iss, roles: .realm_access.roles, exp: (.exp | strftime("%Y-%m-%d %H:%M:%S UTC"))}' >&2
fi

# ─── Copie clipboard si possible ─────────────────────────────────────────────
COPIED=false
if command -v pbcopy > /dev/null 2>&1; then
    echo -n "$TOKEN" | pbcopy
    COPIED=true
elif command -v xclip > /dev/null 2>&1; then
    echo -n "$TOKEN" | xclip -selection clipboard
    COPIED=true
elif command -v wl-copy > /dev/null 2>&1; then
    echo -n "$TOKEN" | wl-copy
    COPIED=true
fi

if "$COPIED"; then
    ok "Token copié dans le clipboard — Cmd-V / Ctrl-V dans Swagger UI"
else
    warn "Pas de clipboard (pbcopy/xclip/wl-copy absents) — copier manuellement le token ci-dessous"
fi

# ─── Sortie ─────────────────────────────────────────────────────────────────
echo "$TOKEN"
