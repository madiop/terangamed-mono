# TerangaMed — patient-service (Étape 4)

## 1. Vue d'ensemble

Le `patient-service` est le **microservice modèle de référence** de TerangaMed. Sa structure et ses patterns serviront de gabarit aux 5 autres services métier (doctor, appointment, medical-record, billing, notification).

| Élément | Valeur |
|---------|--------|
| **Port** | 8081 |
| **Base de données** | `patient_db` (PostgreSQL 16) |
| **Authentification** | JWT Resource Server Keycloak (realm `terangamed`) |
| **Documentation API** | http://localhost:8081/swagger-ui.html |
| **Tests livrés** | 56 (foundation 21 + service 18 + controller 17) |
| **Couverture Jacoco** | ≥ 80 % (entity/dto/config/mapper exclus) |

## 2. Structure du module

```
services/patient-service/
├── pom.xml
├── Dockerfile                                  ← multi-stage, JRE Alpine, healthcheck
└── src/
    ├── main/
    │   ├── java/com/terangamed/patient/
    │   │   ├── PatientServiceApplication.java
    │   │   ├── config/
    │   │   │   ├── JpaConfig.java              ← @EnableJpaAuditing isolé
    │   │   │   ├── SecurityConfig.java         ← Resource Server + @EnableMethodSecurity
    │   │   │   └── OpenApiConfig.java          ← Swagger OAuth2 Keycloak
    │   │   ├── controller/
    │   │   │   └── PatientController.java      ← 7 endpoints REST
    │   │   ├── service/
    │   │   │   └── PatientService.java         ← logique métier
    │   │   ├── repository/
    │   │   │   └── PatientRepository.java      ← JpaRepository + JpaSpecificationExecutor
    │   │   ├── specification/
    │   │   │   └── PatientSpecifications.java  ← filtres dynamiques (pas de :param IS NULL)
    │   │   ├── mapper/
    │   │   │   └── PatientMapper.java          ← MapStruct
    │   │   ├── entity/
    │   │   │   ├── Patient.java                 ← extends BaseAuditEntity
    │   │   │   ├── Civility, Gender, BloodGroup, PatientStatus (enums)
    │   │   └── dto/
    │   │       ├── PatientDto.java
    │   │       ├── CreatePatientRequest.java
    │   │       ├── UpdatePatientRequest.java
    │   │       └── PatientSearchCriteria.java
    │   └── resources/
    │       ├── application.yml
    │       ├── db/migration/
    │       │   └── V1__create_patient_table.sql
    │       └── db/migration-dev/
    │           └── V900__dev_seed_patients.sql ← chargé en profil 'dev' uniquement
    └── test/                                    ← 56 tests JUnit + Mockito + Testcontainers
```

## 3. Endpoints REST

| Méthode | Path | Rôles | Description |
|---------|------|-------|-------------|
| GET | `/api/patients` | Staff | Recherche paginée (critères dynamiques + tri whitelist) |
| GET | `/api/patients/{id}` | Staff | Détail d'un patient |
| GET | `/api/patients/by-mrn/{mrn}` | Staff | Lookup par n° dossier `MR-YYYY-NNNNN` |
| POST | `/api/patients` | Staff | Création (génère MRN + statut ACTIVE) |
| PUT | `/api/patients/{id}` | Staff | Mise à jour partielle |
| POST | `/api/patients/{id}/archive` | ADMIN, RECEPTIONIST | Archivage logique (idempotent) |
| DELETE | `/api/patients/{id}` | ADMIN | Suppression physique |

> **Staff** = ADMIN ∪ DOCTOR ∪ RECEPTIONIST.

Format d'erreur unifié : `ApiError` (de `common-lib`) — inclut timestamp, code, message, path, correlationId, violations (Bean Validation).

## 4. Démarrage local — étape par étape

### 4.1 Pré-requis
- Docker Desktop lancé (avec « Allow the default Docker socket » activé sur macOS — cf. README backend)
- JDK 21
- `cp docker/.env.example docker/.env` (une fois)

### 4.2 Tout démarrer en une commande

