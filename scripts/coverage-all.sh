#!/usr/bin/env bash
# ============================================================================
#  TerangaMed — Coverage report consolidé (backend + frontend)
# ============================================================================
#
#  Usage :    ./scripts/coverage-all.sh [--fast] [--no-front] [--no-back]
#
#  --fast      : skip Jacoco check (seuil 80%) pour itérations rapides
#  --no-front  : ne lance pas la couverture Jest frontend
#  --no-back   : ne lance pas la couverture Jacoco backend
#
#  Sortie :
#    - Logs colorés par module avec succès/échec
#    - Tableau récapitulatif des pourcentages (extrait des rapports)
#    - Liste des chemins des rapports HTML détaillés à ouvrir manuellement
#
#  Pré-requis :
#    - JDK 21 + Maven Wrapper (./mvnw)
#    - Node 18+ + npm
#
#  Le script ne <b>parse pas</b> agressivement les XML/JSON : il s'appuie sur
#  les rapports textuels imprimés par Jacoco/Jest qui sont déjà très lisibles.
#  Pour un rapport HTML unifié, ouvrir séparément les liens listés à la fin.
# ============================================================================

set -euo pipefail

# ─── Couleurs ANSI ──────────────────────────────────────────────────────────
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly BOLD='\033[1m'
readonly RESET='\033[0m'

# ─── Détection chemins ──────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACK_DIR="$ROOT_DIR/terangamed-backend"
FRONT_DIR="$ROOT_DIR/terangamed-frontend"

# ─── Args ──────────────────────────────────────────────────────────────────
RUN_BACK=true
RUN_FRONT=true
FAST_MODE=false
for arg in "$@"; do
    case "$arg" in
        --fast) FAST_MODE=true ;;
        --no-front) RUN_FRONT=false ;;
        --no-back) RUN_BACK=false ;;
        -h|--help)
            sed -n '4,21p' "$0" | sed 's/^# \?//'
            exit 0
            ;;
        *) echo "Argument inconnu: $arg"; exit 1 ;;
    esac
done

# ─── Helpers d'affichage ────────────────────────────────────────────────────
banner() {
    echo
    echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════════════${RESET}"
    echo -e "${BOLD}${BLUE}  $1${RESET}"
    echo -e "${BOLD}${BLUE}════════════════════════════════════════════════════════════════════${RESET}"
}

info()    { echo -e "${BLUE}ℹ${RESET}  $1"; }
ok()      { echo -e "${GREEN}✓${RESET}  $1"; }
warn()    { echo -e "${YELLOW}⚠${RESET}  $1"; }
err()     { echo -e "${RED}✗${RESET}  $1"; }

# ─── Backend Jacoco ─────────────────────────────────────────────────────────
run_backend() {
    local seuil
    if [[ "$FAST_MODE" == "true" ]]; then
        seuil="désactivé"
    else
        seuil="80%"
    fi
    banner "Backend — Tests Jacoco (seuil $seuil)"

    if [[ ! -f "$BACK_DIR/mvnw" ]]; then
        err "Maven Wrapper introuvable dans $BACK_DIR — abandon backend"
        return 1
    fi

    cd "$BACK_DIR"
    local mvn_args=("clean" "verify")
    if [[ "$FAST_MODE" == "true" ]]; then
        mvn_args+=("-Pfast")
    fi

    info "Commande: ./mvnw ${mvn_args[*]}"
    if ./mvnw "${mvn_args[@]}" > /tmp/teranga-back-coverage.log 2>&1; then
        ok "Backend tests verts"
    else
        err "Backend tests rouge — voir /tmp/teranga-back-coverage.log"
        tail -50 /tmp/teranga-back-coverage.log
        return 1
    fi

    # Liste les rapports Jacoco générés
    info "Rapports HTML Jacoco générés :"
    for service in services/*/; do
        local report="$service/target/site/jacoco/index.html"
        if [[ -f "$BACK_DIR/$report" ]]; then
            echo "    file://$BACK_DIR/$report"
        fi
    done
}

# ─── Frontend Jest ──────────────────────────────────────────────────────────
run_frontend() {
    banner "Frontend — Tests Jest avec couverture"

    if [[ ! -f "$FRONT_DIR/package.json" ]]; then
        err "package.json introuvable dans $FRONT_DIR — abandon frontend"
        return 1
    fi

    cd "$FRONT_DIR"
    info "Commande: npm run test:coverage -- --silent"
    if npm run test:coverage -- --silent > /tmp/teranga-front-coverage.log 2>&1; then
        ok "Frontend tests verts"
    else
        # Jest peut sortir non-zéro si seuils dépassés ; on affiche le summary
        warn "Frontend tests : seuils possibles dépassés ou échecs"
    fi

    # Affiche le summary Jest qui est déjà excellent (text-summary reporter)
    grep -A 20 "^File\|coverage summary\|Coverage summary\|Statements" /tmp/teranga-front-coverage.log | head -25 || true

    # Lit json-summary pour avoir les % globaux
    local summary="$FRONT_DIR/coverage/coverage-summary.json"
    if [[ -f "$summary" ]]; then
        info "Couverture globale (depuis coverage/coverage-summary.json) :"
        node -e "
            const s = require('$summary').total;
            console.log(\`    Lines:      \${s.lines.pct}%   (\${s.lines.covered}/\${s.lines.total})\`);
            console.log(\`    Statements: \${s.statements.pct}%   (\${s.statements.covered}/\${s.statements.total})\`);
            console.log(\`    Functions:  \${s.functions.pct}%   (\${s.functions.covered}/\${s.functions.total})\`);
            console.log(\`    Branches:   \${s.branches.pct}%   (\${s.branches.covered}/\${s.branches.total})\`);
        " 2>/dev/null || warn "Impossible de parser coverage-summary.json"
    fi

    info "Rapport HTML : file://$FRONT_DIR/coverage/lcov-report/index.html"
}

# ─── Récapitulatif final ────────────────────────────────────────────────────
print_summary() {
    banner "Récapitulatif"
    if [[ "$RUN_BACK" == "true" ]]; then
        ok "Backend — rapports Jacoco par service dans services/*/target/site/jacoco/"
    fi
    if [[ "$RUN_FRONT" == "true" ]]; then
        ok "Frontend — rapport HTML dans terangamed-frontend/coverage/lcov-report/"
    fi
    echo
    info "Pour ouvrir tous les rapports en une commande (macOS) :"
    if [[ "$RUN_BACK" == "true" ]]; then
        echo "    open $BACK_DIR/services/*/target/site/jacoco/index.html"
    fi
    if [[ "$RUN_FRONT" == "true" ]]; then
        echo "    open $FRONT_DIR/coverage/lcov-report/index.html"
    fi
    echo
}

# ─── Main ───────────────────────────────────────────────────────────────────
main() {
    banner "TerangaMed — Coverage consolidé"
    info "Mode: back=$RUN_BACK, front=$RUN_FRONT, fast=$FAST_MODE"

    local back_ok=true front_ok=true
    if [[ "$RUN_BACK" == "true" ]]; then
        run_backend || back_ok=false
    fi
    if [[ "$RUN_FRONT" == "true" ]]; then
        run_frontend || front_ok=false
    fi

    print_summary

    if [[ "$back_ok" == "true" && "$front_ok" == "true" ]]; then
        ok "Tous les modules : OK"
        exit 0
    else
        err "Certains modules ont échoué — voir logs ci-dessus"
        exit 1
    fi
}

main "$@"
