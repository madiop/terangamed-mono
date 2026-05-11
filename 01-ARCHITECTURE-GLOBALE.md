# TerangaMed — Architecture Globale Détaillée

> **Version** : 1.0 — Étape 1 (Conception)
> **Auteur** : Architecture Senior — Microservices Spring Cloud
> **Statut** : En attente de validation client avant Étape 2

---

## 1. Vision et principes directeurs

TerangaMed est une plateforme de gestion de cabinet médical conçue selon les principes **cloud-native**, **microservices** et **Domain-Driven Design**. L'objectif n'est pas de produire un prototype mais une base de code **production-ready** alignée sur les standards d'une organisation de santé : sécurité forte, traçabilité, résilience, et capacité d'évolution.

Principes structurants :

1. **Indépendance des services** — chaque microservice possède sa base de données, son cycle de vie, son équipe potentielle. Aucune jointure cross-service en base.
2. **Database per service** — pas de schéma partagé. Les données sont synchronisées par événements (Kafka) ou par appels API contrôlés.
3. **Sécurité centralisée, autorisation distribuée** — Keycloak est l'IdP unique ; chaque service est un Resource Server qui valide le JWT et applique ses propres règles RBAC.
4. **Specifications JPA exclusivement** — interdiction stricte des patterns `:param IS NULL OR field = :param`. Les filtres dynamiques sont construits via `Specification<T>` et `CriteriaBuilder`. Cela évite les problèmes de typage PostgreSQL liés aux paramètres null et garantit des plans d'exécution stables.
5. **Resilience-by-design** — Resilience4j (circuit breaker, retry, timeout, bulkhead) sur toute communication synchrone inter-services.
6. **Observabilité** — logs JSON structurés (Logback + Logstash encoder), correlation-id propagé, métriques Micrometer, traces OpenTelemetry-ready.
7. **Tests-first** — chaque module livre ses tests unitaires + intégration (Testcontainers) avec couverture Jacoco ≥ 80 % avant validation.
8. **Configuration externalisée** — Spring Cloud Config Server, profils par environnement (`dev`, `docker`, `prod`).

---

## 2. Vue d'ensemble du système

```
┌──────────────────────────────────────────────────────────────────────────┐
│                            UTILISATEURS                                  │
│         (ADMIN, DOCTOR, RECEPTIONIST — via navigateur)                   │
└───────────────────────────────┬──────────────────────────────────────────┘
                                │ HTTPS
                                ▼
                    ┌───────────────────────┐
                    │  Angular SPA (4200)   │
                    │  keycloak-angular     │
                    └───────────┬───────────┘
                                │  Bearer JWT
                                ▼
        ┌──────────────────────────────────────────────────┐
        │       API Gateway — Spring Cloud Gateway (8080)  │
        │  • Routing dynamique via Eureka                  │
        │  • Resource Server (validation JWT)              │
        │  • Rate limiting / CORS / correlation-id         │
        └───────┬───────────┬───────────┬──────────────────┘
                │           │           │
   ┌────────────┘           │           └────────────┐
   │                        │                        │
   ▼                        ▼                        ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ patient-service │  │  doctor-service │  │appointment-svc  │
│      (8081)     │  │     (8082)      │  │     (8083)      │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
   ┌─────▼─────┐        ┌─────▼─────┐        ┌─────▼─────┐
   │patient_db │        │ doctor_db │        │appoint_db │
   └───────────┘        └───────────┘        └───────────┘

┌──────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│medical-record-svc│  │ billing-service │  │notification-svc │
│      (8084)      │  │     (8085)      │  │     (8086)      │
└────────┬─────────┘  └────────┬────────┘  └────────┬────────┘
         │                     │                    │
   ┌─────▼──────┐         ┌────▼─────┐        ┌─────▼─────┐
   │medrec_db   │         │billing_db│        │notif_db   │
   └────────────┘         └──────────┘        └───────────┘

╔══════════════════════════════════════════════════════════════════╗
║                    INFRASTRUCTURE TRANSVERSALE                   ║
╠══════════════════════════════════════════════════════════════════╣
║  Eureka Server (8761)   │  Config Server (8888)                  ║
║  Keycloak (8180)        │  Kafka + Zookeeper (9092)              ║
║  PostgreSQL (5432)      │  Prometheus / Grafana (optionnel)      ║
╚══════════════════════════════════════════════════════════════════╝
```

