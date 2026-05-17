# TerangaMed — Test de l'API via Swagger UI (Étape 10C)

Guide pratique pour tester chaque microservice métier via son interface Swagger UI, avec authentification OAuth2 Keycloak intégrée.

## 1. URLs Swagger UI par service

Chaque microservice expose son propre Swagger UI sur son port. La spec OpenAPI brute est disponible sur `/v3/api-docs` (utile pour `curl`/Postman/CI).

| Service | Swagger UI | OpenAPI JSON | Tag principal |
|---------|------------|--------------|---------------|
| patient-service | http://localhost:8081/swagger-ui.html | http://localhost:8081/v3/api-docs | Patients |
| doctor-service | http://localhost:8082/swagger-ui.html | http://localhost:8082/v3/api-docs | Doctors |
| appointment-service | http://localhost:8083/swagger-ui.html | http://localhost:8083/v3/api-docs | Appointments |
| medical-record-service | http://localhost:8084/swagger-ui.html | http://localhost:8084/v3/api-docs | MedicalRecords / Consultations / Antecedents / Prescriptions |
| notification-service | http://localhost:8086/swagger-ui.html | http://localhost:8086/v3/api-docs | Notifications |

> ℹ️ L'API Gateway (port 8080) n'expose pas de Swagger UI agrégé en V1. Cette intégration est trackée mais reportée — elle nécessite `springdoc-openapi-starter-webflux` et une refonte du routage de discovery.

## 2. Authentification dans Swagger

Chaque service propose **deux schémas** au choix dans le bouton **Authorize** :

| Schéma | Mécanisme | Quand l'utiliser |
|--------|-----------|------------------|
| `keycloak-password` | OAuth2 Password Flow — login direct dans Swagger | Cas standard — c'est le plus simple |
| `bearer-auth` | JWT Bearer collé manuellement | Si tu as déjà un token via curl ou Keycloak |

### 2.1 Login via `keycloak-password` (recommandé)

1. Ouvre Swagger UI d'un service (ex. http://localhost:8081/swagger-ui.html)
2. Clique sur **Authorize** (cadenas en haut à droite)
3. Section `keycloak-password (OAuth2, password)` — remplis :
   - `client_id` : `swagger-ui`
   - `client_secret` : *(laisser vide — client public)*
   - `username` : `admin`, `dr.martin` ou `reception` (cf. doc Keycloak §2)
   - `password` : `admin` / `doctor123` / `reception`
   - **Scopes** : cocher `openid`, `profile`, `email`
4. Clique **Authorize** → Swagger récupère le token et l'injecte sur chaque requête suivante
5. Ferme la modale — toutes les opérations sont maintenant authentifiées

> ⚠️ **Token-url** utilisé : `http://localhost:8180/realms/terangamed/protocol/openid-connect/token`. Si Keycloak n'est pas démarré, l'authent échoue avec un message générique côté Swagger.

### 2.2 Login via `bearer-auth` (fallback)

Si le flow Password ne fonctionne pas (firewall, CORS, etc.), récupère un JWT manuellement :

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8180/realms/terangamed/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=terangamed-frontend" \
  -d "username=dr.martin" \
  -d "password=doctor123" \
  -d "scope=openid profile email" | jq -r .access_token)

