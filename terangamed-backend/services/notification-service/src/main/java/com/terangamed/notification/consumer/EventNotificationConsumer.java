package com.terangamed.notification.consumer;

import com.terangamed.common.kafka.TerangaMedTopics;
import com.terangamed.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumer Kafka pour les 3 topics métier TerangaMed.
 *
 * <h3>Stratégie</h3>
 * Trois {@code @KafkaListener} dédiés (un par topic) — séparation claire et
 * débogage plus simple que des topic-patterns. La logique d'ingestion est
 * factorisée dans {@link #handle(ConsumerRecord, Acknowledgment, String)}.
 *
 * <h3>Désérialisation Avro</h3>
 * Le consumer reçoit {@link GenericRecord} (mode générique) — pas
 * {@code SpecificRecord} — car notification-service ne dépend pas des classes
 * Avro générées par les autres services. On passe par le format JSON (toString())
 * pour la persistance et l'affichage debug.
 *
 * <h3>Retry + DLT</h3>
 * {@link RetryableTopic} configure 3 tentatives avec backoff exponentiel.
 * Avec {@code multiplier=2.0}, les retry-topics générés sont :
 * <ul>
 *   <li>{@code <topic>-retry-1000} (1ʳᵉ relance après 1s)</li>
 *   <li>{@code <topic>-retry-2000} (2ᵉ relance après 2s)</li>
 *   <li>{@code <topic>-dlt} (Dead Letter Topic — au-delà de toutes les tentatives)</li>
 * </ul>
 *
 * <p>Ces topics annexes sont créés automatiquement au démarrage par Spring
 * Kafka via {@link org.springframework.kafka.core.KafkaAdmin} (cf.
 * {@link com.terangamed.notification.config.KafkaProducerConfig}). On garde
 * le contrôle des paramètres ({@code numPartitions=1} — volume faible —
 * et {@code replicationFactor=1} — single broker dev). En prod, augmenter
 * le RF à ≥2 quand le cluster est multi-broker.
 *
 * <p>Les exceptions d'idempotence sont gérées au niveau service (pas de
 * retry dans ce cas — l'event est silencieusement ack-é).
 *
 * <h3>Acknowledgment manuel</h3>
 * {@code spring.kafka.listener.ack-mode=manual_immediate} (défini en config-repo)
 * → on contrôle quand le offset est commité, après persistance réussie.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventNotificationConsumer {

    private final NotificationService notificationService;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            autoCreateTopics = "true",
            numPartitions = "1",
            replicationFactor = "1"
    )
    @KafkaListener(
            topics = TerangaMedTopics.PATIENT_EVENTS,
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onPatientEvent(ConsumerRecord<String, GenericRecord> record,
                               Acknowledgment ack) {
        handle(record, ack, TerangaMedTopics.PATIENT_EVENTS);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            autoCreateTopics = "true",
            numPartitions = "1",
            replicationFactor = "1")
    @KafkaListener(
            topics = TerangaMedTopics.APPOINTMENT_EVENTS,
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onAppointmentEvent(ConsumerRecord<String, GenericRecord> record,
                                   Acknowledgment ack) {
        handle(record, ack, TerangaMedTopics.APPOINTMENT_EVENTS);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            autoCreateTopics = "true",
            numPartitions = "1",
            replicationFactor = "1")
    @KafkaListener(
            topics = TerangaMedTopics.MEDICAL_EVENTS,
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onMedicalEvent(ConsumerRecord<String, GenericRecord> record,
                               Acknowledgment ack) {
        handle(record, ack, TerangaMedTopics.MEDICAL_EVENTS);
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            sameIntervalTopicReuseStrategy = SameIntervalTopicReuseStrategy.SINGLE_TOPIC,
            autoCreateTopics = "true",
            numPartitions = "1",
            replicationFactor = "1")
    @KafkaListener(
            topics = TerangaMedTopics.DOCTOR_EVENTS,
            groupId = "notification-service",
            containerFactory = "kafkaListenerContainerFactory")
    public void onDoctorEvent(ConsumerRecord<String, GenericRecord> record,
                              Acknowledgment ack) {
        handle(record, ack, TerangaMedTopics.DOCTOR_EVENTS);
    }

    /**
     * Traitement commun : extrait headers, persiste, ack le offset.
     * <p>Si une RuntimeException échappe d'ici, {@code @RetryableTopic} prend
     * le relais (retry exponentiel, puis DLT).
     */
    private void handle(ConsumerRecord<String, GenericRecord> record,
                        Acknowledgment ack, String topic) {
        UUID eventId = EventHeadersExtractor.eventId(record.headers());
        String eventType = EventHeadersExtractor.header(record.headers(), EventHeadersExtractor.EVENT_TYPE);
        String aggregateType = EventHeadersExtractor.header(record.headers(), EventHeadersExtractor.AGGREGATE_TYPE);
        String aggregateId = EventHeadersExtractor.header(record.headers(), EventHeadersExtractor.AGGREGATE_ID);

        if (eventId == null || eventType == null || aggregateType == null || aggregateId == null) {
            log.error("Event mal-formé : headers manquants — topic={}, partition={}, offset={}",
                    topic, record.partition(), record.offset());
            // On ack quand même pour ne pas boucler indéfiniment sur un event corrompu.
            ack.acknowledge();
            return;
        }

        String payloadJson = record.value() != null ? record.value().toString() : null;

        notificationService.ingest(eventId, topic, eventType, aggregateType, aggregateId, payloadJson);
        ack.acknowledge();
    }
}
