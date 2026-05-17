package com.terangamed.medical.client;

import com.terangamed.medical.dto.AppointmentSnapshotDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Client Feign vers appointment-service. Le RDV est OPTIONNEL pour une consultation
 * (consultation hors-RDV possible), donc l'appel n'est fait QUE si appointmentId
 * est fourni dans la requête de création.
 */
@FeignClient(name = "appointment-service", contextId = "appointmentServiceClient")
public interface AppointmentServiceClient {

    @GetMapping("/api/appointments/{id}")
    AppointmentSnapshotDto findById(@PathVariable("id") Long id);
}
