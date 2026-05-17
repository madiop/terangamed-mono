package com.terangamed.medical.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Ordonnance complète — incluant les lignes médicaments")
public record PrescriptionDto(
        Long id,
        String prescriptionNumber,
        Long consultationId,
        Instant issuedAt,
        LocalDate validUntil,
        String generalInstructions,
        List<PrescriptionLineDto> lines,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        Long version
) {
}
