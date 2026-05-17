-- =============================================================================
-- V3 — Partial unique index sur doctors.email (fix préventif identique à patient)
-- =============================================================================
-- <p>Même cause racine que patient-service : {@code uk_doctor_email UNIQUE} traite
-- la chaîne vide comme valeur, donc 2 médecins sans email crashent au PUT.
--
-- <p>Bug pas encore manifesté côté doctor-service (les médecins du seed dev ont
-- tous un email), mais la classe d'erreur identique justifie un fix préventif.
--
-- <p>Cf. dette technique #57 + commentaire similaire dans
-- {@code patient-service/V2__partial_unique_email.sql}.

UPDATE doctors SET email = NULL WHERE email = '';
UPDATE doctors SET phone = NULL WHERE phone = '';

ALTER TABLE doctors DROP CONSTRAINT IF EXISTS uk_doctor_email;

CREATE UNIQUE INDEX uk_doctor_email ON doctors (email) WHERE email IS NOT NULL;
