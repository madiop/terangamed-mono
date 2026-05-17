package com.terangamed.appointment.specification;

import com.terangamed.appointment.AbstractPostgresIntegrationTest;
import com.terangamed.appointment.config.JpaConfig;
import com.terangamed.appointment.dto.AppointmentSearchCriteria;
import com.terangamed.appointment.entity.Appointment;
import com.terangamed.appointment.entity.AppointmentStatus;
import com.terangamed.appointment.repository.AppointmentRepository;
import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JpaAuditingAutoConfiguration.class)
@Import(JpaConfig.class)
class AppointmentSpecificationsTest extends AbstractPostgresIntegrationTest {

    @Autowired AppointmentRepository repository;

    private static final Instant BASE = Instant.parse("2026-06-15T10:00:00Z");

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.save(build(1L, 1L, "Diop Fatou", "Martin Jean",
                BASE, AppointmentStatus.PLANNED));
        repository.save(build(2L, 1L, "Sow Aminata", "Martin Jean",
                BASE.plus(1, ChronoUnit.HOURS), AppointmentStatus.CONFIRMED));
        repository.save(build(1L, 2L, "Diop Fatou", "Diallo Mariama",
                BASE.plus(1, ChronoUnit.DAYS), AppointmentStatus.COMPLETED));
    }

    @Test
    void empty_criteria_returns_all() {
        var results = repository.findAll(AppointmentSpecifications.withCriteria(
                new AppointmentSearchCriteria(null, null, null, null, null, null, null)));
        assertThat(results).hasSize(3);
    }

    @Test
    void null_criteria_returns_all() {
        assertThat(repository.findAll(AppointmentSpecifications.withCriteria(null))).hasSize(3);
    }

    @Test
    void filter_by_patient_id() {
        var results = repository.findAll(AppointmentSpecifications.withCriteria(
                new AppointmentSearchCriteria(1L, null, null, null, null, null, null)));
        assertThat(results).hasSize(2);
    }

    @Test
    void filter_by_doctor_id() {
        var results = repository.findAll(AppointmentSpecifications.withCriteria(
                new AppointmentSearchCriteria(null, 1L, null, null, null, null, null)));
        assertThat(results).hasSize(2);
    }

    @Test
    void filter_by_status() {
        var results = repository.findAll(AppointmentSpecifications.withCriteria(
                new AppointmentSearchCriteria(null, null, AppointmentStatus.COMPLETED, null, null, null, null)));
        assertThat(results).hasSize(1);
    }

    @Test
    void filter_by_start_time_range() {
        var results = repository.findAll(AppointmentSpecifications.withCriteria(
                new AppointmentSearchCriteria(null, null, null,
                        BASE.minus(1, ChronoUnit.HOURS),
                        BASE.plus(2, ChronoUnit.HOURS), null, null)));
        assertThat(results).hasSize(2);
    }

    @Test
    void filter_by_start_time_from_only() {
        var results = repository.findAll(AppointmentSpecifications.withCriteria(
                new AppointmentSearchCriteria(null, null, null,
                        BASE.plus(12, ChronoUnit.HOURS), null, null, null)));
        assertThat(results).hasSize(1);
    }

    @Test
    void filter_by_start_time_to_only() {
        var results = repository.findAll(AppointmentSpecifications.withCriteria(
                new AppointmentSearchCriteria(null, null, null,
                        null, BASE.plus(30, ChronoUnit.MINUTES), null, null)));
        assertThat(results).hasSize(1);
    }

    @Test
    void filter_by_patient_name_partial_case_insensitive() {
        var results = repository.findAll(AppointmentSpecifications.withCriteria(
                new AppointmentSearchCriteria(null, null, null, null, null, "DIOP", null)));
        assertThat(results).hasSize(2);
    }

    @Test
    void filter_by_doctor_name_partial() {
        var results = repository.findAll(AppointmentSpecifications.withCriteria(
                new AppointmentSearchCriteria(null, null, null, null, null, null, "diallo")));
        assertThat(results).hasSize(1);
    }

    @Test
    void blank_text_filters_are_ignored() {
        var results = repository.findAll(AppointmentSpecifications.withCriteria(
                new AppointmentSearchCriteria(null, null, null, null, null, "  ", "  ")));
        assertThat(results).hasSize(3);
    }

    @Test
    void utility_class_not_instantiable() {
        assertThatThrownBy(() -> {
            var ctor = AppointmentSpecifications.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    private static Appointment build(Long patientId, Long doctorId, String patientName,
                                     String doctorName, Instant start, AppointmentStatus status) {
        return Appointment.builder()
                .patientId(patientId)
                .doctorId(doctorId)
                .patientNameSnapshot(patientName)
                .doctorNameSnapshot(doctorName)
                .startTime(start)
                .endTime(start.plus(30, ChronoUnit.MINUTES))
                .durationMinutes(30)
                .status(status)
                .build();
    }
}
