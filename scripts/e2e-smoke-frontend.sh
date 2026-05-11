#!/usr/bin/env bash
# ============================================================================
#  TerangaMed — Smoke E2E frontend (CI-friendly)
# ============================================================================
#
#  Usage :
#    ./scripts/e2e-smoke-frontend.sh [--build] [--keep] [--quiet] [--no-teardown-on-fail]
#
#  Flags :
#    --build               : force `docker compose build` avant up
#    --keep                : ne pas tear-down après succès
#    --no-teardown-on-fail : laisser le stack up en cas d'échec (debug)
#    --quiet               : sortie minimale (CI)
#    -h, --help            : affiche cette aide
#
#  Phases :
#    1. Pre-checks                                              (~1 s)
#    2. Réseau terangamed-net (créé si absent)                  (~1 s)
#    3. Détection présence du backend (api-gateway joignable ?) (~2 s)
#    4. docker compose up -d (build si --build)                 (~30 s à 5 min)
#    5. Wait container healthy                                  (~30 s)
#    6. Test bundle SPA servi (200 + <app-root>)                (~1 s)
#    7. Test cache headers (immutable sur assets hashés)        (~1 s)
#    8. Test reverse-proxy /api/* (502 si pas de backend, 401 si up)  (~2 s)
#    9. (Optionnel — si backend up) Test full path /api/patients
#       avec token via le reverse-proxy Nginx                   (~5 s)
#   10. Récap + tear-down (sauf --keep)
#
#  Exit code :
#    - 0 si tout OK
#    - 1 si une phase échoue
#    - 2 si pré-requis manquants
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
            sed -n '4,33p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *) err "Argument inconnu: $1"; exit 2 ;;
    esac
done

# ─── Résolution des chemins ─────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_DIR="$REPO_ROOT/terangamed-frontend/docker"

DC=(docker compose)

# ─── Pré-checks ──────────────────────────────────────────────────────────────
banner "TerangaMed — Smoke E2E frontend"

for cmd in docker curl; do
    if ! command -v "$cmd" > /dev/null 2>&1; then
        err "$cmd introuvable"; exit 2
    fi
done
if ! "${DC[@]}" version > /dev/null 2>&1; then
    err "docker compose v2 indisponible"; exit 2
fi

[[ -f "$COMPOSE_DIR/docker-compose.yml" ]] || { err "Compose absent: $COMPOSE_DIR/docker-compose.yml"; exit 2; }

ok "Pré-requis OK (docker, curl, compose v2)"

# ─── Phase 2 : réseau Docker ─────────────────────────────────────────────────
banner "Phase 2/10 — Réseau Docker terangamed-net"

if docker network inspect terangamed-net > /dev/null 2>&1; then
    ok "Réseau terangamed-net déjà présent"
else
    info "Réseau terangamed-net absent — création (mode standalone, pas de backend détecté)"
    docker network create terangamed-net > /dev/null
    ok "Réseau créé"
fi

# ─── Phase 3 : détection présence du backend ────────────────────────────────
banner "Phase 3/10 — Détection backend (api-gateway joignable ?)"

BACKEND_UP=false
# Le backend expose api-gateway sur :8080 sur l'hôte. On teste depuis l'hôte
# (pas depuis le réseau Docker) — c'est le moyen le plus universel.
if curl -fsS --max-time 3 http://localhost:8080/actuator/health > /dev/null 2>&1; then
    BACKEND_UP=true
    ok "Backend détecté (api-gateway répond sur :8080)"
else
    warn "Backend non détecté — on testera uniquement le bundle SPA et le 502 du reverse-proxy"
    warn "Pour un test complet, lance d'abord : ./scripts/e2e-smoke-backend.sh --keep"
fi

# ─── Hook de tear-down auto ──────────────────────────────────────────────────
STACK_STARTED=false
teardown() {
    info "Tear-down du stack frontend..."
    "${DC[@]}" --project-directory "$COMPOSE_DIR" down --remove-orphans > /dev/null 2>&1 || true
    ok "Stack frontend arrêté"
}
on_exit() {
    local code=$?
    if "$STACK_STARTED"; then
        if (( code == 0 )) && ! "$KEEP"; then
            teardown
        elif (( code != 0 )) && "$TEARDOWN_ON_FAIL"; then
            teardown
        else
            warn "Stack frontend laissé up. Pour arrêter :"
            warn "    cd $COMPOSE_DIR && docker compose down"
        fi
    fi
    exit "$code"
}
trap on_exit EXIT

