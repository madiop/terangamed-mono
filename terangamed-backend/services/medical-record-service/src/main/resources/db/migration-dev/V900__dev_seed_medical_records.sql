-- ═════════════════════════════════════════════════════════════════════════════
-- Seed dev — dossiers médicaux fictifs (chargé en profil 'dev' uniquement)
--
-- Hypothèses (cohérence cross-service avec les seeds patient-service et doctor-service) :
--   patient_id 1..5  → Diallo, Diop, Sow, Faye, Ndiaye (cf. patient-service V900)
--   doctor_id  1..3  → Martin (généraliste), Diallo (pédiatre), Sall (cardiologue)
-- ═════════════════════════════════════════════════════════════════════════════

-- ─── Dossiers médicaux ───
INSERT INTO medical_records (
    patient_id, blood_type, allergies_summary, notes,
    soft_deleted, version, created_at, updated_at, created_by, updated_by
) VALUES
  (1, 'O_POS', 'Pénicilline (urticaire 2018)',
   'Patient suivi régulièrement pour HTA. Compliant au traitement.',
   FALSE, 0, NOW(), NOW(), 'system', 'system'),

  (2, 'A_POS', NULL,
   'Aucun antécédent notable.',
   FALSE, 0, NOW(), NOW(), 'system', 'system'),

  (3, 'B_NEG', 'Aspirine (gastrite)',
   'Suivi diabétique.',
   FALSE, 0, NOW(), NOW(), 'system', 'system'),

  (4, 'AB_POS', NULL,
   NULL,
   FALSE, 0, NOW(), NOW(), 'system', 'system'),

  (5, 'UNKNOWN', NULL,
   'Patient nouveau — à compléter au prochain RDV.',
   FALSE, 0, NOW(), NOW(), 'system', 'system')

ON CONFLICT (patient_id) DO NOTHING;

-- ─── Antécédents ───
-- IDs des dossiers récupérés via subquery (résiste aux IDs auto-incrémentés)
INSERT INTO antecedents (
    medical_record_id, type, title, description, onset_date, active,
    version, created_at, updated_at, created_by, updated_by
) VALUES
  ((SELECT id FROM medical_records WHERE patient_id = 1),
   'ALLERGY', 'Pénicilline', 'Urticaire généralisé après amoxicilline (2018)',
   '2018-03-15', TRUE, 0, NOW(), NOW(), 'dr.martin', 'dr.martin'),

  ((SELECT id FROM medical_records WHERE patient_id = 1),
   'MEDICAL_CONDITION', 'Hypertension artérielle', 'HTA modérée — équilibrée sous traitement',
   '2020-06-01', TRUE, 0, NOW(), NOW(), 'dr.martin', 'dr.martin'),

  ((SELECT id FROM medical_records WHERE patient_id = 1),
   'MEDICATION', 'Amlodipine 5mg', '1cp/j le matin (HTA)',
   '2020-06-15', TRUE, 0, NOW(), NOW(), 'dr.martin', 'dr.martin'),

  ((SELECT id FROM medical_records WHERE patient_id = 3),
   'MEDICAL_CONDITION', 'Diabète type 2', 'Découverte fortuite — diabétique non insulinodépendant',
   '2022-11-20', TRUE, 0, NOW(), NOW(), 'dr.martin', 'dr.martin'),

  ((SELECT id FROM medical_records WHERE patient_id = 3),
   'ALLERGY', 'Aspirine', 'Gastrite après prise prolongée',
   '2019-01-10', TRUE, 0, NOW(), NOW(), 'dr.martin', 'dr.martin'),

  ((SELECT id FROM medical_records WHERE patient_id = 3),
   'SURGERY', 'Appendicectomie', 'Chirurgie sans complication — Hôpital Principal Dakar',
   '2010-07-22', FALSE, 0, NOW(), NOW(), 'dr.martin', 'dr.martin'),

  ((SELECT id FROM medical_records WHERE patient_id = 3),
   'FAMILY', 'Diabète paternel', 'Père diabétique type 2 décédé à 68 ans',
   NULL, TRUE, 0, NOW(), NOW(), 'dr.martin', 'dr.martin')
ON CONFLICT DO NOTHING;

