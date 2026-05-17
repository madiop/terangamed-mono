package com.terangamed.medical.repository;

import com.terangamed.medical.entity.PrescriptionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionLineRepository extends JpaRepository<PrescriptionLine, Long> {

    List<PrescriptionLine> findByPrescriptionIdOrderByIdAsc(Long prescriptionId);

    void deleteByPrescriptionId(Long prescriptionId);
}
