# TerangaMed — Contexte projet (réutilisable)

> Document de synthèse pour reprise rapide. À lire **en premier** quand on revient sur le projet après une pause ou en début de nouvelle session.
> Dernière mise à jour : 2026-05-11 (fin Étape 10B — Dockerisation finale + smoke E2E intégrés)

## 1. Vue d'ensemble

**TerangaMed** est une application de gestion de cabinet médical pour le marché sénégalais — architecture microservices Spring Boot 3 + frontend Angular 17 + Keycloak + Kafka. Multi-rôles (ADMIN / DOCTOR / RECEPTIONIST / PATIENT), multi-devises (XOF + autres), méthodes de paiement adaptées au contexte local (Wave, Orange Money…).

**Objectifs métier** :
- Dossier médical informatisé (DMI) : patients, antécédents, consultations, ordonnances
- Prise de rendez-vous + planning hebdomadaire
- Notifications événementielles (Kafka) pour audit médico-légal
- Intégrations futures : facturation, PDF d'ordonnance, télémédecine

**Conventions** :
- Langage de travail et commentaires : **français**
- Code production-ready uniquement, pas de "demo code"
- Clean Architecture, séparation stricte Controller/Service/Repository
- Tests à hauteur de couverture ≥ 80% (Jacoco backend) + pyramide de tests E2E Playwright

## 2. Stack technique

| Couche | Technologie | Version |
|--------|-------------|---------|
| Langage backend | Java | 21 (LTS) |
| Framework backend | Spring Boot | 3.2.5 |
| Microservices | Spring Cloud | 2023.0.1 |
| Sécurité | Keycloak | 24 |
| Base de données | PostgreSQL | 16 |
| Migrations | Flyway | (intégré Spring Boot) |
| Messaging | Apache Kafka | KRaft mode (sans ZK) |
| Schéma events | Avro + Schema Registry | Confluent |
| ORM | Spring Data JPA + Specifications | (interdiction `:p IS NULL OR ...`) |
| Mapping DTO | MapStruct | 1.6.3 + Lombok 1.18.34 |
| Doc API | springdoc-openapi | 2.x |
| Tests backend | JUnit 5 + Mockito + Testcontainers | — |
| Frontend | Angular | 17 (signals + standalone components) |
| UI | Angular Material + Tailwind | — |
| Tests frontend | Jest (unit) + Playwright (E2E) | — |
| Build | Maven (multi-module) + npm | — |

## 3. Architecture

### 3.1 Composants infrastructure

| Service | Port | Rôle |
|---------|------|------|
| config-server | 8888 | Spring Cloud Config — config-repo statique embarqué |
| discovery-server | 8761 | Eureka — registry pour les microservices |
| api-gateway | 8080 | Spring Cloud Gateway — routage + JWT validation |
| keycloak | 8180 | OAuth2/OIDC provider (realm `terangamed`) |
| postgres | 5432 | DB partagée (1 schéma par service) |
| kafka | 9092 | Broker KRaft |
| schema-registry | 8081 (interne) | Confluent Schema Registry pour Avro |
| kafka-ui | 8090 | UI debug Kafka topics |

### 3.2 Microservices métier

| Service | Port | Responsabilité |
|---------|------|----------------|
| patient-service | 8081 | CRUD patients, recherche paginée |
| doctor-service | 8082 | CRUD médecins, transitions d'état (active/inactive/suspended) |
| appointment-service | 8083 | RDV + Feign vers patient/doctor + Resilience4j |
| medical-record-service | 8084 | Dossier médical, consultations, antécédents, ordonnances |
| notification-service | 8086 | Consumer Kafka multi-topics + REST history (audit) |

### 3.3 Frontend

- **terangamed-frontend** (Angular 17, port dev 4200)
- Modules : Dashboard, Patients, Rendez-vous, Dossiers médicaux, Personnel
- Auth Keycloak via PKCE + AuthService signals + AuthGuard/RoleGuard + JwtInterceptor
- Tests : 195 Jest unit + 73 Playwright E2E

## 4. Décisions techniques majeures

### 4.1 Spring Data JPA Specifications (pas de JPQL ad hoc)