-- ─── Consultations ───
INSERT INTO consultations (
    medical_record_id, doctor_id, appointment_id, consultation_date,
    motif, vital_signs, examen_clinique_notes, diagnostic, observations, recommandations,
    next_appointment_suggested,
    signed, signed_at, signed_by,
    soft_deleted, version, created_at, updated_at, created_by, updated_by
) VALUES
  -- Consultation signée — patient 1, dr martin
  ((SELECT id FROM medical_records WHERE patient_id = 1), 1, NULL,
   NOW() - INTERVAL '30 days',
   'Contrôle HTA trimestriel',
   '{"weightKg":78,"heightCm":172,"bloodPressureSystolic":135,"bloodPressureDiastolic":85,"heartRateBpm":74,"temperatureCelsius":36.7,"oxygenSaturationPercent":98}'::jsonb,
   'TA bien contrôlée sous traitement. Pas de signe d''insuffisance cardiaque.',
   'HTA équilibrée',
   'Patient compliant. Auto-mesures TA à domicile correctes.',
   'Poursuite Amlodipine 5mg. Régime hyposodé. Activité physique régulière.',
   (CURRENT_DATE + INTERVAL '90 days')::date,
   TRUE, NOW() - INTERVAL '30 days' + INTERVAL '15 minutes', 'dr.martin',
   FALSE, 0, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days', 'dr.martin', 'dr.martin'),

  -- Consultation NON signée — patient 3, dr martin (en cours)
  ((SELECT id FROM medical_records WHERE patient_id = 3), 1, NULL,
   NOW() - INTERVAL '2 days',
   'Toux persistante depuis 5 jours',
   '{"weightKg":65,"heightCm":168,"bloodPressureSystolic":120,"bloodPressureDiastolic":78,"heartRateBpm":82,"temperatureCelsius":37.4,"oxygenSaturationPercent":97,"respiratoryRateBpm":18}'::jsonb,
   'Toux sèche, gorge érythémateuse. Pas de râle pulmonaire.',
   'Bronchite virale probable',
   'Sub-fébrile à 37.4°C. État général conservé.',
   'Repos, hydratation. Antitussif si gêne nocturne. Reconsulter si fièvre > 38.5°C ou aggravation.',
   NULL,
   FALSE, NULL, NULL,
   FALSE, 0, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', 'dr.martin', 'dr.martin'),

  -- Consultation signée — patient 5 (nouveau patient — premier RDV)
  ((SELECT id FROM medical_records WHERE patient_id = 5), 1, NULL,
   NOW() - INTERVAL '7 days',
   'Premier RDV — bilan général',
   '{"weightKg":70,"heightCm":175,"bloodPressureSystolic":118,"bloodPressureDiastolic":76,"heartRateBpm":68,"temperatureCelsius":36.5,"oxygenSaturationPercent":99}'::jsonb,
   'Examen clinique sans particularité.',
   'Bilan initial RAS',
   'Patient en bonne santé apparente. Pas d''antécédents.',
   'Bilan biologique standard. Vaccinations à jour à vérifier.',
   (CURRENT_DATE + INTERVAL '180 days')::date,
   TRUE, NOW() - INTERVAL '7 days' + INTERVAL '20 minutes', 'dr.martin',
   FALSE, 0, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days', 'dr.martin', 'dr.martin');

-- ─── Ordonnance + lignes (pour la consultation HTA signée — patient 1) ───
WITH consultation_hta AS (
    SELECT id FROM consultations
    WHERE medical_record_id = (SELECT id FROM medical_records WHERE patient_id = 1)
    AND motif LIKE 'Contrôle HTA%'
    LIMIT 1
)
INSERT INTO prescriptions (
    prescription_number, consultation_id, issued_at, valid_until, general_instructions,
    version, created_at, updated_at, created_by, updated_by
)
SELECT
    'ORD-2026-00001', id,
    NOW() - INTERVAL '30 days',
    (CURRENT_DATE + INTERVAL '60 days')::date,
    'Renouvellement traitement HTA. Contrôle TA à domicile recommandé 2x/semaine.',
    0, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days', 'dr.martin', 'dr.martin'
FROM consultation_hta
ON CONFLICT (prescription_number) DO NOTHING;

INSERT INTO prescription_lines (
    prescription_id, medication_name, dosage, frequency, duration, route, instructions, quantity,
    version, created_at, updated_at, created_by, updated_by
)
SELECT p.id,
    'Amlodipine 5mg', '1 comprimé', '1 fois par jour le matin', '90 jours', 'ORAL',
    'À prendre à heure fixe. Ne pas arrêter sans avis médical.', 3,
    0, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days', 'dr.martin', 'dr.martin'
FROM prescriptions p WHERE p.prescription_number = 'ORD-2026-00001';
