package com.terangamed.medical.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.terangamed.medical.entity.BloodType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Dossier médical complet d'un patient")
public record MedicalRecordDto(
        Long id,
        Long patientId,
        BloodType bloodType,
        String allergiesSummary,
        String notes,
        Boolean softDeleted,
        Instant deletedAt,
        String deletedBy,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        Long version
) {
}