**Pourquoi** : éviter le piège PostgreSQL `WHERE (:param IS NULL OR col = :param)` qui pose des problèmes de type inference avec les paramètres null. **Toutes les recherches paginées dynamiques** passent par des `Specification<T>` typées (cf. `PatientSpecifications`, `AppointmentSpecifications`, etc.).

### 4.2 Outbox Pattern pour les events Kafka

**Pourquoi** : garantir l'atomicité entre la persistance JPA et la publication Kafka. Implémentation dans `common-lib` :
- Table `outbox_events` (status `PENDING` / `PUBLISHED` / `FAILED`)
- `OutboxEventRelay` poller @Scheduled, retry 3 fois exponential backoff, purge des PUBLISHED après 7 jours
- 5 producers : patient, doctor, appointment, medical-record (3 events)

### 4.3 Audit JPA + `BaseAuditEntity`

**Pourquoi** : `@CreatedBy` / `@LastModifiedBy` automatiques via `AuditorAware` qui lit le JWT.

⚠️ **Particularité** : `@Setter` est ajouté explicitement sur `BaseAuditEntity` pour permettre aux **tests** de forcer `createdBy` post-build (Lombok `@Builder` ne traverse pas l'héritage). Dans le code prod, ces setters ne doivent **pas** être appelés directement — Spring Auditing s'en charge.

### 4.4 Partial Unique Index pour emails (dette #57)

**Pourquoi** : la contrainte `UNIQUE(email)` classique de PostgreSQL traite `''` (chaîne vide) comme une valeur. Donc deux patients sans email mais avec `email=''` violent la contrainte au PUT → 500.

**Fix en 2 temps** :
1. Migration Flyway `V2__partial_unique_email.sql` (patient) et `V3__partial_unique_email.sql` (doctor) :
   ```sql
   UPDATE patients SET email = NULL WHERE email = '';
   ALTER TABLE patients DROP CONSTRAINT IF EXISTS uk_patient_email;
   CREATE UNIQUE INDEX uk_patient_email ON patients (email) WHERE email IS NOT NULL;
   ```
2. Service-side `normalizeBlankFields(entity)` appelé dans `create()` et `update()` après mapping DTO → entité.

### 4.5 Header `X-Doctor-Id` (workaround V1)

**Pourquoi** : `ConsultationController.create()` doit identifier le médecin connecté pour set `doctorId`. La résolution propre passe par `keycloak_subject ↔ doctors.keycloakSubject` mais ce mapping n'est pas encore exposé côté doctor-service.

**Workaround V1** : header HTTP `X-Doctor-Id` requis. Sera remplacé par lookup Feign en étape 8/intégration cross-services V2. Documenté dans `ConsultationController.resolveCurrentDoctorId()`.

### 4.6 Test Pyramide

| Niveau | Stack | Couverture |
|--------|-------|------------|
| Unit (backend) | JUnit 5 + Mockito | Service / Specifications / Mapper |
| Slice (backend) | `@WebMvcTest` + `@DataJpaTest` | Controller + Repository |
| Intégration (backend) | Testcontainers (Postgres, Kafka) | Outbox + flow Kafka |
| Unit (frontend) | Jest | Facades + services Angular |
| E2E (frontend) | Playwright | Navigation + permissions par rôle (pas full CRUD) |

**Décision E2E** : on ne teste **pas** le CRUD complet en Playwright (trop flaky avec mat-select async). Le full CRUD est couvert par Jest unit + tests manuels Swagger. E2E = navigation + matrice de permissions par route.

### 4.7 Convention Angular

- `@else if` interdit avec `as` alias (NG5002)
- `signal.set()` interdit dans `effect()` (silencieusement bloqué) → préférer `computed()`

## 5. Endpoints (53 au total)

Tous sécurisés par JWT Keycloak + `@PreAuthorize`. Tous documentés Swagger avec `@Operation` + `@ApiResponses` exhaustifs.

### 5.1 patient-service (8081)

| Méthode | Path | Rôle | Notes |
|---------|------|------|-------|
| GET | `/api/patients` | DOCTOR/ADMIN/RECEPTIONIST | Search paginée + Specs |
| GET | `/api/patients/{id}` | DOCTOR/ADMIN/RECEPTIONIST | — |
| POST | `/api/patients` | DOCTOR/ADMIN/RECEPTIONIST | 201 + Location |
| PUT | `/api/patients/{id}` | DOCTOR/ADMIN/RECEPTIONIST | normalizeBlankFields |
| DELETE | `/api/patients/{id}` | ADMIN | Soft-delete |

### 5.2 doctor-service (8082)

| Méthode | Path | Rôle | Notes |
|---------|------|------|-------|
| GET | `/api/doctors` | * (auth) | Search paginée |
| GET | `/api/doctors/{id}` | * (auth) | — |
| POST | `/api/doctors` | ADMIN | Création médecin |
| PUT | `/api/doctors/{id}` | ADMIN | normalizeBlankFields |
| PATCH | `/api/doctors/{id}/status` | ADMIN | Transitions état (active/inactive/suspended) |
| DELETE | `/api/doctors/{id}` | ADMIN | Soft-delete |

### 5.3 appointment-service (8083)

| Méthode | Path | Rôle | Notes |
|---------|------|------|-------|
| GET | `/api/appointments` | DOCTOR/ADMIN/RECEPTIONIST | Search + Feign |
| GET | `/api/appointments/{id}` | DOCTOR/ADMIN/RECEPTIONIST | — |
| POST | `/api/appointments` | DOCTOR/ADMIN/RECEPTIONIST | 409 si overlap |
| PUT | `/api/appointments/{id}` | DOCTOR/ADMIN/RECEPTIONIST | — |
| POST | `/api/appointments/{id}/confirm` | DOCTOR/ADMIN/RECEPTIONIST | Transition |
| POST | `/api/appointments/{id}/complete` | DOCTOR | Transition |
| POST | `/api/appointments/{id}/cancel` | DOCTOR/ADMIN/RECEPTIONIST | Transition |
| POST | `/api/appointments/{id}/no-show` | DOCTOR/ADMIN/RECEPTIONIST | Transition |
| DELETE | `/api/appointments/{id}` | ADMIN | Soft-delete |

### 5.4 medical-record-service (8084)

**MedicalRecordController** :

| Méthode | Path | Rôle |
|---------|------|------|
| GET | `/api/medical-records` | DOCTOR/ADMIN |
| GET | `/api/medical-records/{id}` | DOCTOR/ADMIN/PATIENT (filtré) |
| GET | `/api/medical-records/by-patient/{patientId}` | DOCTOR/ADMIN/PATIENT |
| POST | `/api/medical-records` | DOCTOR/ADMIN |
| PUT | `/api/medical-records/{id}` | DOCTOR/ADMIN |

**ConsultationController** :

| Méthode | Path | Rôle | Notes |
|---------|------|------|-------|
| GET | `/api/consultations` | DOCTOR/ADMIN | Search + Specs |
| GET | `/api/consultations/{id}` | DOCTOR/ADMIN/PATIENT | — |
| POST | `/api/consultations` | DOCTOR | Header `X-Doctor-Id` requis |
| PUT | `/api/consultations/{id}` | DOCTOR | 409 si signée |
| POST | `/api/consultations/{id}/sign` | DOCTOR | Terminal — immuable après |
| DELETE | `/api/consultations/{id}` | ADMIN | Soft-delete médico-légal |

**AntecedentController** :

| Méthode | Path | Rôle |
|---------|------|------|
| GET | `/api/antecedents/by-record/{recordId}` | DOCTOR/ADMIN/PATIENT |
| GET | `/api/antecedents/{id}` | DOCTOR/ADMIN/PATIENT |
| POST | `/api/antecedents` | DOCTOR/ADMIN |
| PUT | `/api/antecedents/{id}` | DOCTOR/ADMIN |
| DELETE | `/api/antecedents/{id}` | DOCTOR/ADMIN |

**PrescriptionController** :

| Méthode | Path | Rôle | Notes |
|---------|------|------|-------|
| POST | `/api/prescriptions/by-consultation/{cid}` | DOCTOR | 1 ordonnance/consultation max |
| GET | `/api/prescriptions/{id}` | DOCTOR/ADMIN/PATIENT | Avec lignes |
| GET | `/api/prescriptions/by-consultation/{cid}` | DOCTOR/ADMIN/PATIENT | — |
| PUT | `/api/prescriptions/{id}` | DOCTOR | 409 si consultation signée |
| DELETE | `/api/prescriptions/{id}` | DOCTOR | Cascade lignes |
| POST | `/api/prescriptions/{id}/lines` | DOCTOR | Ajout ligne |
| PUT | `/api/prescriptions/{id}/lines/{lineId}` | DOCTOR | — |
| DELETE | `/api/prescriptions/{id}/lines/{lineId}` | DOCTOR | — |
| GET | `/api/prescriptions/{id}/pdf` | DOCTOR/ADMIN/PATIENT | **501 Not Implemented** (V1) |

### 5.5 notification-service (8086)

| Méthode | Path | Rôle |
|---------|------|------|
| GET | `/api/notifications` | ADMIN |
| GET | `/api/notifications/{id}` | ADMIN |

## 6. Sécurité

### 6.1 Keycloak realm `terangamed`

Configuré via `terangamed-realm.json` (auto-import au démarrage).

**Clients** :

| Client | Type | Usage |
|--------|------|-------|
| `terangamed-frontend` | Public + PKCE S256 | Angular SPA |
| `terangamed-backend` | Confidentiel | Service account |
| `swagger-ui` | Public + PKCE | Swagger UIs des 5 services (multi-redirect URI) |

**Audience mapper** : `terangamed-frontend` et `swagger-ui` ajoutent `terangamed-backend` au claim `aud` — permet validation stricte d'audience côté microservices.

### 6.2 Rôles realm

| Rôle | Lu depuis | Permissions principales |
|------|-----------|-------------------------|
| ADMIN | `realm_access.roles` | Tout — CRUD doctors, audit notifications |
| DOCTOR | idem | CRUD consultations/antécédents/ordonnances + signature |
| RECEPTIONIST | idem | CRUD patients (sauf delete), CRUD RDV |
| PATIENT | idem | Lecture filtrée de son propre dossier |

Filtrage PATIENT implémenté dans `MedicalRecordAccessChecker.ensureCanAccess()` (vérifie que `patientId` du record == `patientId` résolu depuis JWT).

### 6.3 Utilisateurs seed (dev uniquement)

| Username | Password | Rôle |
|----------|----------|------|
| `admin` | `admin` | ADMIN |
| `dr.martin` | `doctor123` | DOCTOR |
| `reception` | `reception` | RECEPTIONIST |

⚠️ **NE PAS UTILISER EN PROD** — passwords en clair dans le realm export.

### 6.4 Configuration Spring Security par service

- `OAuth2ResourceServer` JWT validation (issuer-uri = realm Keycloak)
- `JwtAuthConverter` custom : extrait `realm_access.roles` → `ROLE_*`
- `@PreAuthorize` sur chaque endpoint via constantes `SecurityRoles.HAS_ADMIN`, `HAS_DOCTOR`, `HAS_DOCTOR_ADMIN_OR_PATIENT`, etc. (centralisées dans common-lib)
- CORS configuré pour `http://localhost:4200` (frontend) + ports Swagger UI

### 6.5 Swagger OAuth2 intégré

Chaque `OpenApiConfig.java` déclare 2 schémas :
- `keycloak-password` — OAuth2 Password Flow (login direct dans Swagger)
- `bearer-auth` — JWT Bearer fallback

Token URL : `${KEYCLOAK_TOKEN_URL:http://localhost:8180/realms/terangamed/protocol/openid-connect/token}` (env-driven).

## 7. Kafka — events publiés

Topics + schémas Avro versionnés dans Schema Registry. Producers via Outbox pattern uniquement.

Convention de nommage : `terangamed.<bounded-context>.events` (cf. `TerangaMedTopics`). Le bounded-context **`medical`** correspond au service medical-record-service (le nom du topic suit le contexte métier, pas le nom du service).

| Topic (nom complet côté broker) | Producer | Events |
|---------------------------------|----------|--------|
| `terangamed.patient.events` | patient-service | PatientCreated, PatientUpdated, PatientDeleted |
| `terangamed.doctor.events` | doctor-service | DoctorCreated, DoctorStatusChanged |
| `terangamed.appointment.events` | appointment-service | AppointmentScheduled, AppointmentConfirmed, AppointmentCompleted, AppointmentCancelled, AppointmentNoShow |
| `terangamed.medical.events` | medical-record-service | ConsultationCreated, ConsultationSigned, PrescriptionCreated |

**Consumer** : `notification-service` consomme tous les topics et persiste dans `notifications` (audit + debug).

## 8. État d'avancement

### 8.1 Étapes terminées

| Étape | Description |
|-------|-------------|
| 1 | Conception globale ✅ |
| 2 | Infra (Config, Eureka, Gateway) ✅ |
| 3 | Keycloak realm + clients + rôles + utilisateurs ✅ |
| 4 | patient-service complet (4 sous-étapes) ✅ |
| 5 | doctor-service complet ✅ |
| 6 | appointment-service complet (Feign + Resilience4j) ✅ |
| 7 | medical-record-service complet (4 sous-étapes) ✅ |
| 8 | Kafka intégration (6 sous-étapes : infra → producers → consumer → validation) ✅ |
| 9 | Frontend Angular complet (modules Dashboard / Patients / RDV / Dossiers / Personnel) ✅ |
| 10A | Tests complets (E2E Playwright + dette tests backend) ✅ |
| 10C.1 | @ApiResponse exhaustifs sur les 3 controllers manquants ✅ |
| 10C.3 | Smoke script `swagger-smoke.sh` ✅ |
| 10C.4 | Doc utilisateur `10-SWAGGER-API.md` ✅ |
| 10C.5 | Validation manuelle E2E via Swagger UI ✅ (2026-05-10) |
| 10B | Dockerisation finale + smoke E2E intégré ✅ (2026-05-11) |

### 8.2 État qualité actuel

- **Tests backend** : 5/5 services verts (Maven `test`)
- **Tests frontend** : 195 Jest + 73 Playwright E2E verts
- **Coverage** : seuils Jacoco backend ≥ 80% — Jest frontend abaissé temporairement (lines 20%, dette #63)
- **Endpoints documentés** : 53/53 (100%) avec `@Operation` + `@ApiResponses`
- **Dette technique #57** (uk_patient_email) : résolue
- **Smoke Swagger** : script automatisé prêt
- **Validation E2E manuelle** : happy path + cas négatifs OK, audit Kafka OK
- **Auth Swagger** : un seul scheme `bearer-auth` (password flow Swagger UI 5.13 cassé)

### 8.3 Correctifs livrés pendant 10C.5

Plusieurs bugs et trous de doc révélés par la validation manuelle ont été corrigés :

| Domaine | Correctif | Fichiers |
|---------|-----------|----------|
| Kafka | `KafkaProducerConfig` + `KafkaTemplate(defaultRetryTopicKafkaTemplate)` + `KafkaAdmin` exigés par `@RetryableTopic` | `notification-service/.../config/KafkaProducerConfig.java` + test |
| Kafka | `@RetryableTopic(autoCreateTopics = "true")` + `numPartitions=1` + `replicationFactor=1` | `EventNotificationConsumer.java` |
| Kafka | `kafka-init` nettoyé : suppression des DLT `*.DLT` mal nommés (Spring Kafka utilise `-dlt` minuscules) | `docker-compose.yml` |
| Swagger | Suppression de `keycloak-password` (Password flow cassé Swagger UI 5.13) — un seul scheme `bearer-auth` | 5 × `OpenApiConfig.java` |
| Swagger | Suppression de `@SecurityRequirement(name = "keycloak-oauth2")` (scheme inexistant) | 3 × controllers (Patient, Doctor, Appointment) |
| Swagger | Inversion ordre `servers` : direct (8081/82/83/84/86) en premier, gateway 8080 en second (évite cross-origin) | 4 × `OpenApiConfig.java` |
| Smoke | `swagger-smoke.sh` recalibré : seuils min_paths sur les paths uniques réels + check `bearer-auth` au lieu de `keycloak-password` | `swagger-smoke.sh` |
| Keycloak | `reset-keycloak-realm.sh` : reimport idempotent via API admin (sans reset volume Postgres) | `scripts/reset-keycloak-realm.sh` |
| Auth | `get-token.sh <user>` : récupère un JWT et le copie dans le clipboard (workaround bug Swagger UI) | `scripts/get-token.sh` |
| Doc | Checklist `10C5-VALIDATION-CHECKLIST.md` ajoutée (runbook + happy path + cas négatifs + Kafka + clôture) | `docs/10C5-VALIDATION-CHECKLIST.md` |

### 8.4 Dette doc identifiée mais non traitée

À reprendre dans une mini-PR séparée — non-bloquante :

| ID | Description | Priorité |
|----|-------------|----------|
| 10C.4-bis | Aligner les payloads d'exemple dans `10-SWAGGER-API.md` sur les vrais DTOs (10 écarts listés dans `10C5-VALIDATION-CHECKLIST.md` §7.1) | Basse |
| 03-doc | `03-KEYCLOAK-SETUP.md` §6.2 référence encore `keycloak-oauth2` + Auth Code flow obsolètes | Basse |

## 9. Tâches restantes

### 9.1 Étape 10C — restes optionnels

| ID | Tâche | Priorité | Effort | Statut |
|----|-------|----------|--------|--------|
| 10C.2 | Aggregated Swagger UI sur API Gateway (springdoc-webflux) | Basse | 45 min | Optionnel — reportable |

### 9.2 Étapes futures

| Étape | Description | Priorité |
|-------|-------------|----------|
| #63 | Étendre coverage frontend Jest aux composants (cible 60%+) | Moyenne |
| 8/V2 | Lookup Feign keycloak_subject → doctorId (suppression header `X-Doctor-Id`) | Moyenne |
| Prod-readiness | Gaps de production-readiness listés dans `11-DOCKER-DEPLOYMENT.md` §10 (TLS, secrets management, RF Kafka ≥ 2, backup Postgres…) | Moyenne |
| Swagger UI | Bumper springdoc-openapi (pour réintroduire le password flow dans `OpenApiConfig` quand le bug 5.13 sera corrigé) | Basse |
| Future | PDF d'ordonnance (endpoint `/prescriptions/{id}/pdf` actuellement 501) | Basse |
| Future | Audience JWT validation stricte (`spring.security.oauth2.resourceserver.jwt.audiences`) | Basse |

### 9.3 Améliorations cosmétiques connues

- Mockito + JDK 21 : warning "Java agent loaded dynamically" — fix en ajoutant `-XX:+EnableDynamicAgentLoading` à `<argLine>` Surefire (cf. tech-debt)

## 10. Quick start

Trois modes possibles selon ce que tu fais. Tous démarrent depuis la racine du repo.

### 10.A Démarrage 100 % Docker (recommandé — depuis 10B)

```bash
# Une seule fois — création du réseau partagé
docker network create terangamed-net

# Démarrer le backend complet (12 services) en arrière-plan
cd terangamed-backend/docker && docker compose up -d
cd ../..

# Démarrer le frontend (Nginx + bundle Angular)
cd terangamed-frontend/docker && docker compose up -d
cd ../..

# Validation E2E automatique (≈ 4-12 min selon cache Docker)
./scripts/e2e-smoke-backend.sh --keep    # backend + smoke; --keep laisse up
./scripts/e2e-smoke-frontend.sh          # frontend + détection backend automatique
```

URLs : Frontend http://localhost:4200 — API Gateway http://localhost:8080 —
Swagger direct http://localhost:8081-86 — Keycloak http://localhost:8180 (admin/admin) —
Eureka http://localhost:8761 (eureka/eureka).

Cleanup :
```bash
cd terangamed-frontend/docker && docker compose down && cd ../..
cd terangamed-backend/docker && docker compose down && cd ../..
docker network rm terangamed-net   # optionnel
```

Doc complète : [`terangamed-backend/docs/11-DOCKER-DEPLOYMENT.md`](terangamed-backend/docs/11-DOCKER-DEPLOYMENT.md).

### 10.B Démarrage hybride dev (backend Docker, frontend `ng serve`)

```bash
# Backend dockerisé
docker network create terangamed-net 2>/dev/null
cd terangamed-backend/docker && docker compose up -d
cd ../..

# Frontend en `ng serve` (hot-reload + DevTools)
cd terangamed-frontend
npm install   # une seule fois
npm start     # http://localhost:4200 — proxy.conf.json mappe /api/* → :8080
```

### 10.C Démarrage 100 % local (sans Docker pour Spring)

Utile pour debug profond IDE (breakpoints natifs, profiling). Voir
[`10C5-VALIDATION-CHECKLIST.md`](terangamed-backend/docs/10C5-VALIDATION-CHECKLIST.md) §1.

### 10.D Tests automatisés

```bash
# Couverture backend + frontend consolidée
./scripts/coverage-all.sh

# Smoke Swagger sur les 5 services (≤ 5 s)
./scripts/swagger-smoke.sh

# E2E Playwright (suppose frontend up sur :4200)
cd terangamed-frontend && npx playwright test
```

## 11. Convention de travail (instructions projet)

Issues du fichier projet — **règles strictes** :

1. **Travail incrémental** — service par service, validation entre chaque sous-étape
2. **Code production-ready** — pas de "demo code"
3. **Spring Data JPA Specifications uniquement** — interdiction `:p IS NULL OR ...`
4. **Sécurité dès le début** — Keycloak intégré sur chaque endpoint
5. **Tests en parallèle du code** — couverture ≥ 80%, Testcontainers si nécessaire
6. **Documentation** — chaque décision technique commentée
7. **Swagger fonctionnel** — chaque endpoint testable via Swagger avec OAuth2
8. **Dockerisable** — chaque service avec son Dockerfile
9. **Si information manque → demander avant de coder**
10. **Robustesse > rapidité**

## 12. Références doc

| Doc | Contenu |
|-----|---------|
| `docs/guide_implementation_terangamed.md` | Roadmap étape par étape (origine projet) |
| `docs/02-FRONTEND-DESIGN-NOTES.md` | Notes design frontend |
| `terangamed-backend/docs/03-KEYCLOAK-SETUP.md` | Setup Keycloak realm + utilisateurs |
| `terangamed-backend/docs/04-PATIENT-SERVICE.md` | Doc patient-service (référence pattern) |
| `terangamed-backend/docs/08-KAFKA-INTEGRATION.md` | Architecture Kafka + Outbox + topics |
| `terangamed-backend/docs/10-SWAGGER-API.md` | **Test API via Swagger UI** (étape 10C.4) |
| `terangamed-backend/docs/10C5-VALIDATION-CHECKLIST.md` | Checklist validation manuelle E2E (étape 10C.5) |
| `terangamed-backend/docs/11-DOCKER-DEPLOYMENT.md` | **Déploiement Docker complet** (étape 10B) — architecture 2 stacks indépendants, 3 modes de démarrage, smoke E2E, troubleshooting, prod-readiness gaps |
| `scripts/coverage-all.sh` | Coverage back+front consolidé |
| `scripts/swagger-smoke.sh` | Smoke test Swagger des 5 services |
| `scripts/e2e-smoke-backend.sh` | Smoke E2E backend dockerisé (9 phases — boot + healthy + Eureka + Swagger + auth + CRUD) |
| `scripts/e2e-smoke-frontend.sh` | Smoke E2E frontend dockerisé (10 phases — auto-adaptatif selon présence backend) |
| `scripts/get-token.sh` | Helper auth Keycloak — récupère un JWT + copie clipboard |
| `scripts/reset-keycloak-realm.sh` | Reimport idempotent du realm Keycloak via API admin |

## 13. Mémoire persistante (à connaître si reprise par un agent)

Memos enregistrés à respecter automatiquement :
- **Angular `as` alias règle** : `as` interdit sur `@else if`, NG5002 récurrent
- **Angular signal writes in effect** : `signal.set()` silencieusement bloqué dans `effect()`, préférer `computed()`

---

*Document généré automatiquement à la fin de l'étape 10C.4. À actualiser manuellement quand des décisions techniques majeures sont prises.*
