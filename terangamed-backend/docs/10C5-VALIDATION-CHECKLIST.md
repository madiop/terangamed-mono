# TerangaMed — Étape 10C.5 — Checklist de validation manuelle Swagger UI

> **Objectif** : valider de bout en bout, via Swagger UI, que les 5 microservices répondent comme attendu sur un scénario réaliste de cabinet médical, et que les invariants critiques (auth, rôles, signature consultation, overlap RDV, dette #57 email vide) tiennent.
>
> **Durée estimée** : ~45 min (incluant démarrage infra). Faisable en une session.
>
> **Mode d'emploi** : ouvrir ce fichier dans un éditeur Markdown qui supporte les cases à cocher GitHub-style (VS Code / IntelliJ / GitHub.com), cocher au fur et à mesure. Noter les anomalies en §6.
>
> **Pré-requis logiciels** : Docker 24+, Java 21, Maven (via wrapper), `curl`, `jq`, un navigateur récent (Chrome/Firefox/Edge).
>
> ⚠️ **À lire avant de commencer** : les payloads ci-dessous sont alignés sur le **code actuel**. Le doc utilisateur `10-SWAGGER-API.md` (étape 10C.4) contient quelques exemples obsolètes signalés en §7.2 — ce fichier-ci fait foi pour la validation 10C.5.

---

## §1. Pré-requis — Démarrage de l'infrastructure

### 1.1. Vérifications préalables (host)

- [ ] Docker tourne : `docker info | head -5` répond sans erreur
- [ ] Ports libres : `lsof -nP -iTCP -sTCP:LISTEN -P | egrep ':(5432|8080|8081|8082|8083|8084|8086|8090|8180|8761|8888|9092)\s' || echo "OK aucun conflit"`
- [ ] `jq` installé : `jq --version` (sinon : `brew install jq` / `apt install jq`)
- [ ] Java 21 actif : `java -version 2>&1 | head -1` doit afficher `21`

### 1.2. Démarrer les conteneurs d'infrastructure

```bash
cd "$(pwd | sed 's|/[^/]*$||')/terangamed-backend/docker" 2>/dev/null || cd terangamed-backend/docker
docker compose up -d postgres keycloak kafka schema-registry kafka-ui
```

- [ ] La commande retourne sans erreur
- [ ] `docker compose ps` montre les 5 conteneurs en état `running` / `healthy`

### 1.3. Readiness des composants externes

Polling jusqu'à OK (timeout 60s) — copier-coller :

```bash
echo -n "Postgres "; for i in $(seq 1 30); do docker compose exec -T postgres pg_isready -U postgres >/dev/null 2>&1 && { echo "OK"; break; }; sleep 2; echo -n "."; done

echo -n "Keycloak realm "; for i in $(seq 1 60); do curl -fs http://localhost:8180/realms/terangamed/.well-known/openid-configuration >/dev/null && { echo "OK"; break; }; sleep 2; echo -n "."; done

echo -n "Kafka broker "; for i in $(seq 1 30); do docker compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list >/dev/null 2>&1 && { echo "OK"; break; }; sleep 2; echo -n "."; done

echo -n "Schema Registry "; for i in $(seq 1 30); do curl -fs http://localhost:8081/subjects >/dev/null && { echo "OK"; break; }; sleep 2; echo -n "."; done
```

- [ ] Les 4 lignes affichent `OK` (et non un échec après 30/60 itérations)
- [ ] **Si Keycloak ne répond pas** : `docker compose logs keycloak | tail -50` — le realm `terangamed` doit être importé au boot. Si manquant, vérifier le mount `./keycloak/realm/terangamed-realm.json` dans `docker-compose.yml`.

### 1.4. Démarrer config-server + discovery-server EN PREMIER

> ⚠️ **L'ordre compte**. Sans `discovery-server` (Eureka) actif, les appels Feign cross-services
> (appointment → patient/doctor, medical-record → patient/doctor) échouent et le circuit breaker
> Resilience4j renvoie `409 PATIENT_SERVICE_UNAVAILABLE` au lieu de la vraie réponse.
> Sans `config-server`, les services métier peuvent démarrer en mode dégradé (fail-fast: false)
> mais perdent leurs surcharges spécifiques (datasource pool, logging, etc.).

```bash
cd terangamed-backend/

# Terminal 1 — config-server (port 8888)
./mvnw -pl infrastructure/config-server spring-boot:run
# Attendre : "Started ConfigServerApplication"
# Sanity : curl -fs http://localhost:8888/actuator/health | jq .

# Terminal 2 — discovery-server / Eureka (port 8761)
./mvnw -pl infrastructure/discovery-server spring-boot:run
# Attendre : "Started DiscoveryServerApplication"
# Sanity : curl -fs http://localhost:8761/actuator/health | jq .
```

- [ ] config-server : `actuator/health` → `{"status":"UP"}`
- [ ] discovery-server : `actuator/health` → `{"status":"UP"}`

### 1.5. Démarrer les 5 microservices Spring Boot

Choix recommandé : 5 terminaux séparés (logs lisibles indépendamment). Alternative : IntelliJ "Run Multiple".

> 💡 **Astuce** : après tout edit d'une `@Configuration` (OpenApiConfig, SecurityConfig, KafkaProducerConfig…),
> faire `./mvnw -pl services/X -am clean install -DskipTests` AVANT le `spring-boot:run` —
> sinon Maven peut sauter la recompilation et le service tourne sur l'ancien `.class`.

```bash
# Depuis terangamed-backend/
./mvnw -pl services/patient-service        spring-boot:run     # terminal 3
./mvnw -pl services/doctor-service         spring-boot:run     # terminal 4
./mvnw -pl services/appointment-service    spring-boot:run     # terminal 5
./mvnw -pl services/medical-record-service spring-boot:run     # terminal 6
./mvnw -pl services/notification-service   spring-boot:run     # terminal 7
```

- [ ] Les 5 services affichent `Started ...Application in N.NNN seconds` dans leurs logs
- [ ] Aucune `BeanCreationException` / `Connection refused` / `Caused by: org.flywaydb`

### 1.5.bis Validation Eureka — les 5 microservices doivent être registered

Compter ~30 s entre le `Started` d'un service et son apparition dans Eureka.

```bash
curl -s -u eureka:eureka http://localhost:8761/eureka/apps -H "Accept: application/json" \
  | jq -r '.applications.application[]?.name' | sort
```

- [ ] Les 5 noms apparaissent (en MAJUSCULES, convention Eureka) :
  - [ ] `PATIENT-SERVICE`
  - [ ] `DOCTOR-SERVICE`
  - [ ] `APPOINTMENT-SERVICE`
  - [ ] `MEDICAL-RECORD-SERVICE`
  - [ ] `NOTIFICATION-SERVICE`

> ⚠️ Si l'un manque après 1 min : Feign cross-service échouera → `409 *_SERVICE_UNAVAILABLE`
> au moment du happy path §3.3 (RDV) et §3.5 (consultation). Vérifier les logs du service
> manquant — typiquement un problème `eureka.client.serviceUrl.defaultZone` non résolu.

### 1.6. Readiness des 5 services métier

```bash
for port in 8081 8082 8083 8084 8086; do
  printf "%s " "$port"
  status=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/actuator/health")
  test "$status" = "200" && echo "UP" || echo "KO ($status)"
done
```

- [ ] Les 5 lignes affichent `UP`
- [ ] Si l'une affiche `KO` : `curl -s http://localhost:<port>/actuator/health | jq .` — examiner `components.{db,discoveryComposite,kafka}.status`

### 1.7. Smoke Swagger automatique (sanity)

```bash
./scripts/swagger-smoke.sh
```

- [ ] Sortie `Tous services OK` (ou équivalent), exit code `0`
- [ ] Si échec : ne pas continuer la validation manuelle tant que le smoke script ne passe pas (régression à régler d'abord)

### 1.8. Sanity auth Keycloak — token `reception` testable

> ⚠️ Étape critique avant la §2. Si elle échoue, tous les Authorize Swagger échoueront en boucle (401).

```bash
curl -sS -X POST "http://localhost:8180/realms/terangamed/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=swagger-ui&username=reception&password=reception&scope=openid profile email" \
  | jq -r '.access_token // .error_description'
```

- [ ] La sortie est un JWT (long string commençant par `eyJ...`)
- [ ] **Si la sortie est `Invalid user credentials` ou `User not found`** → le realm n'a pas été réimporté après la dernière modification du `realm-export.json`. Lancer le reset :

```bash
./scripts/reset-keycloak-realm.sh
```

> 📖 **Pourquoi** : Keycloak persiste son état dans Postgres. `--import-realm` n'a d'effet **qu'au premier boot** quand le realm n'existe pas. Tout changement ultérieur du JSON (ajout d'un user, modif d'un client, nouveau rôle) reste sans effet jusqu'à un réimport explicite. Ce script supprime le realm via API admin et le recrée — sans toucher au volume Postgres (les seeds dev des autres services restent intacts).

- [ ] Le script affiche `Token reception OK — roles=[RECEPTIONIST]`
- [ ] Re-test : la commande curl ci-dessus retourne maintenant un JWT

---

## §2. Authentification multi-rôles dans Swagger

### 2.1. Règles d'or (à mémoriser)

| Rôle Swagger | Username | Password | À utiliser pour |
|--------------|----------|----------|-----------------|
| ADMIN        | `admin`     | `admin`     | doctor-service POST/PUT/DELETE/PATCH `/api/doctors`, notification-service `/api/notifications` |
| DOCTOR       | `dr.martin` | `doctor123` | medical-record-service (consultations / prescriptions / antecedents) |
| RECEPTIONIST | `reception` | `reception` | patient-service create/update, appointment-service |

> ⚠️ **Le token expire en 5 min** (réglage Keycloak `Access Token Lifespan`). Si tu vois un `401` après un long break, refais l'**Authorize**.

### 2.2. Login Swagger UI — méthode `bearer-auth` (recommandée)

> 📖 **Pourquoi pas le password flow ?** Le scheme OAuth2 Password Flow de Swagger UI 5.13 (bundlé avec springdoc-openapi 2.5.0) a un bug client-side qui empêche l'attache du Bearer après Authorize : le bouton affiche "Authorized" mais aucun token n'est stocké → **toutes les requêtes partent en 401**. Le mode `bearer-auth` (coller un JWT manuellement) contourne ce bug à 100%. Symptôme observable : le curl reproduit par Swagger UI ne contient pas de header `Authorization: Bearer ...`.

#### a. Récupérer un token (1 commande)

```bash
./scripts/get-token.sh reception
```

Le script imprime l'access_token et **le copie automatiquement dans le clipboard** (macOS via `pbcopy`, Linux via `xclip` / `wl-copy`). Variantes utiles :

```bash
./scripts/get-token.sh admin               # autre user (mot de passe par défaut connu : admin)
./scripts/get-token.sh dr.martin           # mot de passe par défaut : doctor123
./scripts/get-token.sh reception --decode  # affiche aussi le payload JWT (sub, aud, roles, exp)
```

- [ ] Le script affiche `✓ Token obtenu (expire en Ns)`
- [ ] Le script affiche `✓ Token copié dans le clipboard`
- [ ] (`--decode` optionnel) le payload contient bien :
  - [ ] `iss` = `http://localhost:8180/realms/terangamed`
  - [ ] `aud` contient `terangamed-backend`
  - [ ] `realm_access.roles` contient le rôle attendu (`RECEPTIONIST` / `DOCTOR` / `ADMIN`)

#### b. Coller le token dans Swagger UI

1. Ouvrir http://localhost:8081/swagger-ui.html
2. Cliquer sur **Authorize** (cadenas en haut à droite)
3. Section `bearer-auth (HTTP, Bearer)` — coller le token (Cmd-V / Ctrl-V)
   - **NE PAS** ajouter le préfixe `Bearer` — Swagger l'ajoute lui-même
4. Cliquer **Authorize** → fermer la modale

- [ ] Un `GET /api/patients` répond **200** (page vide ou peuplée par les seeds dev) — pas `401`
- [ ] Le curl reproduit par Swagger contient bien `-H 'Authorization: Bearer eyJ...'`

### 2.3. Re-authentification dans les autres services

Le script `get-token.sh` produit des tokens **réutilisables sur tous les Swagger UI** (audience `terangamed-backend` commune). Pour chaque service à tester :

1. Ouvrir le Swagger UI cible (`8082`, `8083`, `8084`, `8086`)
2. Authorize → bearer-auth → coller le **même token** (s'il n'a pas expiré)
3. Si token expiré : relancer `./scripts/get-token.sh <user>`

Pour switcher de rôle (ex. `reception` → `admin`) :
```bash
./scripts/get-token.sh admin   # nouveau token, écrase le clipboard
```

- [ ] Login `reception` accepté sur 8081, 8082 (lecture), 8083, 8084 (lecture)
- [ ] Login `dr.martin` accepté sur 8081 (lecture), 8082 (lecture), 8083, 8084
- [ ] Login `admin` accepté sur **les 5** Swagger UI (8081, 8082, 8083, 8084, 8086)

### 2.4. Note historique — pourquoi un seul scheme ?

Le scheme `keycloak-password` (OAuth2 Password Flow) **n'est plus exposé** dans la spec OpenAPI depuis le correctif final de mai 2026 — Swagger UI 5.13 (bundlé avec springdoc-openapi 2.5.0) a un bug client-side qui empêche l'attache du Bearer après Authorize. La spec actuelle ne déclare donc qu'un seul scheme : `bearer-auth`.

Si une future bump de springdoc fait remarcher le password flow, le scheme pourra être réintroduit dans `OpenApiConfig` côté code. Pour l'instant : KISS, on ne propose qu'un seul scheme fiable.

- [ ] Note lue ✅

---

## §3. Happy Path — Scénario E2E

> **Profil de connexion par défaut pour cette section** : `dr.martin` (DOCTOR), sauf bascule explicite signalée.
>
> **À noter au fur et à mesure** : remplir les IDs retournés ci-dessous au fur et à mesure (réutilisés dans les étapes suivantes).
>
> | Variable | Valeur | Étape |
> |----------|--------|-------|
> | `PATIENT_ID` | `_____` | §3.1 |
> | `DOCTOR_ID`  | `_____` | §3.2 |
> | `APPOINTMENT_ID` | `_____` | §3.3 |
> | `RECORD_ID` | `_____` | §3.4 |
> | `CONSULTATION_ID` | `_____` | §3.5 |
> | `ANTECEDENT_ID` | `_____` | §3.6 |
> | `PRESCRIPTION_ID` | `_____` | §3.7 |
> | `LINE_ID` | `_____` | §3.7 |

### 3.1. Créer un patient (patient-service:8081, role RECEPTIONIST)

> Bascule en `reception` / `reception` dans Swagger 8081.

`POST /api/patients` avec ce body :

```json
{
  "civility": "MME",
  "lastName": "Ndiaye",
  "firstName": "Aïssatou",
  "birthDate": "1985-03-15",
  "gender": "FEMALE",
  "phone": "+221771234567",
  "email": "aissatou.ndiaye@example.sn",
  "addressLine1": "12 rue Mohamed V",
  "city": "Dakar",
  "country": "Sénégal",
  "bloodGroup": "O_POS"
}
```

- [ ] Réponse **201 Created**
- [ ] Header `Location` présent : `http://localhost:8081/api/patients/<id>`
- [ ] Body contient `medicalRecordNumber` non null (format `PAT-YYYY-NNNNN`)
- [ ] Body contient `status: "ACTIVE"`
- [ ] **Noter `PATIENT_ID`** dans le tableau ci-dessus

### 3.2. Créer un médecin (doctor-service:8082, role ADMIN)

> ⚠️ **Bascule obligatoire** vers `admin` / `admin` dans Swagger 8082 (`POST /api/doctors` est ADMIN-only).

`POST /api/doctors` :

```json
{
  "lastName": "Martin",
  "firstName": "Jean",
  "specialty": "GENERAL_MEDICINE",
  "email": "jean.martin@terangamed.local",
  "phone": "+221770000001",
  "officeAddress": "Cabinet TerangaMed, Plateau, Dakar",
  "yearsOfExperience": 10,
  "consultationFee": 25000,
  "consultationFeeCurrency": "XOF",
  "bio": "Médecin généraliste, 10 ans d'expérience"
}
```

- [ ] Réponse **201 Created**
- [ ] `licenseNumber` rempli automatiquement (format `MED-YYYY-NNNNN`)
- [ ] `status: "ACTIVE"`
- [ ] **Noter `DOCTOR_ID`**

### 3.3. Créer un rendez-vous (appointment-service:8083, role RECEPTIONIST)

> Bascule retour `reception` / `reception` dans Swagger 8083.

`POST /api/appointments` :

```json
{
  "patientId": <PATIENT_ID>,
  "doctorId": <DOCTOR_ID>,
  "startTime": "2026-06-15T10:00:00Z",
  "durationMinutes": 30,
  "reason": "Première consultation — bilan de santé"
}
```

- [ ] Réponse **201 Created**
- [ ] `endTime` calculé automatiquement = `startTime + durationMinutes` (10:30:00Z)
- [ ] `status: "SCHEDULED"`
- [ ] Snapshots `patientName`, `doctorName` remplis (Feign vers patient/doctor a fonctionné)
- [ ] **Noter `APPOINTMENT_ID`**

### 3.4. Créer le dossier médical (medical-record-service:8084, role RECEPTIONIST ou ADMIN)

> ⚠️ **Important** : `POST /api/medical-records` est `@PreAuthorize(HAS_ADMIN_OR_RECEPTIONIST)`.
> Le **DOCTOR ne peut PAS créer un dossier** (logique métier : c'est l'accueil ou l'admin qui ouvre
> le dossier quand le patient arrive ; le médecin y rattache ensuite ses consultations / antécédents
> / ordonnances qui eux sont DOCTOR-only). En DOCTOR → 403 ACCESS_DENIED.
>
> Bascule en `reception` / `reception` dans Swagger 8084 :
>
> ```bash
> ./scripts/get-token.sh reception   # token copié dans clipboard
> ```
> puis Authorize → bearer-auth → coller → Authorize.

`POST /api/medical-records` :

```json
{
  "patientId": <PATIENT_ID>,
  "bloodType": "O_POS",
  "allergiesSummary": "Pénicilline",
  "notes": "Patiente suivie pour bilan annuel"
}
```

- [ ] Réponse **201 Created**
- [ ] **Noter `RECORD_ID`**

> 💡 Pour la suite (§3.5 → §3.8), bascule **en `dr.martin`** : `./scripts/get-token.sh dr.martin`.

### 3.5. Créer la consultation (medical-record-service:8084, role DOCTOR)

> ⚠️ **Header obligatoire** : ajouter dans Swagger l'en-tête `X-Doctor-Id: <DOCTOR_ID>`. Cf. workaround V1 documenté dans `ConsultationController.resolveCurrentDoctorId()`.

`POST /api/consultations` (avec `X-Doctor-Id: <DOCTOR_ID>`) :

```json
{
  "medicalRecordId": <RECORD_ID>,
  "appointmentId": <APPOINTMENT_ID>,
  "consultationDate": "2026-06-15T10:30:00",
  "motif": "Bilan de santé annuel",
  "examenCliniqueNotes": "Auscultation cardio-pulmonaire normale",
  "diagnostic": "Patiente en bonne santé générale",
  "observations": "Recommandation : poursuivre activité physique 3×/semaine",
  "recommandations": "Contrôle dans 12 mois",
  "vitalSigns": {
    "weightKg": 65.0,
    "heightCm": 168,
    "temperatureCelsius": 36.8,
    "heartRateBpm": 72,
    "bloodPressureSystolic": 120,
    "bloodPressureDiastolic": 80,
    "oxygenSaturationPercent": 98
  }
}
```

- [ ] Réponse **201 Created**
- [ ] `signed: false`
- [ ] `vitalSigns.bmi` calculé automatiquement (= 23.0 environ pour ces valeurs)
- [ ] **Noter `CONSULTATION_ID`**

### 3.6. Ajouter un antécédent (medical-record-service:8084, role DOCTOR)

`POST /api/antecedents` :

```json
{
  "medicalRecordId": <RECORD_ID>,
  "type": "ALLERGY",
  "title": "Pénicilline",
  "description": "Réaction urticaire en 2018",
  "onsetDate": "2018-06-10",
  "active": true
}
```

- [ ] Réponse **201 Created**
- [ ] **Noter `ANTECEDENT_ID`**

### 3.7. Créer une ordonnance + ligne (medical-record-service:8084, role DOCTOR)

`POST /api/prescriptions/by-consultation/<CONSULTATION_ID>` :

```json
{
  "validUntil": "2026-09-15",
  "generalInstructions": "À prendre pendant les repas, avec un grand verre d'eau",
  "lines": [
    {
      "medicationName": "Paracétamol 500mg",
      "dosage": "1 comprimé",
      "frequency": "3 fois par jour",
      "duration": "5 jours",
      "route": "ORAL",
      "instructions": "Si douleur ou fièvre",
      "quantity": 1
    }
  ]
}
```

- [ ] Réponse **201 Created**
- [ ] `prescriptionNumber` rempli automatiquement (format `ORD-YYYY-NNNNN`)
- [ ] Body contient `lines[]` avec 1 entrée
- [ ] **Noter `PRESCRIPTION_ID`** et `LINE_ID` (= `lines[0].id`)

### 3.8. Signer la consultation (medical-record-service:8084, role DOCTOR)

> ⚠️ Action **terminale** — toute modification ultérieure renverra `409 CONSULTATION_SIGNED`. C'est volontaire (traçabilité médico-légale).

`POST /api/consultations/<CONSULTATION_ID>/sign`

- [ ] Réponse **200 OK** (ou 204)
- [ ] Body de retour `signed: true`
- [ ] `signedBy` rempli (= `dr.martin` ou son sub Keycloak selon implémentation)
- [ ] `signedAt` daté de maintenant

✅ **Happy path complet validé** si toutes les cases ci-dessus sont cochées.

---

## §4. Cas négatifs ciblés (invariants critiques)

> ⚠️ **Réutilise les IDs créés en §3.** Chaque cas est ciblé sur un invariant qu'on ne veut **jamais** voir régresser.

### 4.1. Sécurité — sans token → `401`

Dans Swagger, cliquer **Authorize** → **Logout** sur tous les schémas.

Tester `GET /api/patients` (Swagger 8081) :

- [ ] Réponse **401 Unauthorized**
- [ ] Body contient un `error` ou un `message` clair, **jamais** une stack trace

> Re-`Authorize` en `reception` avant de continuer.

### 4.2. Sécurité — RECEPTIONIST tente `POST /api/doctors` → `403`

Dans Swagger 8082, basculer en `reception` / `reception`. Tenter `POST /api/doctors` avec le même payload qu'en §3.2.

- [ ] Réponse **403 Forbidden**
- [ ] Aucun médecin n'a été créé (vérifier via `GET /api/doctors`)

> Re-bascule `admin` pour la suite si besoin.

### 4.3. Métier — Création RDV sur slot pris → `409 APPOINTMENT_OVERLAP`

Dans Swagger 8083, en `reception`, retenter le **même** `POST /api/appointments` qu'en §3.3 (mêmes `doctorId` + `startTime` + `durationMinutes`).

- [ ] Réponse **409 Conflict**
- [ ] Body `code: "APPOINTMENT_OVERLAP"`
- [ ] Aucun deuxième RDV n'a été créé (`GET /api/appointments?doctorId=<DOCTOR_ID>` en montre toujours qu'un)

### 4.4. Métier — PUT consultation après signature → `409 CONSULTATION_SIGNED`

Dans Swagger 8084, en `dr.martin` (header `X-Doctor-Id` toujours requis), tenter `PUT /api/consultations/<CONSULTATION_ID>` avec un body modifié (par exemple changer `motif`).

- [ ] Réponse **409 Conflict**
- [ ] Body `code: "CONSULTATION_SIGNED"`
- [ ] La consultation reste inchangée (`GET /api/consultations/<CONSULTATION_ID>` montre les valeurs d'origine)

### 4.5. Métier — PUT prescription d'une consultation signée → `409 CONSULTATION_SIGNED`

Tenter `PUT /api/prescriptions/<PRESCRIPTION_ID>` avec un body modifié (par exemple `validUntil` à `2027-01-01`).

- [ ] Réponse **409 Conflict**
- [ ] Body `code: "CONSULTATION_SIGNED"`

### 4.6. Métier — Ajout d'une ligne sur prescription d'une consultation signée → `409`

Tenter `POST /api/prescriptions/<PRESCRIPTION_ID>/lines` avec un nouveau médicament.

- [ ] Réponse **409 Conflict**
- [ ] Body `code: "CONSULTATION_SIGNED"`

### 4.7. Dette #57 — Email vide vs vide ne fait pas conflit (régression à éviter)

> Ce cas valide la résolution de la dette #57 (partial unique index sur email).

Dans Swagger 8081, en `reception` :

a) Créer un patient **sans email** (champ omis dans le body) :

```json
{
  "civility": "M",
  "lastName": "Diop",
  "firstName": "Mamadou",
  "birthDate": "1990-01-15",
  "gender": "MALE"
}
```

- [ ] Réponse **201 Created** — noter cet `id` (appelons-le `P2`)

b) Créer un **deuxième** patient sans email (mêmes principes, `firstName` différent) :

