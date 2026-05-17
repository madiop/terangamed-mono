package com.terangamed.common.outbox;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Scheduler "relay" qui pousse les events {@link OutboxEventStatus#PENDING}
 * vers Kafka.
 *
 * <h3>Garanties</h3>
 * <ul>
 *   <li><b>At-least-once delivery</b> : un event peut être publié plusieurs fois
 *       (si crash après l'envoi mais avant le commit DB). Le consumer doit donc
 *       être idempotent — l'event-id est passé en clé/header pour faciliter ça.</li>
 *   <li><b>Multi-instance safe</b> : le {@code FOR UPDATE SKIP LOCKED} permet à
 *       plusieurs replicas du service de tourner ce relay en parallèle sans
 *       traiter les mêmes events.</li>
 *   <li><b>Backoff implicite</b> : les events échoués restent {@code PENDING}
 *       jusqu'à {@code maxAttempts}. Au-delà → {@code FAILED}, intervention humaine.</li>
 * </ul>
 *
 * <h3>Headers Kafka</h3>
 * Chaque message émis porte les headers :
 * <ul>
 *   <li>{@code event-id} : UUID stable (déduplication consumer)</li>
 *   <li>{@code event-type} : ex {@code patient.created}</li>
 *   <li>{@code aggregate-type} / {@code aggregate-id}</li>
 * </ul>
 */
@Slf4j
@Service
public class OutboxEventRelay {

    /** KafkaTemplate dédié — value-serializer = ByteArraySerializer (payload déjà sérialisé). */
    private final KafkaTemplate<String, byte[]> kafkaBytesTemplate;
    private final OutboxEventRepository repository;
    private final OutboxProperties properties;

    public OutboxEventRelay(KafkaTemplate<String, byte[]> kafkaBytesTemplate,
                            OutboxEventRepository repository,
                            OutboxProperties properties) {
        this.kafkaBytesTemplate = kafkaBytesTemplate;
        this.repository = repository;
        this.properties = properties;
    }

    /**
     * Cycle de polling — fixé sur {@code terangamed.outbox.poll-interval}.
     * <p>Lit les events {@code PENDING} avec un verrou pessimiste, les pousse
     * vers Kafka, puis met à jour leur statut. Tout dans une transaction.
     *
     * <p><b>Pourquoi cette SpEL ?</b> {@code fixedDelayString} en Spring Boot
     * 3.2 attend une valeur ms parsable en long. On évalue donc une SpEL qui
     * lit le {@code Duration} typé (parsé par {@code @ConfigurationProperties})
     * et appelle {@code .toMillis()}. Le bean {@code outboxProperties} est
     * exposé par {@code OutboxAutoConfiguration} via un {@code @Bean} dédié
     * (sinon {@code @EnableConfigurationProperties} le nommerait
     * {@code terangamed.outbox-FQCN} et la SpEL planterait).
     */
    @Scheduled(fixedDelayString = "#{@outboxProperties.pollInterval.toMillis()}")
    @Transactional
    public void relay() {
        if (!properties.isEnabled()) {
            return;
        }

        List<OutboxEvent> batch = repository.findPendingForUpdate(
                PageRequest.of(0, properties.getPollBatchSize()));

        if (batch.isEmpty()) {
            return;
        }
        log.debug("Outbox relay : traitement de {} events", batch.size());

        for (OutboxEvent event : batch) {
            try {
                send(event);
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setProcessedAt(Instant.now());
                event.setLastError(null);
            } catch (Exception ex) {
                handleFailure(event, ex);
            }
        }
        repository.saveAll(batch);
    }

    /** Purge périodique des events publiés depuis plus de {@code purgeAfter}. */
    @Scheduled(cron = "0 0 3 * * *") // 3h du matin chaque jour
    @Transactional
    public void purge() {
        if (!properties.isPurgeEnabled()) {
            return;
        }
        Instant threshold = Instant.now().minus(properties.getPurgeAfter());
        long deleted = repository.deleteByStatusAndProcessedAtBefore(
                OutboxEventStatus.PUBLISHED, threshold);
        if (deleted > 0) {
            log.info("Outbox purge : {} events supprimés (publiés avant {})", deleted, threshold);
        }
    }

    // ─────────────────────────── Helpers privés ───────────────────────────

    private void send(OutboxEvent event) throws Exception {
        ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                event.getTopic(),
                null, // partition auto
                event.getPartitionKey(),
                event.getPayload());

        record.headers().add(new RecordHeader("event-id",
                event.getId().toString().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("event-type",
                event.getEventType().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("aggregate-type",
                event.getAggregateType().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader("aggregate-id",
                event.getAggregateId().getBytes(StandardCharsets.UTF_8)));

        // Bloque sur l'ack — assure que la transaction ne commit pas avant la confirmation Kafka.
        kafkaBytesTemplate.send(record).get();
    }

    private void handleFailure(OutboxEvent event, Exception ex) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(ex.getMessage());
        event.setProcessedAt(Instant.now());
        if (event.getAttempts() >= properties.getMaxAttempts()) {
            event.setStatus(OutboxEventStatus.FAILED);
            log.error("Outbox event {} en FAILED après {} tentatives — investigation manuelle requise. Cause : {}",
                    event.getId(), event.getAttempts(), ex.getMessage());
        } else {
            log.warn("Outbox event {} échec tentative {}/{} : {}",
                    event.getId(), event.getAttempts(), properties.getMaxAttempts(), ex.getMessage());
        }
    }

    /**
     * API utilitaire pour tester / forcer un cycle. Public pour les tests
     * d'intégration uniquement (pas de @Scheduled appelé directement).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void relayNow() {
        relay();
    }
}
