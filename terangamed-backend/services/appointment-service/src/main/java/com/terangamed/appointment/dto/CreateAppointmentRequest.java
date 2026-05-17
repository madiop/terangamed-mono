package com.terangamed.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

@Schema(description = "Payload de création d'un rendez-vous")
public record CreateAppointmentRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        @NotNull @Positive
        Long patientId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        @NotNull @Positive
        Long doctorId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-06-15T10:00:00Z")
        @NotNull
        @Future
        Instant startTime,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "30",
                description = "Durée en minutes (entre 15 et 240)")
        @NotNull @Min(15) @Max(240)
        Integer durationMinutes,

        @Schema(description = "Motif du rendez-vous", example = "Consultation suivi diabète")
        String reason,

        @Schema(description = "Notes additionnelles")
        String notes
) {
}