```bash
cd terangamed-backend/docker
docker compose up -d --build
```

Suivre les logs jusqu'à voir tous les services healthy :

```bash
docker compose ps                              # statut + health
docker compose logs -f patient-service         # logs Flyway → Hibernate → started
```

Ordre de démarrage attendu (≈ 2 minutes total) :
1. `postgres` → healthy en ~10s
2. `keycloak` → healthy en ~45s (import du realm)
3. `config-server` → healthy en ~20s
4. `discovery-server` → healthy en ~20s
5. `api-gateway` → healthy en ~30s
6. `patient-service` → healthy en ~40s

### 4.3 Vérifications rapides

```bash
# Patient-service est enregistré dans Eureka
curl -u eureka:eureka http://localhost:8761/eureka/apps/PATIENT-SERVICE | grep status

# Health propre
curl http://localhost:8081/actuator/health
# {"status":"UP",...}

# Routes Gateway
curl http://localhost:8080/actuator/gateway/routes | jq '.[] | {id, uri}'

# Patients seedés visibles via la DB
docker exec terangamed-postgres psql -U terangamed -d patient_db \
  -c "SELECT medical_record_number, last_name, first_name, status FROM patients;"
# Doit lister 6 patients fictifs (MR-2026-00001 à MR-2026-00005 + MR-2025-00099 archivé)
```

## 5. Tester l'API via Swagger UI

### 5.1 Ouvrir l'UI

http://localhost:8081/swagger-ui.html

### 5.2 S'authentifier

1. Cliquer **Authorize** (cadenas en haut à droite)
2. `client_id` : `swagger-ui` (pré-rempli)
3. `client_secret` : laisser vide
4. Cocher les scopes `openid profile email`
5. Cliquer **Authorize** → redirection vers Keycloak
6. Se connecter avec un compte de test :
   - `dr.martin` / `doctor123` (DOCTOR)
   - `admin` / `admin` (ADMIN)
   - `reception` / `reception` (RECEPTIONIST)
7. Retour automatique sur Swagger — JWT injecté dans toutes les requêtes

### 5.3 Tester les endpoints

- **GET /api/patients** → liste paginée des 6 seeds
- **GET /api/patients/1** → détails du patient #1
- **POST /api/patients** → créer un nouveau patient (le MRN est généré automatiquement)
- **POST /api/patients/{id}/archive** → tester avec `dr.martin` → 403 ; avec `admin` → 204

### 5.4 Récupérer un JWT manuellement (curl)

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/terangamed/protocol/openid-connect/token \
  -d "grant_type=password" \
  -d "client_id=terangamed-frontend" \
  -d "username=dr.martin" \
  -d "password=doctor123" \
  | jq -r '.access_token')

curl -H "Authorization: Bearer $TOKEN" \
     http://localhost:8080/api/patients?lastName=diop
