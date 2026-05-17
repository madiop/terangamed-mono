package com.terangamed.appointment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.terangamed.appointment.entity.AppointmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Représentation complète d'un rendez-vous")
public record AppointmentDto(
        Long id,
        Long patientId,
        Long doctorId,
        String patientNameSnapshot,
        String doctorNameSnapshot,
        Instant startTime,
        Instant endTime,
        Integer durationMinutes,
        String reason,
        String notes,
        AppointmentStatus status,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        Long version
) {
}
