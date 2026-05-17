package com.terangamed.medical.repository;

import com.terangamed.medical.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository {@link MedicalRecord}. Specifications activées pour les requêtes
 * dynamiques (filtres typés sur soft_deleted, patientId).
 */
@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long>,
        JpaSpecificationExecutor<MedicalRecord> {

    Optional<MedicalRecord> findByPatientId(Long patientId);

    boolean existsByPatientId(Long patientId);
}
