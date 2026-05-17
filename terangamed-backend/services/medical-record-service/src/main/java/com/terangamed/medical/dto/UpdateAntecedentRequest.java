package com.terangamed.medical.dto;

import com.terangamed.medical.entity.AntecedentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Mise à jour partielle d'un antécédent. {@code medicalRecordId} non modifiable
 * (déplacement d'un antécédent entre dossiers non autorisé).
 */
@Schema(description = "Mise à jour partielle d'un antécédent")
public record UpdateAntecedentRequest(
        AntecedentType type,
        @Size(max = 200) String title,
        @Size(max = 5000) String description,
        @PastOrPresent LocalDate onsetDate,
        Boolean active
) {
}