---

## 3. Inventaire des composants

### 3.1 Services d'infrastructure

| Composant         | Port  | Rôle                                                       | Technologie                |
|-------------------|-------|------------------------------------------------------------|----------------------------|
| `config-server`   | 8888  | Configuration centralisée (Git-backed ou native)           | Spring Cloud Config        |
| `discovery-server`| 8761  | Service registry / discovery                               | Spring Cloud Netflix Eureka|
| `api-gateway`     | 8080  | Edge service, routing, sécurité périmétrique               | Spring Cloud Gateway (reactive) |
| `keycloak`        | 8180  | IdP OAuth2/OIDC                                            | Keycloak 24.x              |
| `kafka`           | 9092  | Message broker (events)                                    | Apache Kafka 3.7 (KRaft)   |
| `postgres`        | 5432  | SGBD                                                       | PostgreSQL 16              |

### 3.2 Microservices métier

| Service                  | Port  | Base de données     | Responsabilités principales                                          |
|--------------------------|-------|---------------------|----------------------------------------------------------------------|
| `patient-service`        | 8081  | `patient_db`        | CRUD patients, données démographiques, recherche avancée             |
| `doctor-service`         | 8082  | `doctor_db`         | Médecins, spécialités, plannings/disponibilités                      |
| `appointment-service`    | 8083  | `appointment_db`    | RDV, créneaux, statuts (PLANNED, CONFIRMED, CANCELLED, COMPLETED)    |
| `medical-record-service` | 8084  | `medical_record_db` | Dossiers médicaux, consultations, prescriptions, antécédents         |
| `billing-service`        | 8085  | `billing_db`        | Facturation, paiements, remboursements, exports comptables           |
| `notification-service`   | 8086  | `notification_db`   | Email/SMS asynchrone, consommateur Kafka, historique d'envois        |

### 3.3 Frontend

| Composant            | Port | Stack                                                                |
|----------------------|------|----------------------------------------------------------------------|
| `terangamed-frontend`| 4200 | Angular 17 + standalone components, keycloak-angular, Angular Material|

---

## 4. Modèle de domaine (Bounded Contexts)

Chaque service correspond à un **bounded context** au sens DDD. Les entités ne sont pas partagées entre services — quand un service a besoin d'une donnée d'un autre, il la référence par ID, et duplique localement les attributs strictement nécessaires (consistency eventual via Kafka).

### 4.1 Patient (patient-service)
- `Patient` : id, numéroDossier, civilité, nom, prénom, dateNaissance, sexe, téléphone, email, adresse, groupeSanguin, allergies, contactUrgence, statut.
- Recherche : par nom, prénom, téléphone, email, dateNaissance (range), groupeSanguin, statut → **Specifications**.

### 4.2 Doctor (doctor-service)
- `Doctor` : id, numéroOrdre, nom, prénom, spécialité, email, téléphone, statut.
- `Availability` : id, doctorId, jourSemaine, heureDébut, heureFin, slotDuration.
- Recherche : par spécialité, nom, statut → **Specifications**.

### 4.3 Appointment (appointment-service)
- `Appointment` : id, patientId (ref), doctorId (ref), patientNameSnapshot, doctorNameSnapshot, dateTime, duration, motif, statut, notes.
- Validation : pas de chevauchement pour un même médecin (contrainte applicative + index unique partiel).
- Communication : appel Feign vers `patient-service` et `doctor-service` à la création pour valider l'existence et capturer le snapshot ; publication d'événements `AppointmentCreated`, `AppointmentCancelled`, etc.
- Recherche : par patient, médecin, statut, période → **Specifications**.

