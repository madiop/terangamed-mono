package com.terangamed.appointment.dto;

import com.terangamed.appointment.entity.AppointmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Critères de recherche multi-attributs pour les rendez-vous")
public record AppointmentSearchCriteria(

        @Schema(description = "Filtrer par patient", example = "1")
        Long patientId,

        @Schema(description = "Filtrer par médecin", example = "1")
        Long doctorId,

        @Schema(description = "Statut du RDV")
        AppointmentStatus status,

        @Schema(description = "RDV débutant après (incluse)")
        Instant startTimeFrom,

        @Schema(description = "RDV débutant avant (incluse)")
        Instant startTimeTo,

        @Schema(description = "Recherche partielle dans le snapshot du nom patient")
        String patientName,

        @Schema(description = "Recherche partielle dans le snapshot du nom médecin")
        String doctorName
) {
}
