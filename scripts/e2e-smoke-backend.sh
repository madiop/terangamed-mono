#!/usr/bin/env bash
# ============================================================================
#  TerangaMed — Smoke E2E backend (CI-friendly)
# ============================================================================
#
#  Usage :
#    ./scripts/e2e-smoke-backend.sh [--build] [--keep] [--quiet] [--no-teardown-on-fail]
#
#  Flags :
#    --build               : force `docker compose build` avant up (utile après edit code)
#    --keep                : ne pas tear-down après succès (laisser le stack up pour itérer)
#    --no-teardown-on-fail : laisser le stack up en cas d'échec (debug — sinon tear-down auto)
#    --quiet               : sortie minimale (CI-friendly)
#    -h, --help            : affiche cette aide
#
#  Phases (durée typique entre parenthèses) :
#    1. Pre-checks : docker / jq / curl + scripts annexes        (~1 s)
#    2. docker compose up -d (build si --build)                  (~30 s à 10 min selon cache)
#    3. Wait infra : Postgres + Keycloak + Kafka + Schema Reg.   (~60 s)
#    4. Wait Spring : config-server, discovery-server, gateway   (~90 s)
#    5. Wait Eureka registration des 5 microservices             (~30 s)
#    6. swagger-smoke.sh sur les 5 services                      (~5 s)
#    7. Auth check : get-token.sh reception                      (~2 s)
#    8. CRUD check : POST /api/patients + GET liste + assert     (~3 s)
#    9. Teardown ou keep
#
#  Exit code :
#    - 0 si tout OK
#    - 1 si une phase échoue
#    - 2 si pré-requis manquants
#
#  Pré-requis :
#    - Docker engine (compose v2)
#    - curl, jq
#    - ./scripts/swagger-smoke.sh, ./scripts/get-token.sh
#    - Le réseau host-resolve : localhost:8081-8086, 8180, 8761, 8888 doivent être libres
# ============================================================================

set -euo pipefail

# ─── Couleurs ────────────────────────────────────────────────────────────────
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly BOLD='\033[1m'
readonly RESET='\033[0m'

QUIET=false
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

# ─── Args ──────────────────────────────────────────────────────────────────
BUILD=false
KEEP=false
TEARDOWN_ON_FAIL=true
while [[ $# -gt 0 ]]; do
    case "$1" in
        --build)               BUILD=true; shift ;;
        --keep)                KEEP=true; shift ;;
        --no-teardown-on-fail) TEARDOWN_ON_FAIL=false; shift ;;
        --quiet)               QUIET=true; shift ;;
        -h|--help)
            sed -n '4,36p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *)
            err "Argument inconnu: $1"
            exit 2
            ;;
    esac
done

# ─── Résolution des chemins (résilient au cwd) ──────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$REPO_ROOT/terangamed-backend/docker"
SWAGGER_SMOKE="$SCRIPT_DIR/swagger-smoke.sh"
GET_TOKEN="$SCRIPT_DIR/get-token.sh"

# Compose v2 — récupère le bon binaire (docker compose vs docker-compose)
DC=(docker compose)

# ─── Pre-checks ─────────────────────────────────────────────────────────────
banner "TerangaMed — Smoke E2E backend"

for cmd in docker curl jq; do
    if ! command -v "$cmd" > /dev/null 2>&1; then
        err "$cmd introuvable"; exit 2
    fi
done
if ! "${DC[@]}" version > /dev/null 2>&1; then
    err "docker compose v2 indisponible (essayer 'docker-compose' v1 non supporté)"
    exit 2
fi

[[ -f "$SWAGGER_SMOKE" ]] || { err "Script absent: $SWAGGER_SMOKE"; exit 2; }
[[ -f "$GET_TOKEN" ]]     || { err "Script absent: $GET_TOKEN"; exit 2; }
[[ -f "$COMPOSE_DIR/docker-compose.yml" ]] || { err "Compose absent: $COMPOSE_DIR/docker-compose.yml"; exit 2; }

ok "Pré-requis OK (docker, jq, curl, scripts annexes)"

