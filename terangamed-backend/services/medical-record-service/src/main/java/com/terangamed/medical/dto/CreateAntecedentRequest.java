package com.terangamed.medical.dto;

import com.terangamed.medical.entity.AntecedentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Création d'un antécédent dans un dossier médical")
public record CreateAntecedentRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
        @NotNull @Positive
        Long medicalRecordId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "ALLERGY")
        @NotNull
        AntecedentType type,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Pénicilline")
        @NotBlank @Size(max = 200)
        String title,

        @Size(max = 5000)
        String description,

        @Schema(description = "Date d'apparition / diagnostic (passé ou présent)")
        @PastOrPresent
        LocalDate onsetDate,

        @Schema(description = "Antécédent toujours d'actualité ? Défaut true.")
        Boolean active
) {
}
