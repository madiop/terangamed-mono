package com.terangamed.appointment.specification;

import com.terangamed.appointment.dto.AppointmentSearchCriteria;
import com.terangamed.appointment.entity.Appointment;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * Specifications JPA pour la recherche dynamique de RDV.
 * Pattern null-safe identique aux autres services.
 */
public final class AppointmentSpecifications {

    private AppointmentSpecifications() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Specification<Appointment> withCriteria(AppointmentSearchCriteria c) {
        if (c == null) {
            return Specification.where(null);
        }
        return Specification
                .where(equalsField("patientId", c.patientId()))
                .and(equalsField("doctorId", c.doctorId()))
                .and(equalsField("status", c.status()))
                .and(startTimeBetween(c.startTimeFrom(), c.startTimeTo()))
                .and(likeIgnoreCase("patientNameSnapshot", c.patientName()))
                .and(likeIgnoreCase("doctorNameSnapshot", c.doctorName()));
    }

    private static Specification<Appointment> likeIgnoreCase(String field, String value) {
        if (value == null || value.isBlank()) return null;
        String pattern = "%" + value.toLowerCase().trim() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern);
    }

    private static <T> Specification<Appointment> equalsField(String field, T value) {
        if (value == null) return null;
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    private static Specification<Appointment> startTimeBetween(Instant from, Instant to) {
        if (from == null && to == null) return null;
        return (root, query, cb) -> {
            if (from != null && to != null) return cb.between(root.get("startTime"), from, to);
            if (from != null) return cb.greaterThanOrEqualTo(root.get("startTime"), from);
            return cb.lessThanOrEqualTo(root.get("startTime"), to);
        };
    }
}
