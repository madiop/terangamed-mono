package com.terangamed.medical.dto;

import com.terangamed.medical.entity.BloodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Payload de mise à jour partielle d'un dossier médical.
 * Tout champ {@code null} est ignoré (sémantique partial-update via MapStruct).
 *
 * <p>Le {@code patientId} n'est pas modifiable — le dossier est attaché au patient
 * pour toute sa vie.
 */
@Schema(description = "Mise à jour partielle d'un dossier médical")
public record UpdateMedicalRecordRequest(
        BloodType bloodType,

        @Size(max = 2000)
        String allergiesSummary,

        @Size(max = 5000)
        String notes
) {
}
