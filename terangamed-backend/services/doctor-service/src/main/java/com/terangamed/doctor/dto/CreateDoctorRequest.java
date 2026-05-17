package com.terangamed.doctor.dto;

import com.terangamed.common.finance.Currency;
import com.terangamed.doctor.entity.Specialty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Données pour créer un médecin (POST /api/doctors).
 *
 * <p>Le {@code licenseNumber} est généré automatiquement par le service
 * (format {@code MED-YYYY-NNNNN}). Le statut est initialisé à {@code ACTIVE}.
 */
@Schema(description = "Payload de création d'un médecin")
public record CreateDoctorRequest(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Martin")
        @NotBlank @Size(max = 100)
        String lastName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Jean")
        @NotBlank @Size(max = 100)
        String firstName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "GENERAL_MEDICINE")
        @NotNull
        Specialty specialty,

        @Schema(example = "j.martin@terangamed.local")
        @Email @Size(max = 100)
        String email,

        @Size(max = 20)
        String phone,

        @Size(max = 250)
        String officeAddress,

        @Schema(example = "10")
        @PositiveOrZero @Min(0)
        Integer yearsOfExperience,

        @Schema(description = "Tarif consultation (montant numérique)", example = "15000")
        @DecimalMin(value = "0.0", inclusive = true)
        BigDecimal consultationFee,

        @Schema(description = "Devise du tarif. Si null, la devise par défaut configurée est utilisée (XOF/FCFA pour le Sénégal)",
                example = "XOF")
        Currency consultationFeeCurrency,

        @Schema(description = "Biographie courte (texte libre)")
        String bio,

        @Schema(description = "Identifiant Keycloak (sub) du compte utilisateur à lier à ce médecin. " +
                "Optionnel — peut être renseigné plus tard via PUT.",
                example = "ffd2c2cd-ab6f-4da4-9508-694a0eb24eba")
        UUID keycloakSubject
) {
}
