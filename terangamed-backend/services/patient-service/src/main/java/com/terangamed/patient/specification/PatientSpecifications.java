package com.terangamed.patient.specification;

import com.terangamed.patient.dto.PatientSearchCriteria;
import com.terangamed.patient.entity.Patient;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Specifications JPA pour la recherche dynamique de patients.
 *
 * <p><b>Règle d'architecture TerangaMed (rappel)</b> : aucune requête JPQL
 * dynamique avec {@code :param IS NULL OR field = :param}. Tous les filtres
 * dynamiques passent par des {@link Specification} qui retournent {@code null}
 * quand le critère est vide. Spring Data ignore les Specifications nulles à la
 * composition, ce qui rend automatiquement le critère « non appliqué ».
 *
 * <p><b>Avantages</b> :
 * <ul>
 *   <li>Pas de paramètre {@code null} typé injecté dans le SQL — PostgreSQL ne
 *       se plaint plus de coercitions de type ambiguës (ex: {@code bytea} vs
 *       {@code varchar})</li>
 *   <li>Plans d'exécution stables : seul le SQL nécessaire est généré</li>
 *   <li>Composition facile, testable unitairement</li>
 * </ul>
 *
 * <p><b>Pattern</b> : un helper privé par type de critère (texte, égalité,
 * intervalle de date). Chaque helper retourne {@code null} si le critère est
 * vide, sinon une {@link Specification} effective.
 */
public final class PatientSpecifications {

    private PatientSpecifications() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    /**
     * Compose toutes les Specifications correspondant aux critères non-vides.
     */
    public static Specification<Patient> withCriteria(PatientSearchCriteria c) {
        if (c == null) {
            return Specification.where(null); // équivalent à un cb.conjunction() — match all
        }
        return Specification
                .where(likeIgnoreCase("lastName", c.lastName()))
                .and(likeIgnoreCase("firstName", c.firstName()))
                .and(likeIgnoreCase("medicalRecordNumber", c.medicalRecordNumber()))
                .and(likeIgnoreCase("phone", c.phone()))
                .and(likeIgnoreCase("email", c.email()))
                .and(likeIgnoreCase("city", c.city()))
                .and(equalsField("status", c.status()))
                .and(equalsField("gender", c.gender()))
                .and(equalsField("bloodGroup", c.bloodGroup()))
                .and(birthDateBetween(c.birthDateFrom(), c.birthDateTo()));
    }

    // ─────────────────────────── Helpers ───────────────────────────

    /**
     * Retourne une Specification {@code LIKE %value%} insensible à la casse,
     * ou {@code null} si la valeur est {@code null}/blanche (le critère est ignoré).
     */
    private static Specification<Patient> likeIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String pattern = "%" + value.toLowerCase().trim() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
    }

    /**
     * Retourne une Specification d'égalité, ou {@code null} si la valeur est {@code null}.
     */
    private static <T> Specification<Patient> equalsField(String field, T value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    /**
     * Retourne une Specification {@code birthDate BETWEEN from AND to} avec gestion
     * des bornes optionnelles. {@code null} si les deux bornes sont absentes.
     */
    private static Specification<Patient> birthDateBetween(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return null;
        }
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("birthDate"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("birthDate"), from);
            }
            return cb.lessThanOrEqualTo(root.get("birthDate"), to);
        };
    }

    // ─────────────────────────── Specifications publiques additionnelles ───────────

    /**
     * Specification utile pour exclure les dossiers archivés des recherches courantes.
     */
    public static Specification<Patient> notArchived() {
        return (root, query, cb) -> cb.notEqual(root.get("status"),
                com.terangamed.patient.entity.PatientStatus.ARCHIVED);
    }
}
