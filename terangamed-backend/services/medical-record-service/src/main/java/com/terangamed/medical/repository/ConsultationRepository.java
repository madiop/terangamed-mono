package com.terangamed.medical.repository;

import com.terangamed.medical.entity.Consultation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConsultationRepository extends JpaRepository<Consultation, Long>,
        JpaSpecificationExecutor<Consultation> {

    Optional<Consultation> findByAppointmentId(Long appointmentId);

    boolean existsByAppointmentId(Long appointmentId);
}
