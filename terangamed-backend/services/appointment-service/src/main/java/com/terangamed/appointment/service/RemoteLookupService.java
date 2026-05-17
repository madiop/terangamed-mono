package com.terangamed.appointment.service;

import com.terangamed.appointment.dto.DoctorSnapshotDto;
import com.terangamed.appointment.dto.PatientSnapshotDto;
import com.terangamed.appointment.feign.DoctorServiceClient;
import com.terangamed.appointment.feign.PatientServiceClient;
import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ResourceNotFoundException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Façade au-dessus des clients Feign vers patient-service et doctor-service.
 * Applique :
 * <ul>
 *   <li>{@code @CircuitBreaker} — coupe les appels après N échecs (cf. application.yml)</li>
 *   <li>{@code @Retry} — réessaye en cas d'erreur transitoire (timeout, 5xx)</li>
 *   <li>fallback methods — utilisées par le circuit breaker quand il est OPEN</li>
 * </ul>
 *
 * <p><b>Mapping des erreurs</b> :
 * <ul>
 *   <li>{@code FeignException.NotFound} (404) → {@link ResourceNotFoundException} → 404 côté API</li>
 *   <li>autres {@code FeignException} → fallback (CB ouvert) → {@link ConflictException}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteLookupService {

    public static final String CB_PATIENT = "patient-service";
    public static final String CB_DOCTOR = "doctor-service";

    private final PatientServiceClient patientClient;
    private final DoctorServiceClient doctorClient;

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

    // ─────────────────────────── Fallbacks ───────────────────────────
    //
    // Appelés par Resilience4j quand le circuit breaker est OPEN (trop d'échecs)
    // ou quand toutes les tentatives Retry ont échoué. La signature DOIT inclure
    // l'argument original PLUS un Throwable.
    //
    // Stratégie : on ne renvoie pas un objet "factice" — on lève une ConflictException
    // pour signaler clairement que la création de RDV n'est pas possible maintenant
    // (le service downstream est down). Le client peut retry plus tard.

    @SuppressWarnings("unused") // appelée par Resilience4j via réflexion
    private PatientSnapshotDto patientFallback(Long patientId, Throwable ex) {
        log.warn("Patient lookup fallback for id={} — cause: {}", patientId, ex.getMessage());
        if (ex instanceof ResourceNotFoundException notFound) {
            throw notFound; // ne pas masquer le 404 derrière un 503
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
}
