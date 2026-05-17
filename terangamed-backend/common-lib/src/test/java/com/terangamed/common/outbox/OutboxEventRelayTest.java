package com.terangamed.common.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventRelayTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Mock(strictness = Mock.Strictness.LENIENT)
    KafkaTemplate kafkaTemplate;

    @Mock OutboxEventRepository repository;

    OutboxProperties properties;
    OutboxEventRelay relay;

    @BeforeEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void setUp() {
        properties = new OutboxProperties();
        properties.setMaxAttempts(3);
        relay = new OutboxEventRelay(kafkaTemplate, repository, properties);

        // Par défaut, succès (l'envoi retourne un future complété)
        SendResult sendResult = mock(SendResult.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
    }

    @SuppressWarnings("unchecked")
    private static <T> T mock(Class<T> c) {
        return org.mockito.Mockito.mock(c);
    }

    private static OutboxEvent pendingEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .topic("terangamed.patient.events")
                .partitionKey("42")
                .aggregateType("Patient")
                .aggregateId("42")
                .eventType("patient.created")
                .payload(new byte[]{1, 2, 3})
                .status(OutboxEventStatus.PENDING)
                .attempts(0)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void no_pending_events_does_nothing() {
        when(repository.findPendingForUpdate(any(PageRequest.class)))
                .thenReturn(List.of());

        relay.relay();

        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
        verify(repository, never()).saveAll(any());
    }

    @Test
    void disabled_via_properties_skips_polling() {
        properties.setEnabled(false);

        relay.relay();

        verify(repository, never()).findPendingForUpdate(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void successful_publish_marks_event_published_with_processed_at() {
        OutboxEvent event = pendingEvent();
        when(repository.findPendingForUpdate(any(PageRequest.class)))
                .thenReturn(List.of(event));

        relay.relay();

        ArgumentCaptor<ProducerRecord> recordCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(recordCaptor.capture());
        ProducerRecord<String, byte[]> sent = recordCaptor.getValue();

        assertThat(sent.topic()).isEqualTo("terangamed.patient.events");
        assertThat(sent.key()).isEqualTo("42");
        assertThat(sent.value()).containsExactly(1, 2, 3);

        // Headers
        var headers = sent.headers();
        assertThat(new String(headers.lastHeader("event-id").value(), StandardCharsets.UTF_8))
                .isEqualTo(event.getId().toString());
        assertThat(new String(headers.lastHeader("event-type").value(), StandardCharsets.UTF_8))
                .isEqualTo("patient.created");
        assertThat(new String(headers.lastHeader("aggregate-type").value(), StandardCharsets.UTF_8))
                .isEqualTo("Patient");
        assertThat(new String(headers.lastHeader("aggregate-id").value(), StandardCharsets.UTF_8))
                .isEqualTo("42");

        // État final
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getProcessedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
        verify(repository).saveAll(any());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void failure_increments_attempts_and_keeps_pending_below_max() {
        OutboxEvent event = pendingEvent();
        when(repository.findPendingForUpdate(any(PageRequest.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new ExecutionException("Kafka down", new RuntimeException("boom")));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        relay.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isNotBlank();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void failure_at_max_attempts_marks_event_failed() {
        OutboxEvent event = pendingEvent();
        event.setAttempts(2); // Avec maxAttempts=3, le prochain échec → FAILED
        when(repository.findPendingForUpdate(any(PageRequest.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new ExecutionException("Kafka down", new RuntimeException("boom")));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        relay.relay();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(3);
    }

    @Test
    void purge_disabled_does_nothing() {
        properties.setPurgeEnabled(false);
        relay.purge();
        verify(repository, never()).deleteByStatusAndProcessedAtBefore(any(), any());
    }

    @Test
    void purge_deletes_old_published_events() {
        properties.setPurgeAfter(Duration.ofDays(7));
        when(repository.deleteByStatusAndProcessedAtBefore(
                org.mockito.ArgumentMatchers.eq(OutboxEventStatus.PUBLISHED), any(Instant.class)))
                .thenReturn(42L);

        relay.purge();

        verify(repository).deleteByStatusAndProcessedAtBefore(
                org.mockito.ArgumentMatchers.eq(OutboxEventStatus.PUBLISHED), any(Instant.class));
    }

    @Test
    void relay_now_delegates_to_relay() {
        when(repository.findPendingForUpdate(any(PageRequest.class)))
                .thenReturn(List.of());
        relay.relayNow();
        verify(repository).findPendingForUpdate(any(PageRequest.class));
    }
}
