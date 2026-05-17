package com.terangamed.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO partiel du médecin — utilisé uniquement pour valider l'existence + le statut
 * et capturer le snapshot du nom dans l'appointment.
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
    public String fullName() {
        return lastName + " " + firstName;
    }

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