```json
{
  "civility": "M",
  "lastName": "Sarr",
  "firstName": "Ousmane",
  "birthDate": "1992-04-20",
  "gender": "MALE"
}
```

- [ ] Réponse **201 Created** (et **non** `409 EMAIL_ALREADY_EXISTS`)

c) Sur le patient `P2`, faire un `PUT` sans toucher l'email :

```json
{
  "civility": "M",
  "lastName": "Diop",
  "firstName": "Mamadou",
  "birthDate": "1990-01-15",
  "gender": "MALE",
  "phone": "+221770000099"
}
```

- [ ] Réponse **200 OK** (et **non** `409` ni `500`)
- [ ] Si **500** → la dette #57 régresse ; **arrêter et noter en §6**

### 4.8. Sécurité PATIENT — non implémentable simplement en V1

> ℹ️ La validation du filtrage PATIENT (un patient ne doit voir que son propre dossier) nécessite un user `patient.aissatou` configuré dans Keycloak avec `keycloakSubject` lié à `Patient.id`. Ce setup n'est pas dans les seeds dev V1 et est tracké comme dette future.

- [ ] Skip noté ✅

---

## §5. Vérification Kafka — events publiés via Outbox

> Délai à connaître : `OutboxEventRelay` est `@Scheduled` (typiquement toutes les 1-5 secondes). Attendre **5-10 secondes** après le happy path avant de checker.