# ─── Réseau Docker partagé (external: true côté compose) ────────────────────
# Le compose backend ET le compose frontend déclarent `terangamed-net` en
# `external: true` — donc le réseau doit exister avant le `up`. Le créer
# ici de manière idempotente garantit un démarrage robuste, quel que soit
# l'ordre dans lequel les stacks sont lancés.
if docker network inspect terangamed-net > /dev/null 2>&1; then
    ok "Réseau terangamed-net déjà présent"
else
    info "Création du réseau terangamed-net..."
    docker network create terangamed-net > /dev/null
    ok "Réseau terangamed-net créé"
fi

# ─── Helpers ────────────────────────────────────────────────────────────────

# Polling sur une URL — timeout en secondes, intervalle 2s.
# Args : <label> <url> <timeout_s> [<curl_extra_arg>...]
# Retour : 0 si UP, 1 sinon
wait_http_up() {
    local label="$1" url="$2" timeout_s="$3"
    shift 3
    local elapsed=0
    while (( elapsed < timeout_s )); do
        if curl -fs --max-time 3 "$@" "$url" > /dev/null 2>&1; then
            ok "$label : UP (${elapsed}s)"
            return 0
        fi
        sleep 2
        elapsed=$(( elapsed + 2 ))
    done
    err "$label : TIMEOUT après ${timeout_s}s (URL: $url)"
    return 1
}

# Polling sur l'état d'un container Docker (running + healthy).
# Args : <service-name> <timeout_s>
wait_compose_healthy() {
    local svc="$1" timeout_s="$2"
    local elapsed=0
    while (( elapsed < timeout_s )); do
        local status
        status=$("${DC[@]}" --project-directory "$COMPOSE_DIR" ps --format json "$svc" 2>/dev/null \
            | jq -r '.Health // .State // empty' | head -1)
        case "$status" in
            healthy)         ok "$svc : healthy (${elapsed}s)"; return 0 ;;
            running)         # service sans healthcheck (rare ici) — OK si le port répond
                ok "$svc : running (pas de healthcheck — ${elapsed}s)"
                return 0 ;;
            unhealthy|exited)
                err "$svc : $status"
                "${DC[@]}" --project-directory "$COMPOSE_DIR" logs --tail 30 "$svc" || true
                return 1 ;;
        esac
        sleep 2
        elapsed=$(( elapsed + 2 ))
    done
    err "$svc : TIMEOUT après ${timeout_s}s"
    "${DC[@]}" --project-directory "$COMPOSE_DIR" logs --tail 50 "$svc" || true
    return 1
}

# Tear-down propre du stack
teardown() {
    info "Tear-down du stack..."
    "${DC[@]}" --project-directory "$COMPOSE_DIR" down --remove-orphans > /dev/null 2>&1 || true
    ok "Stack arrêté"
}

# Hook de sortie : tear-down auto si on a démarré le stack ET (succès ET pas --keep) OU (échec ET teardown_on_fail)
EXIT_CODE=0
STACK_STARTED=false
on_exit() {
    local code=$?
    if "$STACK_STARTED"; then
        if (( code == 0 )) && ! "$KEEP"; then
            teardown
        elif (( code != 0 )) && "$TEARDOWN_ON_FAIL"; then
            teardown
        else
            warn "Stack laissé up (--keep ou --no-teardown-on-fail). Pour arrêter manuellement :"
            warn "    cd $COMPOSE_DIR && docker compose down"
        fi
    fi
    exit "$code"
}
trap on_exit EXIT

# ─── Phase 2 : docker compose up ─────────────────────────────────────────────
banner "Phase 2/9 — Démarrage du stack backend"

if "$BUILD"; then
    info "Build des images (--build) — peut prendre 5-10 min au premier run..."
    if ! "${DC[@]}" --project-directory "$COMPOSE_DIR" build > /tmp/teranga-build.log 2>&1; then
        err "Build a échoué — derniers logs :"
        tail -30 /tmp/teranga-build.log >&2
        exit 1
    fi
    ok "Build terminé"
fi

