package com.terangamed.medical.service;

import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.medical.client.AppointmentServiceClient;
import com.terangamed.medical.client.DoctorServiceClient;
import com.terangamed.medical.client.PatientServiceClient;
import com.terangamed.medical.dto.AppointmentSnapshotDto;
import com.terangamed.medical.dto.DoctorSnapshotDto;
import com.terangamed.medical.dto.PatientSnapshotDto;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteLookupServiceTest {

    @Mock PatientServiceClient patientClient;
    @Mock DoctorServiceClient doctorClient;
    @Mock AppointmentServiceClient appointmentClient;

    RemoteLookupService service;

    @BeforeEach
    void setUp() {
        service = new RemoteLookupService(patientClient, doctorClient, appointmentClient);
    }

    private FeignException notFound() {
        Request req = Request.create(Request.HttpMethod.GET, "/x", Collections.emptyMap(),
                null, StandardCharsets.UTF_8, new RequestTemplate());
        return new FeignException.NotFound("Not Found", req, null, Collections.emptyMap());
    }

    @Test
    void fetch_patient_returns_dto() {
        PatientSnapshotDto dto = new PatientSnapshotDto(42L, "MRN-1", "X", "Y", null, "ACTIVE", "kc-sub-x");
        when(patientClient.findById(42L)).thenReturn(dto);

        assertThat(service.fetchPatient(42L)).isEqualTo(dto);
    }

    @Test
    void fetch_patient_translates_404_to_resource_not_found() {
        when(patientClient.findById(99L)).thenThrow(notFound());

        assertThatThrownBy(() -> service.fetchPatient(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void patient_fallback_propagates_resource_not_found() {
        ResourceNotFoundException expected = new ResourceNotFoundException("Patient", 1L);
        org.junit.jupiter.api.Assertions.assertThrows(ResourceNotFoundException.class,
                () -> invokeFallback("patientFallback", 1L, expected));
    }

    @Test
    void patient_fallback_returns_conflict_for_other_errors() {
        assertThatThrownBy(() -> invokeFallback("patientFallback", 1L, new RuntimeException("CB OPEN")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("PATIENT_SERVICE_UNAVAILABLE");
    }

    @Test
    void fetch_doctor_returns_dto() {
        DoctorSnapshotDto dto = new DoctorSnapshotDto(101L, "MED", "Sall", "Cheikh", "GENERAL_MEDICINE", "ACTIVE");
        when(doctorClient.findById(101L)).thenReturn(dto);

        assertThat(service.fetchDoctor(101L)).isEqualTo(dto);
    }

    @Test
    void fetch_doctor_translates_404() {
        when(doctorClient.findById(999L)).thenThrow(notFound());
        assertThatThrownBy(() -> service.fetchDoctor(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void doctor_fallback_returns_conflict_for_other_errors() {
        assertThatThrownBy(() -> invokeFallback("doctorFallback", 1L, new RuntimeException("CB OPEN")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("DOCTOR_SERVICE_UNAVAILABLE");
    }

    @Test
    void fetch_appointment_returns_dto() {
        AppointmentSnapshotDto dto = new AppointmentSnapshotDto(1L, 42L, 101L,
                LocalDateTime.now(), LocalDateTime.now(), "PLANNED");
        when(appointmentClient.findById(1L)).thenReturn(dto);

        assertThat(service.fetchAppointment(1L)).isEqualTo(dto);
    }

    @Test
    void fetch_appointment_translates_404() {
        when(appointmentClient.findById(99L)).thenThrow(notFound());
        assertThatThrownBy(() -> service.fetchAppointment(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void appointment_fallback_returns_conflict_for_other_errors() {
        assertThatThrownBy(() -> invokeFallback("appointmentFallback", 1L, new RuntimeException("Timeout")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("APPOINTMENT_SERVICE_UNAVAILABLE");
    }

    /**
     * Les méthodes fallback sont privées — invocation par réflexion pour les
     * tester directement (équivalent à ce que ferait Resilience4j).
     */
    @SuppressWarnings("UnusedReturnValue")
    private Object invokeFallback(String name, Long id, Throwable cause) throws Exception {
        var method = RemoteLookupService.class.getDeclaredMethod(name, Long.class, Throwable.class);
        method.setAccessible(true);
        try {
            return method.invoke(service, id, cause);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
    }
}
