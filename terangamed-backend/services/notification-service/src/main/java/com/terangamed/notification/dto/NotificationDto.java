package com.terangamed.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.terangamed.notification.entity.NotificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Notification persistée — historique d'event Kafka consommé")
public record NotificationDto(
        Long id,
        UUID eventId,
        String sourceTopic,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payloadJson,
        NotificationStatus status,
        Instant receivedAt,
        Instant deliveredAt,
        String deliveryError,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}
