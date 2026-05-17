package com.terangamed.medical.repository;

import com.terangamed.medical.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    Optional<Prescription> findByConsultationId(Long consultationId);

    Optional<Prescription> findByPrescriptionNumber(String prescriptionNumber);

    boolean existsByPrescriptionNumber(String prescriptionNumber);

    boolean existsByConsultationId(Long consultationId);

    /**
     * Calcule la séquence max pour une année donnée (utilisée par
     * {@code PrescriptionService} pour générer le prochain n° {@code ORD-YYYY-NNNNN}).
     *
     * <p>Pattern identique à {@code DoctorRepository.findMaxSequenceForYear} —
     * utilisation de {@code SUBSTRING + CAST} pour extraire la séquence numérique.
     */
    @Query("""
            SELECT MAX(CAST(SUBSTRING(p.prescriptionNumber, 10) AS int))
            FROM Prescription p
            WHERE SUBSTRING(p.prescriptionNumber, 5, 4) = :year
            """)
    Optional<Integer> findMaxSequenceForYear(@Param("year") String year);
}
