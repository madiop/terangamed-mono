package com.terangamed.patient.dto;

import com.terangamed.patient.entity.BloodGroup;
import com.terangamed.patient.entity.Gender;
import com.terangamed.patient.entity.PatientStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Critères de recherche pour {@code GET /api/patients}.
 *
 * <p>Chaque champ {@code null} ou blanc est ignoré par les Specifications
 * (cf. {@link com.terangamed.patient.specification.PatientSpecifications}).
 * Cela permet à l'API d'accepter des recherches partielles (ex: filtrer
 * uniquement par nom et statut) sans construire d'URL ou de body conditionnel.
 *
 * <p>Tous les champs textuels sont matchés en {@code LIKE %...%} insensible
 * à la casse côté Specification.
 */
@Schema(description = "Critères de recherche multi-attributs pour les patients. " +
        "Tous les champs sont optionnels — les champs vides sont ignorés.")
public record PatientSearchCriteria(

        @Schema(description = "Nom de famille (recherche partielle, insensible à la casse)",
                example = "diop")
        String lastName,

        @Schema(description = "Prénom (recherche partielle, insensible à la casse)",
                example = "fatou")
        String firstName,

        @Schema(description = "N° de dossier médical (recherche partielle)",
                example = "MR-2026")
        String medicalRecordNumber,

        @Schema(description = "Téléphone (recherche partielle)",
                example = "0177")
        String phone,

        @Schema(description = "Email (recherche partielle, insensible à la casse)",
                example = "@terangamed")
        String email,

        @Schema(description = "Statut du dossier")
        PatientStatus status,

        @Schema(description = "Sexe biologique")
        Gender gender,

        @Schema(description = "Groupe sanguin")
        BloodGroup bloodGroup,

        @Schema(description = "Date de naissance — borne basse (incluse)",
                example = "1980-01-01")
        LocalDate birthDateFrom,

        @Schema(description = "Date de naissance — borne haute (incluse)",
                example = "2010-12-31")
        LocalDate birthDateTo,

        @Schema(description = "Ville (recherche partielle, insensible à la casse)",
                example = "dakar")
        String city
) {
}
