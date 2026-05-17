package com.terangamed.appointment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;

/**
 * Update partiel d'un RDV. Tout champ {@code null} est ignoré.
 *
 * <p><b>Note</b> : changer {@code patientId} ou {@code doctorId} après création
 * n'est pas autorisé (annuler et recréer un RDV à la place). Ces champs ne sont
 * donc pas dans la requête d'update.
 *
 * <p>Le changement de statut passe par les endpoints dédiés
 * ({@code /confirm}, {@code /complete}, {@code /cancel}, {@code /no-show}).
 */
@Schema(description = "Payload de mise à jour partielle d'un RDV (replanification)")
public record UpdateAppointmentRequest(

        @Schema(description = "Nouvelle date/heure (doit être future)")
        @Future
        Instant startTime,

        @Schema(description = "Nouvelle durée (15 à 240 minutes)")
        @Min(15) @Max(240)
        Integer durationMinutes,

        @Schema(description = "Motif du RDV (texte libre)")
        String reason,

        @Schema(description = "Notes additionnelles")
        String notes
) {
}
