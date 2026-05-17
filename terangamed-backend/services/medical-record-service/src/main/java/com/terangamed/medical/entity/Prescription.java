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

import java.time.Instant;
import java.time.LocalDate;

/**
 * Ordonnance médicale — au plus une par {@link Consultation}.
 *
 * <p>Le numéro {@code prescriptionNumber} (format {@code ORD-YYYY-NNNNN}) est généré
 * par {@code PrescriptionService}. Lié à une consultation par {@code consultation_id}
 * (UNIQUE → 1-1 logique).
 *
 * <p>Les lignes de prescription (un médicament + posologie) sont des entités
 * séparées ({@link PrescriptionLine}) gérées via le service.
 */
@Entity
@Table(name = "prescriptions", indexes = {
        @Index(name = "idx_prescription_consultation", columnList = "consultation_id", unique = true),
        @Index(name = "idx_prescription_number", columnList = "prescription_number", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "prescriptionNumber", "consultationId", "issuedAt"})
public class Prescription extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prescription_number", nullable = false, unique = true, length = 30)
    private String prescriptionNumber;

    @Column(name = "consultation_id", nullable = false, unique = true)
    private Long consultationId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    /** Date de fin de validité — par défaut +3 mois côté service. */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "general_instructions", columnDefinition = "TEXT")
    private String generalInstructions;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