### 5.1. Lecture via notification-service (audit consolidé)

Dans Swagger 8086, basculer en `admin` / `admin`.

`GET /api/notifications?page=0&size=50&sort=occurredAt,desc`

- [ ] Réponse **200 OK**
- [ ] Le tableau `content` contient **au minimum** ces `eventType` (en plus des éventuels events des essais 4.x) :

| eventType attendu | Source | Étape happy path |
|-------------------|--------|------------------|
| `PatientCreated` | patient-service | §3.1 |
| `DoctorCreated` | doctor-service | §3.2 |
| `AppointmentScheduled` | appointment-service | §3.3 |
| `ConsultationCreated` | medical-record-service | §3.5 |
| `PrescriptionCreated` | medical-record-service | §3.7 |
| `ConsultationSigned` | medical-record-service | §3.8 |

Cocher chaque event vu :

- [ ] `PatientCreated`
- [ ] `DoctorCreated`
- [ ] `AppointmentScheduled`
- [ ] `ConsultationCreated`
- [ ] `PrescriptionCreated`
- [ ] `ConsultationSigned`

### 5.2. Sanity Kafka — vérification visuelle (optionnel)

Ouvrir http://localhost:8090 (Kafka UI) → cluster local → `Topics` :

- [ ] Topics présents (préfixés par `terangamed.` côté broker, raccourcis dans Kafka UI) :
  - [ ] `terangamed.patient.events`
  - [ ] `terangamed.doctor.events`
  - [ ] `terangamed.appointment.events`
  - [ ] `terangamed.medical.events` *(et non `medical-record.events` — le nom du topic suit le bounded-context, pas le nom du service)*
