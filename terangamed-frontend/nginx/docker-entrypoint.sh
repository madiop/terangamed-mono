#!/bin/sh
# ─────────────────────────────────────────────────────────────────────────────
# TerangaMed — Frontend entrypoint
#
# Plugué dans /docker-entrypoint.d/ (chargé automatiquement par l'image
# nginx:alpine officielle AVANT le démarrage de Nginx).
#
# Rôle : substituer les variables ${API_BACKEND_URL} (et autres) dans le
# template /etc/nginx/templates/default.conf.template, et écrire le résultat
# dans /etc/nginx/conf.d/default.conf — la config effective de Nginx.
#
# Ainsi, la même image Docker peut tourner :
#   - en local avec API_BACKEND_URL=http://api-gateway:8080 (DNS Docker)
#   - en staging avec API_BACKEND_URL=https://api.staging.terangamed.example
#   - en prod   avec API_BACKEND_URL=https://api.terangamed.production.com
# ... sans rebuild.
#
# ⚠️ envsubst ne substitue QUE les variables explicitement listées en arg
#    (sinon Nginx récupère des $variables Nginx vides — bug subtil).
# ─────────────────────────────────────────────────────────────────────────────

set -eu

: "${API_BACKEND_URL:=http://api-gateway:8080}"
: "${KEYCLOAK_ISSUER:=http://localhost:8180/realms/terangamed}"
: "${API_BASE_URL:=}"

# ─── 1) Rendu de la config Nginx (reverse-proxy /api/*) ─────────────────────
# Template stocké hors de /etc/nginx/templates/ pour ne PAS être traité par le
# script officiel /docker-entrypoint.d/20-envsubst-on-templates.sh (qui
# substituerait trop de variables et casserait $host, $remote_addr, etc.).
NGINX_TEMPLATE=/etc/nginx/templates-custom/default.conf.template
NGINX_TARGET=/etc/nginx/conf.d/default.conf

if [ ! -f "$NGINX_TEMPLATE" ]; then
    echo "[entrypoint] ERROR: template absent: $NGINX_TEMPLATE" >&2
    exit 1
fi

echo "[entrypoint] Rendering Nginx config..."
echo "[entrypoint]   API_BACKEND_URL=${API_BACKEND_URL}"

# Liste explicite des variables à substituer (cf. ⚠️ ci-dessus).
# Si on en ajoute une nouvelle dans le template, l'ajouter ici aussi.
envsubst '${API_BACKEND_URL}' < "$NGINX_TEMPLATE" > "$NGINX_TARGET"

# Sanity check : la valeur de $api_backend doit être une URL http(s) valide.
# On vérifie que la directive `set $api_backend "..."` contient bien une URL —
# preuve que ${API_BACKEND_URL} a été substitué correctement.
# NB : on ne cherche PAS `proxy_pass http` car la config utilise une variable
# Nginx (`proxy_pass $api_backend`) pour forcer la résolution DNS au runtime.
if ! grep -qE 'set[[:space:]]+\$api_backend[[:space:]]+"https?://' "$NGINX_TARGET"; then
    echo "[entrypoint] ERROR: API_BACKEND_URL n'a pas été substitué correctement dans $NGINX_TARGET" >&2
    echo "[entrypoint]        Vérifie que la variable d'env API_BACKEND_URL est bien une URL valide" >&2
    echo "[entrypoint]        (actuelle: ${API_BACKEND_URL})" >&2
    grep -nE 'set[[:space:]]+\$api_backend' "$NGINX_TARGET" >&2 || true
    exit 1
fi

# ─── 2) Rendu du runtime-config.json (Keycloak / API base URL côté SPA) ─────
# Le SPA fetch ce fichier AVANT le bootstrap Angular (cf. src/main.ts) pour
# obtenir les URLs externes — ainsi la même image Docker tourne en local,
# staging, ou prod sans rebuild Angular.
RUNTIME_TEMPLATE=/etc/teranga-templates/runtime-config.json.template
RUNTIME_TARGET=/usr/share/nginx/html/assets/runtime-config.json

if [ ! -f "$RUNTIME_TEMPLATE" ]; then
    echo "[entrypoint] ERROR: template absent: $RUNTIME_TEMPLATE" >&2
    exit 1
fi

mkdir -p "$(dirname "$RUNTIME_TARGET")"

echo "[entrypoint] Rendering SPA runtime config..."
echo "[entrypoint]   KEYCLOAK_ISSUER=${KEYCLOAK_ISSUER}"
echo "[entrypoint]   API_BASE_URL=${API_BASE_URL:-<empty>}"

envsubst '${KEYCLOAK_ISSUER} ${API_BASE_URL}' < "$RUNTIME_TEMPLATE" > "$RUNTIME_TARGET"

# Sanity check : KEYCLOAK_ISSUER doit ressembler à une URL OIDC valide.
if ! grep -qE '"keycloakIssuer"[[:space:]]*:[[:space:]]*"https?://[^"]+/realms/[^"]+"' "$RUNTIME_TARGET"; then
    echo "[entrypoint] ERROR: KEYCLOAK_ISSUER invalide dans $RUNTIME_TARGET" >&2
    echo "[entrypoint]        Attendu : http(s)://<host>/realms/<realm>" >&2
    echo "[entrypoint]        Reçu    : ${KEYCLOAK_ISSUER}" >&2
    cat "$RUNTIME_TARGET" >&2
    exit 1
fi

echo "[entrypoint] Config rendered OK — Nginx démarre."
