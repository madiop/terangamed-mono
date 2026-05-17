# 08 — Intégration Kafka & Smoke test e2e

Ce document décrit l'architecture événementielle de TerangaMed (étape 8) et fournit
un **smoke test end-to-end** pour valider le flux producer → Kafka → consumer.

---

## 1. Architecture événementielle

```
┌─────────────────┐                                           ┌────────────────────┐
│ patient-service │ ──┐                                       │ notification-service│
│ doctor-service  │   │                                       │ (consumer)          │
│ appointment-svc │   │     ┌──────────────┐                  │                     │
│ medical-svc     │ ──┼───> │  Kafka       │ ────────────────>│  • 3 @KafkaListener │
└─────────────────┘   │     │  3 topics    │                  │  • Idempotence      │
                      │     │  + 4 DLT     │                  │  • Persistance      │
        Outbox        │     │              │                  │  • REST history     │
        Pattern       │     │  ┌─────────┐ │                  └────────────────────┘
                      └──>  │  │ Schema  │ │                          │
                            │  │ Registry│ │                          ▼
                            │  └─────────┘ │                   notification_db
                            └──────────────┘
```

### Topics

| Topic | Producer | Events |
|---|---|---|
| `terangamed.patient.events` | patient-service | `patient.created`, `patient.updated`, `patient.archived` |
| `terangamed.doctor.events` | doctor-service | (vide en V1) |
| `terangamed.appointment.events` | appointment-service | `appointment.scheduled`, `confirmed`, `cancelled`, `completed`, `no-show` |
| `terangamed.medical.events` | medical-record-service | `consultation.created`, `consultation.signed`, `prescription.created` |

Chaque topic principal a un Dead Letter Topic associé : `*.DLT`.

### Pattern Outbox

Tous les producers utilisent le **transactional outbox pattern** :
1. Le service métier insère l'event dans `outbox_events` (même transaction que l'aggregate)
2. `OutboxEventRelay` (scheduler `@Scheduled` toutes les 1s) lit les événements `PENDING`
3. Push vers Kafka avec headers `event-id`, `event-type`, `aggregate-type`, `aggregate-id`
4. Marque `PUBLISHED` ou `FAILED` selon le résultat

**Garanties** : at-least-once delivery + atomicité aggregate/event + retry exponentiel.

---

## 2. Démarrage de la stack

```bash
cd terangamed-backend/docker
cp .env.example .env       # une seule fois
docker compose up -d
```

Démarrage attendu (~2 min selon machine) :
- `postgres` + `keycloak` → `kafka` + `schema-registry` + `kafka-init` (one-shot, exit 0)
- `config-server` → `discovery-server` → `api-gateway`
- `patient-service` (8081), `doctor-service` (8082), `appointment-service` (8083),
  `medical-record-service` (8084), `notification-service` (8086)

Vérifier que tous les services sont `healthy` :

```bash
docker compose ps --format "table {{.Name}}\t{{.Status}}"
```

---

## 3. Vérifications préliminaires

### 3.1. Topics Kafka créés

```bash
docker compose logs kafka-init | tail -20
```

Sortie attendue :
```
  ✓ terangamed.patient.events
  ✓ terangamed.doctor.events
  ✓ terangamed.appointment.events
  ✓ terangamed.medical.events
  ✓ terangamed.patient.events.DLT (DLT)
  ...
```

### 3.2. Schema Registry actif

```bash
curl http://localhost:8085/subjects
# []  (vide tant qu'aucun event n'a été publié)
```

### 3.3. Kafka UI accessible

Ouvrir [http://localhost:8090](http://localhost:8090) — vérifier que le cluster
`terangamed` apparaît avec 4 topics + 4 DLTs.

---

## 4. Smoke test e2e

### 4.1. Récupérer un token JWT

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8180/realms/terangamed/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=swagger-ui&username=admin&password=admin" \
  | jq -r '.access_token')

echo "Token : ${TOKEN:0:40}..."
```

### 4.2. Créer un patient (via Gateway)

```bash
curl -X POST "http://localhost:8080/api/patients" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "civility": "M",
    "lastName": "Smoke",
    "firstName": "Test",
    "birthDate": "1990-06-15",
    "gender": "MALE",
    "email": "smoke.test@terangamed.local",
    "city": "Dakar",
    "country": "Sénégal"
  }'
