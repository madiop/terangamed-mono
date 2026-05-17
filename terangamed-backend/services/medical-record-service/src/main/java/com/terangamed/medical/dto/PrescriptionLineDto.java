package com.terangamed.medical.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.terangamed.medical.entity.MedicationRoute;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ligne d'ordonnance — un médicament avec sa posologie")
public record PrescriptionLineDto(
        Long id,
        Long prescriptionId,
        String medicationName,
        String dosage,
        String frequency,
        String duration,
        MedicationRoute route,
        String instructions,
        Integer quantity,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        Long version
) {
}
