package com.terangamed.patient.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.terangamed.patient.entity.BloodGroup;
import com.terangamed.patient.entity.Civility;
import com.terangamed.patient.entity.Gender;
import com.terangamed.patient.entity.PatientStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Représentation complète d'un patient pour les réponses API (GET).
 *
 * <p>Inclut les colonnes d'audit ({@code createdAt}, {@code updatedAt},
 * {@code createdBy}, {@code updatedBy}) et le numéro de version (utilisé pour
 * le verrouillage optimiste — le client doit le renvoyer lors d'un PUT pour
 * détecter les conflits d'écriture concurrente).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Représentation complète d'un patient")
public record PatientDto(
        @Schema(description = "Identifiant technique", example = "42")
        Long id,

        @Schema(description = "N° de dossier médical", example = "MR-2026-00001")
        String medicalRecordNumber,

        @Schema(description = "Civilité")
        Civility civility,

        @Schema(description = "Nom de famille", example = "Diop")
        String lastName,

        @Schema(description = "Prénom", example = "Fatou")
        String firstName,

        @Schema(description = "Date de naissance", example = "1985-03-12")
        LocalDate birthDate,

        @Schema(description = "Sexe biologique")
        Gender gender,

        @Schema(description = "Téléphone", example = "0177000001")
        String phone,

        @Schema(description = "Email", example = "fatou.diop@example.sn")
        String email,

        @Schema(description = "Adresse — ligne 1")
        String addressLine1,

        @Schema(description = "Adresse — ligne 2")
        String addressLine2,

        @Schema(description = "Code postal", example = "10000")
        String postalCode,

        @Schema(description = "Ville", example = "Dakar")
        String city,

        @Schema(description = "Pays", example = "Sénégal")
        String country,

        @Schema(description = "Groupe sanguin")
        BloodGroup bloodGroup,

        @Schema(description = "Allergies connues (texte libre)")
        String allergies,

        @Schema(description = "Nom du contact d'urgence")
        String emergencyContactName,

        @Schema(description = "Téléphone du contact d'urgence")
        String emergencyContactPhone,

        @Schema(description = "Statut du dossier")
        PatientStatus status,

        @Schema(description = "Horodatage de création (UTC)")
        Instant createdAt,

        @Schema(description = "Horodatage de dernière modification (UTC)")
        Instant updatedAt,

        @Schema(description = "Utilisateur qui a créé l'enregistrement")
        String createdBy,

        @Schema(description = "Utilisateur de la dernière modification")
        String updatedBy,

        @Schema(description = "Numéro de version (optimistic locking)", example = "0")
        Long version
) {
}