- [ ] Sur `terangamed.medical.events`, onglet **Messages** : on voit ≥ 3 messages récents (consultation.created / prescription.created / consultation.signed)
- [ ] Aucun topic en `Under-replicated partitions` ou `Out-of-sync`

### 5.3. Sanity Outbox — la table doit être vidée

> ⚠️ **Important — utilisateur PostgreSQL** : le user du compose est `terangamed` (cf. `POSTGRES_USER` dans `docker-compose.yml`), **pas** `postgres`. Une commande avec `-U postgres` retourne l'erreur `FATAL: role "postgres" does not exist`.

Depuis `terangamed-backend/docker/` (le `docker compose` doit voir le `docker-compose.yml`) :

```bash
for db in patient_db doctor_db appointment_db medical_record_db; do
  echo "─── $db ───"
  docker compose exec -T postgres psql -U terangamed -d "$db" \
    -c "SELECT status, count(*) FROM outbox_events GROUP BY status;"
done
```

- [ ] Pas de ligne `FAILED` dans `outbox_events` sur les 4 schémas
- [ ] Les `PENDING` se résorbent (re-lancer après 10s : doit décroître ou rester à 0)
- [ ] Les `PUBLISHED` peuvent rester (purge programmée à 7 jours côté `OutboxEventRelay`)

