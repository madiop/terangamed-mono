-- ═════════════════════════════════════════════════════════════════════════════
-- Seed data DEV uniquement (chargé via spring.flyway.locations += migration-dev
-- en profil dev). 6 patients fictifs pour démarrer l'UI Angular avec du contenu
-- réaliste.
--
-- ⚠ Ne JAMAIS être chargé en prod. Le filtrage est fait via la propriété
-- spring.flyway.locations = classpath:db/migration  (sans migration-dev).
-- ═════════════════════════════════════════════════════════════════════════════

INSERT INTO patients (
    medical_record_number, civility, last_name, first_name, birth_date, gender,
    phone, email, address_line1, postal_code, city, country, blood_group,
    allergies, emergency_contact_name, emergency_contact_phone, status,
    version, created_at, updated_at, created_by, updated_by
) VALUES
  ('MR-2026-00001', 'MME', 'Diop', 'Fatou', '1985-03-12', 'FEMALE',
   '+221770001001', 'fatou.diop@example.sn', 'Avenue Léopold Sédar Senghor', '11000', 'Dakar', 'Sénégal',
   'O_POS', 'Pénicilline', 'Mamadou Diop', '+221770001002', 'ACTIVE',
   0, NOW(), NOW(), 'system', 'system'),

  ('MR-2026-00002', 'M',   'Ndiaye', 'Ousmane', '1972-11-05', 'MALE',
   '+221771002001', 'ousmane.ndiaye@example.sn', 'Rue de la Médina', '11000', 'Dakar', 'Sénégal',
   'A_NEG', NULL, 'Awa Ndiaye', '+221771002002', 'ACTIVE',
   0, NOW(), NOW(), 'system', 'system'),

  ('MR-2026-00003', 'MME', 'Sow', 'Aminata', '2005-06-20', 'FEMALE',
   '+221772003001', 'aminata.sow@example.sn', 'Cité Keur Gorgui', '12000', 'Dakar', 'Sénégal',
   'B_POS', 'Arachide, gluten', 'Cheikh Sow', '+221772003002', 'ACTIVE',
   0, NOW(), NOW(), 'system', 'system'),

  ('MR-2026-00004', 'M',   'Ba', 'Cheikh', '1990-01-15', 'MALE',
   '+221773004001', 'cheikh.ba@example.sn', 'Sicap Liberté 6', '13000', 'Dakar', 'Sénégal',
   'AB_POS', NULL, 'Khady Ba', '+221773004002', 'ACTIVE',
   0, NOW(), NOW(), 'system', 'system'),

  ('MR-2026-00005', 'MME', 'Fall', 'Mariama', '1978-09-30', 'FEMALE',
   '+221774005001', 'mariama.fall@example.sn', 'Almadies — Route de Ngor', '14000', 'Dakar', 'Sénégal',
   'O_NEG', 'Latex', 'Ibrahima Fall', '+221774005002', 'INACTIVE',
   0, NOW(), NOW(), 'system', 'system'),

  ('MR-2025-00099', 'M',   'Diouf', 'Modou', '1965-04-18', 'MALE',
   '+221775006001', 'modou.diouf@example.sn', 'Saint-Louis — Avenue Faidherbe', '32000', 'Saint-Louis', 'Sénégal',
   'A_POS', NULL, NULL, NULL, 'ARCHIVED',
   0, NOW(), NOW(), 'system', 'system')

ON CONFLICT (medical_record_number) DO NOTHING;
