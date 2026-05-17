package com.terangamed.medical.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * DTO partiel renvoyé par {@code appointment-service} via Feign.
 * Utilisé pour valider l'existence + cohérence patient/doctor lors de la
 * création d'une consultation liée à un RDV.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppointmentSnapshotDto(
        Long id,
        Long patientId,
        Long doctorId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status
) {
    public boolean isCompleted() {
        return "COMPLETED".equals(status);
    }
}
