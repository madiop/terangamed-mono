# ─────────────────────────────────────────────────────────────────────────────
# TerangaMed — Frontend Angular 17 + Nginx (port 4200 / interne 80)
#
# Build :
#   docker build -f Dockerfile -t terangamed/frontend:1.0.0 .
#
# Run standalone (sans backend) :
#   docker run --rm -p 4200:80 terangamed/frontend:1.0.0
#   → Bundle servi sur http://localhost:4200, mais /api/* renverra 502
#     (Nginx ne peut pas joindre l'upstream backend).
#
# Run avec backend dans le réseau Docker `terangamed-net` :
#   docker network create terangamed-net  # si pas déjà créé par le backend
#   docker run --rm -p 4200:80 \
#     --network terangamed-net \
#     terangamed/frontend:1.0.0
#   → Nginx reverse-proxy /api/* vers http://api-gateway:8080
#
# Run contre un backend remote (staging, prod) :
#   docker run --rm -p 4200:80 \
#     -e API_BACKEND_URL=https://api.terangamed.production.com \
#     terangamed/frontend:1.0.0
#
# ⚠️  Keycloak : l'URL `keycloak.issuer` est hardcodée au moment du build
#     Angular (cf. src/environments/environment.prod.ts). Pour la surcharger
#     côté runtime sans rebuilder, voir doc 11-DOCKER-DEPLOYMENT.md §"Surcharge
#     Keycloak en runtime".
# ─────────────────────────────────────────────────────────────────────────────

# ─── Stage 1 : build Angular (Node 20 alpine) ───────────────────────────────
FROM node:20-alpine AS builder
WORKDIR /workspace

# Copie d'abord package*.json pour profiter du cache npm install / ci
# (les fichiers source changent plus souvent que les deps)
COPY package.json package-lock.json* ./

# `npm ci` (et non `npm install`) :
#  - Reproductible : utilise package-lock.json à la lettre
#  - Plus rapide (pas de résolution de versions)
#  - Échoue si package-lock manque ou est obsolète → garantit la cohérence
RUN npm ci --no-audit --no-fund --prefer-offline

# Copie des sources Angular (séparé du package.json pour le cache)
COPY angular.json tsconfig.json tsconfig.app.json ./
COPY src ./src

# Build de production — fileReplacements applique environment.prod.ts.
# `--output-mode=static` n'est PAS utilisé (pas de SSR/prerender requis ici).
RUN npm run build:prod -- --output-path=dist


# ─── Stage 2 : runtime Nginx alpine ──────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

# envsubst utilisé par l'entrypoint pour injecter API_BACKEND_URL au démarrage
RUN apk add --no-cache gettext

# Suppression de la conf Nginx par défaut (on utilise la nôtre)
RUN rm -f /etc/nginx/conf.d/default.conf

# Copie du template DANS UN DOSSIER NON STANDARD :
# /etc/nginx/templates/ est traité automatiquement par le script officiel
# /docker-entrypoint.d/20-envsubst-on-templates.sh qui peut substituer trop de
# variables (notamment écraser $host, $remote_addr Nginx natifs avec des
# valeurs vides issues de l'env). On préfère gérer le rendu nous-mêmes via
# notre script 40-render-nginx-conf.sh qui filtre explicitement.
COPY nginx/default.conf.template /etc/nginx/templates-custom/default.conf.template
# Template du runtime-config.json — substitué par l'entrypoint au démarrage
# du container pour injecter les URLs Keycloak / API sans rebuild.
COPY nginx/runtime-config.json.template /etc/teranga-templates/runtime-config.json.template
# `--chmod=0755` (Docker 20.10+) garantit le bit +x AU MOMENT du COPY
# (atomique). Évite que `RUN chmod +x` post-COPY soit cassé par un cache de
# layer. Sans le bit +x, le master script /docker-entrypoint.sh ignore
# silencieusement notre script → /etc/nginx/conf.d/default.conf jamais rendu
# → Nginx démarre sans server block sur :80 → "Connection refused".
COPY --chmod=0755 nginx/docker-entrypoint.sh /docker-entrypoint.d/40-render-nginx-conf.sh

# Copie du bundle Angular depuis le builder.
# Avec @angular-devkit/build-angular:application (Angular 17+), la sortie est
# `<outputPath>/browser/` pour le SPA. On y pointe directement.
COPY --from=builder /workspace/dist/browser /usr/share/nginx/html

# Variables d'env runtime (avec defaults raisonnables pour le mode "même
# réseau Docker que le backend"). Surchargeables via `docker run -e ...`
# ou `environment:` dans docker-compose.yml.
ENV API_BACKEND_URL=http://api-gateway:8080
# URLs injectées au démarrage dans /assets/runtime-config.json (cf. entrypoint).
# Defaults = mode local-Docker sur la machine du dev (Keycloak exposé sur
# localhost:8180 par le stack backend). En prod, surcharger via docker-compose
# ou `-e KEYCLOAK_ISSUER=https://auth.example.com/realms/terangamed`.
ENV KEYCLOAK_ISSUER=http://localhost:8180/realms/terangamed
ENV API_BASE_URL=""

# Healthcheck — Nginx répond sur / (le bundle index.html doit être servi)
# ⚠️ DEUX PIÈGES corrigés ici :
#  1. `nginx:alpine` utilise busybox wget qui NE SUPPORTE PAS `--no-verbose`
#     ni `--tries=1` (options GNU wget). Options 100% compatibles busybox :
#     `-q --spider`.
#  2. On utilise `127.0.0.1` (et NON `localhost`) parce que sur Alpine, le
#     resolver hosts peut renvoyer `::1` (IPv6) en priorité. Or notre directive
#     `listen 80;` ne couvre QUE IPv4 (0.0.0.0:80). busybox wget tente IPv6
#     et échoue avec "Connection refused" sans fallback IPv4 → healthcheck
#     marqué unhealthy alors que Nginx fonctionne parfaitement en IPv4.
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=5 \
    CMD wget -q --spider http://127.0.0.1/ || exit 1

# Port interne — exposé en 4200 sur l'hôte via docker-compose
EXPOSE 80

# L'image officielle nginx:alpine a un entrypoint qui exécute /docker-entrypoint.d/*.sh
# avant de lancer nginx. Notre script y est plugué automatiquement.
# Pas besoin de redéfinir CMD/ENTRYPOINT.
