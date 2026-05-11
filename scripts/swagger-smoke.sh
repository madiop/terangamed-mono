#!/usr/bin/env bash
# ============================================================================
#  TerangaMed — Swagger smoke test
# ============================================================================
#
#  Usage :    ./scripts/swagger-smoke.sh [--service <name>] [--verbose]
#
#  --service <name>  : ne teste qu'un service (patient|doctor|appointment|
#                      medical-record|notification)
#  --verbose         : affiche la liste détaillée des paths trouvés
#  -h, --help        : affiche cette aide
#
#  Pour chaque service, vérifie en moins de 5s :
#    1. GET /v3/api-docs           → 200 + JSON OpenAPI valide + paths > 0
#    2. GET /swagger-ui.html       → 200 ou 302 (redirect vers /swagger-ui/index.html)
#    3. Présence du securityScheme keycloak-password dans le JSON
#    4. Nombre de paths >= seuil minimum (détection de régression)
#
#  Pré-requis :
#    - Stack démarrée (Keycloak + 5 services)
#    - curl (toujours présent macOS/Linux)
#    - jq optionnel (fallback Python si absent)
#
#  Exit code :
#    - 0 si tous les services smoke OK
#    - 1 si au moins un service a échoué
#    - 2 si pré-requis manquants
# ============================================================================

set -euo pipefail

# ─── Couleurs ANSI (alignées sur coverage-all.sh) ───────────────────────────
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly BOLD='\033[1m'
readonly RESET='\033[0m'

# ─── Catalogue des services (name|port|min_paths) ──────────────────────────
# Le seuil min_paths protège contre une régression silencieuse où un controller
# entier serait perdu au refactor (Spring ne crashe pas, juste les routes
# disparaissent du Swagger).
#
# ⚠️ ATTENTION — DÉFINITION DE min_paths
# min_paths = nombre de **paths uniques** OpenAPI (= clés de l'objet `.paths`
# du JSON `/v3/api-docs`), PAS le nombre d'opérations.
# Plusieurs verbes HTTP sur un même path ne comptent que pour 1.
#
# Exemples patient-service (4 paths, 7 opérations) :
#   /api/patients              → GET + POST                    (1 path)
#   /api/patients/{id}         → GET + PUT + DELETE            (1 path)
#   /api/patients/by-mrn/{mrn} → GET                            (1 path)
#   /api/patients/{id}/archive → POST                           (1 path)
#
# Comment maintenir ces seuils :
#   - Ajout d'un nouveau path → bumper le seuil dans la PR
#   - Suppression d'un path  → relire d'abord pourquoi (régression possible)
#   - Ajout d'une nouvelle méthode HTTP sur un path existant → ne change rien
#
# Pour vérifier les valeurs actuelles : ./scripts/swagger-smoke.sh --verbose
readonly SERVICES=(
    "patient|8081|4"
    "doctor|8082|7"
    "appointment|8083|6"
    "medical-record|8084|14"
    "notification|8086|2"
)

# ─── Helpers d'affichage ────────────────────────────────────────────────────
banner() {
    echo
    echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════════════${RESET}"
    echo -e "${BOLD}${BLUE}  $1${RESET}"
    echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════════════${RESET}"
}

info()  { echo -e "${BLUE}ℹ${RESET}  $1"; }
ok()    { echo -e "${GREEN}✓${RESET}  $1"; }
warn()  { echo -e "${YELLOW}⚠${RESET}  $1"; }
err()   { echo -e "${RED}✗${RESET}  $1"; }

# ─── Args ──────────────────────────────────────────────────────────────────
TARGET_SERVICE=""
VERBOSE=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --service)
            TARGET_SERVICE="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            # Affiche le header descriptif (entre les 2 premières lignes "# ===")
            sed -n '4,28p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *)
            err "Argument inconnu: $1"
            exit 2
            ;;
    esac
done

# ─── Vérif pré-requis ──────────────────────────────────────────────────────
if ! command -v curl > /dev/null 2>&1; then
    err "curl introuvable — abandon"
    exit 2
fi

# Détection jq : si absent on utilise Python pour parser le JSON
HAS_JQ=false
if command -v jq > /dev/null 2>&1; then
    HAS_JQ=true
fi

# ─── Helpers JSON (jq ou fallback Python) ───────────────────────────────────
# Compte le nombre de paths dans une spec OpenAPI passée en stdin
count_paths() {
    if [[ "$HAS_JQ" == "true" ]]; then
        jq '.paths | length' 2>/dev/null || echo "0"
    else
        python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d.get('paths', {})))" 2>/dev/null || echo "0"
    fi
}

