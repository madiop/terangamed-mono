package com.terangamed.medical.entity;

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
 * Dossier médical d'un patient — un seul {@code MedicalRecord} par patient
 * (contrainte d'unicité sur {@code patient_id}).
 *
 * <p>Pas de FK SQL vers patient-service (database-per-service). L'existence du
 * patient est validée à la création via Feign vers patient-service.
 *
 * <p>Soft-delete : on ne supprime jamais physiquement un dossier médical
 * (traçabilité médico-légale). Les consultations restent accessibles en lecture
 * pour audit.
 */
@Entity
@Table(name = "medical_records", indexes = {
        @Index(name = "idx_medical_record_patient", columnList = "patient_id", unique = true),
        @Index(name = "idx_medical_record_soft_deleted", columnList = "soft_deleted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "patientId", "bloodType", "softDeleted"})
public class MedicalRecord extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Référence patient-service. Pas de FK SQL — validation cross-service via Feign. */
    @Column(name = "patient_id", nullable = false, unique = true)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_type", length = 10)
    private BloodType bloodType;

    /**
     * Synthèse des allergies du patient (texte libre, court).
     * Pour le détail catégorisé, voir {@link Antecedent} avec
     * {@link AntecedentType#ALLERGY}.
     */
    @Column(name = "allergies_summary", columnDefinition = "TEXT")
    private String allergiesSummary;

    /** Notes générales libres (alerte allergique critique, info importante…). */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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
