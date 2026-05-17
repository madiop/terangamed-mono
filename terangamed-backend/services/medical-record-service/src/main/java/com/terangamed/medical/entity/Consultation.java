package com.terangamed.medical.entity;

import com.terangamed.common.audit.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Consultation médicale — un enregistrement par visite/téléconsultation.
 *
 * <p>Liens cross-service (sans FK SQL — validés via Feign) :
 * <ul>
 *   <li>{@code doctorId} — médecin auteur (doctor-service)</li>
 *   <li>{@code appointmentId} — rendez-vous source (appointment-service), nullable
 *       pour permettre une consultation "hors RDV"</li>
 *   <li>{@code medicalRecordId} — dossier du patient (cohérence locale)</li>
 * </ul>
 *
 * <h3>Cycle de vie</h3>
 * <ol>
 *   <li>Création (DOCTOR uniquement) → {@code signed=false}, modifiable</li>
 *   <li>Signature ({@link com.terangamed.medical.service.ConsultationService#sign})
 *       → {@code signed=true} terminal, plus aucune modif possible</li>
 *   <li>Soft-delete (ADMIN uniquement) → {@code softDeleted=true}, exclu des
 *       requêtes par défaut mais conservé pour audit médico-légal</li>
 * </ol>
 *
 * <p>Les signes vitaux ({@link VitalSigns}) sont stockés en JSONB via Hibernate 6 :
 * {@code @JdbcTypeCode(SqlTypes.JSON)}. PostgreSQL gère la sérialisation native.
 */
@Entity
@Table(name = "consultations", indexes = {
        @Index(name = "idx_consultation_record", columnList = "medical_record_id"),
        @Index(name = "idx_consultation_doctor", columnList = "doctor_id"),
        @Index(name = "idx_consultation_appointment", columnList = "appointment_id"),
        @Index(name = "idx_consultation_date", columnList = "consultation_date"),
        @Index(name = "idx_consultation_signed", columnList = "signed"),
        @Index(name = "idx_consultation_soft_deleted", columnList = "soft_deleted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "medicalRecordId", "doctorId", "consultationDate", "signed", "softDeleted"})
public class Consultation extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "medical_record_id", nullable = false)
    private Long medicalRecordId;

    @Column(name = "doctor_id", nullable = false)
    private Long doctorId;

    /** Lien optionnel vers un RDV concrétisé (consultation peut être "spontanée"). */
    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "consultation_date", nullable = false)
    private LocalDateTime consultationDate;

    @Column(name = "motif", nullable = false, columnDefinition = "TEXT")
    private String motif;

    /**
     * Signes vitaux structurés (JSONB).
     * Hibernate 6 sérialise via Jackson en JSON natif PostgreSQL.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vital_signs", columnDefinition = "jsonb")
    private VitalSigns vitalSigns;

    @Column(name = "examen_clinique_notes", columnDefinition = "TEXT")
    private String examenCliniqueNotes;

    @Column(name = "diagnostic", columnDefinition = "TEXT")
    private String diagnostic;

    @Column(name = "observations", columnDefinition = "TEXT")
    private String observations;

    @Column(name = "recommandations", columnDefinition = "TEXT")
    private String recommandations;

    /** Date suggérée pour le prochain RDV — purement indicatif. */
    @Column(name = "next_appointment_suggested")
    private LocalDate nextAppointmentSuggested;

    @Column(name = "signed", nullable = false)
    @Builder.Default
    private Boolean signed = false;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "signed_by", length = 100)
    private String signedBy;

    @Column(name = "soft_deleted", nullable = false)
    @Builder.Default
    private Boolean softDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
