# TerangaMed — Déploiement Docker (étape 10B)

Guide opérationnel pour déployer TerangaMed via Docker Compose en environnement
local (dev/QA) ou containérisé (staging). Cette doc finalise l'étape **10B —
Dockerisation finale**.

> 🎯 **Principe directeur** : le **backend** et le **frontend** sont deux stacks
> Docker Compose **totalement indépendants**. Chaque stack peut être démarré,
> arrêté, redéployé sans toucher à l'autre. Ils communiquent via un réseau
> Docker partagé (`terangamed-net`) géré hors stack.

## 1. Vue d'ensemble

### 1.1 Architecture des 2 stacks

```
┌──────────────────────────────── Hôte (Mac / Linux) ────────────────────────────┐
│                                                                                │
│  ┌────────── Réseau Docker `terangamed-net` (external) ──────────────────┐    │
│  │                                                                       │    │
│  │  ┌─── Stack BACKEND (terangamed-backend/docker/) ────────────────┐    │    │
│  │  │                                                               │    │    │
│  │  │  • postgres:5432       • config-server:8888                   │    │    │
│  │  │  • keycloak:8180       • discovery-server:8761 (Eureka)       │    │    │
│  │  │  • kafka:9092          • api-gateway:8080                     │    │    │
│  │  │  • schema-registry     • patient-service:8081                 │    │    │
│  │  │  • kafka-ui:8090       • doctor-service:8082                  │    │    │
│  │  │  • kafka-init (job)    • appointment-service:8083             │    │    │
│  │  │                        • medical-record-service:8084          │    │    │
│  │  │                        • notification-service:8086            │    │    │
│  │  └───────────────────────────────────────────────────────────────┘    │    │
│  │                                                                       │    │
│  │  ┌─── Stack FRONTEND (terangamed-frontend/docker/) ──────────────┐    │    │
│  │  │  • frontend (Nginx + Angular bundle) → port 4200              │    │    │
│  │  │    reverse-proxy /api/* → api-gateway:8080                    │    │    │
│  │  └───────────────────────────────────────────────────────────────┘    │    │
│  └───────────────────────────────────────────────────────────────────────┘    │
│                                                                                │
└────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Philosophie

- **Indépendance des cycles de vie** : `docker compose down` du backend ne tue PAS le frontend (il continue à servir le bundle, juste `/api/*` retourne 502 jusqu'au retour du backend).
- **Réseau externe** : `terangamed-net` n'est créé ni détruit par les stacks. Il est géré par les scripts smoke (création idempotente) ou manuellement.
- **Build once, deploy anywhere** : `API_BACKEND_URL` configurable au runtime côté frontend → une seule image pour dev/staging/prod.
- **Sécurité par défaut** : ports bindés sur `127.0.0.1` (pas exposition LAN sauf intention explicite).

## 2. Pré-requis

| Composant | Version min | Vérification |
|-----------|------------|--------------|
| Docker Engine | 24.0+ | `docker version` |
| Docker Compose v2 | intégré | `docker compose version` |
| RAM disponible | 6 Go | recommandé pour la stack complète (backend + frontend) |
| Disque libre | 5 Go | images + volumes |
| Outils CLI | curl, jq | pour les smoke scripts |
| Ports libres | 4200, 5432, 8080-8086, 8090, 8180, 8761, 8888 | `lsof -nP -iTCP -sTCP:LISTEN -P` |

## 3. Démarrage — les 3 modes

### 3.1 Mode 1 — Backend uniquement

```bash
cd terangamed-backend/docker
docker network create terangamed-net 2>/dev/null    # idempotent
docker compose up -d
```

→ Démarre **12 services** backend. Aucune dépendance au frontend.

### 3.2 Mode 2 — Frontend uniquement

```bash
docker network create terangamed-net 2>/dev/null    # idempotent
cd terangamed-frontend/docker
docker compose up -d
```

→ Démarre Nginx + bundle Angular sur `http://localhost:4200`. **Sans backend up**, les appels `/api/*` renvoient `502 Bad Gateway` (preuve que le reverse-proxy est actif).

### 3.3 Mode 3 — Full stack (les 2 en parallèle)

```bash
docker network create terangamed-net 2>/dev/null

# Lancer dans l'ordre que tu veux — les 2 stacks sont indépendants
cd terangamed-backend/docker && docker compose up -d
cd ../../terangamed-frontend/docker && docker compose up -d
```

Ou via les scripts smoke (recommandé) :

```bash
cd ../..
./scripts/e2e-smoke-backend.sh --keep    # backend up + validation E2E + stack laissé up
./scripts/e2e-smoke-frontend.sh          # détecte backend, valide jusqu'au full E2E à travers Nginx
```

## 4. Inventaire backend (12 services)

### 4.1 Infrastructure externe

| Service | Port hôte | Healthcheck | Rôle |
|---------|-----------|-------------|------|
| `postgres` | 5432 | `pg_isready` | Base partagée (7 bases : keycloak, patient_db, doctor_db, appointment_db, medical_record_db, notification_db, billing_db) |
| `keycloak` | 8180 | `/health/ready` | OAuth2/OIDC provider — realm `terangamed` importé au boot |
| `kafka` | 29092 | `kafka-broker-api-versions` | Broker KRaft (sans Zookeeper) — single broker dev |
| `schema-registry` | 8085 | `/subjects` | Confluent Schema Registry pour les schémas Avro |
| `kafka-ui` | 8090 | (sans healthcheck) | UI Provectus pour debug Kafka |
| `kafka-init` | — | (job one-shot) | Crée les 4 topics primaires Kafka au boot |

### 4.2 Spring Cloud infra

| Service | Port | Healthcheck | Rôle |
|---------|------|-------------|------|
| `config-server` | 8888 | `/actuator/health` | Spring Cloud Config — config-repo natif |
| `discovery-server` | 8761 | `/actuator/health` | Eureka — service registry |
| `api-gateway` | 8080 | `/actuator/health` | Spring Cloud Gateway — routage + JWT validation |

### 4.3 Microservices métier

| Service | Port | Healthcheck | Rôle |
|---------|------|-------------|------|
| `patient-service` | 8081 | `/actuator/health` | CRUD patients |
| `doctor-service` | 8082 | `/actuator/health` | CRUD médecins + état (active/inactive) |
| `appointment-service` | 8083 | `/actuator/health` | RDV + Feign vers patient/doctor + Resilience4j |
| `medical-record-service` | 8084 | `/actuator/health` | Dossier médical + consultation + ordonnance |
| `notification-service` | 8086 | `/actuator/health` | Consumer Kafka multi-topics + REST history |

## 5. Inventaire frontend (1 service)

| Service | Port hôte | Image | Healthcheck | Rôle |
|---------|-----------|-------|-------------|------|
| `frontend` | 4200 → 80 | `terangamed/frontend:1.0.0` | `wget -q --spider http://127.0.0.1/` | Nginx servant le bundle Angular + reverse-proxy `/api/*` → `api-gateway:8080` |

### 5.1 Image Docker — détails

**Multi-stage build** :
- **Stage 1** (`node:20-alpine`) : `npm ci` puis `ng build --configuration production` → output `dist/browser/`
- **Stage 2** (`nginx:1.27-alpine`) : copie le bundle, ajoute config Nginx templatée, healthcheck

**Image finale ~50 Mo** (sans le builder Node ni node_modules).

### 5.2 Pattern envsubst runtime

Le template `nginx/default.conf.template` contient `${API_BACKEND_URL}` qui est substitué au démarrage du container par `/docker-entrypoint.d/40-render-nginx-conf.sh`. Cela permet de réutiliser **la même image Docker** en dev/staging/prod :

```bash
# Dev local (default — pointe vers le réseau Docker `terangamed-net`)
docker run terangamed/frontend:1.0.0
# → API_BACKEND_URL=http://api-gateway:8080 (DNS Docker)

# Staging
docker run -e API_BACKEND_URL=https://api.staging.terangamed.example terangamed/frontend:1.0.0

# Production
docker run -e API_BACKEND_URL=https://api.terangamed.production.com terangamed/frontend:1.0.0
```

## 6. Variables d'environnement

### 6.1 Backend — `terangamed-backend/docker/.env.example`

| Variable | Défaut | Description |
|----------|--------|-------------|
| `POSTGRES_USER` | `terangamed` | User PostgreSQL (`-U terangamed` pour `psql`) |
| `POSTGRES_PASSWORD` | `terangamed` | À changer en prod |
| `KEYCLOAK_ADMIN` | `admin` | User admin Keycloak |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | À changer en prod |
| `CONFIG_SERVER_USER` | `config` | Basic auth Config Server |
| `CONFIG_SERVER_PASSWORD` | `config` | idem |
| `EUREKA_USER` | `eureka` | Basic auth Eureka |
| `EUREKA_PASSWORD` | `eureka` | idem |
| `KAFKA_CLUSTER_ID` | (fixe) | Cluster ID KRaft — **NE PAS CHANGER** après premier boot |
| `KAFKA_HOST_PORT` | 29092 | Port broker Kafka exposé sur l'hôte |
| `*_BIND_HOST` | `127.0.0.1` | Bind interface (localhost only par défaut) |

### 6.2 Frontend — `terangamed-frontend/docker/.env.example`

| Variable | Défaut | Description |
|----------|--------|-------------|
| `API_BACKEND_URL` | `http://api-gateway:8080` | URL de l'api-gateway (DNS Docker en mode local) |
| `FRONTEND_HOST_PORT` | 4200 | Port host exposé (cohérent avec `webOrigins` Keycloak) |
| `FRONTEND_BIND_HOST` | `127.0.0.1` | Bind interface |

### 6.3 Pour usage en prod

```bash
cd terangamed-backend/docker
cp .env.example .env
# Éditer .env : changer les password admins, regenerate KAFKA_CLUSTER_ID si fresh deploy
docker compose up -d
```

## 7. Smoke E2E — validation automatique

### 7.1 Smoke backend (`scripts/e2e-smoke-backend.sh`)

Cycle complet en ~3-10 min (premier run avec build = 10 min, runs ultérieurs cached ~3 min) :

```bash
./scripts/e2e-smoke-backend.sh [--build] [--keep] [--quiet] [--no-teardown-on-fail]
```

**9 phases** : pré-checks → docker compose up → wait infra healthy → wait Spring infra → Eureka registration → wait 5 microservices healthy → swagger-smoke → auth token → POST patient + GET liste assert.

Exit code 0 = full pipeline OK depuis Postgres jusqu'au CRUD applicatif.

### 7.2 Smoke frontend (`scripts/e2e-smoke-frontend.sh`)

```bash
./scripts/e2e-smoke-frontend.sh [--build] [--keep] [--quiet] [--no-teardown-on-fail]
```

**10 phases** auto-adaptatives :
- Si backend détecté (`api-gateway` répond sur :8080) → mode full stack, test E2E à travers le reverse-proxy Nginx avec token
- Si backend pas détecté → mode standalone, test bundle SPA + 502 sur reverse-proxy (preuve d'activité)

### 7.3 Smoke Swagger seul (`scripts/swagger-smoke.sh`)

```bash
./scripts/swagger-smoke.sh [--service <name>] [--verbose]
```

Vérifie en <5 s que les 5 microservices exposent `/v3/api-docs` + scheme `bearer-auth`. Réutilisé en interne par `e2e-smoke-backend.sh`.

### 7.4 Helper auth (`scripts/get-token.sh`)

```bash
./scripts/get-token.sh reception     # par défaut password reception/reception
./scripts/get-token.sh admin
./scripts/get-token.sh dr.martin --decode
```

Imprime un access_token Keycloak + copie dans le clipboard (`pbcopy` / `xclip`).

## 8. Procédures opérationnelles

### 8.1 Logs

```bash
# Logs en temps réel d'un service
cd terangamed-backend/docker
docker compose logs -f patient-service

# Logs 100 dernières lignes
docker compose logs --tail 100 medical-record-service

# Logs de tous les services
docker compose logs -f
```

### 8.2 Restart d'un seul service

```bash
cd terangamed-backend/docker
docker compose restart patient-service       # SIGTERM + relance, garde le volume
docker compose up -d --force-recreate doctor-service   # détruit + recrée le container
```

### 8.3 Rebuild d'une image après modif code

```bash
cd terangamed-backend/docker
docker compose build patient-service         # rebuild juste cette image
docker compose up -d patient-service         # restart avec la nouvelle image
```

⚠️ **Bytecode obsolète** : après modif d'un `@Configuration` Spring, ne pas oublier `docker compose build` — sinon le container démarre sur l'image précédente (bug observé sur OpenApiConfig en mai 2026, cf. règle mémoire).

### 8.4 Reset complet (efface volumes)

```bash
cd terangamed-backend/docker
docker compose down -v                       # ⚠️ supprime postgres-data + kafka-data
docker network rm terangamed-net 2>/dev/null
# Repartir de zéro :
docker network create terangamed-net
docker compose up -d
```

### 8.5 Reset du realm Keycloak sans toucher au volume Postgres

```bash
./scripts/reset-keycloak-realm.sh
```

Utile quand on a modifié `terangamed-realm.json` après le premier boot (les changements ne sont pas pris automatiquement). Le script DELETE/POST le realm via l'API admin sans toucher au volume Postgres → préserve les seeds dev des autres services.

## 9. Troubleshooting

### 9.1 `network terangamed-net declared as external, but could not be found`

Le réseau Docker n'existe pas. Création idempotente :
```bash
docker network create terangamed-net
```

### 9.2 `medical-record-service injoignable` au smoke phase 6

Eureka registration ≠ Web context prêt. Le smoke E2E gère ça via `wait_compose_healthy` entre Eureka et swagger-smoke. Si ça plante quand même, augmenter le timeout dans `e2e-smoke-backend.sh` (actuellement 120s).

### 9.3 `PATIENT_SERVICE_UNAVAILABLE` lors d'un POST RDV

Cross-service Feign (appointment → patient/doctor). Causes :
- Eureka pas registered → vérifier `curl -u eureka:eureka http://localhost:8761/eureka/apps`
- patient-service pas healthy → `docker compose ps`

### 9.4 `UNKNOWN_TOPIC_OR_PARTITION` (notification-service)

Topics retry/DLT manquants. Avec le fix de mai 2026, `@RetryableTopic(autoCreateTopics="true")` les crée automatiquement au boot. Si problème persistant, vérifier le KafkaAdmin bean :
```bash
docker compose logs notification-service | grep -i kafkaAdmin
```

### 9.5 Healthcheck `unhealthy` sur l'image frontend

Quasi-toujours dû au pattern wget busybox sur `localhost` (résolution IPv6). Le healthcheck utilise `127.0.0.1` explicite ; si encore un problème, vérifier que le bundle `index.html` existe :
```bash
docker exec terangamed-frontend ls -la /usr/share/nginx/html/
```

### 9.6 `wget --no-verbose: unrecognized option`

L'image utilise busybox wget (compatible POSIX uniquement). Options supportées : `-q --spider URL` (et non `--no-verbose --tries=1`). Tous les Dockerfile ont été corrigés en mai 2026.

### 9.7 401 sur tous les endpoints Swagger malgré Authorize

Bug du flow OAuth2 Password sur Swagger UI 5.13 (bundlé springdoc 2.5.0). Solution : utiliser le scheme `bearer-auth` avec un token obtenu via `./scripts/get-token.sh <user>`. Cf. `10C5-VALIDATION-CHECKLIST.md` §2.

## 10. Production-readiness — gap analysis

Cette stack Docker Compose est **prête pour dev/QA/staging**. Pour aller en **prod réelle**, il reste à :

| Gap | Priorité | Effort estimé |
|-----|----------|---------------|
| TLS/HTTPS partout (Nginx, gateway, Keycloak) — Let's Encrypt ou cert provider | Haute | 1 j |
| Secrets management (Vault, Docker secrets, AWS SM) — actuellement passwords en `.env` | Haute | 1 j |
| Replication factor Kafka ≥ 2 (actuellement 1) — exige cluster multi-broker | Haute | 0.5 j |
| Backup automatique Postgres (`pg_dump` planifié + rétention) | Haute | 0.5 j |
| Audience JWT validation stricte (`spring.security.oauth2.resourceserver.jwt.audiences`) | Moyenne | 2 h |
| Migration Compose → Kubernetes (Helm charts) ou Swarm | Moyenne | 3-5 j |
| Logs centralisés (Loki/ELK + parsers JSON Logstash) | Moyenne | 1 j |
| Métriques (Prometheus scraping `/actuator/prometheus`) + dashboards Grafana | Moyenne | 1 j |
| Tracing distribué (OpenTelemetry → Jaeger/Tempo) | Basse | 1 j |
| Tuning JVM par service (heap, GC) selon le profil de charge | Basse | 0.5 j |
| Image scanning (Trivy, Snyk) en CI | Basse | 2 h |

## 11. Annexes

### 11.1 Fichiers clé

```
terangamed-backend/
├── docker/
│   ├── docker-compose.yml          ← stack backend (12 services)
│   ├── .env.example                ← variables backend
│   ├── keycloak/realm-export.json  ← realm `terangamed` (auto-import)
│   └── postgres/01-create-databases.sql  ← création bases au 1er boot
├── infrastructure/{config,discovery,api-gateway}/Dockerfile
├── services/{patient,doctor,appointment,medical-record,notification}-service/Dockerfile
└── .dockerignore                   ← exclut target/, .idea/, .git/, etc.

terangamed-frontend/
├── Dockerfile                      ← multi-stage Node 20 → Nginx
├── .dockerignore                   ← exclut node_modules/, dist/, e2e/
├── nginx/
│   ├── default.conf.template       ← reverse-proxy /api/* + cache + dual-stack
│   └── docker-entrypoint.sh        ← envsubst runtime (API_BACKEND_URL)
└── docker/
    ├── docker-compose.yml          ← stack frontend (1 service)
    └── .env.example                ← API_BACKEND_URL, FRONTEND_HOST_PORT

scripts/
├── e2e-smoke-backend.sh            ← validation CI backend dockerisé
├── e2e-smoke-frontend.sh           ← validation CI frontend dockerisé (auto-adaptatif)
├── swagger-smoke.sh                ← vérif paths + scheme bearer-auth (5 services)
├── get-token.sh                    ← récupère JWT Keycloak (+ clipboard)
└── reset-keycloak-realm.sh         ← reimport idempotent du realm
```

### 11.2 Commandes de référence rapide

```bash
# ── Démarrage propre stack complète ──
docker network create terangamed-net 2>/dev/null
cd terangamed-backend/docker && docker compose up -d
cd ../../terangamed-frontend/docker && docker compose up -d

# ── Arrêt propre ──
cd terangamed-frontend/docker && docker compose down
cd ../../terangamed-backend/docker && docker compose down

# ── Validation E2E ──
./scripts/e2e-smoke-backend.sh --keep    # ~3 min
./scripts/e2e-smoke-frontend.sh          # ~1 min (détecte backend)

# ── Status global ──
docker compose -f terangamed-backend/docker/docker-compose.yml ps
docker compose -f terangamed-frontend/docker/docker-compose.yml ps

# ── Tear-down + cleanup volumes ──
docker compose -f terangamed-backend/docker/docker-compose.yml down -v
docker network rm terangamed-net
```

### 11.3 URLs locales

| URL | Service |
|-----|---------|
| http://localhost:4200 | Frontend Angular (via Nginx) |
| http://localhost:8080 | API Gateway (toutes les routes `/api/*`) |
| http://localhost:8081-8084,:8086 | Swagger UI direct par microservice |
| http://localhost:8090 | Kafka UI |
| http://localhost:8180 | Keycloak admin console (`admin/admin`) |
| http://localhost:8761 | Eureka dashboard (`eureka/eureka` basicAuth) |
| http://localhost:8888 | Config Server (`config/config` basicAuth) |

## 12. Références

- [`10C5-VALIDATION-CHECKLIST.md`](./10C5-VALIDATION-CHECKLIST.md) — Checklist validation manuelle E2E
- [`10-SWAGGER-API.md`](./10-SWAGGER-API.md) — Test API via Swagger UI
- [`08-KAFKA-INTEGRATION.md`](./08-KAFKA-INTEGRATION.md) — Architecture Kafka + Outbox
- [`03-KEYCLOAK-SETUP.md`](./03-KEYCLOAK-SETUP.md) — Setup Keycloak realm
- Docker Compose docs : https://docs.docker.com/compose/
- Spring Boot Docker best practices : https://spring.io/guides/topicals/spring-boot-docker/

---

*Doc Docker Deployment finalisée à la fin de l'étape 10B (mai 2026). À actualiser si de nouveaux services sont ajoutés au compose ou si la topology change.*