# Vérifie la présence d'un security scheme nommé
has_security_scheme() {
    local scheme="$1"
    if [[ "$HAS_JQ" == "true" ]]; then
        jq -e ".components.securitySchemes.\"$scheme\"" > /dev/null 2>&1
    else
        python3 -c "
import json,sys
d = json.load(sys.stdin)
schemes = d.get('components', {}).get('securitySchemes', {})
sys.exit(0 if '$scheme' in schemes else 1)
" 2>/dev/null
    fi
}

# Liste les paths (pour --verbose)
list_paths() {
    if [[ "$HAS_JQ" == "true" ]]; then
        jq -r '.paths | keys[]' 2>/dev/null || true
    else
        python3 -c "
import json,sys
d = json.load(sys.stdin)
for p in sorted(d.get('paths', {}).keys()):
    print(p)
" 2>/dev/null || true
    fi
}

# ─── Test d'un service ──────────────────────────────────────────────────────
# Args : name port min_paths
# Retour : 0 si OK, 1 sinon
check_service() {
    local name="$1"
    local port="$2"
    local min_paths="$3"
    local base="http://localhost:$port"
    local errors=0

    echo
    info "${BOLD}${name}-service${RESET} (port $port)"

    # 1. /v3/api-docs accessible
    local docs
    if ! docs=$(curl -sf --max-time 3 "$base/v3/api-docs" 2>/dev/null); then
        err "  /v3/api-docs : injoignable (service down ?)"
        return 1
    fi

    # 2. JSON valide
    if [[ "$HAS_JQ" == "true" ]]; then
        if ! echo "$docs" | jq empty > /dev/null 2>&1; then
            err "  /v3/api-docs : JSON invalide"
            return 1
        fi
    else
        if ! echo "$docs" | python3 -c "import json,sys; json.load(sys.stdin)" > /dev/null 2>&1; then
            err "  /v3/api-docs : JSON invalide"
            return 1
        fi
    fi
    ok "  /v3/api-docs : 200 + JSON valide"

    # 3. Paths >= seuil
    local count
    count=$(echo "$docs" | count_paths)
    if [[ "$count" -lt "$min_paths" ]]; then
        err "  paths trouvés : $count (< seuil $min_paths) — régression probable"
        errors=$((errors + 1))
    else
        ok "  paths trouvés : $count (≥ seuil $min_paths)"
    fi

    # Verbose : liste des paths
    if [[ "$VERBOSE" == "true" ]]; then
        echo "$docs" | list_paths | sed 's/^/      /'
    fi

    # 4. SecurityScheme bearer-auth présent
    # On a abandonné le password flow OAuth2 (cassé sur Swagger UI 5.13) au profit
    # de bearer-auth + ./scripts/get-token.sh. Vérifie que ce scheme est toujours
    # exposé — sans lui, Swagger UI ne pourrait pas attacher de Bearer.
    if echo "$docs" | has_security_scheme "bearer-auth"; then
        ok "  securityScheme bearer-auth : présent"
    else
        err "  securityScheme bearer-auth : ABSENT — auth Swagger cassée"
        errors=$((errors + 1))
    fi

    # 5. /swagger-ui.html accessible (200 ou 302)
    local ui_code
    ui_code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$base/swagger-ui.html" 2>/dev/null || echo "000")
    if [[ "$ui_code" == "200" || "$ui_code" == "302" ]]; then
        ok "  /swagger-ui.html : HTTP $ui_code"
    else
        err "  /swagger-ui.html : HTTP $ui_code (attendu 200 ou 302)"
        errors=$((errors + 1))
    fi

    return "$errors"
}

# ─── Main ───────────────────────────────────────────────────────────────────
main() {
    banner "TerangaMed — Swagger smoke test"
    if [[ "$HAS_JQ" == "true" ]]; then
        info "Parser : jq"
    else
        info "Parser : python3 (jq absent, fallback)"
    fi
    if [[ -n "$TARGET_SERVICE" ]]; then
        info "Cible : ${TARGET_SERVICE}-service uniquement"
    fi

    local total=0
    local passed=0
    local failed_services=()

    for entry in "${SERVICES[@]}"; do
        IFS='|' read -r name port min_paths <<< "$entry"
        if [[ -n "$TARGET_SERVICE" && "$TARGET_SERVICE" != "$name" ]]; then
            continue
        fi
        total=$((total + 1))
        if check_service "$name" "$port" "$min_paths"; then
            passed=$((passed + 1))
        else
            failed_services+=("$name")
        fi
    done

    if [[ "$total" -eq 0 ]]; then
        err "Aucun service ne correspond au filtre --service '$TARGET_SERVICE'"
        exit 2
    fi

    banner "Récapitulatif"
    if [[ "${#failed_services[@]}" -eq 0 ]]; then
        ok "Tous les services Swagger OK : $passed/$total"
        exit 0
    else
        err "Services en échec : ${failed_services[*]}"
        err "Bilan : $passed/$total OK"
        exit 1
    fi
}

main "$@"
