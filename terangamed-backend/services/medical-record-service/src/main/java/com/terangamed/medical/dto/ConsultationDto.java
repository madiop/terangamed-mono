package com.terangamed.medical.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Consultation médicale détaillée")
public record ConsultationDto(
        Long id,
        Long medicalRecordId,
        Long doctorId,
        Long appointmentId,
        LocalDateTime consultationDate,
        String motif,
        VitalSignsDto vitalSigns,
        String examenCliniqueNotes,
        String diagnostic,
        String observations,
        String recommandations,
        LocalDate nextAppointmentSuggested,
        Boolean signed,
        Instant signedAt,
        String signedBy,
        Boolean softDeleted,
        Instant deletedAt,
        String deletedBy,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        Long version
) {
}