# ─── Phase 4 : démarrage du stack frontend ──────────────────────────────────
banner "Phase 4/10 — Démarrage du stack frontend"

if "$BUILD"; then
    info "Build de l'image frontend (--build)..."
    if ! "${DC[@]}" --project-directory "$COMPOSE_DIR" build > /tmp/teranga-frontend-build.log 2>&1; then
        err "Build a échoué :"
        tail -20 /tmp/teranga-frontend-build.log >&2
        exit 1
    fi
    ok "Build terminé"
fi

info "docker compose up -d..."
if ! "${DC[@]}" --project-directory "$COMPOSE_DIR" up -d > /tmp/teranga-frontend-up.log 2>&1; then
    err "docker compose up a échoué :"
    tail -20 /tmp/teranga-frontend-up.log >&2
    exit 1
fi
STACK_STARTED=true
ok "Stack frontend démarré"

# ─── Phase 5 : wait healthy ──────────────────────────────────────────────────
banner "Phase 5/10 — Attente healthy"

elapsed=0
TIMEOUT=60
while (( elapsed < TIMEOUT )); do
    status=$("${DC[@]}" --project-directory "$COMPOSE_DIR" ps --format json frontend 2>/dev/null \
        | head -1 | grep -oE '"Health":"[^"]*"' | cut -d'"' -f4)
    case "$status" in
        healthy)
            ok "frontend : healthy (${elapsed}s)"
            break ;;
        unhealthy)
            err "frontend : unhealthy"
            "${DC[@]}" --project-directory "$COMPOSE_DIR" logs --tail 30 frontend >&2 || true
            exit 1 ;;
    esac
    sleep 2
    elapsed=$(( elapsed + 2 ))
done
if (( elapsed >= TIMEOUT )); then
    err "Timeout : frontend pas healthy après ${TIMEOUT}s"
    "${DC[@]}" --project-directory "$COMPOSE_DIR" logs --tail 50 frontend >&2 || true
    exit 1
fi

# ─── Phase 6 : bundle SPA servi ──────────────────────────────────────────────
banner "Phase 6/10 — Bundle SPA Angular servi"

PORT="${FRONTEND_HOST_PORT:-4200}"
RESP=$(curl -sS -w '\n%{http_code}' --max-time 5 "http://localhost:$PORT/")
HTTP_CODE=$(echo "$RESP" | tail -1)
BODY=$(echo "$RESP" | sed '$d')   # tout sauf dernière ligne — POSIX (`head -n -1` est GNU-only)

if [[ "$HTTP_CODE" != "200" ]]; then
    err "GET / a retourné HTTP $HTTP_CODE (attendu 200)"; exit 1
fi
ok "GET / : 200 OK"

if echo "$BODY" | grep -qE '<(tm-)?app-root|<title>'; then
    ok "Bundle Angular détecté (app-root ou title trouvé dans index.html)"
else
    err "index.html ne contient pas le marqueur Angular attendu"
    echo "$BODY" | head -20 >&2
    exit 1
fi

# ─── Phase 7 : cache headers ────────────────────────────────────────────────
banner "Phase 7/10 — Cache headers (immutable sur assets hashés)"

# Cherche le bundle main.HASH.js dans le HTML pour tester son cache header
JS_BUNDLE=$(echo "$BODY" | grep -oE 'main\.[a-f0-9-]+\.js' | head -1)
if [[ -n "$JS_BUNDLE" ]]; then
    CACHE_HDR=$(curl -sI --max-time 5 "http://localhost:$PORT/$JS_BUNDLE" \
        | grep -i '^cache-control:' | tr -d '\r' | sed 's/^[Cc]ache-[Cc]ontrol:[[:space:]]*//')
    if echo "$CACHE_HDR" | grep -qi 'immutable'; then
        ok "Asset hashé ($JS_BUNDLE) : Cache-Control = $CACHE_HDR"
    else
        warn "Asset hashé sans header 'immutable' : Cache-Control = $CACHE_HDR (cosmétique, non bloquant)"
    fi
