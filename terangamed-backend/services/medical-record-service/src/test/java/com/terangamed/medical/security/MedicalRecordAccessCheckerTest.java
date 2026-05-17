package com.terangamed.medical.security;

import com.terangamed.common.exception.ForbiddenException;
import com.terangamed.common.security.SecurityRoles;
import com.terangamed.medical.dto.PatientSnapshotDto;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.service.RemoteLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRecordAccessCheckerTest {

    @Mock CurrentUserProvider currentUser;
    @Mock RemoteLookupService remoteLookup;

    MedicalRecordAccessChecker checker;

    @BeforeEach
    void setUp() {
        checker = new MedicalRecordAccessChecker(currentUser, remoteLookup);
    }

    private static MedicalRecord recordOf(Long patientId) {
        return MedicalRecord.builder().id(1L).patientId(patientId).build();
    }

    @Test
    void admin_can_access_any_record() {
        when(currentUser.hasRole(SecurityRoles.ADMIN)).thenReturn(true);
        assertThatNoException().isThrownBy(() -> checker.ensureCanAccess(recordOf(42L)));
    }

    @Test
    void doctor_can_access_any_record() {
        when(currentUser.hasRole(SecurityRoles.ADMIN)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.DOCTOR)).thenReturn(true);
        assertThatNoException().isThrownBy(() -> checker.ensureCanAccess(recordOf(42L)));
    }

    @Test
    void patient_can_access_own_record() {
        when(currentUser.hasRole(SecurityRoles.ADMIN)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.DOCTOR)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.PATIENT)).thenReturn(true);
        when(currentUser.subject()).thenReturn("kc-sub-42");
        when(remoteLookup.fetchPatient(42L)).thenReturn(
                new PatientSnapshotDto(42L, "MRN-1", "X", "Y", null, "ACTIVE", "kc-sub-42"));

        assertThatNoException().isThrownBy(() -> checker.ensureCanAccess(recordOf(42L)));
    }

    @Test
    void patient_cannot_access_other_record() {
        when(currentUser.hasRole(SecurityRoles.ADMIN)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.DOCTOR)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.PATIENT)).thenReturn(true);
        when(currentUser.subject()).thenReturn("kc-sub-42");
        when(remoteLookup.fetchPatient(99L)).thenReturn(
                new PatientSnapshotDto(99L, "MRN-X", "Z", "W", null, "ACTIVE", "kc-sub-99"));

        assertThatThrownBy(() -> checker.ensureCanAccess(recordOf(99L)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("propre dossier");
    }

    @Test
    void patient_with_null_subject_is_rejected() {
        when(currentUser.hasRole(SecurityRoles.ADMIN)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.DOCTOR)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.PATIENT)).thenReturn(true);
        when(currentUser.subject()).thenReturn(null);

        assertThatThrownBy(() -> checker.ensureCanAccess(recordOf(42L)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("subject");
    }

    @Test
    void user_with_no_known_role_is_rejected() {
        when(currentUser.hasRole(SecurityRoles.ADMIN)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.DOCTOR)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.PATIENT)).thenReturn(false);

        assertThatThrownBy(() -> checker.ensureCanAccess(recordOf(42L)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ensure_can_access_patient_works_for_doctor() {
        when(currentUser.hasRole(SecurityRoles.ADMIN)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.DOCTOR)).thenReturn(true);
        assertThatNoException().isThrownBy(() -> checker.ensureCanAccessPatient(42L));
    }

    @Test
    void ensure_can_access_patient_works_for_owning_patient() {
        when(currentUser.hasRole(SecurityRoles.ADMIN)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.DOCTOR)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.PATIENT)).thenReturn(true);
        when(currentUser.subject()).thenReturn("kc-sub-42");
        when(remoteLookup.fetchPatient(42L)).thenReturn(
                new PatientSnapshotDto(42L, "MRN-1", "X", "Y", null, "ACTIVE", "kc-sub-42"));

        assertThatNoException().isThrownBy(() -> checker.ensureCanAccessPatient(42L));
    }

    @Test
    void ensure_can_access_patient_rejects_other_patient() {
        when(currentUser.hasRole(SecurityRoles.ADMIN)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.DOCTOR)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.PATIENT)).thenReturn(true);
        when(currentUser.subject()).thenReturn("kc-sub-42");
        when(remoteLookup.fetchPatient(99L)).thenReturn(
                new PatientSnapshotDto(99L, "MRN-X", "Z", "W", null, "ACTIVE", "kc-sub-99"));

        assertThatThrownBy(() -> checker.ensureCanAccessPatient(99L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void ensure_can_access_patient_rejects_unknown_role() {
        when(currentUser.hasRole(SecurityRoles.ADMIN)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.DOCTOR)).thenReturn(false);
        when(currentUser.hasRole(SecurityRoles.PATIENT)).thenReturn(false);

        assertThatThrownBy(() -> checker.ensureCanAccessPatient(42L))
                .isInstanceOf(ForbiddenException.class);
    }
}
