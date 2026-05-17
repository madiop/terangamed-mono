package com.terangamed.patient.dto;

import com.terangamed.patient.entity.BloodGroup;
import com.terangamed.patient.entity.Civility;
import com.terangamed.patient.entity.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Données pour créer un nouveau patient (POST {@code /api/patients}).
 *
 * <p><b>Champs auto-gérés par le service</b>, donc absents de cette DTO :
 * <ul>
 *   <li>{@code medicalRecordNumber} — généré par {@code PatientService.generateMedicalRecordNumber()}</li>
 *   <li>{@code status} — initialisé à {@code ACTIVE} à la création</li>
 *   <li>{@code id}, {@code version} — gérés par JPA</li>
 *   <li>colonnes d'audit — peuplées par {@code AuditingEntityListener}</li>
 * </ul>
 */
@Schema(description = "Payload de création d'un patient — champs marqués obligatoires en rouge dans Swagger")
public record CreatePatientRequest(

        @Schema(description = "Civilité", requiredMode = Schema.RequiredMode.REQUIRED, example = "MME")
        @NotNull
        Civility civility,

        @Schema(description = "Nom de famille", requiredMode = Schema.RequiredMode.REQUIRED, example = "Diop")
        @NotBlank
        @Size(max = 100)
        String lastName,

        @Schema(description = "Prénom", requiredMode = Schema.RequiredMode.REQUIRED, example = "Fatou")
        @NotBlank
        @Size(max = 100)
        String firstName,

        @Schema(description = "Date de naissance (ISO YYYY-MM-DD)", requiredMode = Schema.RequiredMode.REQUIRED, example = "1985-03-12")
        @NotNull
        @Past
        LocalDate birthDate,

        @Schema(description = "Sexe biologique", requiredMode = Schema.RequiredMode.REQUIRED, example = "FEMALE")
        @NotNull
        Gender gender,

        @Schema(description = "Téléphone", example = "0177000001")
        @Size(max = 20)
        String phone,

        @Schema(description = "Email — doit être unique dans tout le cabinet", example = "fatou.diop@example.sn")
        @Email
        @Size(max = 100)
        String email,

        @Schema(description = "Adresse — ligne 1")
        @Size(max = 200)
        String addressLine1,

        @Schema(description = "Adresse — ligne 2 (optionnel)")
        @Size(max = 200)
        String addressLine2,

        @Schema(description = "Code postal")
        @Size(max = 20)
        String postalCode,

        @Schema(description = "Ville", example = "Dakar")
        @Size(max = 100)
        String city,

        @Schema(description = "Pays", example = "Sénégal")
        @Size(max = 100)
        String country,

        @Schema(description = "Groupe sanguin (UNKNOWN si non renseigné)", example = "O_POS")
        BloodGroup bloodGroup,

        @Schema(description = "Allergies connues (texte libre)")
        String allergies,

        @Schema(description = "Nom du contact d'urgence")
        @Size(max = 200)
        String emergencyContactName,

        @Schema(description = "Téléphone du contact d'urgence")
        @Size(max = 20)
        String emergencyContactPhone
) {
}
