package com.terangamed.medical.dto;

import com.terangamed.medical.entity.BloodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload de création d'un dossier médical (1 par patient)")
public record CreateMedicalRecordRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
        @NotNull @Positive
        Long patientId,

        @Schema(example = "O_POS")
        BloodType bloodType,

        @Schema(description = "Synthèse rapide des allergies critiques (alerte clinique)")
        @Size(max = 2000)
        String allergiesSummary,

        @Schema(description = "Notes générales du dossier")
        @Size(max = 5000)
        String notes
) {
}
