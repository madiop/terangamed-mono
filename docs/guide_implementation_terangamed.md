# Guide étape par étape – Implémentation Terangamed

## Objectif
Construire une application de gestion de cabinet médical en microservices (Spring Boot + Spring Cloud + Angular + Keycloak) sans blocage.

---

## Phase 0 – Préparation

### Étape 0.1 – Installer les outils
- Java 17+
- Maven / Gradle
- Node.js + Angular CLI
- Docker + Docker Compose
- PostgreSQL

### Étape 0.2 – Créer la structure
```
terangamed-backend/
terangamed-frontend/
```

---

## Phase 1 – Infrastructure backend

### Étape 1.1 – Config Server
- Créer projet Spring Boot
- Ajouter Spring Cloud Config
- Externaliser configuration

### Étape 1.2 – Eureka Server
- Ajouter Spring Cloud Netflix Eureka
- Configurer discovery

### Étape 1.3 – API Gateway
- Spring Cloud Gateway
- Configurer routes dynamiques

---

## Phase 2 – Sécurité Keycloak

### Étape 2.1 – Lancer Keycloak (Docker)
- Créer realm
- Créer clients (frontend + backend)

### Étape 2.2 – Configurer Spring Security
- OAuth2 Resource Server
- JWT validation

### Étape 2.3 – Tester token
- Obtenir token via Keycloak
- Tester endpoint sécurisé

---

## Phase 3 – Microservices métier

### Étape 3.1 – patient-service
- Entité Patient
- Repository + Specifications
- Service + Controller
- Tests unitaires

### Étape 3.2 – doctor-service
(même structure)

### Étape 3.3 – appointment-service
- Gestion rendez-vous
- Relations patient / doctor

### Étape 3.4 – autres services
- billing
- medical-record

---

## Phase 4 – Specifications (IMPORTANT)

- Créer classe Specification par entité
- Gérer filtres dynamiques
- Ne jamais utiliser :param IS NULL

---

## Phase 5 – Swagger

- Ajouter springdoc-openapi
- Configurer OAuth2 Keycloak
- Tester endpoints

---

## Phase 6 – Communication inter-services

- OpenFeign ou WebClient
- Gestion timeout + retry
- Resilience4j

---

## Phase 7 – Frontend Angular

### Étape 7.1 – Initialisation
```
ng new terangamed-frontend
```

### Étape 7.2 – Intégration Keycloak
- Installer keycloak-js
- Guards

### Étape 7.3 – Modules
- patients
- doctors
- appointments

---

## Phase 8 – Base de données

- PostgreSQL par service
- Migration Flyway

---

## Phase 9 – Tests

- JUnit + Mockito
- Spring Boot Test
- Testcontainers
- Objectif ≥ 80%

---

## Phase 10 – Docker

- Dockerfile par service
- docker-compose global

---

## Phase 11 – Validation finale

- Tester via Swagger
- Vérifier sécurité
- Vérifier communication inter-services

---

## Checklist finale

- [ ] Auth Keycloak OK
- [ ] Swagger OK
- [ ] Microservices OK
- [ ] Tests ≥ 80%
- [ ] Docker OK

---

## Conseils

- Avancer service par service
- Tester à chaque étape
- Ne pas tout coder d’un coup

