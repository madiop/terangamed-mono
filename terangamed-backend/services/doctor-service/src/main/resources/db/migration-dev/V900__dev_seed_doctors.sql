-- ═════════════════════════════════════════════════════════════════════════════
-- Seed dev — 5 médecins fictifs (chargé uniquement en profil 'dev')
-- ═════════════════════════════════════════════════════════════════════════════

INSERT INTO doctors (
    license_number, last_name, first_name, specialty,
    email, phone, office_address, years_of_experience,
    consultation_fee, consultation_fee_currency, bio,
    status, version, created_at, updated_at, created_by, updated_by
) VALUES
  ('MED-2026-00001', 'Martin', 'Jean', 'GENERAL_MEDICINE',
   'jean.martin@terangamed.local', '+221770100001',
   'Cabinet Plateau, Avenue Léopold Sédar Senghor, Dakar', 18,
   15000.00, 'XOF',
   'Médecin généraliste, ancien chef de service à l''hôpital Principal de Dakar.',
   'ACTIVE', 0, NOW(), NOW(), 'system', 'system'),

  ('MED-2026-00002', 'Diallo', 'Mariama', 'PEDIATRICS',
   'mariama.diallo@terangamed.local', '+221770100002',
   'Cabinet Almadies', 12,
   20000.00, 'XOF',
   'Pédiatre — spécialiste vaccinations et suivi nourrissons.',
   'ACTIVE', 0, NOW(), NOW(), 'system', 'system'),

  ('MED-2026-00003', 'Sall', 'Cheikh', 'CARDIOLOGY',
   'cheikh.sall@terangamed.local', '+221770100003',
   'Clinique Pasteur, Dakar', 22,
   30000.00, 'XOF',
   'Cardiologue — échographie, ECG, holter.',
   'ACTIVE', 0, NOW(), NOW(), 'system', 'system'),

  ('MED-2026-00004', 'Kane', 'Fatou', 'GYNECOLOGY',
   'fatou.kane@terangamed.local', '+221770100004',
   'Cabinet Médina', 8,
   25000.00, 'XOF',
   'Gynécologue obstétricienne.',
   'ON_LEAVE', 0, NOW(), NOW(), 'system', 'system'),

  ('MED-2025-00050', 'Diop', 'Amadou', 'DENTISTRY',
   'amadou.diop@terangamed.local', '+221770100005',
   NULL, 30,
   18000.00, 'XOF',
   'Chirurgien-dentiste retraité.',
   'RETIRED', 0, NOW(), NOW(), 'system', 'system')

ON CONFLICT (license_number) DO NOTHING;
