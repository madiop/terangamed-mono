package com.terangamed.medical.dto;

import com.terangamed.medical.entity.MedicationRoute;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Ajout d'une ligne à une ordonnance")
public record CreatePrescriptionLineRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Amoxicilline 500mg")
        @NotBlank @Size(max = 200)
        String medicationName,

        @Schema(example = "1 gélule")
        @Size(max = 100)
        String dosage,

        @Schema(example = "3 fois par jour")
        @Size(max = 100)
        String frequency,

        @Schema(example = "7 jours")
        @Size(max = 100)
        String duration,

        @Schema(example = "ORAL")
        MedicationRoute route,

        @Size(max = 5000)
        String instructions,

        @Schema(description = "Nombre de boîtes prescrites", example = "1")
        @Positive
        Integer quantity
) {
}
