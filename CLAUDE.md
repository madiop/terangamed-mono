# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working language

Travailler et commenter **en français**. Documentation projet, commit messages, et commentaires de code sont tous en français (cf. `PROJECT_CONTEXT.md` §1).

## Repository layout

Monorepo à deux racines indépendantes (chacune avec son propre `.git` imbriqué) :

- `terangamed-backend/` — multi-module Maven (Spring Boot 3.2.5 / Java 21 / Spring Cloud 2023.0.1)
- `terangamed-frontend/` — Angular 17 standalone (Jest unit + Playwright E2E)
- `scripts/` — orchestration cross-stack (coverage, smoke E2E, helpers Keycloak)
- `docs/`, `PROJECT_CONTEXT.md`, `01-ARCHITECTURE-GLOBALE.md` — doc fonctionnelle/architecturale (lire `PROJECT_CONTEXT.md` en premier pour reprise rapide)

## Backend — commandes Maven

Toutes depuis `terangamed-backend/` (utiliser le wrapper `./mvnw`).

```bash
./mvnw clean verify                                  # build + tests + couverture Jacoco (check ≥ 80%)
./mvnw clean verify -Pfast                           # itération rapide — skip Jacoco check (tests tournent quand même)
./mvnw -pl services/patient-service -am clean verify # build d'un seul service (+ ses deps, dont common-lib)
./mvnw -pl common-lib clean verify                   # build common-lib seul
./mvnw -pl services/patient-service test -Dtest=PatientServiceTest        # 1 classe de tests
./mvnw -pl services/patient-service test -Dtest=PatientServiceTest#shouldX # 1 méthode
```

Rapport Jacoco par module : `<module>/target/site/jacoco/index.html`.

**Java 21 obligatoire** (`java -version`). Switch via `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`.

### Testcontainers sur macOS

Docker Desktop 4.71+ ne crée plus `/var/run/docker.sock` par défaut et la socket de redirection `~/.docker/run/docker.sock` n'est pas suivie. Pointer Testcontainers vers la vraie socket :

```bash
DOCKER_REAL_SOCKET=$(docker info --format '{{range .Labels}}{{.}}{{"\n"}}{{end}}' \
  | grep 'com.docker.desktop.address' | sed 's|com.docker.desktop.address=unix://||')
cat > "$HOME/.testcontainers.properties" <<EOF
docker.host=unix://$DOCKER_REAL_SOCKET
EOF
```

Alternative : cocher « Allow the default Docker socket » dans Docker Desktop Settings → Advanced.

## Frontend — commandes npm

Toutes depuis `terangamed-frontend/`.

```bash
npm start                  # dev server :4200 avec proxy /api → :8080
npm run build:prod         # build optimisé → dist/
npm test                   # Jest unit
npm run test:watch
npm run test:coverage      # + rapport coverage/lcov-report/index.html
npm test -- path/to/file.spec.ts                    # 1 fichier de test
npm test -- -t "should do X"                        # 1 nom de test
npm run test:e2e           # Playwright (backend doit tourner)
npm run test:e2e:ui        # mode UI Playwright
npm run test:e2e:smoke     # uniquement tests @smoke
npm run lint               # ESLint TS + templates
npm run format             # Prettier
```

Le proxy `/api/*` → `http://localhost:8080` est défini dans `proxy.conf.json` (le frontend ne tape jamais directement les services).

## Démarrage full-stack

Trois modes (détails complets dans `PROJECT_CONTEXT.md` §10) :

```bash
# Mode 100% Docker (recommandé)
docker network create terangamed-net 2>/dev/null
cd terangamed-backend/docker && docker compose up -d && cd ../..
cd terangamed-frontend/docker && docker compose up -d && cd ../..

# Mode hybride dev (backend Docker, frontend ng serve)
cd terangamed-backend/docker && docker compose up -d
cd terangamed-frontend && npm start

# Smoke E2E automatisés
./scripts/e2e-smoke-backend.sh --keep
./scripts/e2e-smoke-frontend.sh
./scripts/swagger-smoke.sh           # ≤ 5s — valide les 5 OpenAPI exposés
./scripts/coverage-all.sh            # coverage back + front consolidé
./scripts/get-token.sh dr.martin     # JWT → clipboard (workaround Swagger UI 5.13)
```

URLs : Frontend `:4200` — Gateway `:8080` — Swagger direct `:8081-86` — Keycloak `:8180` (admin/admin) — Eureka `:8761`.

Users seed (dev uniquement, en clair dans le realm) : `admin/admin` (ADMIN), `dr.martin/doctor123` (DOCTOR), `reception/reception` (RECEPTIONIST).

