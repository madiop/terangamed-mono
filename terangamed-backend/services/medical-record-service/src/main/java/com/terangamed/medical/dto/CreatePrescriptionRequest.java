package com.terangamed.medical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Création d'une ordonnance liée à une consultation.
 *
 * <p>Le {@code consultationId} est passé dans le path de l'endpoint
 * (POST /api/consultations/{id}/prescription), pas dans le body — d'où son absence ici.
 *
 * <p>Au moins une ligne est requise (une ordonnance vide n'a pas de sens métier).
 * Le numéro {@code prescriptionNumber} est généré côté serveur (format
 * {@code ORD-YYYY-NNNNN}).
 */
@Schema(description = "Création d'une ordonnance avec ses lignes")
public record CreatePrescriptionRequest(

        @Schema(description = "Date de fin de validité (par défaut : +3 mois)")
        @Future
        LocalDate validUntil,

        @Size(max = 5000)
        String generalInstructions,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Au moins une ligne médicament")
        @NotEmpty @Valid
        List<CreatePrescriptionLineRequest> lines
) {
}