---

## §6. Résultats

### 6.1. Synthèse rapide

| Section | Statut | Anomalies |
|---------|--------|-----------|
| §1 Infra démarrage | ☐ OK ☐ KO |  |
| §2 Auth multi-rôles | ☐ OK ☐ KO |  |
| §3 Happy path | ☐ OK ☐ KO |  |
| §4.1-4.6 Cas négatifs sécu+métier | ☐ OK ☐ KO |  |
| §4.7 Dette #57 (email vide) | ☐ OK ☐ KO |  |
| §5 Kafka events | ☐ OK ☐ KO |  |

### 6.2. Anomalies détaillées (à remplir si KO)

| ID | Section | Endpoint / scénario | Comportement attendu | Comportement observé | Sévérité (P0/P1/P2) | Issue tracker |
|----|---------|--------------------|----------------------|--------------------|--------------------|---------------|
|    |         |                    |                      |                    |                    |               |

### 6.3. Captures à archiver (recommandé)

- [ ] Screenshot du token JWT décodé (jwt.io) — preuve `aud = terangamed-backend` + rôles
- [ ] Screenshot d'un 401 (sans auth)
- [ ] Screenshot d'un 403 (mauvais rôle)
- [ ] Screenshot du `409 CONSULTATION_SIGNED`
- [ ] Screenshot de la liste `notifications` montrant les 6 events

