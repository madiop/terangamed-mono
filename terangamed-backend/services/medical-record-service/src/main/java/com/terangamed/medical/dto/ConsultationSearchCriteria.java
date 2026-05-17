package com.terangamed.medical.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Critères de recherche dynamique pour les consultations.
 * <p>Tout champ {@code null} est ignoré (semantique JPA Specification).
 */
@Schema(description = "Filtres de recherche pour les consultations")
public record ConsultationSearchCriteria(

        @Schema(description = "Filtre par patientId (typiquement utilisé par un PATIENT pour son historique)")
        Long patientId,

        @Schema(description = "Filtre par médecin")
        Long doctorId,

        @Schema(description = "Date min (incluse) — borne basse de consultationDate")
        LocalDate fromDate,

        @Schema(description = "Date max (incluse) — borne haute de consultationDate")
        LocalDate toDate,

        @Schema(description = "Si true, n'inclure que les consultations signées ; si false, que les non signées ; null = pas de filtre")
        Boolean signed,

        @Schema(description = "Filtre texte sur motif/diagnostic (LIKE case-insensitive)")
        String keyword
) {
}
