-- ═════════════════════════════════════════════════════════════════════════════
-- TerangaMed — patient-service — Migration V1
-- Crée la table patients avec contraintes, index, et colonnes d'audit.
-- ═════════════════════════════════════════════════════════════════════════════

CREATE TABLE patients (
    id                       BIGSERIAL                  PRIMARY KEY,
    medical_record_number    VARCHAR(30)                NOT NULL,
    civility                 VARCHAR(10)                NOT NULL,
    last_name                VARCHAR(100)               NOT NULL,
    first_name               VARCHAR(100)               NOT NULL,
    birth_date               DATE                       NOT NULL,
    gender                   VARCHAR(10)                NOT NULL,
    phone                    VARCHAR(20),
    email                    VARCHAR(100),
    address_line1            VARCHAR(200),
    address_line2            VARCHAR(200),
    postal_code              VARCHAR(20),
    city                     VARCHAR(100),
    country                  VARCHAR(100),
    blood_group              VARCHAR(10),
    allergies                TEXT,
    emergency_contact_name   VARCHAR(200),
    emergency_contact_phone  VARCHAR(20),
    status                   VARCHAR(20)                NOT NULL,
    version                  BIGINT                     NOT NULL DEFAULT 0,

    -- Audit (BaseAuditEntity)
    created_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    created_by               VARCHAR(100),
    updated_by               VARCHAR(100),

    -- Contraintes
    CONSTRAINT uk_patient_medical_record_number UNIQUE (medical_record_number),
    CONSTRAINT uk_patient_email UNIQUE (email),
    CONSTRAINT chk_patient_status   CHECK (status   IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_patient_gender   CHECK (gender   IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT chk_patient_civility CHECK (civility IN ('M', 'MME', 'MLLE', 'DR', 'AUTRE')),
    CONSTRAINT chk_patient_blood    CHECK (
        blood_group IS NULL
        OR blood_group IN ('A_POS','A_NEG','B_POS','B_NEG','AB_POS','AB_NEG','O_POS','O_NEG','UNKNOWN')
    )
);

-- ─── Index ───────────────────────────────────────────────────────────────────
-- Index fonctionnels sur lower(...) pour les recherches insensibles à la casse
-- (utilisés par les Specifications via cb.like(cb.lower(...)))
CREATE INDEX idx_patient_last_name_lower  ON patients (lower(last_name));
CREATE INDEX idx_patient_first_name_lower ON patients (lower(first_name));
CREATE INDEX idx_patient_email_lower      ON patients (lower(email));

-- Index simples
CREATE INDEX idx_patient_phone        ON patients (phone);
CREATE INDEX idx_patient_birth_date   ON patients (birth_date);
CREATE INDEX idx_patient_status       ON patients (status);

-- ─── Commentaires (auto-doc dans pgAdmin / DBeaver) ──────────────────────────
COMMENT ON TABLE  patients IS 'Patients du cabinet médical TerangaMed';
COMMENT ON COLUMN patients.medical_record_number IS 'Identifiant métier unique — format MR-YYYY-NNNNN';
COMMENT ON COLUMN patients.version  IS 'Verrouillage optimiste JPA';
COMMENT ON COLUMN patients.status   IS 'Cycle de vie : ACTIVE | INACTIVE | ARCHIVED';