else
    warn "Pas de bundle main.HASH.js détecté dans index.html (test cache skipped)"
fi

# index.html DOIT être no-cache
INDEX_CACHE=$(curl -sI --max-time 5 "http://localhost:$PORT/index.html" \
    | grep -i '^cache-control:' | tr -d '\r' | sed 's/^[Cc]ache-[Cc]ontrol:[[:space:]]*//')
if echo "$INDEX_CACHE" | grep -qi 'no-store\|no-cache'; then
    ok "index.html : Cache-Control = $INDEX_CACHE (no-cache OK)"
else
    err "index.html devrait avoir 'no-cache' / 'no-store' : reçu '$INDEX_CACHE'"
    exit 1
fi

# ─── Phase 8 : reverse-proxy /api/* actif ───────────────────────────────────
banner "Phase 8/10 — Reverse-proxy /api/* actif"

PROXY_RESP=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 8 "http://localhost:$PORT/api/patients")

if "$BACKEND_UP"; then
    # Backend up → /api/patients sans token doit retourner 401 (auth requise)
    if [[ "$PROXY_RESP" == "401" ]]; then
        ok "/api/patients : 401 (proxy → backend OK, token manquant — comportement attendu)"
    else
        err "/api/patients : HTTP $PROXY_RESP (attendu 401 quand backend up sans token)"
        exit 1
    fi
else
    # Backend down → /api/patients doit retourner 502 (proxy ne peut pas joindre upstream)
    # ⚠️ Peut aussi être 504 (timeout DNS) ou 500 (resolver indisponible)
    case "$PROXY_RESP" in
        502|504|500)
            ok "/api/patients : $PROXY_RESP (proxy actif, upstream non joignable — comportement attendu)" ;;
        *)
            err "/api/patients : HTTP $PROXY_RESP (attendu 502/504/500 quand backend down)"
            exit 1 ;;
    esac
fi

# ─── Phase 9 : (optionnel) full E2E avec token si backend up ────────────────
banner "Phase 9/10 — Full E2E via reverse-proxy (si backend up)"

if "$BACKEND_UP" && [[ -x "$SCRIPT_DIR/get-token.sh" ]]; then
    TOKEN=$("$SCRIPT_DIR/get-token.sh" reception 2>/dev/null | tail -1)
    if [[ -n "$TOKEN" && ${#TOKEN} -gt 100 ]]; then
        # Test : appel via le reverse-proxy Nginx (port frontend), pas direct au backend
        AUTH_RESP=$(curl -sS -o /dev/null -w '%{http_code}' \
            --max-time 8 \
            -H "Authorization: Bearer $TOKEN" \
            "http://localhost:$PORT/api/patients")
        if [[ "$AUTH_RESP" == "200" ]]; then
            ok "GET /api/patients via reverse-proxy + token : 200 — pipeline frontend→backend OK"
        else
            err "GET /api/patients via reverse-proxy + token : HTTP $AUTH_RESP (attendu 200)"
            exit 1
        fi
    else
        warn "get-token.sh n'a pas retourné de JWT — phase 9 skipped"
    fi
else
    info "Backend non up — phase 9 skipped (lance d'abord e2e-smoke-backend.sh --keep pour la couverture complète)"
fi

# ─── Phase 10 : récap ───────────────────────────────────────────────────────
banner "Phase 10/10 — Récap"

ok "Smoke E2E frontend : SUCCESS"
if "$BACKEND_UP"; then
    info "Mode : full stack (backend détecté + frontend testé jusqu'au token through reverse-proxy)"
else
    info "Mode : frontend standalone (backend pas démarré — couverture partielle, mais isolation correcte)"
fi
if "$KEEP"; then
    info "Stack frontend laissé up (--keep). Pour arrêter :"
    info "    cd $COMPOSE_DIR && docker compose down"
fi
exit 0
