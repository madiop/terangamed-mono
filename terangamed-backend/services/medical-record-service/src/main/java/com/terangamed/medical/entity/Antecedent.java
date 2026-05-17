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

import java.time.LocalDate;

/**
 * Antécédent médical catégorisé d'un patient — appartient à un {@link MedicalRecord}.
 *
 * <p>Plusieurs antécédents par dossier (relation N-1 par {@code medical_record_id}).
 * Le champ {@code active} permet de marquer un antécédent comme résolu sans le
 * supprimer (ex: une infection guérie reste un ATCD historique).
 *
 * <p>Pas de relation JPA cascade — toutes les opérations passent par les services
 * pour conserver la maîtrise du flow (et activer Specifications).
 */
@Entity
@Table(name = "antecedents", indexes = {
        @Index(name = "idx_antecedent_record", columnList = "medical_record_id"),
        @Index(name = "idx_antecedent_type", columnList = "type"),
        @Index(name = "idx_antecedent_active", columnList = "active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "medicalRecordId", "type", "title", "active"})
public class Antecedent extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "medical_record_id", nullable = false)
    private Long medicalRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private AntecedentType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Date d'apparition / diagnostic (null si inconnue). */
    @Column(name = "onset_date")
    private LocalDate onsetDate;

    /** {@code true} si l'antécédent est toujours d'actualité. */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