### 4.4 Medical Record (medical-record-service)
- `MedicalRecord` : id, patientId, doctorId, appointmentId, dateConsultation, motif, diagnostic, observations, prescription (JSONB), antécédents.
- Sécurité renforcée : seul le médecin créateur ou un ADMIN peut modifier.

### 4.5 Billing (billing-service)
- `Invoice` : id, patientId, appointmentId, montantHT, tva, montantTTC, statut (DRAFT, ISSUED, PAID, CANCELLED), dateÉmission, datePaiement.
- `InvoiceLine` : id, invoiceId, description, quantité, prixUnitaire, total.
- Recherche : par patient, statut, période, montant → **Specifications**.

### 4.6 Notification (notification-service)
- `NotificationLog` : id, type (EMAIL/SMS), destinataire, sujet, contenu, statut (SENT, FAILED), tentatives, événementSource.
- Consomme : `appointment-events`, `billing-events`.
- Templates Thymeleaf pour les emails.

---

## 5. Sécurité Keycloak

### 5.1 Realm et clients

**Realm** : `terangamed`

| Client                  | Type           | Usage                                            |
|-------------------------|----------------|--------------------------------------------------|
| `terangamed-frontend`   | public         | SPA Angular, flow Authorization Code + PKCE      |
| `terangamed-gateway`    | confidential   | Gateway en tant que Resource Server              |
| `terangamed-backend`    | bearer-only    | Tous les microservices métier (validation JWT)   |
| `swagger-ui`            | public         | OAuth2 dans les UIs Swagger des services         |

### 5.2 Rôles (realm roles)

- `ADMIN` — accès complet, gestion des utilisateurs et configuration
- `DOCTOR` — lecture/écriture sur dossiers de ses patients, consultation planning
- `RECEPTIONIST` — gestion patients, RDV, facturation (sans accès aux dossiers médicaux complets)

### 5.3 Mapping rôles → endpoints (extrait)

| Endpoint                                  | ADMIN | DOCTOR | RECEPTIONIST |
|-------------------------------------------|:-----:|:------:|:------------:|
| `POST /api/patients`                      |   ✓   |   ✓    |      ✓       |
| `DELETE /api/patients/{id}`               |   ✓   |        |              |
| `GET /api/medical-records/{patientId}`    |   ✓   |   ✓    |              |
| `POST /api/medical-records`               |   ✓   |   ✓    |              |
| `POST /api/appointments`                  |   ✓   |   ✓    |      ✓       |
| `POST /api/invoices`                      |   ✓   |        |      ✓       |
| `GET /api/doctors`                        |   ✓   |   ✓    |      ✓       |

### 5.4 Propagation du JWT

- Le frontend obtient un JWT via Keycloak (PKCE).
- Le JWT est envoyé en `Authorization: Bearer <token>` à la Gateway.
- La Gateway valide le token, propage le header vers les microservices.
- Chaque microservice agit comme **Resource Server** (`spring-boot-starter-oauth2-resource-server`) et valide le JWT auprès de Keycloak (introspection ou JWK URL).
- En appel Feign inter-service, un `RequestInterceptor` propage l'`Authorization` courant. Pour les appels asynchrones initiés par un consumer Kafka, on utilise un **client_credentials** flow (compte machine).

### 5.5 Swagger + OAuth2

Chaque service expose `/swagger-ui.html` avec un schéma OAuth2 `authorizationCode` pointant vers le realm Keycloak. L'utilisateur clique sur **Authorize**, est redirigé vers Keycloak, se connecte, et le token est automatiquement injecté dans tous les `try it out`.

---

## 6. Communication inter-services

### 6.1 Synchrone (OpenFeign + Resilience4j)

Cas d'usage : `appointment-service` doit valider qu'un patient et un médecin existent avant de créer un RDV.

```
appointment-service ──Feign──▶ patient-service.findById(patientId)
appointment-service ──Feign──▶ doctor-service.findById(doctorId)
```