## Architecture — vue d'ensemble

Microservices Spring Boot orchestrés par 3 composants d'infra + IdP Keycloak + Kafka. Frontend Angular SPA séparé.

```
Angular SPA (4200) ──Bearer JWT──> API Gateway (8080)
                                       │
        ┌──────────────┬───────────────┼───────────────┬──────────────┐
        ▼              ▼               ▼               ▼              ▼
   patient(8081)  doctor(8082)  appointment(8083)  medical(8084)  notification(8086)
        │              │               │               │              ▲
        └──────────────┴───────────────┴───── Kafka ────┘              │
                       Schema Registry + 4 topics + Outbox             │
                                                                       │
   Infra : config-server(8888) · discovery-server(8761) · keycloak(8180) · postgres(5432)
```

### Modules Maven (parent `terangamed-backend/pom.xml`)

| Module | Rôle |
|---|---|
| `common-lib` | exceptions (`BaseException`/`GlobalExceptionHandler`), security (`JwtAuthConverter`, `SecurityRoles`), pagination + `SortValidator`, audit (`BaseAuditEntity` + `AuditorAware`), outbox pattern, kafka helpers, finance (Currency XOF + PaymentMethod Wave/Orange Money) |
| `infrastructure/config-server` (8888) | Spring Cloud Config — config-repo natif embarqué |
| `infrastructure/discovery-server` (8761) | Eureka registry |
| `infrastructure/api-gateway` (8080) | Spring Cloud Gateway (reactive) + JWT validation + CORS |
| `services/patient-service` (8081) | Référence du pattern Controller/Service/Repo/Specification/DTO/Mapper |
| `services/doctor-service` (8082) | Médecins + transitions d'état (active/inactive/suspended) |
| `services/appointment-service` (8083) | RDV + Feign vers patient/doctor + Resilience4j |
| `services/medical-record-service` (8084) | Dossier, consultations, antécédents, ordonnances (JSONB + signature immuable) |
| `services/notification-service` (8086) | Consumer Kafka multi-topics + audit REST |

### Découpe Java de chaque service métier

```
com.terangamed.<service>/
├── controller/        # @RestController + @PreAuthorize
├── service/           # Logique métier, transactions
├── repository/        # Spring Data JPA (JpaRepository + JpaSpecificationExecutor)
├── specification/     # Specification<T> — filtres dynamiques composables
├── entity/            # @Entity, étend BaseAuditEntity
├── dto/               # Records / DTOs
├── mapper/            # MapStruct (génère ...MapperImpl)
└── config/            # SecurityFilterChain, OpenApiConfig, etc.
```

### Frontend Angular — découpe

```
src/app/
├── core/         # auth (Keycloak PKCE + AuthService signals + AuthGuard/RoleGuard + JwtInterceptor), http, layout (sidebar/topbar)
├── shared/       # composants/pipes/directives partagés
├── api/          # clients HTTP typés + models — un client par microservice
└── features/     # modules métier lazy-loaded — dashboard, patients, appointments, medical, admin, auth
```

Path aliases (cf. `jest.config.js` + `tsconfig.json`) : `@core/*`, `@shared/*`, `@features/*`, `@api/*`, `@env/*`.

## Conventions backend non-négociables

### 1. Specifications JPA exclusivement

**Interdiction stricte** de `@Query` avec `:param IS NULL OR field = :param` (problème de type inference PostgreSQL avec params null). Filtres dynamiques **toujours** via `Specification<Entity>` qui retourne `null` quand le critère est vide — la composition `.where(...).and(...)` ignore les `null`. Exemple dans `PatientSpecifications`.

### 2. Exceptions métier

Hériter de `BaseException` (common-lib). Le `GlobalExceptionHandler` mappe en `ApiError` unifié. Codes existants : `ResourceNotFoundException`, `ConflictException("CODE", msg)`, `BadRequestException`.

### 3. Tri sécurisé

Avant tout `findAll(Pageable)`, appeler `SortValidator.sanitize(pageable, ALLOWED_FIELDS)` — protège contre tri sur champs sensibles/non-indexés.

### 4. Audit JPA

Entités métier étendent `BaseAuditEntity` (4 colonnes `createdAt/updatedAt/createdBy/updatedBy`). **Chaque service doit annoter sa classe `@SpringBootApplication` avec `@EnableJpaAuditing(auditorAwareRef = "terangamedAuditorAware")`** — la lib ne l'auto-active pas (le `JpaMetamodelMappingContext` exige des entités, qui sont scannées par le service consommateur). Le bean `terangamedAuditorAware` (extrait l'user du JWT) est fourni par common-lib.

