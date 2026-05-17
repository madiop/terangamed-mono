-- =============================================================================
-- V4 — Liaison Doctor <-> compte Keycloak (sub claim)
-- =============================================================================
-- <p>Ajoute la colonne `keycloak_subject` (UUID nullable) pour faire le mapping
-- entre un {@code Doctor} et le compte utilisateur Keycloak qui se connecte.
-- Précédemment, le frontend tentait un fallback fragile email Keycloak ↔ email
-- Doctor (cf. CurrentDoctorService), et medical-record-service exigeait un
-- header `X-Doctor-Id` (workaround V1). Remplacé en V2 par un lookup propre via
-- {@code GET /api/doctors/me} qui résout sub JWT → DoctorDto.
--
-- <p><b>Nullable</b> : tous les médecins n'ont pas de compte Keycloak (anciens
-- dossiers, médecins RETIRED, etc.) — la liaison reste optionnelle, mais unique
-- quand renseignée (index partiel comme pour email).

ALTER TABLE doctors
    ADD COLUMN keycloak_subject UUID;

CREATE UNIQUE INDEX uk_doctor_keycloak_subject
    ON doctors (keycloak_subject)
    WHERE keycloak_subject IS NOT NULL;
