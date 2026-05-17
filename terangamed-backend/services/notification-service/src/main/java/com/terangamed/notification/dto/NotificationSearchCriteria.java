package com.terangamed.notification.dto;

import com.terangamed.notification.entity.NotificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Filtres de recherche d'historique de notifications")
public record NotificationSearchCriteria(
        @Schema(description = "Topic Kafka source", example = "terangamed.patient.events")
        String topic,

        @Schema(description = "Type d'event", example = "patient.created")
        String eventType,

        @Schema(description = "Type d'aggregate", example = "Patient")
        String aggregateType,

        @Schema(description = "ID de l'aggregate (string)", example = "42")
        String aggregateId,

        NotificationStatus status,

        @Schema(description = "Borne basse de receivedAt (UTC)")
        Instant fromDate,

        @Schema(description = "Borne haute de receivedAt (UTC)")
        Instant toDate
) {
}