info "docker compose up -d (12 services)..."
if ! "${DC[@]}" --project-directory "$COMPOSE_DIR" up -d > /tmp/teranga-up.log 2>&1; then
    err "docker compose up a échoué — logs :"
    tail -30 /tmp/teranga-up.log >&2
    exit 1
fi
STACK_STARTED=true
ok "Stack démarré (services en cours d'initialisation)"

# ─── Phase 3 : infra externe ────────────────────────────────────────────────
banner "Phase 3/9 — Readiness infra externe (Postgres, Keycloak, Kafka, Schema Registry)"

wait_compose_healthy "postgres" 60          || exit 1
wait_compose_healthy "keycloak" 120         || exit 1   # Keycloak boot peut être long
wait_compose_healthy "kafka" 60             || exit 1
wait_compose_healthy "schema-registry" 60   || exit 1

# kafka-init est un job one-shot qui sort en 0 — pas de healthcheck
info "kafka-init (job one-shot — création des topics primaires)..."
sleep 3   # laisse le temps au job de finir

# ─── Phase 4 : services Spring infra ─────────────────────────────────────────
banner "Phase 4/9 — Readiness Spring infra (Config, Eureka, Gateway)"

wait_compose_healthy "config-server" 90     || exit 1
wait_compose_healthy "discovery-server" 90  || exit 1
wait_compose_healthy "api-gateway" 120      || exit 1

# ─── Phase 5 : Eureka registration des microservices ────────────────────────
banner "Phase 5/9 — Eureka registration des 5 microservices"

# Attente Eureka API responsive (basicAuth eureka:eureka — defaut config-repo)
wait_http_up "Eureka API" "http://localhost:8761/eureka/apps" 30 -u eureka:eureka || exit 1

