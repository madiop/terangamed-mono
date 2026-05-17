package com.terangamed.doctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.terangamed.common.finance.Currency;
import com.terangamed.doctor.entity.DoctorStatus;
import com.terangamed.doctor.entity.Specialty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Représentation complète d'un médecin")
public record DoctorDto(
        Long id,
        String licenseNumber,
        String lastName,
        String firstName,
        Specialty specialty,
        String email,
        String phone,
        String officeAddress,
        Integer yearsOfExperience,
        BigDecimal consultationFee,
        Currency consultationFeeCurrency,
        String bio,
        DoctorStatus status,
        @Schema(description = "Identifiant Keycloak (sub) du compte utilisateur lié, ou null si non lié",
                example = "ffd2c2cd-ab6f-4da4-9508-694a0eb24eba")
        UUID keycloakSubject,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        Long version
) {
}
