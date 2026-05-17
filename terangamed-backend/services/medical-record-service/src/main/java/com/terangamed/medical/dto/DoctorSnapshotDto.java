package com.terangamed.medical.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO partiel renvoyé par {@code doctor-service} via Feign — utilisé pour
 * valider l'existence + le statut du médecin référencé dans une consultation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DoctorSnapshotDto(
        Long id,
        String licenseNumber,
        String lastName,
        String firstName,
        String specialty,
        String status
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
