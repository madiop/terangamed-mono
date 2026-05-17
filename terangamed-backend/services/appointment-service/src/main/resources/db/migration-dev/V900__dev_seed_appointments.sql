-- ═════════════════════════════════════════════════════════════════════════════
-- Seed dev appointment-service — chargé uniquement en profil 'dev' via
-- spring.flyway.locations += classpath:db/migration-dev
--
-- Couvre les 5 statuts (PLANNED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW)
-- pour permettre de tester le workflow de transitions d'état dans l'UI.
--
-- Dates relatives à NOW() pour rester pertinent peu importe la date d'exécution :
--   - RDV passés terminés (COMPLETED) : -7 à -2 jours
--   - RDV récemment annulés/absents : -3 à -1 jours
--   - RDV aujourd'hui ou imminents (CONFIRMED) : ±0 à +1 jours
--   - RDV à venir (PLANNED) : +2 à +14 jours
--
-- Référence patient_id (1..6) et doctor_id (1..5) — IDs alloués par les seeds
-- patient et doctor (V900). Les snapshots noms doivent rester cohérents.
-- ═════════════════════════════════════════════════════════════════════════════

INSERT INTO appointments (
    patient_id, doctor_id,
    patient_name_snapshot, doctor_name_snapshot,
    start_time, end_time, duration_minutes,
    reason, notes, status,
    version, created_at, updated_at, created_by, updated_by
) VALUES
-- ─── RDV PASSÉS TERMINÉS ───
  (1, 1, 'Diop Fatou', 'Dr Martin Jean',
   NOW() - INTERVAL '7 days' + TIME '09:00',
   NOW() - INTERVAL '7 days' + TIME '09:30', 30,
   'Bilan annuel', 'Tension correcte, pas de plainte particulière.',
   'COMPLETED', 0, NOW() - INTERVAL '8 days', NOW() - INTERVAL '7 days', 'system', 'system'),

  (2, 1, 'Ndiaye Ousmane', 'Dr Martin Jean',
   NOW() - INTERVAL '5 days' + TIME '10:00',
   NOW() - INTERVAL '5 days' + TIME '10:30', 30,
   'Renouvellement traitement', 'Ordonnance prolongée 3 mois.',
   'COMPLETED', 0, NOW() - INTERVAL '6 days', NOW() - INTERVAL '5 days', 'system', 'system'),

  (3, 2, 'Sow Aminata', 'Dr Diallo Mariama',
   NOW() - INTERVAL '3 days' + TIME '14:30',
   NOW() - INTERVAL '3 days' + TIME '15:00', 30,
   'Consultation pédiatrique', 'Vaccinations à jour. RAS.',
   'COMPLETED', 0, NOW() - INTERVAL '4 days', NOW() - INTERVAL '3 days', 'system', 'system'),

-- ─── RDV PASSÉS ANNULÉS ───
  (4, 3, 'Bâ Ibrahima', 'Dr Sall Cheikh',
   NOW() - INTERVAL '2 days' + TIME '11:00',
   NOW() - INTERVAL '2 days' + TIME '11:30', 30,
   'Suivi cardiologique', 'Annulé à la demande du patient.',
   'CANCELLED', 0, NOW() - INTERVAL '5 days', NOW() - INTERVAL '2 days', 'system', 'system'),

-- ─── RDV PASSÉS NO_SHOW ───
  (5, 1, 'Faye Awa', 'Dr Martin Jean',
   NOW() - INTERVAL '1 days' + TIME '15:00',
   NOW() - INTERVAL '1 days' + TIME '15:30', 30,
   'Contrôle général',
   'Patient ne s''est pas présenté.',
   'NO_SHOW', 0, NOW() - INTERVAL '5 days', NOW() - INTERVAL '1 days', 'system', 'system'),

