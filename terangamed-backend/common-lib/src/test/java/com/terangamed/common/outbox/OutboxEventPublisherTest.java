package com.terangamed.common.outbox;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.Schema;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock OutboxEventRepository repository;
    @Mock KafkaAvroSerializer serializer;

    OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxEventPublisher(repository, serializer);
    }

    /** Stub minimaliste — Avro requiert getSchema() qui est impossible à mocker simplement. */
    private static SpecificRecord fakeAvroRecord() {
        return new SpecificRecord() {
            private static final Schema SCHEMA = Schema.createRecord("Fake",
                    null, "test", false, java.util.List.of());

            @Override public Schema getSchema() { return SCHEMA; }
            @Override public Object get(int field) { return null; }
            @Override public void put(int field, Object value) { }
        };
    }

    @Test
    void publish_serializes_payload_and_persists_pending_event() {
        when(serializer.serialize(anyString(), any(SpecificRecord.class)))
                .thenReturn(new byte[]{0, 1, 2, 3, 4, 5});
        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        OutboxEvent saved = publisher.publish(
                "terangamed.patient.events", "42",
                "Patient", "42", "patient.created",
                fakeAvroRecord());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent persisted = captor.getValue();

        assertThat(persisted.getTopic()).isEqualTo("terangamed.patient.events");
        assertThat(persisted.getPartitionKey()).isEqualTo("42");
        assertThat(persisted.getAggregateType()).isEqualTo("Patient");
        assertThat(persisted.getAggregateId()).isEqualTo("42");
        assertThat(persisted.getEventType()).isEqualTo("patient.created");
        assertThat(persisted.getPayload()).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(persisted.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(persisted.getAttempts()).isZero();
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(saved).isSameAs(persisted);
    }

    @Test
    void publish_passes_topic_to_serializer() {
        when(serializer.serialize(anyString(), any(SpecificRecord.class)))
                .thenReturn(new byte[]{1});
        when(repository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        publisher.publish("custom.topic", null, "X", "1", "x.created", fakeAvroRecord());

        verify(serializer).serialize(eq("custom.topic"), any(SpecificRecord.class));
    }

    private static String eq(String value) { return org.mockito.ArgumentMatchers.eq(value); }
}