Pour les tests, `BaseAuditEntity` expose des setters (Lombok `@Builder` ne traverse pas l'héritage) — en prod, **ne pas** les appeler manuellement, Spring Auditing s'en charge.

### 5. Outbox Pattern pour les events Kafka

Pas de `kafkaTemplate.send()` direct. Persister en base via la table `outbox_events` (status `PENDING`/`PUBLISHED`/`FAILED`), l'`OutboxEventRelay` poller @Scheduled publie + retry exponential backoff + purge des PUBLISHED après 7 jours. Garantit l'atomicité JPA ↔ Kafka.

Topics Kafka : `terangamed.<bounded-context>.events` — patient, doctor, appointment, **medical** (= medical-record-service). Schémas Avro versionnés dans Schema Registry (Confluent). Consumer unique : notification-service (avec idempotence + retry + DLT en `*-dlt` minuscules).

### 6. Sécurité

`@PreAuthorize(SecurityRoles.HAS_ADMIN_OR_DOCTOR)` etc. — **toujours** via les constantes de `SecurityRoles` (common-lib), jamais en string littéral. Rôles realm Keycloak : `ADMIN`, `DOCTOR`, `RECEPTIONIST`, `PATIENT` (lus depuis `realm_access.roles`, mappés en `ROLE_*` par `JwtAuthConverter`).

Filtrage PATIENT (lecture de son seul dossier) implémenté dans `MedicalRecordAccessChecker.ensureCanAccess()`.

### 7. Email vide → NULL

Contrainte `UNIQUE(email)` PostgreSQL traite `''` comme une valeur ⇒ 2 records sans email entrent en collision. Pattern : migration Flyway crée un **partial unique index** `WHERE email IS NOT NULL`, et le service appelle `normalizeBlankFields(entity)` dans `create()` et `update()` après mapping DTO → entité (cf. patient `V2__partial_unique_email.sql`, doctor `V3__...`).

### 8. Workaround V1 — header `X-Doctor-Id`

`ConsultationController.create()` exige le header `X-Doctor-Id` (mapping `keycloak_subject ↔ doctors.keycloakSubject` pas encore exposé). Documenté dans `resolveCurrentDoctorId()`. Sera remplacé par Feign lookup en V2.

### 9. Coverage Jacoco

Seuil BUNDLE ≥ 80% (INSTRUCTION coveredratio) dans le parent POM. Excludes au niveau **plugin** (et non `<rule>`) : `**/dto/**`, `**/entity/**`, `**/config/**`, `**/*Application.*`, `**/*AutoConfiguration.*`, `**/*MapperImpl.*`, `**/mapper/**`, `**/avro/**`.

## Conventions frontend non-négociables

- **Standalone components uniquement** — pas de `NgModule` sauf nécessité absolue
- **Change detection `OnPush`** partout
- **TypeScript strict**, préfixe sélecteur `tm-` (composants) / `tm` (directives)
- **Signals** : `signal.set()` est silencieusement bloqué dans `effect()` → utiliser `computed()`
- **Template `@else if`** : `as` alias interdit (erreur NG5002 récurrente)
- Coverage Jest abaissée temporairement (lines 20% — dette #63) — viser 60%+ pour nouveaux composants
- **E2E Playwright = navigation + matrice de permissions**, pas full CRUD (trop flaky avec mat-select async — CRUD couvert par Jest + manuel Swagger)

## Doc clé à consulter selon le contexte

| Besoin | Fichier |
|---|---|
| Reprise rapide (état actuel + endpoints + decisions) | `PROJECT_CONTEXT.md` |
| Architecture détaillée (DDD, bounded contexts) | `01-ARCHITECTURE-GLOBALE.md` |
| Setup Keycloak (realm, users, JWT, Swagger OAuth2) | `terangamed-backend/docs/03-KEYCLOAK-SETUP.md` |
| Pattern référence d'un service | `terangamed-backend/docs/04-PATIENT-SERVICE.md` |
| Kafka (topics, Outbox, Avro, smoke) | `terangamed-backend/docs/08-KAFKA-INTEGRATION.md` |
| Test API via Swagger UI | `terangamed-backend/docs/10-SWAGGER-API.md` |
| Déploiement Docker complet | `terangamed-backend/docs/11-DOCKER-DEPLOYMENT.md` |
| Frontend design system | `docs/02-FRONTEND-DESIGN-NOTES.md` |
