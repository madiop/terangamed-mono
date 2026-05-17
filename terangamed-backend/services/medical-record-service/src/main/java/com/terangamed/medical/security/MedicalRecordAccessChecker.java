package com.terangamed.medical.security;

import com.terangamed.common.exception.ForbiddenException;
import com.terangamed.common.security.SecurityRoles;
import com.terangamed.medical.dto.PatientSnapshotDto;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.service.RemoteLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Vérifications fines (post-{@code @PreAuthorize}) que l'utilisateur connecté
 * est autorisé à accéder à un dossier ou une ressource médicale particulière.
 *
 * <p><b>Règle PATIENT</b> : un PATIENT ne peut accéder qu'à son propre dossier.
 * On résout son {@code patientId} en cherchant le patient dont
 * {@code keycloakSubject} matche le {@code sub} du JWT (via patient-service).
 *
 * <p>Si l'utilisateur est ADMIN ou DOCTOR, l'accès est accordé sans contrainte
 * supplémentaire (le {@code @PreAuthorize} a déjà filtré).
 */
@Component
@RequiredArgsConstructor
public class MedicalRecordAccessChecker {

    private final CurrentUserProvider currentUser;
    private final RemoteLookupService remoteLookup;

    /**
     * Vérifie que l'utilisateur connecté peut accéder au dossier {@code record}.
     * Lève {@link ForbiddenException} sinon.
     */
    public void ensureCanAccess(MedicalRecord record) {
        if (currentUser.hasRole(SecurityRoles.ADMIN) || currentUser.hasRole(SecurityRoles.DOCTOR)) {
            return;
        }
        if (currentUser.hasRole(SecurityRoles.PATIENT)) {
            ensurePatientOwnsRecord(record.getPatientId());
            return;
        }
        throw new ForbiddenException("Aucun rôle autorisé pour accéder à ce dossier");
    }

    /**
     * Idem mais pour un patientId direct (utile quand on n'a pas l'entité chargée).
     */
    public void ensureCanAccessPatient(Long patientId) {
        if (currentUser.hasRole(SecurityRoles.ADMIN) || currentUser.hasRole(SecurityRoles.DOCTOR)) {
            return;
        }
        if (currentUser.hasRole(SecurityRoles.PATIENT)) {
            ensurePatientOwnsRecord(patientId);
            return;
        }
        throw new ForbiddenException("Aucun rôle autorisé");
    }

    private void ensurePatientOwnsRecord(Long patientId) {
        String currentSub = currentUser.subject();
        if (currentSub == null) {
            throw new ForbiddenException("JWT sans subject — accès refusé");
        }
        PatientSnapshotDto patient = remoteLookup.fetchPatient(patientId);
        if (!Objects.equals(currentSub, patient.keycloakSubject())) {
            throw new ForbiddenException(
                    "Un patient ne peut accéder qu'à son propre dossier médical");
        }
    }
}
