package com.terangamed.appointment.repository;

import com.terangamed.appointment.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AppointmentRepository
        extends JpaRepository<Appointment, Long>,
                JpaSpecificationExecutor<Appointment> {

    /**
     * Détecte si un RDV chevauche un autre RDV PLANNED ou CONFIRMED du même médecin.
     *
     * <p>Algorithme de detection d'overlap :
     * deux intervalles [a1, a2] et [b1, b2] se chevauchent ⟺ a1 &lt; b2 ET a2 &gt; b1.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.doctorId = :doctorId
              AND a.status IN ('PLANNED', 'CONFIRMED')
              AND a.startTime < :endTime
              AND a.endTime > :startTime
            """)
    boolean existsOverlapping(@Param("doctorId") Long doctorId,
                              @Param("startTime") Instant startTime,
                              @Param("endTime") Instant endTime);

    /**
     * Variante pour update : exclut l'identifiant du RDV en cours de modification
     * (il ne doit pas se chevaucher avec lui-même).
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.doctorId = :doctorId
              AND a.id <> :excludeId
              AND a.status IN ('PLANNED', 'CONFIRMED')
              AND a.startTime < :endTime
              AND a.endTime > :startTime
            """)
    boolean existsOverlappingExcluding(@Param("doctorId") Long doctorId,
                                       @Param("startTime") Instant startTime,
                                       @Param("endTime") Instant endTime,
                                       @Param("excludeId") Long excludeId);
}
