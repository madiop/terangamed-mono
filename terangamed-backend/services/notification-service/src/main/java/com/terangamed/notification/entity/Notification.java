package com.terangamed.notification.entity;

import com.terangamed.common.audit.BaseAuditEntity;
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
 * Notification persistée — un enregistrement par event Kafka consommé.
 *
 * <h3>Idempotence</h3>
 * Le {@code eventId} (UUID stable côté producer) est marqué UNIQUE en base.
 * Un même event re-consommé (replay, redélivrance Kafka after retry) ne crée
 * pas de doublon — on attrape le {@code DataIntegrityViolationException} et
 * on commit silencieusement le offset.
 *
 * <h3>Payload</h3>
 * Stocké en {@code TEXT} comme représentation JSON Avro (via
 * {@code SpecificRecord.toString()}). Ne reproduit pas exactement le binaire
 * mais offre une lisibilité immédiate en DB pour debug/audit.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "uk_notification_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_notification_topic", columnList = "source_topic"),
        @Index(name = "idx_notification_event_type", columnList = "event_type"),
        @Index(name = "idx_notification_aggregate", columnList = "aggregate_type, aggregate_id"),
        @Index(name = "idx_notification_received_at", columnList = "received_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "eventId", "eventType", "aggregateType", "aggregateId", "status"})
public class Notification extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID stable du producer — clé d'idempotence. */
    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "source_topic", nullable = false, length = 200)
    private String sourceTopic;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    /** Représentation JSON du payload Avro (SpecificRecord.toString()). */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.RECEIVED;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private Instant receivedAt = Instant.now();

    /** Renseigné quand un envoi email/SMS a été tenté (V2). */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** Dernière erreur si l'envoi a échoué. */
    @Column(name = "delivery_error", columnDefinition = "TEXT")
    private String deliveryError;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
