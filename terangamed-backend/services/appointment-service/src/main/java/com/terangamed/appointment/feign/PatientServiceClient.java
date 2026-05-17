package com.terangamed.appointment.feign;

import com.terangamed.appointment.dto.PatientSnapshotDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Client Feign vers patient-service (résolu par Eureka via {@code lb://}).
 *
 * <p><b>Resilience4j</b> est appliqué au niveau de l'instance dans
 * {@link com.terangamed.appointment.service.PatientLookupService} via
 * {@code @CircuitBreaker} + {@code @Retry} + {@code @TimeLimiter}.
 *
 * <p>L'authentification (Bearer JWT) est propagée par {@link FeignAuthInterceptor}.
 */
@FeignClient(name = "patient-service", contextId = "patientServiceClient")
public interface PatientServiceClient {

    @GetMapping("/api/patients/{id}")
    PatientSnapshotDto findById(@PathVariable("id") Long id);
}