Configuration Resilience4j systématique :

```yaml
resilience4j:
  circuitbreaker:
    instances:
      patient-service:
        slidingWindowSize: 20
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
  retry:
    instances:
      patient-service:
        maxAttempts: 3
        waitDuration: 500ms
        retryExceptions: [java.io.IOException, feign.RetryableException]
  timelimiter:
    instances:
      patient-service:
        timeoutDuration: 3s
```

Chaque client Feign a un **fallback** explicite (renvoie `Optional.empty()` ou un DTO dégradé clairement marqué).

### 6.2 Asynchrone (Kafka)

Topics :

| Topic                    | Producteur            | Consommateurs                                         | Schéma payload                  |
|--------------------------|-----------------------|-------------------------------------------------------|---------------------------------|
| `appointment-events`     | appointment-service   | notification-service, billing-service                 | `AppointmentEvent` (CREATED, CANCELLED, COMPLETED) |
| `patient-events`         | patient-service       | medical-record-service (cache léger nom)              | `PatientEvent` (CREATED, UPDATED, DELETED) |
| `billing-events`         | billing-service       | notification-service                                  | `InvoiceEvent` (ISSUED, PAID)   |
| `notification-dlq`       | notification-service  | (monitoring)                                          | Dead-letter pour échecs          |

Sérialisation : JSON via Jackson (évolutif vers Avro + Schema Registry si besoin).
Idempotence : chaque event porte un `eventId` UUID, le consumer maintient une table de déduplication.

---

## 7. Persistance et Specifications JPA

### 7.1 Règle d'or

> **Aucune requête JPQL ne contiendra `:param IS NULL OR ...`**.
> Les filtres dynamiques sont construits exclusivement via `org.springframework.data.jpa.domain.Specification` et l'API Criteria.

### 7.2 Pattern de Specification (preview — détaillé en Étape 4)

```java
public final class PatientSpecifications {

    private PatientSpecifications() {}

    public static Specification<Patient> withCriteria(PatientSearchCriteria c) {
        return Specification
                .where(likeIgnoreCase("lastName", c.lastName()))
                .and(likeIgnoreCase("firstName", c.firstName()))
                .and(equalsField("status", c.status()))
                .and(equalsField("bloodGroup", c.bloodGroup()))
                .and(birthDateBetween(c.birthDateFrom(), c.birthDateTo()));
    }

    private static Specification<Patient> likeIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) return null; // ← null = ignoré, pas de IS NULL
        return (root, q, cb) -> cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%");
    }
    // ...
}
```

Avantages :
- pas de paramètre null injecté en SQL
- typage strict (PostgreSQL ne se plaint plus de `bytea` vs `varchar`)
- combinable, testable unitairement
- pagination/tri natifs via `Pageable`

### 7.3 Pagination et tri sécurisés

Whitelist explicite des champs triables par service (anti-injection / anti-leak) :

```java
private static final Set<String> SORTABLE_FIELDS =
        Set.of("lastName", "firstName", "birthDate", "createdAt");
```

Toute requête de tri sur un champ hors whitelist → `400 Bad Request`.

### 7.4 Migrations

**Flyway** par service. Convention : `V{version}__{description}.sql` dans `src/main/resources/db/migration`. Aucune génération automatique de schéma en production (`spring.jpa.hibernate.ddl-auto=validate`).

---

## 8. Observabilité

- **Logs** : Logback + `logstash-logback-encoder` → JSON, champs `service`, `traceId`, `spanId`, `userId`.
- **Correlation-id** : généré par la Gateway (`X-Correlation-Id`) et propagé via Feign interceptor + Kafka headers.
- **Métriques** : `spring-boot-starter-actuator` + Micrometer (`/actuator/prometheus` exposé).
- **Health** : `/actuator/health` lu par Docker healthchecks.

---

## 9. Stack technique figée

