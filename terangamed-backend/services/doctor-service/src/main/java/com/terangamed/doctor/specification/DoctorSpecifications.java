package com.terangamed.doctor.specification;

import com.terangamed.doctor.dto.DoctorSearchCriteria;
import com.terangamed.doctor.entity.Doctor;
import com.terangamed.doctor.entity.DoctorStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Specifications JPA pour la recherche dynamique de médecins.
 *
 * <p>Pattern : {@code null = critère ignoré} (cf. patient-service).
 * Aucune requête JPQL avec {@code :param IS NULL OR ...}.
 */
public final class DoctorSpecifications {

    private DoctorSpecifications() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    public static Specification<Doctor> withCriteria(DoctorSearchCriteria c) {
        if (c == null) {
            return Specification.where(null);
        }
        return Specification
                .where(likeIgnoreCase("lastName", c.lastName()))
                .and(likeIgnoreCase("firstName", c.firstName()))
                .and(likeIgnoreCase("licenseNumber", c.licenseNumber()))
                .and(likeIgnoreCase("email", c.email()))
                .and(equalsField("specialty", c.specialty()))
                .and(equalsField("status", c.status()))
                .and(minYearsOfExperience(c.minYearsOfExperience()))
                .and(maxConsultationFee(c.maxConsultationFee()));
    }

    /**
     * Specification utile : seulement les médecins ACTIVE (pour l'API publique
     * de prise de RDV depuis le frontend).
     */
    public static Specification<Doctor> onlyActive() {
        return (root, query, cb) -> cb.equal(root.get("status"), DoctorStatus.ACTIVE);
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static Specification<Doctor> likeIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase().trim() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
    }

    private static <T> Specification<Doctor> equalsField(String field, T value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    private static Specification<Doctor> minYearsOfExperience(Integer min) {
        if (min == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("yearsOfExperience"), min);
    }

    private static Specification<Doctor> maxConsultationFee(BigDecimal max) {
        if (max == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("consultationFee"), max);
    }
}
