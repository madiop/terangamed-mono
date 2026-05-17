package com.terangamed.appointment.service;

import com.terangamed.appointment.dto.DoctorSnapshotDto;
import com.terangamed.appointment.dto.PatientSnapshotDto;
import com.terangamed.appointment.feign.DoctorServiceClient;
import com.terangamed.appointment.feign.PatientServiceClient;
import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ResourceNotFoundException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Tests Mockito de {@link RemoteLookupService}. On teste :
 * <ul>
 *   <li>Le happy path (DTO retourné)</li>
 *   <li>Le mapping {@code FeignException.NotFound} → {@code ResourceNotFoundException}</li>
 *   <li>Les fallbacks invoqués manuellement (Resilience4j n'est pas instrumenté en
 *       test unitaire, mais on vérifie que les méthodes fallback ont la bonne logique).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RemoteLookupServiceTest {

    @Mock PatientServiceClient patientClient;
    @Mock DoctorServiceClient doctorClient;

    RemoteLookupService service;

    @BeforeEach
    void setUp() {
        service = new RemoteLookupService(patientClient, doctorClient);
    }

    private static FeignException.NotFound notFound() {
        Request request = Request.create(Request.HttpMethod.GET, "/test",
                new HashMap<>(), null, new RequestTemplate());
        return new FeignException.NotFound("not found", request, null, null);
    }

    @Test
    void fetchPatient_returns_dto_on_success() {
        PatientSnapshotDto expected = new PatientSnapshotDto(
                1L, "MR-2026-00001", "Diop", "Fatou", "f@example.sn", "ACTIVE");
        when(patientClient.findById(1L)).thenReturn(expected);

        PatientSnapshotDto result = service.fetchPatient(1L);

        assertThat(result).isEqualTo(expected);
        assertThat(result.fullName()).isEqualTo("Diop Fatou");
    }

    @Test
    void fetchPatient_translates_404_to_ResourceNotFound() {
        when(patientClient.findById(999L)).thenThrow(notFound());

        assertThatThrownBy(() -> service.fetchPatient(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Patient")
                .hasMessageContaining("999");
    }

    @Test
    void fetchDoctor_returns_dto_on_success() {
        DoctorSnapshotDto expected = new DoctorSnapshotDto(
                1L, "MED-2026-00001", "Martin", "Jean", "GENERAL_MEDICINE", "ACTIVE");
        when(doctorClient.findById(1L)).thenReturn(expected);

        DoctorSnapshotDto result = service.fetchDoctor(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.fullName()).isEqualTo("Martin Jean");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void fetchDoctor_isActive_returns_false_when_on_leave() {
        DoctorSnapshotDto onLeave = new DoctorSnapshotDto(
                1L, "MED-2026-00001", "Martin", "Jean", "GENERAL_MEDICINE", "ON_LEAVE");
        when(doctorClient.findById(1L)).thenReturn(onLeave);

        assertThat(service.fetchDoctor(1L).isActive()).isFalse();
    }

    @Test
    void fetchDoctor_translates_404_to_ResourceNotFound() {
        when(doctorClient.findById(999L)).thenThrow(notFound());

        assertThatThrownBy(() -> service.fetchDoctor(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Doctor")
                .hasMessageContaining("999");
    }

    // ─────────────────────────── Fallbacks (invoqués via réflexion) ───────────────────────────
    //
    // En production, Resilience4j appelle ces méthodes quand le CB est OPEN. En test
    // unitaire, on n'instrumente pas Resilience4j ; on accède aux fallbacks par réflexion
    // pour vérifier leur logique : 404 préservé, autres erreurs → 503 ConflictException.

    @Test
    void patientFallback_propagates_ResourceNotFoundException() throws Exception {
        var fallback = RemoteLookupService.class.getDeclaredMethod(
                "patientFallback", Long.class, Throwable.class);
        fallback.setAccessible(true);

        ResourceNotFoundException original = new ResourceNotFoundException("Patient", 1L);

        assertThatThrownBy(() -> fallback.invoke(service, 1L, original))
                .hasCauseInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void patientFallback_translates_other_errors_to_ServiceUnavailable() throws Exception {
        var fallback = RemoteLookupService.class.getDeclaredMethod(
                "patientFallback", Long.class, Throwable.class);
        fallback.setAccessible(true);

        RuntimeException networkError = new RuntimeException("Connection refused");

        assertThatThrownBy(() -> fallback.invoke(service, 1L, networkError))
                .hasCauseInstanceOf(ConflictException.class)
                .cause()
                .hasMessageContaining("Patient service is temporarily unavailable");
    }

    @Test
    void doctorFallback_propagates_ResourceNotFoundException() throws Exception {
        var fallback = RemoteLookupService.class.getDeclaredMethod(
                "doctorFallback", Long.class, Throwable.class);
        fallback.setAccessible(true);

        ResourceNotFoundException original = new ResourceNotFoundException("Doctor", 1L);

        assertThatThrownBy(() -> fallback.invoke(service, 1L, original))
                .hasCauseInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void doctorFallback_translates_other_errors_to_ServiceUnavailable() throws Exception {
        var fallback = RemoteLookupService.class.getDeclaredMethod(
                "doctorFallback", Long.class, Throwable.class);
        fallback.setAccessible(true);

        RuntimeException timeout = new RuntimeException("Read timed out");

        assertThatThrownBy(() -> fallback.invoke(service, 1L, timeout))
                .hasCauseInstanceOf(ConflictException.class)
                .cause()
                .extracting("errorCode").isEqualTo("DOCTOR_SERVICE_UNAVAILABLE");
    }
}