EXPECTED_SERVICES=(PATIENT-SERVICE DOCTOR-SERVICE APPOINTMENT-SERVICE MEDICAL-RECORD-SERVICE NOTIFICATION-SERVICE)
EUREKA_TIMEOUT=180   # premier boot inclut Flyway migrations + connect Eureka
elapsed=0
while (( elapsed < EUREKA_TIMEOUT )); do
    registered=$(curl -fs -u eureka:eureka "http://localhost:8761/eureka/apps" \
        -H "Accept: application/json" 2>/dev/null \
        | jq -r '.applications.application[]?.name' | sort -u)
    missing=()
    for svc in "${EXPECTED_SERVICES[@]}"; do
        echo "$registered" | grep -qx "$svc" || missing+=("$svc")
    done
    if (( ${#missing[@]} == 0 )); then
        ok "5/5 microservices registered dans Eureka (${elapsed}s)"
        break
    fi
    sleep 5
    elapsed=$(( elapsed + 5 ))
done
if (( ${#missing[@]} > 0 )); then
    err "Services manquants dans Eureka après ${EUREKA_TIMEOUT}s : ${missing[*]}"
    for svc in "${missing[@]}"; do
        local_svc=$(echo "$svc" | tr '[:upper:]' '[:lower:]')
        warn "Logs $local_svc (50 dernières lignes) :"
        "${DC[@]}" --project-directory "$COMPOSE_DIR" logs --tail 50 "$local_svc" 2>&1 | head -60 || true
    done
    exit 1
fi

# ⚠️ Eureka registration ≠ service complètement initialisé.
# Le client Eureka s'enregistre dès que le contexte Spring est créé, mais le
# Web context (qui expose /v3/api-docs et /api/*) peut prendre quelques secondes
# de plus à monter (init JPA, Kafka producers, beans Mapstruct, etc.).
# On wait_compose_healthy sur les 5 microservices pour garantir qu'ils sont
# pleinement prêts AVANT swagger-smoke (sinon timeout 3s curl → faux négatif).
info "Attente healthy Docker pour les 5 microservices (post-Eureka)..."
wait_compose_healthy "patient-service"        120 || exit 1
wait_compose_healthy "doctor-service"         120 || exit 1
wait_compose_healthy "appointment-service"    120 || exit 1
wait_compose_healthy "medical-record-service" 120 || exit 1
wait_compose_healthy "notification-service"   120 || exit 1

# ─── Phase 6 : swagger-smoke ────────────────────────────────────────────────
banner "Phase 6/9 — Swagger smoke (paths + securityScheme)"

if "$QUIET"; then
    "$SWAGGER_SMOKE" > /tmp/teranga-smoke.log 2>&1 || {
        err "swagger-smoke a échoué — logs :"
        cat /tmp/teranga-smoke.log >&2
        exit 1
    }
    ok "swagger-smoke passé"
else
    "$SWAGGER_SMOKE" || exit 1
fi

# ─── Phase 7 : auth (récupération d'un token) ───────────────────────────────
banner "Phase 7/9 — Auth — récupération d'un token reception"

TOKEN=$("$GET_TOKEN" reception 2>/dev/null | tail -1)
if [[ -z "$TOKEN" || ${#TOKEN} -lt 100 ]]; then
    err "get-token.sh n'a pas retourné un JWT valide"
    "$GET_TOKEN" reception 2>&1 | tail -10 >&2 || true
    exit 1
fi
ok "Token reception obtenu (${#TOKEN} chars)"

# ─── Phase 8 : CRUD check ───────────────────────────────────────────────────
banner "Phase 8/9 — CRUD check : POST patient + GET liste + assert"

# Email horodaté pour idempotence (re-run du smoke ne casse pas sur 409 email unique)
TIMESTAMP=$(date +%s)
SMOKE_EMAIL="smoke-${TIMESTAMP}@e2e.local"
SMOKE_FIRSTNAME="E2E-${TIMESTAMP}"

PAYLOAD=$(jq -n \
    --arg email "$SMOKE_EMAIL" \
    --arg firstname "$SMOKE_FIRSTNAME" \
    '{
        civility: "MME",
        lastName: "SmokeTest",
        firstName: $firstname,
        birthDate: "1990-01-15",
        gender: "FEMALE",
        phone: "+221770000999",
        email: $email
    }')

info "POST /api/patients → email=$SMOKE_EMAIL"
RESP=$(curl -sS -w '\n%{http_code}' \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$PAYLOAD" \
    "http://localhost:8081/api/patients")

HTTP_CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')   # tout sauf dernière ligne — POSIX (`head -n -1` est GNU-only)

if [[ "$HTTP_CODE" != "201" ]]; then
    err "POST /api/patients a retourné HTTP $HTTP_CODE (attendu 201)"
    echo "$BODY" | jq . >&2 2>/dev/null || echo "$BODY" >&2
    exit 1
fi

CREATED_ID=$(echo "$BODY" | jq -r '.id // empty')
if [[ -z "$CREATED_ID" ]]; then
    err "Body 201 sans champ .id : $BODY"
    exit 1
fi
ok "Patient créé : id=$CREATED_ID"

info "GET /api/patients?firstName=$SMOKE_FIRSTNAME → assert présence"
LIST=$(curl -fs -H "Authorization: Bearer $TOKEN" \
    "http://localhost:8081/api/patients?firstName=$SMOKE_FIRSTNAME&size=10")
FOUND=$(echo "$LIST" | jq -r --arg id "$CREATED_ID" '.content[] | select(.id == ($id|tonumber)) | .id' | head -1)

if [[ -z "$FOUND" ]]; then
    err "Patient id=$CREATED_ID introuvable dans la liste filtrée"
    echo "$LIST" | jq '.content[] | {id, firstName, lastName}' >&2
    exit 1
fi
ok "Patient présent dans la liste — pipeline auth + create + search OK"

# ─── Phase 9 : récap ────────────────────────────────────────────────────────
banner "Phase 9/9 — Récap"

ok "Smoke E2E backend : SUCCESS"
info "Patient de test créé : id=$CREATED_ID, email=$SMOKE_EMAIL (laissé en base, soft-delete possible)"

if "$KEEP"; then
    info "Stack laissé up (--keep). Pour arrêter :"
    info "    cd $COMPOSE_DIR && docker compose down"
fi

EXIT_CODE=0
exit 0