echo "$TOKEN"
```

Dans Swagger UI → **Authorize** → section `bearer-auth` → coller le JWT (sans préfixe `Bearer`, Swagger l'ajoute) → **Authorize**.

> ℹ️ Le token expire en 5 minutes (réglage Keycloak `Access Token Lifespan`). Si tu reçois un 401 après quelques minutes, refais la procédure.

## 3. Workflow type — création d'un dossier complet

Scénario de bout en bout pour tester l'intégration cross-services. À jouer dans cet ordre, avec `dr.martin` (rôle DOCTOR).

### 3.1 Créer un patient (patient-service:8081)

`POST /api/patients`

```json
{
  "firstName": "Aïssatou",
  "lastName": "Ndiaye",
  "birthDate": "1985-03-15",
  "gender": "FEMALE",
  "phone": "+221771234567",
  "email": "aissatou@example.sn",
  "preferredLanguage": "FR"
}
```

→ Réponse 201 — note le `id` retourné (appelons-le `PATIENT_ID`).

### 3.2 Créer un médecin (doctor-service:8082)

> ⚠️ Cet endpoint nécessite **rôle ADMIN** uniquement. Re-`Authorize` en tant que `admin/admin` avant d'envoyer.

`POST /api/doctors`

```json
{
  "firstName": "Jean",
  "lastName": "Martin",
  "specialty": "Médecine générale",
  "phone": "+221770000001",
  "email": "jean.martin@terangamed.local",
  "consultationPriceXof": 25000,
  "preferredCurrency": "XOF",
  "preferredPaymentMethod": "WAVE"
}
```

→ Réponse 201 — note le `id` retourné (`DOCTOR_ID`).

### 3.3 Créer un rendez-vous (appointment-service:8083)

Re-`Authorize` en `dr.martin` ou `reception`.

`POST /api/appointments`

```json
{
  "patientId": <PATIENT_ID>,
  "doctorId": <DOCTOR_ID>,
  "scheduledAt": "2026-06-15T10:00:00Z",
  "durationMinutes": 30,
  "reason": "Première consultation"
}
```

→ Réponse 201 — note le `id` retourné (`APPOINTMENT_ID`).

### 3.4 Créer un dossier médical + consultation (medical-record-service:8084)

#### a. Créer le dossier (s'il n'existe pas)

`POST /api/medical-records` :

```json
{
  "patientId": <PATIENT_ID>,
  "bloodType": "O_POSITIVE"
}
```

→ Note le `id` (`RECORD_ID`).

#### b. Créer la consultation

> ⚠️ Cet endpoint requiert le **header `X-Doctor-Id: <DOCTOR_ID>`** en V1 (résolution depuis JWT prévue à l'étape 8 — cf. `ConsultationController.resolveCurrentDoctorId`). Renseigne-le dans Swagger via la section "Parameters".

`POST /api/consultations` (avec header `X-Doctor-Id: <DOCTOR_ID>`)

```json
{
  "medicalRecordId": <RECORD_ID>,
  "appointmentId": <APPOINTMENT_ID>,
  "diagnosis": "Bilan de santé général",
  "notes": "Patient en bonne santé",
  "vitalSigns": {
    "bloodPressureSystolic": 120,
    "bloodPressureDiastolic": 80,
    "heartRate": 72,
    "temperature": 36.8
  }
}
```

→ Réponse 201 — note le `id` (`CONSULTATION_ID`).

#### c. Ajouter un antécédent

`POST /api/antecedents`

```json
{
  "medicalRecordId": <RECORD_ID>,
  "type": "ALLERGY",
  "label": "Pénicilline",
  "severity": "HIGH",
  "active": true
}
```

#### d. Créer une ordonnance

`POST /api/prescriptions/by-consultation/<CONSULTATION_ID>`

```json
{
  "validUntil": "2026-07-15",
  "instructions": "À prendre pendant les repas",
  "lines": [
    {
      "medicationName": "Paracétamol 500mg",
      "dosage": "1 comprimé",
      "frequency": "3 fois par jour",
      "durationDays": 5
    }
  ]
}
```

#### e. Signer la consultation (terminal)

> ⚠️ Une fois signée, la consultation et son ordonnance deviennent **immuables** (409 sur tout PUT/DELETE). C'est volontaire — traçabilité médico-légale.

`POST /api/consultations/<CONSULTATION_ID>/sign`

### 3.5 Vérifier les notifications Kafka (notification-service:8086)

> ⚠️ Endpoint **ADMIN uniquement**. Re-`Authorize` en `admin/admin`.

`GET /api/notifications?page=0&size=20&sort=occurredAt,desc`

Tu dois voir au moins 4 entrées générées par le scénario :
- `PatientCreated` (étape 3.1)
- `AppointmentScheduled` (étape 3.3)
- `ConsultationCreated` + `ConsultationSigned` (étape 3.4 b/e)
- `PrescriptionCreated` (étape 3.4 d)

Si la liste est vide, vérifie que Kafka tourne (`docker compose ps` dans `terangamed-backend/docker`).

## 4. Troubleshooting

### 4.1 401 sur tous les endpoints

- **Token expiré** : re-`Authorize` (5 minutes par défaut)
- **Mauvais user** : certains endpoints exigent un rôle précis (cf. `@PreAuthorize` dans le controller — visible dans Swagger via la description de l'opération)
- **Keycloak down** : `curl -s http://localhost:8180/realms/terangamed/.well-known/openid-configuration` doit renvoyer un JSON. Si timeout → relancer Keycloak

