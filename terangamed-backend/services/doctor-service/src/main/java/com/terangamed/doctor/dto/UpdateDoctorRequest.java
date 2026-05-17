package com.terangamed.doctor.dto;

import com.terangamed.common.finance.Currency;
import com.terangamed.doctor.entity.DoctorStatus;
import com.terangamed.doctor.entity.Specialty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Données pour mettre à jour partiellement un médecin (PUT /api/doctors/{id}).
 * Tout champ {@code null} est ignoré (sémantique partial-update).
 */
@Schema(description = "Payload de mise à jour partielle d'un médecin")
public record UpdateDoctorRequest(
        @Size(max = 100) String lastName,
        @Size(max = 100) String firstName,
        Specialty specialty,
        @Email @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(max = 250) String officeAddress,
        @PositiveOrZero Integer yearsOfExperience,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal consultationFee,
        Currency consultationFeeCurrency,
        String bio,
        DoctorStatus status,
        @Schema(description = "Liaison Keycloak — null laisse inchangé (partial-update). " +
                "Pour délier, utiliser un endpoint dédié (à venir) plutôt que UPDATE.")
        UUID keycloakSubject
) {
}
