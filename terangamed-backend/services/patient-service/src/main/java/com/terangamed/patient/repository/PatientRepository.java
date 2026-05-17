package com.terangamed.patient.repository;

import com.terangamed.patient.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository Spring Data JPA pour {@link Patient}.
 *
 * <p>Étend {@link JpaSpecificationExecutor} pour les filtres dynamiques via
 * {@code Specification<Patient>} — c'est le SEUL mécanisme autorisé pour les
 * recherches multi-critères dans TerangaMed (cf. règle d'architecture interdisant
 * les requêtes JPQL avec {@code :param IS NULL OR ...}).
 *
 * <p>Méthodes de lookup direct : pour les recherches par identifiant métier
 * (medicalRecordNumber, email), on utilise des derived queries Spring Data —
 * pas de JPQL maison nécessaire.
 */
@Repository
public interface PatientRepository
        extends JpaRepository<Patient, Long>,
                JpaSpecificationExecutor<Patient> {

    Optional<Patient> findByMedicalRecordNumber(String medicalRecordNumber);

    boolean existsByMedicalRecordNumber(String medicalRecordNumber);

    Optional<Patient> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Renvoie le plus grand suffixe numérique d'un n° dossier pour une année donnée.
     * Utilisé par le service métier pour générer le prochain {@code MR-YYYY-NNNNN}.
     *
     * <p>Pas de Specification ici — la requête est statique et non paramétrée
     * dynamiquement, donc le pattern derived query est légitime.
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT MAX(CAST(SUBSTRING(p.medicalRecordNumber, 9) AS int))
            FROM Patient p
            WHERE p.medicalRecordNumber LIKE CONCAT('MR-', :year, '-%')
            """)
    Optional<Integer> findMaxSequenceForYear(@org.springframework.data.repository.query.Param("year") String year);
}
