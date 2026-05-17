-- ═════════════════════════════════════════════════════════════════════════════
-- TerangaMed — medical-record-service — Migration V1
-- Schéma : medical_records, antecedents, consultations, prescriptions, prescription_lines
-- ═════════════════════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────────────────────
-- Table : medical_records (1 par patient, soft-delete uniquement)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE medical_records (
    id                       BIGSERIAL                  PRIMARY KEY,
    patient_id               BIGINT                     NOT NULL,
    blood_type               VARCHAR(10),
    allergies_summary        TEXT,
    notes                    TEXT,

    soft_deleted             BOOLEAN                    NOT NULL DEFAULT FALSE,
    deleted_at               TIMESTAMP WITH TIME ZONE,
    deleted_by               VARCHAR(100),

    version                  BIGINT                     NOT NULL DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    created_by               VARCHAR(100),
    updated_by               VARCHAR(100),

    CONSTRAINT uk_medical_record_patient UNIQUE (patient_id),
    CONSTRAINT chk_medical_record_blood_type CHECK (
        blood_type IS NULL OR blood_type IN (
            'A_POS', 'A_NEG', 'B_POS', 'B_NEG',
            'AB_POS', 'AB_NEG', 'O_POS', 'O_NEG', 'UNKNOWN'
        )
    ),
    -- Cohérence : si soft_deleted=true alors deleted_at et deleted_by sont obligatoires.
    CONSTRAINT chk_medical_record_soft_delete_pair CHECK (
        (soft_deleted = FALSE AND deleted_at IS NULL AND deleted_by IS NULL)
        OR (soft_deleted = TRUE AND deleted_at IS NOT NULL AND deleted_by IS NOT NULL)
    )
);

CREATE INDEX idx_medical_record_patient        ON medical_records (patient_id);
CREATE INDEX idx_medical_record_soft_deleted   ON medical_records (soft_deleted);

COMMENT ON TABLE  medical_records IS 'Dossier médical — 1 par patient (soft-delete uniquement, traçabilité médico-légale)';
COMMENT ON COLUMN medical_records.patient_id IS 'Référence patient-service (pas de FK SQL, validation Feign)';

-- ─────────────────────────────────────────────────────────────────────────────
-- Table : antecedents (catégorisés, N par dossier)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE antecedents (
    id                       BIGSERIAL                  PRIMARY KEY,
    medical_record_id        BIGINT                     NOT NULL,
    type                     VARCHAR(30)                NOT NULL,
    title                    VARCHAR(200)               NOT NULL,
    description              TEXT,
    onset_date               DATE,
    active                   BOOLEAN                    NOT NULL DEFAULT TRUE,

    version                  BIGINT                     NOT NULL DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    created_by               VARCHAR(100),
    updated_by               VARCHAR(100),

    CONSTRAINT fk_antecedent_record FOREIGN KEY (medical_record_id)
        REFERENCES medical_records(id) ON DELETE CASCADE,
    CONSTRAINT chk_antecedent_type CHECK (type IN (
        'ALLERGY', 'MEDICAL_CONDITION', 'SURGERY', 'MEDICATION', 'FAMILY'
    ))
);

CREATE INDEX idx_antecedent_record  ON antecedents (medical_record_id);
CREATE INDEX idx_antecedent_type    ON antecedents (type);
CREATE INDEX idx_antecedent_active  ON antecedents (active);

COMMENT ON TABLE antecedents IS 'Antécédents médicaux catégorisés (allergies, ATCD, chirurgies, traitements, familial)';

-- ─────────────────────────────────────────────────────────────────────────────
-- Table : consultations (avec signes vitaux JSONB + signature terminale)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE consultations (
    id                          BIGSERIAL                  PRIMARY KEY,
    medical_record_id           BIGINT                     NOT NULL,
    doctor_id                   BIGINT                     NOT NULL,
    appointment_id              BIGINT,
    consultation_date           TIMESTAMP                  NOT NULL,

    motif                       TEXT                       NOT NULL,
    vital_signs                 JSONB,
    examen_clinique_notes       TEXT,
    diagnostic                  TEXT,
    observations                TEXT,
    recommandations             TEXT,
    next_appointment_suggested  DATE,

    signed                      BOOLEAN                    NOT NULL DEFAULT FALSE,
    signed_at                   TIMESTAMP WITH TIME ZONE,
    signed_by                   VARCHAR(100),

    soft_deleted                BOOLEAN                    NOT NULL DEFAULT FALSE,
    deleted_at                  TIMESTAMP WITH TIME ZONE,
    deleted_by                  VARCHAR(100),

    version                     BIGINT                     NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP WITH TIME ZONE   NOT NULL,
    updated_at                  TIMESTAMP WITH TIME ZONE   NOT NULL,
    created_by                  VARCHAR(100),
    updated_by                  VARCHAR(100),

    CONSTRAINT fk_consultation_record FOREIGN KEY (medical_record_id)
        REFERENCES medical_records(id) ON DELETE CASCADE,

    -- Cohérence : signature
    CONSTRAINT chk_consultation_sign_pair CHECK (
        (signed = FALSE AND signed_at IS NULL AND signed_by IS NULL)
        OR (signed = TRUE AND signed_at IS NOT NULL AND signed_by IS NOT NULL)
    ),
    -- Cohérence : soft-delete
    CONSTRAINT chk_consultation_soft_delete_pair CHECK (
        (soft_deleted = FALSE AND deleted_at IS NULL AND deleted_by IS NULL)
        OR (soft_deleted = TRUE AND deleted_at IS NOT NULL AND deleted_by IS NOT NULL)
    )
);

