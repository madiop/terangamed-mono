-- ═════════════════════════════════════════════════════════════════════════════
-- TerangaMed — appointment-service — Migration V1
-- ═════════════════════════════════════════════════════════════════════════════

CREATE TABLE appointments (
    id                       BIGSERIAL                  PRIMARY KEY,
    patient_id               BIGINT                     NOT NULL,
    doctor_id                BIGINT                     NOT NULL,
    patient_name_snapshot    VARCHAR(200)               NOT NULL,
    doctor_name_snapshot     VARCHAR(200)               NOT NULL,
    start_time               TIMESTAMP WITH TIME ZONE   NOT NULL,
    end_time                 TIMESTAMP WITH TIME ZONE   NOT NULL,
    duration_minutes         INTEGER                    NOT NULL,
    reason                   TEXT,
    notes                    TEXT,
    status                   VARCHAR(20)                NOT NULL,
    version                  BIGINT                     NOT NULL DEFAULT 0,

    created_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    updated_at               TIMESTAMP WITH TIME ZONE   NOT NULL,
    created_by               VARCHAR(100),
    updated_by               VARCHAR(100),

    CONSTRAINT chk_appointment_status CHECK (
        status IN ('PLANNED', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')
    ),
    CONSTRAINT chk_appointment_duration CHECK (duration_minutes > 0),
    CONSTRAINT chk_appointment_time_order CHECK (end_time > start_time)
);

CREATE INDEX idx_appointment_patient            ON appointments (patient_id);
CREATE INDEX idx_appointment_doctor             ON appointments (doctor_id);
CREATE INDEX idx_appointment_start              ON appointments (start_time);
CREATE INDEX idx_appointment_status             ON appointments (status);
CREATE INDEX idx_appointment_doctor_time        ON appointments (doctor_id, start_time, end_time);

COMMENT ON TABLE  appointments IS 'Rendez-vous médicaux';
COMMENT ON COLUMN appointments.patient_id IS 'Référence au patient (par ID — pas de FK cross-service)';
COMMENT ON COLUMN appointments.doctor_id IS 'Référence au médecin (par ID — pas de FK cross-service)';
COMMENT ON COLUMN appointments.patient_name_snapshot IS 'Snapshot du nom patient à la création (eventual consistency)';
COMMENT ON COLUMN appointments.end_time IS 'Calculé à l''insertion : start_time + duration_minutes (utilisé pour overlap detection)';
