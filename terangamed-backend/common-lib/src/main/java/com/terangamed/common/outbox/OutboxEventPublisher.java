package com.terangamed.common.outbox;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service write-side du pattern Outbox.
 *
 * <p>Sérialise un {@link SpecificRecord} Avro (avec schema-id Confluent intégré)
 * et l'insère dans la table {@code outbox_events} dans la <b>même transaction</b>
 * que l'aggregate métier. Cela garantit l'atomicité : soit l'aggregate ET l'event
 * sont écrits, soit aucun des deux.
 *
 * <h3>Exemple d'usage côté business service</h3>
 * <pre>
 * &#064;Transactional
 * public PatientDto create(CreatePatientRequest req) {
 *     Patient saved = patientRepository.save(patient);
 *
 *     PatientCreated event = PatientCreated.newBuilder()
 *         .setPatientId(saved.getId().toString())
 *         .setMedicalRecordNumber(saved.getMrn())
 *         .build();
 *
 *     outboxEventPublisher.publish(
 *         "terangamed.patient.events",
 *         saved.getId().toString(),       // partition key
 *         "Patient", saved.getId().toString(),
 *         "patient.created",
 *         event);
 *
 *     return mapper.toDto(saved);
 * }
 * </pre>
 *
 * <p>Le {@link OutboxEventRelay} (scheduler) reprend ces events {@code PENDING}
 * et les pousse vers Kafka. Si le service plante avant que le relay tourne,
 * les events seront repris au démarrage suivant — at-least-once delivery.
 */
@Slf4j
@Service
public class OutboxEventPublisher {

    private final OutboxEventRepository repository;
    private final KafkaAvroSerializer avroSerializer;

    public OutboxEventPublisher(OutboxEventRepository repository,
                                KafkaAvroSerializer avroSerializer) {
        this.repository = repository;
        this.avroSerializer = avroSerializer;
    }

    /**
     * Insère un event dans la table outbox. À appeler depuis une méthode
     * {@code @Transactional} business — l'event sera commité avec l'aggregate.
     *
     * @param topic         topic Kafka cible (ex: {@code terangamed.patient.events})
     * @param partitionKey  clé de partition (assure l'ordre par entité)
     * @param aggregateType type métier (ex: {@code Patient})
     * @param aggregateId   ID de l'aggregate (string)
     * @param eventType     type d'event (ex: {@code patient.created})
     * @param payload       record Avro (SpecificRecord généré depuis .avsc)
     * @return l'OutboxEvent persisté
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent publish(String topic, String partitionKey,
                               String aggregateType, String aggregateId,
                               String eventType, SpecificRecord payload) {
        byte[] serialized = avroSerializer.serialize(topic, payload);

        OutboxEvent event = OutboxEvent.builder()
                .topic(topic)
                .partitionKey(partitionKey)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(serialized)
                .status(OutboxEventStatus.PENDING)
                .attempts(0)
                .build();

        OutboxEvent saved = repository.save(event);
        log.debug("Outbox event inséré : id={}, type={}, aggregate={}/{}",
                saved.getId(), eventType, aggregateType, aggregateId);
        return saved;
    }
}