CREATE INDEX idx_consultation_record       ON consultations (medical_record_id);
CREATE INDEX idx_consultation_doctor       ON consultations (doctor_id);
CREATE INDEX idx_consultation_appointment  ON consultations (appointment_id);
CREATE INDEX idx_consultation_date         ON consultations (consultation_date);
CREATE INDEX idx_consultation_signed       ON consultations (signed);
CREATE INDEX idx_consultation_soft_deleted ON consultations (soft_deleted);

-- Index GIN sur les signes vitaux (JSONB) — utile pour les futures requêtes
-- analytiques (ex: "TA systolique > 140 sur les 6 derniers mois")
CREATE INDEX idx_consultation_vital_signs_gin ON consultations USING GIN (vital_signs);

COMMENT ON TABLE  consultations IS 'Consultations médicales (signature terminale, soft-delete pour traçabilité)';
COMMENT ON COLUMN consultations.vital_signs IS 'Signes vitaux structurés (JSONB) — poids, taille, TA, T°, FC, FR, SpO₂, glycémie';

-- ─────────────────────────────────────────────────────────────────────────────
-- Table : prescriptions (au plus 1 par consultation)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE prescriptions (
    id                       BIGSERIAL                  PRIMARY KEY,
    prescription_number      VARCHAR(30)                NOT NULL,
    consultation_id          BIGINT                     NOT NULL,
    issued_at                TIMESTAMP WITH TIME ZONE   NOT NULL,
    valid_until              DATE,
    general_instructions     TEXT,

    version                  BIGINT                     NOT NULL DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    created_by               VARCHAR(100),
    updated_by               VARCHAR(100),

    CONSTRAINT uk_prescription_number UNIQUE (prescription_number),
    CONSTRAINT uk_prescription_consultation UNIQUE (consultation_id),
    CONSTRAINT fk_prescription_consultation FOREIGN KEY (consultation_id)
        REFERENCES consultations(id) ON DELETE CASCADE,
    CONSTRAINT chk_prescription_validity CHECK (
        valid_until IS NULL OR valid_until >= issued_at::date
    )
);

CREATE INDEX idx_prescription_consultation ON prescriptions (consultation_id);
CREATE INDEX idx_prescription_number       ON prescriptions (prescription_number);

COMMENT ON TABLE  prescriptions IS 'Ordonnance liée à une consultation (1-1 logique)';
COMMENT ON COLUMN prescriptions.prescription_number IS 'N° d''ordonnance — format ORD-YYYY-NNNNN';

-- ─────────────────────────────────────────────────────────────────────────────
-- Table : prescription_lines (un médicament + posologie par ligne)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE prescription_lines (
    id                       BIGSERIAL                  PRIMARY KEY,
    prescription_id          BIGINT                     NOT NULL,
    medication_name          VARCHAR(200)               NOT NULL,
    dosage                   VARCHAR(100),
    frequency                VARCHAR(100),
    duration                 VARCHAR(100),
    route                    VARCHAR(30),
    instructions             TEXT,
    quantity                 INTEGER,

    version                  BIGINT                     NOT NULL DEFAULT 0,
    created_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    created_by               VARCHAR(100),
    updated_by               VARCHAR(100),

    CONSTRAINT fk_prescription_line_prescription FOREIGN KEY (prescription_id)
        REFERENCES prescriptions(id) ON DELETE CASCADE,
    CONSTRAINT chk_prescription_line_route CHECK (
        route IS NULL OR route IN (
            'ORAL', 'INJECTION', 'TOPICAL', 'INHALATION',
            'OPHTHALMIC', 'NASAL', 'RECTAL', 'OTHER'
        )
    ),
    CONSTRAINT chk_prescription_line_quantity CHECK (
        quantity IS NULL OR quantity > 0
    )
);

CREATE INDEX idx_prescription_line_prescription ON prescription_lines (prescription_id);

COMMENT ON TABLE prescription_lines IS 'Lignes de prescription — un médicament + posologie par ligne';
