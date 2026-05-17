package com.terangamed.medical.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.terangamed.medical.entity.AntecedentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Antécédent médical catégorisé")
public record AntecedentDto(
        Long id,
        Long medicalRecordId,
        AntecedentType type,
        String title,
        String description,
        LocalDate onsetDate,
        Boolean active,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        Long version
) {
}
