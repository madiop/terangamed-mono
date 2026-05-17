package com.terangamed.medical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Mise à jour partielle d'une ordonnance — concerne uniquement les méta-données
 * (validité, instructions générales). Les lignes sont gérées séparément via
 * {@code /api/prescriptions/{id}/lines}.
 *
 * <p>Comme la consultation, une ordonnance liée à une consultation signée
 * devient immuable.
 */
@Schema(description = "Mise à jour partielle d'une ordonnance")
public record UpdatePrescriptionRequest(
        @FutureOrPresent LocalDate validUntil,
        @Size(max = 5000) String generalInstructions
) {
}
