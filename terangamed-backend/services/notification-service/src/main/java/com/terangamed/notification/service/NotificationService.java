package com.terangamed.notification.service;

import com.terangamed.common.pagination.PageResponse;
import com.terangamed.common.pagination.SortValidator;
import com.terangamed.notification.dto.NotificationDto;
import com.terangamed.notification.dto.NotificationSearchCriteria;
import com.terangamed.notification.entity.Notification;
import com.terangamed.notification.entity.NotificationStatus;
import com.terangamed.notification.mapper.NotificationMapper;
import com.terangamed.notification.repository.NotificationRepository;
import com.terangamed.notification.specification.NotificationSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service métier des notifications.
 *
 * <h3>Idempotence write-side</h3>
 * {@link #ingest} est appelé par le consumer Kafka. On vérifie d'abord
 * {@code existsByEventId} ; en cas de race condition, la contrainte UNIQUE
 * en base lève {@link DataIntegrityViolationException} qu'on attrape pour
 * commiter le offset Kafka quand même (le doublon n'est pas une erreur fonctionnelle).
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "receivedAt", "createdAt", "eventType", "aggregateType", "status"
    );

    private final NotificationRepository repository;
    private final NotificationMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> search(NotificationSearchCriteria criteria, Pageable pageable) {
        Pageable safe = SortValidator.sanitize(pageable, SORTABLE_FIELDS);
        Page<Notification> page = repository.findAll(
                NotificationSpecifications.withCriteria(criteria), safe);
        return PageResponse.from(page, mapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<NotificationDto> findById(Long id) {
        return repository.findById(id).map(mapper::toDto);
    }

    /**
     * Persiste une notification reçue depuis Kafka. Idempotent : skip silencieux
     * si le {@code eventId} est déjà connu.
     *
     * @return {@code true} si nouvellement persisté, {@code false} si doublon ignoré
     */
    public boolean ingest(UUID eventId, String topic, String eventType,
                          String aggregateType, String aggregateId,
                          String payloadJson) {
        if (repository.existsByEventId(eventId)) {
            log.debug("Notification ignorée (déjà reçue) : eventId={}", eventId);
            return false;
        }
        Notification notification = Notification.builder()
                .eventId(eventId)
                .sourceTopic(topic)
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .payloadJson(payloadJson)
                .status(NotificationStatus.RECEIVED)
                .receivedAt(Instant.now())
                .build();
        try {
            repository.save(notification);
            log.info("Notification persistée : eventId={}, topic={}, type={}, aggregate={}/{}",
                    eventId, topic, eventType, aggregateType, aggregateId);
            return true;
        } catch (DataIntegrityViolationException dup) {
            // Race condition : un autre consumer/replica a inséré entretemps.
            // On accepte le doublon — Kafka commitera le offset comme si on avait traité.
            log.warn("Race condition détectée pour eventId={} — doublon ignoré", eventId);
            return false;
        }
    }
}
