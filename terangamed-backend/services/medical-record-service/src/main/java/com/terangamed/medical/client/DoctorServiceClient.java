package com.terangamed.medical.client;

import com.terangamed.medical.dto.DoctorSnapshotDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "doctor-service", contextId = "doctorServiceClient")
public interface DoctorServiceClient {

    @GetMapping("/api/doctors/{id}")
    DoctorSnapshotDto findById(@PathVariable("id") Long id);

    /**
     * Résolution par {@code sub} JWT — utilisé à la création de consultation
     * pour identifier le DOCTOR connecté sans header app (remplace V1
     * {@code X-Doctor-Id}).
     */
    @GetMapping("/api/doctors/by-keycloak-subject/{subject}")
    DoctorSnapshotDto findByKeycloakSubject(@PathVariable("subject") UUID subject);
}
