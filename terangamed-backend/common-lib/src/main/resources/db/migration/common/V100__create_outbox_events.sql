-- ═════════════════════════════════════════════════════════════════════════════
-- TerangaMed — common-lib — Migration V100 (transversale)
-- Table outbox_events pour pattern transactional outbox.
--
-- Convention de versioning :
--   V1..V99   → migrations métier propres au service
--   V100..V199 → migrations communes fournies par common-lib (cette table, etc.)
--   V900..    → seeds dev (voir db/migration-dev/)
--
-- Activation côté service : ajouter
--   spring.flyway.locations=classpath:db/migration,classpath:db/migration/common
-- dans config-repo/{service}.yml
-- ═════════════════════════════════════════════════════════════════════════════

CREATE TABLE outbox_events (
    id              UUID                       PRIMARY KEY,
    topic           VARCHAR(200)               NOT NULL,
    partition_key   VARCHAR(200),
    aggregate_type  VARCHAR(100)               NOT NULL,
    aggregate_id    VARCHAR(100)               NOT NULL,
    event_type      VARCHAR(100)               NOT NULL,
    payload         BYTEA                      NOT NULL,
    status          VARCHAR(20)                NOT NULL,
    attempts        INTEGER                    NOT NULL DEFAULT 0,
    last_error      TEXT,
    created_at      TIMESTAMP WITH TIME ZONE   NOT NULL,
    processed_at    TIMESTAMP WITH TIME ZONE,
    version         BIGINT                     NOT NULL DEFAULT 0,

    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0)
);

-- Index sur (status, created_at) — clé du polling : les PENDING par ordre d'arrivée
CREATE INDEX idx_outbox_status_created ON outbox_events (status, created_at);

-- Index sur (aggregate_type, aggregate_id) — utile pour debug : "tous les events d'une entité"
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);

COMMENT ON TABLE  outbox_events IS 'Pattern transactional outbox — events Kafka publiés atomiquement avec les writes business';
COMMENT ON COLUMN outbox_events.payload IS 'Avro binaire avec schema-id Confluent (format wire KafkaAvroSerializer)';
COMMENT ON COLUMN outbox_events.partition_key IS 'Clé de partition Kafka (assure ordre par entité)';
