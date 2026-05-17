package com.terangamed.notification.specification;

import com.terangamed.notification.dto.NotificationSearchCriteria;
import com.terangamed.notification.entity.Notification;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtres dynamiques pour la recherche de notifications.
 * Pattern : {@code null = critère ignoré} — aucun {@code :param IS NULL OR ...}.
 */
public final class NotificationSpecifications {

    private NotificationSpecifications() {
        throw new UnsupportedOperationException("Utility class — do not instantiate");
    }

    public static Specification<Notification> withCriteria(NotificationSearchCriteria c) {
        if (c == null) {
            return Specification.where(null);
        }
        return Specification
                .where(equalsField("sourceTopic", c.topic()))
                .and(equalsField("eventType", c.eventType()))
                .and(equalsField("aggregateType", c.aggregateType()))
                .and(equalsField("aggregateId", c.aggregateId()))
                .and(equalsField("status", c.status()))
                .and(receivedFrom(c.fromDate()))
                .and(receivedTo(c.toDate()));
    }

    private static <T> Specification<Notification> equalsField(String field, T value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s && s.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }

    private static Specification<Notification> receivedFrom(java.time.Instant from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("receivedAt"), from);
    }

    private static Specification<Notification> receivedTo(java.time.Instant to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("receivedAt"), to);
    }
}
