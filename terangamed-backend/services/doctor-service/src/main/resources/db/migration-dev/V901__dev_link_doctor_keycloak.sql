-- =============================================================================
-- V901 (dev) — Liaison Doctor ↔ compte Keycloak `dr.martin`
-- =============================================================================
-- <p>Lie le médecin seed `Jean Martin` (MED-2026-00001) au compte Keycloak
-- `dr.martin` dont l'`id` est fixé dans `realm-export.json` (V2 — ajouté en
-- même temps que la suppression du workaround X-Doctor-Id).
--
-- <p>Le `keycloak_subject` ainsi pré-renseigné permet à
-- `GET /api/doctors/me` (appelé par le frontend) et à la création de
-- consultations (medical-record-service) de résoudre le DoctorDto sans header
-- d'app ni recherche par email.
--
-- <p><b>Idempotent</b> : ne touche pas une liaison déjà établie (filtre
-- `keycloak_subject IS NULL`). Si l'instance Keycloak a un autre `id` pour
-- `dr.martin` (volume non reset après le passage à V2), faire manuellement :
-- <pre>
--   UPDATE doctors SET keycloak_subject = '&lt;sub-actuel&gt;'
--   WHERE license_number = 'MED-2026-00001';
-- </pre>
-- ou reset le volume Keycloak pour réimporter le realm avec l'id fixé.

UPDATE doctors
SET keycloak_subject = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaa00000001'
WHERE license_number = 'MED-2026-00001'
  AND keycloak_subject IS NULL;
