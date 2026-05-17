package com.terangamed.medical.dto;

import com.terangamed.medical.entity.MedicationRoute;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Mise à jour partielle d'une ligne d'ordonnance")
public record UpdatePrescriptionLineRequest(
        @Size(max = 200) String medicationName,
        @Size(max = 100) String dosage,
        @Size(max = 100) String frequency,
        @Size(max = 100) String duration,
        MedicationRoute route,
        @Size(max = 5000) String instructions,
        @Positive Integer quantity
) {
}
