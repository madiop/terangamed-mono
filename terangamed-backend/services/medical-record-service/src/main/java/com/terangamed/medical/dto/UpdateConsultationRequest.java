package com.terangamed.medical.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mise à jour partielle d'une consultation. Modifiable UNIQUEMENT par le DOCTOR
 * créateur tant que {@code signed = false}. Une fois signée → {@code ConflictException}.
 *
 * <p>Le {@code medicalRecordId}, {@code doctorId}, {@code appointmentId} ne sont
 * pas modifiables (cohérence métier — pour transférer une consultation, il faut
 * la soft-deleter et en recréer une).
 */
@Schema(description = "Mise à jour partielle d'une consultation (interdite si signée)")
public record UpdateConsultationRequest(
        LocalDateTime consultationDate,
        @Size(max = 5000) String motif,
        @Valid VitalSignsDto vitalSigns,
        @Size(max = 5000) String examenCliniqueNotes,
        @Size(max = 5000) String diagnostic,
        @Size(max = 5000) String observations,
        @Size(max = 5000) String recommandations,
        LocalDate nextAppointmentSuggested
) {
}
