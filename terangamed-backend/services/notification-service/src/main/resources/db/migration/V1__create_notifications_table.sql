-- ═════════════════════════════════════════════════════════════════════════════
-- TerangaMed — notification-service — Migration V1
-- Table notifications : historique des events Kafka consommés.
-- ═════════════════════════════════════════════════════════════════════════════

CREATE TABLE notifications (
    id              BIGSERIAL                  PRIMARY KEY,
    event_id        UUID                       NOT NULL,
    source_topic    VARCHAR(200)               NOT NULL,
    event_type      VARCHAR(100)               NOT NULL,
    aggregate_type  VARCHAR(100)               NOT NULL,
    aggregate_id    VARCHAR(100)               NOT NULL,
    payload_json    TEXT,
    status          VARCHAR(30)                NOT NULL,
    received_at     TIMESTAMP WITH TIME ZONE   NOT NULL,
    delivered_at    TIMESTAMP WITH TIME ZONE,
    delivery_error  TEXT,

    version         BIGINT                     NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE   NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE   NOT NULL,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100),

    CONSTRAINT uk_notification_event_id UNIQUE (event_id),
    CONSTRAINT chk_notification_status CHECK (status IN (
        'RECEIVED', 'SENT_EMAIL', 'SENT_SMS', 'FAILED'
    ))
);

CREATE INDEX idx_notification_topic       ON notifications (source_topic);
CREATE INDEX idx_notification_event_type  ON notifications (event_type);
CREATE INDEX idx_notification_aggregate   ON notifications (aggregate_type, aggregate_id);
CREATE INDEX idx_notification_received_at ON notifications (received_at);

COMMENT ON TABLE  notifications IS 'Historique des events Kafka consommés (audit + base pour V2 envoi email/SMS)';
COMMENT ON COLUMN notifications.event_id IS 'UUID stable du producer — clé d''idempotence';
COMMENT ON COLUMN notifications.payload_json IS 'Représentation JSON du payload Avro (debug/audit)';
