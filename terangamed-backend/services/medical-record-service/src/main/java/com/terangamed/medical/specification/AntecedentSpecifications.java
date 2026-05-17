package com.terangamed.medical.specification;

import com.terangamed.medical.entity.Antecedent;
import com.terangamed.medical.entity.AntecedentType;
import org.springframework.data.jpa.domain.Specification;

/**
 * Specifications JPA pour les antécédents — filtrage par dossier, type, état actif.
 */
public final class AntecedentSpecifications {

    private AntecedentSpecifications() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    public static Specification<Antecedent> byMedicalRecordId(Long medicalRecordId) {
        if (medicalRecordId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("medicalRecordId"), medicalRecordId);
    }

    public static Specification<Antecedent> byType(AntecedentType type) {
        if (type == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Antecedent> onlyActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