-- ─── RDV AUJOURD'HUI CONFIRMÉS ───
  (1, 2, 'Diop Fatou', 'Dr Diallo Mariama',
   DATE_TRUNC('day', NOW()) + TIME '09:30',
   DATE_TRUNC('day', NOW()) + TIME '10:00', 30,
   'Suivi pédiatrique du fils', 'Confirmé hier.',
   'CONFIRMED', 0, NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 days', 'system', 'system'),

  (6, 1, 'Diouf Modou', 'Dr Martin Jean',
   DATE_TRUNC('day', NOW()) + TIME '14:00',
   DATE_TRUNC('day', NOW()) + TIME '14:45', 45,
   'Consultation diabète', 'Apporter glucomètre.',
   'CONFIRMED', 0, NOW() - INTERVAL '5 days', NOW() - INTERVAL '2 days', 'system', 'system'),

  (2, 3, 'Ndiaye Ousmane', 'Dr Sall Cheikh',
   DATE_TRUNC('day', NOW()) + TIME '16:30',
   DATE_TRUNC('day', NOW()) + TIME '17:00', 30,
   'Bilan cardiologique', NULL,
   'CONFIRMED', 0, NOW() - INTERVAL '4 days', NOW() - INTERVAL '1 days', 'system', 'system'),

-- ─── RDV À VENIR PLANIFIÉS (à confirmer) ───
  (3, 1, 'Sow Aminata', 'Dr Martin Jean',
   DATE_TRUNC('day', NOW()) + INTERVAL '1 days' + TIME '08:30',
   DATE_TRUNC('day', NOW()) + INTERVAL '1 days' + TIME '09:00', 30,
   'Bilan annuel',
   'Premier RDV — apporter dossier médical antérieur.',
   'PLANNED', 0, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', 'system', 'system'),

  (4, 2, 'Bâ Ibrahima', 'Dr Diallo Mariama',
   DATE_TRUNC('day', NOW()) + INTERVAL '2 days' + TIME '10:00',
   DATE_TRUNC('day', NOW()) + INTERVAL '2 days' + TIME '10:30', 30,
   'Vaccination de rappel', NULL,
   'PLANNED', 0, NOW() - INTERVAL '1 days', NOW() - INTERVAL '1 days', 'system', 'system'),

  (5, 3, 'Faye Awa', 'Dr Sall Cheikh',
   DATE_TRUNC('day', NOW()) + INTERVAL '3 days' + TIME '15:00',
   DATE_TRUNC('day', NOW()) + INTERVAL '3 days' + TIME '15:45', 45,
   'Première consultation cardiologique', 'Échographie prévue.',
   'PLANNED', 0, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', 'system', 'system'),

  (1, 1, 'Diop Fatou', 'Dr Martin Jean',
   DATE_TRUNC('day', NOW()) + INTERVAL '7 days' + TIME '11:00',
   DATE_TRUNC('day', NOW()) + INTERVAL '7 days' + TIME '11:30', 30,
   'Contrôle hypertension', NULL,
   'PLANNED', 0, NOW() - INTERVAL '1 days', NOW() - INTERVAL '1 days', 'system', 'system'),

  (6, 2, 'Diouf Modou', 'Dr Diallo Mariama',
   DATE_TRUNC('day', NOW()) + INTERVAL '10 days' + TIME '09:00',
   DATE_TRUNC('day', NOW()) + INTERVAL '10 days' + TIME '09:30', 30,
   'Suivi pédiatrique enfant', NULL,
   'PLANNED', 0, NOW(), NOW(), 'system', 'system'),

  (2, 4, 'Ndiaye Ousmane', 'Dr Touré Aïssatou',
   DATE_TRUNC('day', NOW()) + INTERVAL '14 days' + TIME '16:00',
   DATE_TRUNC('day', NOW()) + INTERVAL '14 days' + TIME '16:30', 30,
   'Consultation gynécologie', NULL,
   'PLANNED', 0, NOW(), NOW(), 'system', 'system');

-- ═════════════════════════════════════════════════════════════════════════════
-- Récap : 14 RDV créés
--   3× COMPLETED
--   1× CANCELLED
--   1× NO_SHOW
--   3× CONFIRMED (aujourd'hui)
--   6× PLANNED (futurs J+1 à J+14)
-- ═════════════════════════════════════════════════════════════════════════════
