-- ─────────────────────────────────────────────────────────────────────────────
-- TerangaMed — Création des bases au premier démarrage de PostgreSQL
--
-- Ce fichier est exécuté automatiquement par l'image postgres:16-alpine
-- quand le data directory est vide (premier boot). Les scripts dans
-- /docker-entrypoint-initdb.d/ tournent dans l'ordre alphabétique ; le préfixe
-- '01-' garantit que ce script s'exécute en premier.
--
-- Convention TerangaMed : database-per-service (DDD bounded context).
-- L'utilisateur connecté ($POSTGRES_USER) devient owner par défaut.
--
-- ⚠ Pour réinitialiser : `docker compose down -v` (efface le volume).
-- ─────────────────────────────────────────────────────────────────────────────

CREATE DATABASE keycloak;
CREATE DATABASE patient_db;
CREATE DATABASE doctor_db;
CREATE DATABASE appointment_db;
CREATE DATABASE medical_record_db;
CREATE DATABASE billing_db;
CREATE DATABASE notification_db;
