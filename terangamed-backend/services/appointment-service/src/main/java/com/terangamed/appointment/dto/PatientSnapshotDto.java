package com.terangamed.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO partiel renvoyé par {@code patient-service} via Feign — on ne lit que ce
 * qui est utile pour le snapshot. {@code @JsonIgnoreProperties(ignoreUnknown=true)}
 * autorise patient-service à ajouter des champs sans casser ce client.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientSnapshotDto(
        Long id,
        String medicalRecordNumber,
        String lastName,
        String firstName,
        String email,
        String status
) {
    public String fullName() {
        return lastName + " " + firstName;
    }
}
