package com.terangamed.medical.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO partiel renvoyé par {@code patient-service} via Feign.
 * {@code @JsonIgnoreProperties(ignoreUnknown=true)} autorise patient-service
 * à ajouter des champs sans casser ce client.
 *
 * <p>Le champ {@code keycloakSubject} est crucial pour la sécurité PATIENT :
 * il permet de matcher le {@code sub} du JWT avec le {@code patientId} d'un
 * dossier (voir {@code MedicalRecordSecurityService}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientSnapshotDto(
        Long id,
        String medicalRecordNumber,
        String lastName,
        String firstName,
        String email,
        String status,
        String keycloakSubject
) {
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
