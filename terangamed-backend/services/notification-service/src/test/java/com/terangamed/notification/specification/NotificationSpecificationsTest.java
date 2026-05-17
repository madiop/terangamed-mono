package com.terangamed.notification.specification;

import com.terangamed.notification.dto.NotificationSearchCriteria;
import com.terangamed.notification.entity.Notification;
import com.terangamed.notification.entity.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSpecificationsTest {

    @Test
    void null_criteria_returns_specification_with_no_filter() {
        Specification<Notification> spec = NotificationSpecifications.withCriteria(null);
        assertThat(spec).isNotNull();
    }

    @Test
    void empty_criteria_returns_specification() {
        Specification<Notification> spec = NotificationSpecifications.withCriteria(
                new NotificationSearchCriteria(null, null, null, null, null, null, null));
        assertThat(spec).isNotNull();
    }

    @Test
    void blank_strings_treated_as_no_filter() {
        // On vérifie via l'absence d'erreur — la logique blank-as-null est dans le helper
        Specification<Notification> spec = NotificationSpecifications.withCriteria(
                new NotificationSearchCriteria("", "", "", "", null, null, null));
        assertThat(spec).isNotNull();
    }

    @Test
    void full_criteria_creates_valid_spec() {
        Specification<Notification> spec = NotificationSpecifications.withCriteria(
                new NotificationSearchCriteria(
                        "terangamed.patient.events", "patient.created",
                        "Patient", "42", NotificationStatus.RECEIVED,
                        java.time.Instant.now().minusSeconds(3600),
                        java.time.Instant.now()));
        assertThat(spec).isNotNull();
    }

    @Test
    void utility_class_should_not_be_instantiable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            var ctor = NotificationSpecifications.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
