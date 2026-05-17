package com.terangamed.medical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "Création d'une consultation. Le doctorId est déduit du JWT côté serveur.")
public record CreateConsultationRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
        @NotNull @Positive
        Long medicalRecordId,

        @Schema(description = "Lien optionnel vers un RDV (appointment-service)")
        @Positive
        Long appointmentId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-04-30T10:30:00")
        @NotNull
        LocalDateTime consultationDate,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Toux persistante depuis 5 jours")
        @NotBlank @Size(max = 5000)
        String motif,

        @Valid
        VitalSignsDto vitalSigns,

        @Size(max = 5000)
        String examenCliniqueNotes,

        @Size(max = 5000)
        String diagnostic,

        @Size(max = 5000)
        String observations,

        @Size(max = 5000)
        String recommandations,

        @Schema(description = "Date suggérée pour le prochain RDV")
        LocalDate nextAppointmentSuggested
) {
}
