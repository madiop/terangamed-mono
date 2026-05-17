-- ═════════════════════════════════════════════════════════════════════════════
-- TerangaMed — doctor-service — Migration V1
-- ═════════════════════════════════════════════════════════════════════════════

CREATE TABLE doctors (
    id                       BIGSERIAL                  PRIMARY KEY,
    license_number           VARCHAR(30)                NOT NULL,
    last_name                VARCHAR(100)               NOT NULL,
    first_name               VARCHAR(100)               NOT NULL,
    specialty                VARCHAR(30)                NOT NULL,
    email                    VARCHAR(100),
    phone                    VARCHAR(20),
    office_address           VARCHAR(250),
    years_of_experience      INTEGER,
    consultation_fee         NUMERIC(10, 2),
    bio                      TEXT,
    status                   VARCHAR(20)                NOT NULL,
    version                  BIGINT                     NOT NULL DEFAULT 0,

    created_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    created_by               VARCHAR(100),
    updated_by               VARCHAR(100),

    CONSTRAINT uk_doctor_license_number UNIQUE (license_number),
    CONSTRAINT uk_doctor_email UNIQUE (email),
    CONSTRAINT chk_doctor_status CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'RETIRED')),
    CONSTRAINT chk_doctor_specialty CHECK (specialty IN (
        'GENERAL_MEDICINE', 'CARDIOLOGY', 'DERMATOLOGY', 'PEDIATRICS', 'GYNECOLOGY',
        'DENTISTRY', 'OPHTHALMOLOGY', 'PSYCHIATRY', 'ORTHOPEDICS', 'OTHER'
    )),
    CONSTRAINT chk_doctor_years CHECK (years_of_experience IS NULL OR years_of_experience >= 0),
    CONSTRAINT chk_doctor_fee CHECK (consultation_fee IS NULL OR consultation_fee >= 0)
);

CREATE INDEX idx_doctor_last_name_lower  ON doctors (lower(last_name));
CREATE INDEX idx_doctor_first_name_lower ON doctors (lower(first_name));
CREATE INDEX idx_doctor_email_lower      ON doctors (lower(email));
CREATE INDEX idx_doctor_specialty        ON doctors (specialty);
CREATE INDEX idx_doctor_status           ON doctors (status);

COMMENT ON TABLE  doctors IS 'Médecins du cabinet TerangaMed';
COMMENT ON COLUMN doctors.license_number IS 'N° d''ordre médical — format MED-YYYY-NNNNN';
COMMENT ON COLUMN doctors.consultation_fee IS 'Tarif consultation en FCFA';
