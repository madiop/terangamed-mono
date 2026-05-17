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

/**
 * Ligne d'une ordonnance — un médicament avec sa posologie.
 *
 * <p>Plusieurs {@code PrescriptionLine} par {@link Prescription}. Chaque ligne
 * est minimaliste mais autosuffisante pour l'impression d'une ordonnance papier
 * (champ libre à but de prescription, pas un système de dispensation pharma).
 */
@Entity
@Table(name = "prescription_lines", indexes = {
        @Index(name = "idx_prescription_line_prescription", columnList = "prescription_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "prescriptionId", "medicationName", "dosage", "duration"})
public class PrescriptionLine extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prescription_id", nullable = false)
    private Long prescriptionId;

    @Column(name = "medication_name", nullable = false, length = 200)
    private String medicationName;

    /** Dosage par prise (ex: "500 mg", "1 comprimé"). */
    @Column(name = "dosage", length = 100)
    private String dosage;

    /** Fréquence (ex: "3 fois par jour", "matin et soir"). */
    @Column(name = "frequency", length = 100)
    private String frequency;

    /** Durée du traitement (ex: "7 jours", "1 mois"). */
    @Column(name = "duration", length = 100)
    private String duration;

    @Enumerated(EnumType.STRING)
    @Column(name = "route", length = 30)
    private MedicationRoute route;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    /** Quantité prescrite (nombre de boîtes), nullable. */
    @Column(name = "quantity")
    private Integer quantity;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