```

Vérifier la réponse (HTTP 201, `id`, `medicalRecordNumber: MR-YYYY-NNNNN`).

### 4.3. Vérifier que l'event a été publié

#### Côté Outbox table (DB)

```bash
docker compose exec postgres psql -U terangamed -d patient_db \
  -c "SELECT id, topic, event_type, status, attempts FROM outbox_events ORDER BY created_at DESC LIMIT 3;"
```

Attendu :
```
                  id                  |          topic           |   event_type    |  status   | attempts
--------------------------------------+--------------------------+-----------------+-----------+----------
 xxxxx                                | terangamed.patient.events| patient.created | PUBLISHED | 0
```

#### Côté Kafka UI

[http://localhost:8090](http://localhost:8090) → Cluster `terangamed` → Topics →
`terangamed.patient.events` → Messages → vérifier le message Avro (avec
header `event-type: patient.created`).

#### Côté Schema Registry

```bash
curl http://localhost:8085/subjects
# ["terangamed.patient.events-value"]

curl http://localhost:8085/subjects/terangamed.patient.events-value/versions/latest | jq .schema
```

### 4.4. Vérifier la consommation par notification-service

```bash
docker compose logs notification-service --tail 20 | grep "Notification persistée"
```

Attendu :
```
INFO ... : Notification persistée : eventId=..., topic=terangamed.patient.events,
           type=patient.created, aggregate=Patient/123
```

### 4.5. Endpoint REST history

```bash
curl -s "http://localhost:8080/api/notifications?size=10" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Attendu : page contenant la notification `patient.created` avec `payloadJson` lisible.

---

## 5. Test de bout en bout — flow complet

Pour stresser le système :

```bash
# 1. Créer un patient
PATIENT_ID=$(curl -s -X POST "http://localhost:8080/api/patients" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"civility":"M","lastName":"E2E","firstName":"Flow","birthDate":"1985-01-01","gender":"MALE"}' \
  | jq -r '.id')

# 2. Créer un médecin (si pas déjà fait — réutiliser un seedé en dev)
# (passé pour l'exemple — utiliser doctorId=1 du seed dev)

# 3. Créer un RDV
APPT_ID=$(curl -s -X POST "http://localhost:8080/api/appointments" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"patientId\":$PATIENT_ID,\"doctorId\":1,\"startTime\":\"2027-06-15T10:00:00Z\",\"durationMinutes\":30,\"reason\":\"E2E test\"}" \
  | jq -r '.id')

# 4. Confirmer le RDV
curl -X POST "http://localhost:8080/api/appointments/$APPT_ID/confirm" \
  -H "Authorization: Bearer $TOKEN"

# 5. Vérifier les 2 events apparus dans notification-service
sleep 3
curl -s "http://localhost:8080/api/notifications?aggregateId=$APPT_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '.content[] | {eventType, aggregateId}'
```

Attendu :
```json
{ "eventType": "appointment.confirmed", "aggregateId": "..." }
{ "eventType": "appointment.scheduled", "aggregateId": "..." }
```

---

## 6. Troubleshooting

| Symptôme | Cause probable | Solution |
|---|---|---|
| `kafka-init` ne crée pas les topics | Kafka pas encore healthy | `docker compose restart kafka-init` |
| Schema Registry vide | Aucun message publié | Vérifier outbox_events.status |
| `outbox_events.status = FAILED` | Schema Registry/Kafka inaccessible | Voir `last_error`, vérifier connectivité |
| Notifications dupliquées | Pas de doublons grâce à UNIQUE(event_id) | Logs : `Notification ignorée (déjà reçue)` |
| Consumer bloqué | Poison pill (message corrompu) | Voir DLT : `*.DLT` |

---

## 7. Volumétrie attendue (V1)

| Métrique | Valeur |
|---|---|
| Events par création patient | 1 |
| Events par cycle RDV (scheduled→confirmed→completed) | 3 |
| Events par consultation signée + ordonnance | 3 |
| Lag de propagation outbox→Kafka | < 2 secondes |
| Throughput max théorique | ~3000 events/s par broker (single node dev) |

**Production** : passer Kafka en cluster 3 brokers (replication-factor=3, min.insync.replicas=2).
