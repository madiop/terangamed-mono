package com.terangamed.doctor.repository;

import com.terangamed.doctor.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DoctorRepository
        extends JpaRepository<Doctor, Long>,
                JpaSpecificationExecutor<Doctor> {

    Optional<Doctor> findByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumber(String licenseNumber);

    Optional<Doctor> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Recherche un médecin par son identifiant Keycloak (claim {@code sub} du JWT).
     * Utilisé par {@code GET /api/doctors/me} pour résoudre le DoctorDto du
     * DOCTOR connecté sans dépendre de l'email.
     */
    Optional<Doctor> findByKeycloakSubject(UUID keycloakSubject);

    boolean existsByKeycloakSubject(UUID keycloakSubject);

    /**
     * Renvoie le plus grand suffixe numérique des n° d'ordre médical pour une année.
     * Format : {@code MED-YYYY-NNNNN}. Le suffixe commence à la position 10 (1-indexed).
     */
    @Query("""
            SELECT MAX(CAST(SUBSTRING(d.licenseNumber, 10) AS int))
            FROM Doctor d
            WHERE d.licenseNumber LIKE CONCAT('MED-', :year, '-%')
            """)
    Optional<Integer> findMaxSequenceForYear(@Param("year") String year);
}
