package com.terangamed.medical.repository;

import com.terangamed.medical.entity.Antecedent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AntecedentRepository extends JpaRepository<Antecedent, Long>,
        JpaSpecificationExecutor<Antecedent> {

    List<Antecedent> findByMedicalRecordIdOrderByOnsetDateDesc(Long medicalRecordId);
}
