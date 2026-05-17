package com.terangamed.patient.dto;

import com.terangamed.patient.entity.BloodGroup;
import com.terangamed.patient.entity.Civility;
import com.terangamed.patient.entity.Gender;
import com.terangamed.patient.entity.PatientStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Données pour mettre à jour un patient existant (PUT {@code /api/patients/{id}}).
 *
 * <p><b>Sémantique partial-update</b> : tout champ {@code null} est ignoré
 * (le mapper MapStruct utilise {@code NullValuePropertyMappingStrategy.IGNORE}).
 * Cela permet d'envoyer uniquement les champs réellement modifiés.
 *
 * <p>Pour vider explicitement un champ texte, envoyer une chaîne vide ({@code ""}).
 *
 * <p><b>Champs immuables non présents</b> : {@code id}, {@code medicalRecordNumber},
 * {@code version} (le {@code version} arrive dans l'URL ou un header pour le check
 * d'optimistic locking — voir le contrôleur étape 4.3).
 */
@Schema(description = "Payload de mise à jour d'un patient — tous les champs sont optionnels (partial update)")
public record UpdatePatientRequest(

        @Schema(description = "Civilité")
        Civility civility,

        @Size(max = 100)
        @Schema(description = "Nom de famille")
        String lastName,

        @Size(max = 100)
        @Schema(description = "Prénom")
        String firstName,

        @Past
        @Schema(description = "Date de naissance (doit être passée)")
        LocalDate birthDate,

        @Schema(description = "Sexe biologique")
        Gender gender,

        @Size(max = 20)
        @Schema(description = "Téléphone")
        String phone,

        @Email
        @Size(max = 100)
        @Schema(description = "Email — doit rester unique dans le cabinet")
        String email,

        @Size(max = 200)
        @Schema(description = "Adresse — ligne 1")
        String addressLine1,

        @Size(max = 200)
        String addressLine2,

        @Size(max = 20)
        String postalCode,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String country,

        @Schema(description = "Groupe sanguin")
        BloodGroup bloodGroup,

        @Schema(description = "Allergies connues")
        String allergies,

        @Size(max = 200)
        String emergencyContactName,

        @Size(max = 20)
        String emergencyContactPhone,

        @Schema(description = "Statut du dossier — transitions ACTIVE ↔ INACTIVE ; ARCHIVED via endpoint dédié")
        PatientStatus status
) {
}
