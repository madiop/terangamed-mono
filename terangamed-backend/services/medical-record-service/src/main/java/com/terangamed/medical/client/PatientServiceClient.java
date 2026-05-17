package com.terangamed.medical.client;

import com.terangamed.medical.dto.PatientSnapshotDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Client Feign vers patient-service (résolu par Eureka via {@code lb://}).
 * Resilience4j (CB + retry + timelimiter) appliqué dans
 * {@link com.terangamed.medical.service.RemoteLookupService}.
 */
@FeignClient(name = "patient-service", contextId = "patientServiceClient")
public interface PatientServiceClient {

    @GetMapping("/api/patients/{id}")
    PatientSnapshotDto findById(@PathVariable("id") Long id);
}