| Couche             | Technologie                       | Version |
|--------------------|-----------------------------------|---------|
| Langage backend    | Java                              | 21 (LTS)|
| Spring Boot        |                                   | 3.2.5   |
| Spring Cloud       |                                   | 2023.0.1|
| Build              | Maven (multi-module)              | 3.9+    |
| Base de données    | PostgreSQL                        | 16      |
| Migration          | Flyway                            | 10.x    |
| Mapping            | MapStruct                         | 1.5.5   |
| Validation         | Hibernate Validator (Jakarta)     | 8.x     |
| Tests              | JUnit 5, Mockito, AssertJ         |         |
| Tests intégration  | Testcontainers (PostgreSQL, Kafka)| 1.19.x  |
| Couverture         | Jacoco                            | 0.8.11  |
| Doc API            | springdoc-openapi                 | 2.5.0   |
| Sécurité           | Keycloak                          | 24.0    |
| Broker             | Apache Kafka                      | 3.7     |
| Frontend           | Angular                           | 17      |
| Conteneurisation   | Docker / Docker Compose v2        |         |

---

## 10. Structure des dossiers (cible)

```
terangamed-backend/
├── pom.xml                              # parent POM (gestion dépendances + plugins)
├── common-lib/                          # DTOs partagés, exceptions, sécurité commune
│   ├── pom.xml
│   └── src/main/java/com/terangamed/common/
│       ├── exception/
│       ├── security/
│       └── dto/
├── infrastructure/
│   ├── config-server/
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   └── src/main/resources/config/    # configs centralisées
│   ├── discovery-server/
│   │   ├── pom.xml
│   │   └── Dockerfile
│   └── api-gateway/
│       ├── pom.xml
│       └── Dockerfile
├── services/
│   ├── patient-service/
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   └── src/
│   │       ├── main/java/com/terangamed/patient/
│   │       │   ├── controller/
│   │       │   ├── service/
│   │       │   ├── repository/
│   │       │   ├── specification/
│   │       │   ├── dto/
│   │       │   ├── mapper/
│   │       │   ├── entity/
│   │       │   ├── exception/
│   │       │   ├── config/
│   │       │   └── PatientServiceApplication.java
│   │       ├── main/resources/
│   │       │   ├── application.yml
│   │       │   └── db/migration/
│   │       └── test/java/com/terangamed/patient/
│   │           ├── controller/      # @WebMvcTest
│   │           ├── service/         # unit
│   │           ├── specification/   # unit (in-memory)
│   │           └── integration/     # @SpringBootTest + Testcontainers
│   ├── doctor-service/
│   ├── appointment-service/
│   ├── medical-record-service/
│   ├── billing-service/
│   └── notification-service/
├── docker/
│   ├── docker-compose.yml
│   ├── docker-compose.dev.yml
│   ├── keycloak/
│   │   └── realm-export.json
│   └── postgres/
│       └── init-multiple-databases.sh
└── docs/
    ├── 01-ARCHITECTURE-GLOBALE.md       # ce document
    ├── 02-KEYCLOAK-SETUP.md
    ├── 03-API-CATALOG.md
    └── 04-RUNBOOK.md

terangamed-frontend/
├── package.json
├── angular.json
├── Dockerfile
└── src/
    ├── app/
    │   ├── core/                       # services transverses, interceptors, guards
    │   │   ├── auth/                   # keycloak.service, auth.guard, role.guard
    │   │   ├── interceptors/           # http error, correlation-id
    │   │   └── api/                    # clients HTTP
    │   ├── shared/                     # composants partagés, pipes
    │   ├── features/
    │   │   ├── patients/
    │   │   ├── doctors/
    │   │   ├── appointments/
    │   │   ├── medical-records/
    │   │   └── billing/
    │   └── layout/
    └── environments/
```

---

## 11. Plan d'exécution incrémental

