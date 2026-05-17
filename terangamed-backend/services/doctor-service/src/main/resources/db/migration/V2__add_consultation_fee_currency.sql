-- ═════════════════════════════════════════════════════════════════════════════
-- TerangaMed — doctor-service — Migration V2
-- Ajoute la devise du tarif de consultation (par défaut XOF/FCFA pour le Sénégal).
-- ═════════════════════════════════════════════════════════════════════════════

ALTER TABLE doctors ADD COLUMN consultation_fee_currency VARCHAR(10);

-- Backfill : tous les médecins existants avec un tarif sont en XOF (FCFA)
UPDATE doctors SET consultation_fee_currency = 'XOF' WHERE consultation_fee IS NOT NULL;

ALTER TABLE doctors ADD CONSTRAINT chk_doctor_currency CHECK (
    consultation_fee_currency IS NULL
    OR consultation_fee_currency IN ('XOF', 'XAF', 'EUR', 'USD')
);

-- Cohérence : si un tarif est fourni, la devise doit l'être aussi
ALTER TABLE doctors ADD CONSTRAINT chk_doctor_fee_currency_pair CHECK (
    (consultation_fee IS NULL AND consultation_fee_currency IS NULL)
    OR (consultation_fee IS NOT NULL AND consultation_fee_currency IS NOT NULL)
);

COMMENT ON COLUMN doctors.consultation_fee_currency IS 'Devise ISO 4217 (XOF=FCFA par défaut)';
