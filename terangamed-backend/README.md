# TerangaMed — Backend

Microservices Spring Boot pour la gestion d'un cabinet médical.

## Stack

- **Java 21** (LTS)
- **Spring Boot** 3.2.5
- **Spring Cloud** 2023.0.1
- **PostgreSQL** 16
- **Keycloak** 24 (OAuth2 / OIDC)
- **Maven** 3.9+ multi-module
- **Tests** : JUnit 5 + Mockito + Testcontainers — couverture Jacoco ≥ 80 %

## Prérequis

- JDK 21 (`java -version` doit afficher 21)
- Maven 3.9+ (ou utiliser le wrapper `./mvnw`)
- Docker + Docker Compose (pour Testcontainers et docker-compose)

### Docker Desktop sur macOS — configuration Testcontainers

Sur macOS récent, Docker Desktop ne crée plus `/var/run/docker.sock` par défaut.
Testcontainers ne le trouve pas → erreur « Could not find a valid Docker environment ».

**Solution recommandée** : dans Docker Desktop → Settings → Advanced, cocher
**« Allow the default Docker socket to be used (requires password) »**, puis Apply & Restart.

**Alternative** : exporter `DOCKER_HOST` pointant vers le socket utilisateur :
```bash
echo 'export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock"' >> ~/.zshrc
source ~/.zshrc
```

### Cas spécifique : Docker Desktop 4.71+ — socket de redirection

À partir de Docker Desktop 4.71, la socket `~/.docker/run/docker.sock` ne sert plus que de redirecteur (elle répond Status 400 avec un label `com.docker.desktop.address=...` pointant vers la vraie socket API). Testcontainers ne suit pas cette redirection automatiquement.

**Solution** — pointer directement vers la socket complète :
```bash
# Récupérer le path réel
DOCKER_REAL_SOCKET=$(docker info --format '{{range .Labels}}{{.}}{{"\n"}}{{end}}' \
  | grep 'com.docker.desktop.address' \
  | sed 's|com.docker.desktop.address=unix://||')

# Configurer Testcontainers
cat > "$HOME/.testcontainers.properties" <<EOF
docker.host=unix://$DOCKER_REAL_SOCKET
EOF
```

## Modules livrés

| Étape | Module / Phase                                           | Statut |
|-------|-----------------------------------------------------------|--------|
| 2.1   | `common-lib`                                              | ✅ |
| 2.2   | `infrastructure/config-server`                            | ✅ |
| 2.3   | `infrastructure/discovery-server`                         | ✅ |
| 2.4   | `infrastructure/api-gateway`                              | ✅ |
| 3     | `docker/keycloak/` (realm + compose)                      | ✅ |
| 4     | `services/patient-service` (modèle de référence)          | ✅ |
| 5     | `services/doctor-service`                                 | ✅ |
| 6     | `services/appointment-service` (Feign + Resilience4j)     | ✅ |
| —     | Phase finance — Currency XOF + PaymentMethod (Wave, etc.) | ✅ |
| 7     | `services/medical-record-service` (JSONB + signature)     | ✅ |
| 8.1   | Infrastructure Kafka (KRaft + Schema Registry + UI)       | ✅ |
| 8.2   | Pattern Outbox (common-lib)                               | ✅ |
| 8.3   | Producer patient-service (3 events)                       | ✅ |
| 8.4   | Producers appointment + medical (5 + 3 events)            | ✅ |
| —     | Producer doctor-service (3 events)                        | ✅ |
| 8.5   | `services/notification-service` (consumer 4 topics)       | ✅ |
| 8.6   | Validation finale Kafka + smoke test e2e                  | ✅ |
| 9     | Frontend Angular                                          | ⏳ |
| —     | `services/billing-service`                                | ⏳ |

**Bilan Kafka** : 14 events métier sur 4 topics (patient/doctor/appointment/medical),
consommés par notification-service avec idempotence + retry + DLT. Voir
[docs/08-KAFKA-INTEGRATION.md](docs/08-KAFKA-INTEGRATION.md) pour le smoke test.

## Démarrer l'infrastructure (Keycloak + PostgreSQL)