```

## 6. Tests automatisés

```bash
cd terangamed-backend
./mvnw -pl services/patient-service -am clean verify
```

Tests par couche :

| Couche | Fichier | Type | Cas |
|--------|---------|------|-----|
| Specifications | `PatientSpecificationsTest` | `@DataJpaTest` + Testcontainers PostgreSQL | 14 |
| Repository | `PatientRepositoryTest` | `@DataJpaTest` + Testcontainers PostgreSQL | 7 |
| Service | `PatientServiceTest` | Mockito + MapStruct réel | 18 |
| Controller | `PatientControllerTest` | `@WebMvcTest` + `@WithMockUser` | 17 |

**Total : 56 tests**, couverture Jacoco ≥ 80 % (entity, dto, config, mapper, *Application exclus).

## 7. Patterns clés à reproduire dans les autres services

1. **Specifications JPA** — pattern `null = critère ignoré`, helpers `likeIgnoreCase` / `equalsField` / `betweenDate`. Aucune requête JPQL avec `:param IS NULL OR ...`.
2. **DTOs en records** — un par cas d'usage : `Dto`, `CreateRequest`, `UpdateRequest`, `SearchCriteria`. Validation Bean Validation sur les composants.
3. **Mapper MapStruct** — `componentModel = "spring"`, `unmappedTargetPolicy = IGNORE`, `nullValuePropertyMappingStrategy = IGNORE` pour `updateEntity` (partial update).
4. **Service `@Transactional` (read/write)** — méthodes de lecture marquées `@Transactional(readOnly = true)`.
5. **`SortValidator.sanitize`** — appelé avant chaque `findAll(specification, pageable)` pour bloquer les tris sur champs sensibles.
6. **`@EnableJpaAuditing` dans une classe dédiée** (`JpaConfig.java`) — pas sur la classe `*Application`, sinon `@WebMvcTest` plante sur `jpaMappingContext`.
7. **`SecurityConfig` avec `@EnableMethodSecurity`** + `@PreAuthorize(SecurityRoles.HAS_*)` au niveau méthode du contrôleur.
8. **`OpenApiConfig` avec OAuth2 Keycloak + PKCE** + 2 servers (Gateway + direct).
9. **Tests** : `@DataJpaTest` pour data layer (Testcontainers + `@Import(JpaConfig.class)`), Mockito pur pour service, `@WebMvcTest` + `TestSecurityConfig` interne pour controller.
10. **Dockerfile multi-stage** — builder JDK + runtime JRE, user non-root, healthcheck, `MaxRAMPercentage=75`.

## 8. Checklist de validation

- [x] **Architecture en couches** : controller → service → repository — pas de cross-cutting
- [x] **Specifications uniquement** pour les filtres dynamiques (pas de `:param IS NULL OR …`)
- [x] **Validation Bean Validation** sur les requêtes (NotNull/NotBlank/Size/Past/Email)
- [x] **Pagination + tri whitelisté** via `SortValidator`
- [x] **Sécurité** : Resource Server JWT Keycloak + `@PreAuthorize` par rôle
- [x] **Audit** : `created_at`, `updated_at`, `created_by`, `updated_by` auto-peuplés
- [x] **MRN auto-généré** au format `MR-YYYY-NNNNN`
- [x] **Email unique** vérifié à la création et à la mise à jour
- [x] **Statuts** : ACTIVE / INACTIVE / ARCHIVED, archivage idempotent
- [x] **Verrouillage optimiste** via `@Version`
- [x] **Migrations Flyway** : V1 schéma, V900 seed dev (idempotent ON CONFLICT)
- [x] **Swagger OAuth2 fonctionnel** (testable via UI Swagger après login Keycloak)
- [x] **Tests ≥ 80 %** (56 tests : data layer + service + controller)
- [x] **Dockerfile** multi-stage, user non-root, healthcheck
- [x] **docker-compose** : intégration complète postgres + keycloak + config + eureka + gateway + patient-service
- [x] **Logs structurés** : `com.terangamed.patient` en DEBUG, correlation-id propagé via Gateway
- [x] **Errors uniformes** : `ApiError` JSON (timestamp, code, path, violations)

## 9. Prochains services (Étapes 5-9)

Les 5 autres services suivent le même gabarit. Pour chacun, dérouler :

1. Module Maven `services/{name}-service/` ajouté au parent POM
2. Entité + enums + Specifications dynamiques
3. Repository + migration Flyway
4. DTOs + Mapper + Service avec règles métier propres
5. Controller + Security + Swagger OAuth2
6. Tests ≥ 80 % (data + service + controller)
7. Dockerfile + entrée docker-compose
8. Configuration spécifique dans `config-repo/{name}-service.yml`

| Service | Port | DB | Particularité |
|---------|------|-----|---------------|
| `doctor-service` | 8082 | `doctor_db` | + plannings/disponibilités |
| `appointment-service` | 8083 | `appointment_db` | + Feign vers patient + doctor + Resilience4j |
| `medical-record-service` | 8084 | `medical_record_db` | + sécurité renforcée (DOCTOR créateur seul peut modifier) |
| `billing-service` | 8085 | `billing_db` | + invoiceLines + statut paiement |
| `notification-service` | 8086 | `notification_db` | + consommateur Kafka, templates Thymeleaf |