| Étape | Livrable                                                                     | Statut         |
|-------|------------------------------------------------------------------------------|----------------|
| **1** | **Architecture globale (ce document)**                                       | ⏳ Validation  |
| 2     | Parent POM + `common-lib` + `config-server` + `discovery-server` + `api-gateway` |            |
| 3     | Keycloak — realm export, clients, rôles, utilisateurs de test                |                |
| 4     | `patient-service` complet (entité, Specifications, REST, sécurité, Swagger, tests ≥ 80 %) |   |
| 5     | `doctor-service` complet                                                     |                |
| 6     | `appointment-service` (avec Feign + Resilience4j)                            |                |
| 7     | `medical-record-service`                                                     |                |
| 8     | `billing-service`                                                            |                |
| 9     | `notification-service` + intégration Kafka end-to-end                        |                |
| 10    | Frontend Angular (auth Keycloak + modules CRUD)                              |                |
| 11    | `docker-compose` complet + healthchecks + bootstrap automatique              |                |
| 12    | Procédure de test Swagger + checklist de validation finale                   |                |

À chaque fin d'étape, je livre :
1. Le code et les tests
2. Les commandes de build et de run
3. Une démonstration des endpoints via Swagger
4. Le rapport Jacoco (≥ 80 %)
5. Une demande explicite de validation avant l'étape suivante

---

## 12. Décisions techniques nécessitant validation

Avant de commencer l'Étape 2, j'ai besoin que vous validiez ou corrigiez les points suivants :

1. **Java 21 et Spring Boot 3.2.5** — confirmez-vous ces versions ? (sinon Java 17 + Spring Boot 3.2 reste compatible)
2. **Kafka** dès le début ou plus tard ? Le sujet n'est pas mentionné explicitement dans votre brief, mais il est implicite pour `notification-service`. Je propose **Kafka dès l'étape 9**, après que les services métier soient stables.
3. **Config Server backend** : Git distant ou stockage natif (`file:`) dans le repo ? Je recommande **natif** pour cette phase (plus simple, parfaitement valide en dev/staging) ; migration Git triviale ensuite.
4. **Frontend Angular — UI library** : Angular Material, PrimeNG, ou Tailwind ? Je propose **Angular Material 17** (ergonomie médicale, accessibilité, dark mode out-of-the-box).
5. **Migrations DB** : Flyway (recommandé) ou Liquibase ? Flyway est plus simple et suffit largement ici.
6. **Realm Keycloak** : import automatique au démarrage via `realm-export.json` (recommandé pour la reproductibilité) ou configuration manuelle documentée ?
7. **Données de test** : voulez-vous un dataset de seed (patients/médecins fictifs) chargé via Flyway en profil `dev` ?
8. **Module commun** : OK pour un `common-lib` Maven shared (DTOs, exceptions, security utils) ? Certains puristes préfèrent zéro coupling — je recommande un `common-lib` minimaliste, strictement limité aux artefacts vraiment transverses.

---

## 13. Risques identifiés et mitigations

| Risque                                                                  | Mitigation                                                               |
|-------------------------------------------------------------------------|--------------------------------------------------------------------------|
| Sur-ingénierie pour un cabinet médical de petite taille                 | Démarrage monolithe modulaire envisageable — on assume le choix microservices comme exigence pédagogique/cible |
| Keycloak complexe à configurer manuellement                             | Realm exporté en JSON, importé automatiquement via `KC_IMPORT`           |
| Latence cumulée des appels Feign en cascade                             | Snapshots locaux (patientName/doctorName dans Appointment) + caching     |
| Inconsistance eventual entre services après update patient              | Event `PatientUpdated` consommé par services qui en dépendent            |
| Tests d'intégration lents avec Testcontainers                           | Containers réutilisés via `@Testcontainers(disabledWithoutDocker = true)` + reuse |
| Gestion des transactions distribuées                                    | Saga pattern (orchestré ou choreographié) — non requis en MVP, design prévu |

---

**Prochaine étape (sur validation) : Étape 2 — Parent POM, `common-lib`, `config-server`, `discovery-server`, `api-gateway`, avec leurs Dockerfiles et premiers tests.**