```bash
cd docker
cp .env.example .env       # une seule fois
docker compose up -d postgres keycloak
```

Voir la [documentation Keycloak complète](docs/03-KEYCLOAK-SETUP.md) — utilisateurs de test, obtention de JWT, intégration Swagger.

## Structure

```
terangamed-backend/
├── pom.xml                  # parent : versions BOMs, plugins, jacoco 80%
├── common-lib/              # exceptions, security, pagination
└── (à venir)
    ├── infrastructure/
    │   ├── config-server/
    │   ├── discovery-server/
    │   └── api-gateway/
    └── services/
        ├── patient-service/
        ├── doctor-service/
        ├── appointment-service/
        ├── medical-record-service/
        ├── billing-service/
        └── notification-service/
```

## Build

```bash
# Compilation + tests + couverture
./mvnw clean verify

# Itération rapide (skip jacoco check)
./mvnw clean verify -Pfast

# Module unique
./mvnw -pl common-lib clean verify
```

Le rapport Jacoco est disponible dans `<module>/target/site/jacoco/index.html` après `verify`.

## Conventions

### Exceptions

Toute erreur métier hérite de `BaseException` (dans `common-lib`). Le `GlobalExceptionHandler`
auto-enregistré dans chaque service mappe ces exceptions au format unifié `ApiError`.

```java
throw new ResourceNotFoundException("Patient", id);
throw new ConflictException("APPOINTMENT_OVERLAP", "Slot already taken");
throw new BadRequestException("INVALID_DATE_RANGE", "From > To");
```

### Filtres dynamiques (Specifications JPA)

**Règle absolue** : aucune requête JPQL avec `:param IS NULL OR field = :param`.
Tous les filtres dynamiques passent par des `Specification<Entity>` qui retournent `null`
quand le critère est vide (ce qui s'élimine automatiquement de la composition).

```java
public static Specification<Patient> withCriteria(PatientSearchCriteria c) {
    return Specification
            .where(likeIgnoreCase("lastName", c.lastName()))
            .and(equalsField("status", c.status()));
}

private static Specification<Patient> likeIgnoreCase(String field, String value) {
    if (value == null || value.isBlank()) return null; // ignoré dans la composition
    return (root, q, cb) -> cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%");
}
```

### Tri sécurisé

Utilisation systématique de `SortValidator.sanitize(pageable, ALLOWED_FIELDS)` avant
de passer un `Pageable` au repository — protection contre tri sur champs sensibles ou
non-indexés.

### Auditing JPA (createdAt / updatedAt / createdBy / updatedBy)

Les entités métier héritent de `BaseAuditEntity` (fourni par `common-lib`) pour bénéficier
des 4 colonnes d'audit standard. **Important** : chaque service doit activer l'auditing
sur sa classe principale, sinon les colonnes resteront à `null`.

```java
@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "terangamedAuditorAware")
public class PatientServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PatientServiceApplication.class, args);
    }
}
```

Le bean `terangamedAuditorAware` est auto-configuré par `common-lib` (extraction de
l'utilisateur courant depuis le JWT). Pour le désactiver dans un service spécifique :
`terangamed.jpa.auditing.enabled=false`.

> Pourquoi `@EnableJpaAuditing` n'est pas auto-activé par `common-lib` ? Cette annotation
> instancie immédiatement le `JpaMetamodelMappingContext` qui exige au moins une entité
> JPA. Dans une lib partagée (sans entités) ou dans un test sans `EntityManagerFactory`,
> son activation provoquerait l'erreur « JPA metamodel must not be empty ». On laisse
> donc le service consommateur la déclencher après scan de ses propres entités.

### Sécurité

Chaque service métier importe `common-lib` et utilise `JwtAuthConverter` dans sa
`SecurityFilterChain` pour extraire les rôles Keycloak. Configuration :

```yaml
terangamed:
  security:
    jwt:
      principal-attribute: preferred_username
      resource-id: terangamed-backend

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/terangamed}
```

Endpoints sécurisés via `@PreAuthorize(SecurityRoles.HAS_ADMIN_OR_DOCTOR)`.
