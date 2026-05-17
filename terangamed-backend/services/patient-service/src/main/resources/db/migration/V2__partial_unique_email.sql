-- =============================================================================
-- V2 — Partial unique index sur patients.email
-- =============================================================================
-- <p>Bug initial : la contrainte {@code uk_patient_email UNIQUE (email)} traite
-- la chaîne vide ('') comme une valeur, donc 2 patients sans email réel mais
-- avec {@code email = ''} violent la contrainte au PUT et le service crashe
-- en 500 (cf. dette technique #57).
--
-- <p>Fix en 3 temps :
-- <ol>
--   <li>Normaliser les emails vides existants en NULL (compatibles partial index)</li>
--   <li>Drop la contrainte UNIQUE classique</li>
--   <li>Recréer en partial unique index — PostgreSQL n'applique l'unicité que
--       sur les emails non-NULL, ce qui correspond à la sémantique métier
--       (l'unicité d'email ne concerne que les patients qui en ont un)</li>
-- </ol>
--
-- <p>Idem pour le téléphone — pas de contrainte unique aujourd'hui mais on
-- normalise les '' → NULL pour cohérence.

-- 1. Normalisation des données existantes
UPDATE patients SET email = NULL WHERE email = '';
UPDATE patients SET phone = NULL WHERE phone = '';

-- 2. Drop l'ancienne contrainte stricte
ALTER TABLE patients DROP CONSTRAINT IF EXISTS uk_patient_email;

-- 3. Recréation en partial unique index — l'unicité ne s'applique qu'aux
--    emails effectivement renseignés. Les patients sans email (NULL) ne
--    rentrent pas en conflit entre eux.
CREATE UNIQUE INDEX uk_patient_email ON patients (email) WHERE email IS NOT NULL;