### 6.4. Décision finale

- [ ] ✅ **Validation 10C.5 OK** — aucune anomalie bloquante (P0/P1)
- [ ] ⚠️ **Validation 10C.5 OK avec réserves** — anomalies P2 listées en 6.2, suivies en backlog
- [ ] ❌ **Validation 10C.5 KO** — anomalies P0/P1 à corriger avant 10B (Dockerisation finale)

**Validateur** : __________________________ **Date** : __________________________

---

## §7. Notes & écarts vs documentation existante

### 7.1. Écarts vs `10-SWAGGER-API.md` à corriger en suivi

Pendant la rédaction de cette checklist, j'ai relevé que les payloads d'exemple du doc utilisateur `10-SWAGGER-API.md` (étape 10C.4) divergent des DTOs réels sur plusieurs points. Ce sont des **erreurs documentaires**, pas du code. À reprendre dans une mini-PR séparée :

| Endpoint | Champ du doc 10C.4 | Champ réel (DTO) |
|----------|---------------------|------------------|
| `POST /api/patients` | `preferredLanguage` (n'existe pas) | absent + manquait `civility` (NotNull) |
| `POST /api/patients` | `bloodGroup: "O_POSITIVE"` | `bloodGroup: "O_POS"` (enum réel) |
| `POST /api/doctors` | `specialty: "Médecine générale"` | `specialty: "GENERAL_MEDICINE"` (enum) |
| `POST /api/doctors` | `consultationPriceXof`, `preferredCurrency`, `preferredPaymentMethod` | `consultationFee`, `consultationFeeCurrency`, *(pas de paiement préféré côté doctor)* |
| `POST /api/appointments` | `scheduledAt` | `startTime` |
| `POST /api/consultations` | `diagnosis`, `notes` | `diagnostic`, `observations` (et `motif` en plus, REQUIS) |
| `POST /api/consultations` | `vitalSigns.heartRate`, `temperature` | `heartRateBpm`, `temperatureCelsius` |
| `POST /api/antecedents` | `severity` | absent du DTO réel (à supprimer ou mapper) ; champ présent : `title` (REQUIS) au lieu de `label` |
| `POST /api/prescriptions/by-consultation/<id>` | `instructions` | `generalInstructions` |
| `lines[]` | `medicationName / dosage / frequency / durationDays` | `medicationName / dosage / frequency / **duration**` (string libre) + `route` + `quantity` |

### 7.2. Action de suivi proposée

À ajouter dans `PROJECT_CONTEXT.md` §9 (Tâches restantes) :

> | 10C.4-bis | Corriger les payloads d'exemple dans `10-SWAGGER-API.md` (alignement DTO réels — cf. checklist 10C.5 §7.1) | Basse | 20 min |

---

## §8. Annexes — commandes utiles

### 8.1. Récupérer un token sans Swagger

```bash
get_token() {
  local user="${1:-admin}" pwd="${2:-admin}"
  curl -s -X POST "http://localhost:8180/realms/terangamed/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password&client_id=terangamed-frontend&username=${user}&password=${pwd}&scope=openid profile email" \
    | jq -r .access_token
}

TOKEN_ADMIN=$(get_token admin admin)
TOKEN_DOC=$(get_token dr.martin doctor123)
TOKEN_REC=$(get_token reception reception)
```

### 8.2. Test rapide en curl (smoke API "live")

```bash
curl -s -H "Authorization: Bearer $TOKEN_REC" "http://localhost:8081/api/patients?page=0&size=5" | jq '.content[] | {id, lastName, firstName}'
```

### 8.3. Tail consolidé des 5 services (Linux/macOS)

```bash
# Si tu lances les 5 services en background avec redirection log :
tail -f /tmp/patient.log /tmp/doctor.log /tmp/appointment.log /tmp/medical.log /tmp/notification.log
```

### 8.4. Reset complet (en cas de blocage)

```bash
cd terangamed-backend/docker
docker compose down -v          # ⚠️ supprime les volumes (DB + Keycloak)
docker compose up -d postgres keycloak kafka schema-registry kafka-ui
# Puis relancer les 5 services Spring (les migrations Flyway repassent)
```

---

## §9. Clôture

Une fois toutes les cases cochées et la décision §6.4 prise :

1. Archiver ce fichier (rempli) dans le repo : `terangamed-backend/docs/10C5-VALIDATION-RUNS/run-YYYY-MM-DD.md` (copie)
2. Mettre à jour `PROJECT_CONTEXT.md` :
   - §8.1 Étapes terminées : ajouter ligne `10C.5 ✅`
   - §9.1 Étape 10C en cours : passer 10C.5 de `⏳ À faire` à `✅`
3. Si validation OK → étape suivante : **10B Dockerisation finale**

---

*Checklist alignée sur le code au 2026-05-09. Si tu modifies un controller / DTO après cette date, vérifie que les payloads §3 sont toujours valides avant de la rejouer.*
