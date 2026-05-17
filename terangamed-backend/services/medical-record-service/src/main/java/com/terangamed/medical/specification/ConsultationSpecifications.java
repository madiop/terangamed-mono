package com.terangamed.medical.specification;

import com.terangamed.medical.dto.ConsultationSearchCriteria;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalTime;

/**
 * Specifications JPA pour la recherche dynamique de consultations.
 *
 * <p>Pattern : {@code null = critère ignoré} — aucune JPQL {@code :param IS NULL OR ...}.
 *
 * <p>Le filtre {@code patientId} ne peut pas être appliqué directement sur
 * {@code Consultation} (qui ne porte qu'un {@code medicalRecordId}). On utilise
 * donc une sous-requête JPA Criteria : {@code consultation.medicalRecordId IN
 * (SELECT mr.id FROM MedicalRecord mr WHERE mr.patientId = ?)}. Cela évite
 * d'ajouter une relation JPA bidirectionnelle qui complexifierait les entités.
 */
public final class ConsultationSpecifications {

    private ConsultationSpecifications() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    public static Specification<Consultation> withCriteria(ConsultationSearchCriteria c) {
        if (c == null) {
            return Specification.where(notSoftDeleted());
        }
        return Specification
                .where(notSoftDeleted())
                .and(byPatientId(c.patientId()))
                .and(equalsField("doctorId", c.doctorId()))
                .and(fromConsultationDate(c.fromDate()))
                .and(toConsultationDate(c.toDate()))
                .and(equalsField("signed", c.signed()))
                .and(keywordLike(c.keyword()));
    }

    /** Inclut uniquement les consultations non soft-deleted. */
    public static Specification<Consultation> notSoftDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("softDeleted"));
    }

    /** Toutes les consultations d'un dossier. */
    public static Specification<Consultation> byMedicalRecordId(Long medicalRecordId) {
        if (medicalRecordId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("medicalRecordId"), medicalRecordId);
    }

    // ─────────────────────────── Helpers privés ───────────────────────────

    /**
     * Sous-requête : {@code consultation.medicalRecordId IN (SELECT mr.id FROM
     * MedicalRecord mr WHERE mr.patientId = ?)}.
     */
    private static Specification<Consultation> byPatientId(Long patientId) {
        if (patientId == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            var mr = sub.from(MedicalRecord.class);
            sub.select(mr.get("id"))
                    .where(cb.equal(mr.get("patientId"), patientId));
            return root.get("medicalRecordId").in(sub);
        };
    }

    private static <T> Specification<Consultation> equalsField(String field, T value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    private static Specification<Consultation> fromConsultationDate(java.time.LocalDate from) {
        if (from == null) {
            return null;
        }
        var fromDateTime = from.atStartOfDay();
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("consultationDate"), fromDateTime);
    }

    private static Specification<Consultation> toConsultationDate(java.time.LocalDate to) {
        if (to == null) {
            return null;
        }
        var toDateTime = to.atTime(LocalTime.MAX);
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("consultationDate"), toDateTime);
    }

    private static Specification<Consultation> keywordLike(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + keyword.toLowerCase().trim() + "%";
        return (root, query, cb) -> {
            Predicate motif = cb.like(cb.lower(root.get("motif")), pattern);
            Predicate diag = cb.like(cb.lower(cb.coalesce(root.get("diagnostic"), "")), pattern);
            return cb.or(motif, diag);
        };
    }
}
