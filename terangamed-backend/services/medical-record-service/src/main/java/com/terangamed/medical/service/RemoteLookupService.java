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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Façade au-dessus des clients Feign vers patient-service, doctor-service et
 * appointment-service.
 *
 * <p>Applique :
 * <ul>
 *   <li>{@code @CircuitBreaker} — coupe les appels après N échecs (cf. config)</li>
 *   <li>{@code @Retry} — réessaye les erreurs transitoires</li>
 *   <li>fallback methods — utilisées par le CB quand il est OPEN</li>
 * </ul>
 *
 * <p><b>Mapping erreurs</b> :
 * <ul>
 *   <li>{@code FeignException.NotFound} → {@link ResourceNotFoundException} (404 propagé)</li>
 *   <li>autres erreurs (CB OPEN, timeout, 5xx) → {@link ConflictException} avec
 *       errorCode {@code *_SERVICE_UNAVAILABLE}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteLookupService {

    public static final String CB_PATIENT = "patient-service";
    public static final String CB_DOCTOR = "doctor-service";
    public static final String CB_APPOINTMENT = "appointment-service";

    private final PatientServiceClient patientClient;
    private final DoctorServiceClient doctorClient;
    private final AppointmentServiceClient appointmentClient;

    @CircuitBreaker(name = CB_PATIENT, fallbackMethod = "patientFallback")
    @Retry(name = CB_PATIENT)
    public PatientSnapshotDto fetchPatient(Long patientId) {
        try {
            return patientClient.findById(patientId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Patient", patientId);
        }
    }

    @CircuitBreaker(name = CB_DOCTOR, fallbackMethod = "doctorFallback")
    @Retry(name = CB_DOCTOR)
    public DoctorSnapshotDto fetchDoctor(Long doctorId) {
        try {
            return doctorClient.findById(doctorId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Doctor", doctorId);
        }
    }

    /**
     * Résout le médecin lié à un compte Keycloak via son {@code sub}. Utilisé
     * à la création de consultation pour identifier le DOCTOR connecté
     * (remplace l'ancien header {@code X-Doctor-Id} V1).
     *
     * <p>404 → propagé en {@link ResourceNotFoundException} (le compte Keycloak
     * existe mais n'est pas lié à un Doctor — l'admin doit faire la liaison).
     */
    @CircuitBreaker(name = CB_DOCTOR, fallbackMethod = "doctorBySubjectFallback")
    @Retry(name = CB_DOCTOR)
    public DoctorSnapshotDto fetchDoctorByKeycloakSubject(UUID subject) {
        try {
            return doctorClient.findByKeycloakSubject(subject);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException(
                    "Aucun médecin lié au compte Keycloak '" + subject + "'");
        }
    }

    @CircuitBreaker(name = CB_APPOINTMENT, fallbackMethod = "appointmentFallback")
    @Retry(name = CB_APPOINTMENT)
    public AppointmentSnapshotDto fetchAppointment(Long appointmentId) {
        try {
            return appointmentClient.findById(appointmentId);
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Appointment", appointmentId);
        }
    }

    // ─────────────────────────── Fallbacks ───────────────────────────

    @SuppressWarnings("unused")
    private PatientSnapshotDto patientFallback(Long patientId, Throwable ex) {
        log.warn("Patient lookup fallback for id={} — cause: {}", patientId, ex.getMessage());
        if (ex instanceof ResourceNotFoundException notFound) {
            throw notFound;
        }
        throw new ConflictException("PATIENT_SERVICE_UNAVAILABLE",
                "Patient service is temporarily unavailable. Please retry shortly.");
    }

    @SuppressWarnings("unused")
    private DoctorSnapshotDto doctorFallback(Long doctorId, Throwable ex) {
        log.warn("Doctor lookup fallback for id={} — cause: {}", doctorId, ex.getMessage());
        if (ex instanceof ResourceNotFoundException notFound) {
            throw notFound;
        }
        throw new ConflictException("DOCTOR_SERVICE_UNAVAILABLE",
                "Doctor service is temporarily unavailable. Please retry shortly.");
    }

    @SuppressWarnings("unused")
    private DoctorSnapshotDto doctorBySubjectFallback(UUID subject, Throwable ex) {
        log.warn("Doctor by-subject lookup fallback for subject={} — cause: {}", subject, ex.getMessage());
        if (ex instanceof ResourceNotFoundException notFound) {
            throw notFound;
        }
        throw new ConflictException("DOCTOR_SERVICE_UNAVAILABLE",
                "Doctor service is temporarily unavailable. Please retry shortly.");
    }

    @SuppressWarnings("unused")
    private AppointmentSnapshotDto appointmentFallback(Long appointmentId, Throwable ex) {
        log.warn("Appointment lookup fallback for id={} — cause: {}", appointmentId, ex.getMessage());
        if (ex instanceof ResourceNotFoundException notFound) {
            throw notFound;
        }
        throw new ConflictException("APPOINTMENT_SERVICE_UNAVAILABLE",
                "Appointment service is temporarily unavailable. Please retry shortly.");
    }
}
