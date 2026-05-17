package com.terangamed.common.outbox;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void builder_defaults_status_pending_and_attempts_zero() {
        OutboxEvent event = OutboxEvent.builder()
                .topic("terangamed.patient.events")
                .partitionKey("42")
                .aggregateType("Patient")
                .aggregateId("42")
                .eventType("patient.created")
                .payload(new byte[]{1, 2, 3})
                .build();

        assertThat(event.getId()).isNotNull();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getProcessedAt()).isNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void to_string_includes_key_fields() {
        UUID id = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.builder()
                .id(id)
                .topic("t")
                .aggregateType("Patient")
                .aggregateId("42")
                .eventType("patient.created")
                .build();

        String s = event.toString();
        assertThat(s).contains("Patient");
        assertThat(s).contains("42");
        assertThat(s).contains("patient.created");
        assertThat(s).contains(id.toString());
    }

    @Test
    void all_args_constructor_works() {
        UUID id = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        OutboxEvent event = new OutboxEvent(
                id, "topic", "key", "Aggregate", "1", "evt",
                new byte[]{1}, OutboxEventStatus.PUBLISHED, 3, "err",
                now, now, 0L);

        assertThat(event.getId()).isEqualTo(id);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getAttempts()).isEqualTo(3);
        assertThat(event.getLastError()).isEqualTo("err");
    }
}
