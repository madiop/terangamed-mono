package com.terangamed.appointment.entity;

import com.terangamed.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Entité JPA — un rendez-vous médical entre un patient et un médecin.
 *
 * <p><b>Patient et médecin référencés par ID</b> (pas de jointure JPA cross-service).
 * Les noms sont snapshotés à la création pour éviter les appels réseau lors du
 * listing — eventual consistency acceptable pour un cabinet médical.
 *
 * <p><b>{@code endTime}</b> est calculé à l'insertion ({@code startTime + durationMinutes})
 * et stocké pour faciliter les requêtes d'overlap.
 */
@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appointment_patient", columnList = "patient_id"),
        @Index(name = "idx_appointment_doctor", columnList = "doctor_id"),
        @Index(name = "idx_appointment_start", columnList = "start_time"),
        @Index(name = "idx_appointment_status", columnList = "status"),
        @Index(name = "idx_appointment_doctor_time", columnList = "doctor_id,start_time,end_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "patientId", "doctorId", "startTime", "status"})
public class Appointment extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    @Column(name = "patient_name_snapshot", nullable = false, length = 200)
    private String patientNameSnapshot;

    @Column(name = "doctor_name_snapshot", nullable = false, length = 200)
    private String doctorNameSnapshot;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AppointmentStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
