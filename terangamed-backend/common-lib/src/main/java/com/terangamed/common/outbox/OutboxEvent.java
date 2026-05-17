package com.terangamed.common.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité Outbox — pattern transactional outbox pour publier des événements Kafka
 * de manière atomique avec l'écriture de l'aggregate métier.
 *
 * <h3>Principe</h3>
 * Au lieu de publier directement dans {@code @Transactional}, on insère l'event
 * dans cette table (même transaction que l'aggregate). Un scheduler dédié
 * ({@link OutboxEventRelay}) lit les {@link OutboxEventStatus#PENDING} et les
 * pousse vers Kafka. Cela garantit que l'event est publié <b>SI ET SEULEMENT SI</b>
 * la write business a réussi.
 *
 * <h3>Stockage du payload</h3>
 * Le {@code payload} est sérialisé en Avro binaire (avec schema-id Confluent
 * intégré, format wire de KafkaAvroSerializer). Stocké en {@code BYTEA} → relay
 * envoie tel quel à Kafka via {@code ByteArrayKafkaTemplate}, le consumer
 * désérialise normalement.
 *
 * <h3>Idempotence côté consumer</h3>
 * L'{@code id} (UUID) est utilisé comme {@code key} Kafka pour permettre une
 * déduplication côté consumer (event-id reçu = event déjà traité).
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
        @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "topic", "aggregateType", "aggregateId", "eventType", "status", "attempts"})
public class OutboxEvent {

    /** UUID stable — utilisé comme {@code key} Kafka pour déduplication. */
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @Builder.Default
    private UUID id = UUID.randomUUID();

    /** Topic Kafka cible (ex: {@code terangamed.patient.events}). */
    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    /** Clé de partition Kafka (souvent l'aggregate-id en string — assure l'ordre par entité). */
    @Column(name = "partition_key", length = 200)
    private String partitionKey;

    /** Type d'aggregate métier (ex: {@code Patient}, {@code Appointment}). */
    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    /** ID de l'aggregate (string — accomode UUID, Long, etc.). */
    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    /** Type d'event (ex: {@code patient.created}, {@code consultation.signed}). */
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** Avro binaire (schema-id Confluent inclus). */
    @Column(name = "payload", nullable = false, columnDefinition = "BYTEA")
    private byte[] payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    /** Nombre de tentatives de publication. Incrémenté à chaque retry. */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    /** Dernière erreur rencontrée (null si jamais échoué ou succès final). */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Horodatage de la dernière tentative ou du succès. */
    @Column(name = "processed_at")
    private Instant processedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
