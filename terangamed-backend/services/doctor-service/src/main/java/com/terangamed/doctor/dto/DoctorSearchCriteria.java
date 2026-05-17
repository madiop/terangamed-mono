package com.terangamed.doctor.dto;

import com.terangamed.doctor.entity.DoctorStatus;
import com.terangamed.doctor.entity.Specialty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Critères de recherche pour {@code GET /api/doctors}.
 * Tous les champs vides ou null sont ignorés (pattern Specifications null-safe).
 */
@Schema(description = "Critères de recherche multi-attributs pour les médecins.")
public record DoctorSearchCriteria(

        @Schema(description = "Nom (recherche partielle insensible à la casse)", example = "martin")
        String lastName,

        @Schema(description = "Prénom (recherche partielle insensible à la casse)", example = "jean")
        String firstName,

        @Schema(description = "N° d'ordre médical (recherche partielle)", example = "MED-2026")
        String licenseNumber,

        @Schema(description = "Email (recherche partielle insensible à la casse)")
        String email,

        @Schema(description = "Spécialité")
        Specialty specialty,

        @Schema(description = "Statut du médecin")
        DoctorStatus status,

        @Schema(description = "Années d'expérience minimum", example = "5")
        Integer minYearsOfExperience,

        @Schema(description = "Tarif de consultation maximum (FCFA)", example = "20000")
        BigDecimal maxConsultationFee
) {
}
