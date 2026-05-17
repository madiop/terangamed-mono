package com.terangamed.appointment.feign;

import com.terangamed.appointment.dto.DoctorSnapshotDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "doctor-service", contextId = "doctorServiceClient")
public interface DoctorServiceClient {

    @GetMapping("/api/doctors/{id}")
    DoctorSnapshotDto findById(@PathVariable("id") Long id);
}