### 4.2 403 alors que je suis authentifié

Le rôle de l'utilisateur ne correspond pas à `@PreAuthorize`. Vérifie le mapping :

| Rôle Keycloak | Endpoints accessibles |
|---------------|----------------------|
| `ADMIN` | Tout — y compris création médecins, audit notifications |
| `DOCTOR` | CRUD patients (sauf delete), CRUD RDV, CRUD consultations, antécédents, ordonnances |
| `RECEPTIONIST` | CRUD patients (sauf delete), CRUD RDV uniquement |
| `PATIENT` | Lecture seule de son propre dossier (filtrage côté `MedicalRecordAccessChecker`) |

### 4.3 CORS sur Swagger UI

Si l'auth échoue avec `Network Error` dans la console navigateur :

- Vérifier que Keycloak a bien `http://localhost:8081/*`, `:8082/*`, etc. dans les **Web Origins** du client `swagger-ui`
- Cf. `terangamed-realm.json` — par défaut les 5 services sont déclarés

### 4.4 409 CONFLICT inattendu

- `Patient with email X already exists` → utiliser un email unique ou laisser vide (cf. dette #57 résolue par index partiel)
- `Appointment slot overlap` → choisir un créneau libre
- `Consultation already signed` → comportement attendu, signature terminale
- `Doctor not active` → activer via `PATCH /api/doctors/<id>/status` en ADMIN

### 4.5 Endpoint pas visible dans Swagger UI

- **Hard refresh** du navigateur (Ctrl+Shift+R / Cmd+Shift+R) — Swagger met en cache la spec
- Vérifier que le controller est bien sous `@RestController` + `@RequestMapping`
- Lancer le smoke script : `./scripts/swagger-smoke.sh --service <name> --verbose` liste tous les paths exposés

## 5. Validation automatisée — `swagger-smoke.sh`

Script bash qui vérifie en moins de 30s la santé Swagger des 5 services :

```bash
./scripts/swagger-smoke.sh                # tous les services
./scripts/swagger-smoke.sh --service patient
./scripts/swagger-smoke.sh --verbose      # liste les paths exposés
./scripts/swagger-smoke.sh --help
```

Pour chaque service, le script vérifie :
1. `/v3/api-docs` accessible et JSON valide
2. Nombre de paths ≥ seuil (anti-régression silencieuse)
3. SecurityScheme `keycloak-password` présent
4. `/swagger-ui.html` accessible

Exit codes : `0` si tout OK, `1` si au moins un service échoue, `2` si pré-requis manquants.

## 6. Démarrage rapide pour tester

```bash
# 1. Démarrer l'infrastructure
cd terangamed-backend/docker
docker compose up -d postgres keycloak kafka schema-registry kafka-ui

# 2. Démarrer les microservices (dans 5 terminaux ou IntelliJ)
cd ..
./mvnw -pl services/patient-service spring-boot:run
./mvnw -pl services/doctor-service spring-boot:run
./mvnw -pl services/appointment-service spring-boot:run
./mvnw -pl services/medical-record-service spring-boot:run
./mvnw -pl services/notification-service spring-boot:run

# 3. Vérifier que tout est up
./scripts/swagger-smoke.sh

# 4. Ouvrir Swagger UI
open http://localhost:8081/swagger-ui.html
```

## 7. Références

- Doc Keycloak realm : [`03-KEYCLOAK-SETUP.md`](./03-KEYCLOAK-SETUP.md)
- Doc patient-service : [`04-PATIENT-SERVICE.md`](./04-PATIENT-SERVICE.md)
- Doc Kafka : [`08-KAFKA-INTEGRATION.md`](./08-KAFKA-INTEGRATION.md)
- Spec OpenAPI 3.0.3 : https://spec.openapis.org/oas/v3.0.3
- springdoc-openapi : https://springdoc.org/
