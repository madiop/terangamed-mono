package com.terangamed.appointment.repository;

import com.terangamed.appointment.AbstractPostgresIntegrationTest;
import com.terangamed.appointment.config.JpaConfig;
import com.terangamed.appointment.entity.Appointment;
import com.terangamed.appointment.entity.AppointmentStatus;
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

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JpaAuditingAutoConfiguration.class)
@Import(JpaConfig.class)
class AppointmentRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired AppointmentRepository repository;

    private static final Long DOCTOR_A = 1L;
    private static final Long DOCTOR_B = 2L;
    private static final Instant BASE = Instant.parse("2026-06-15T10:00:00Z");

    @BeforeEach
    void cleanup() { repository.deleteAll(); }

    @Test
    void existsOverlapping_returns_true_for_overlapping_intervals() {
        // RDV existant : 10:00 → 10:30 (30 min)
        repository.save(buildAppointment(DOCTOR_A, BASE, 30, AppointmentStatus.CONFIRMED));

        // Tenter [10:15 → 10:45] → chevauche
        Instant newStart = BASE.plus(15, ChronoUnit.MINUTES);
        Instant newEnd = newStart.plus(30, ChronoUnit.MINUTES);

        assertThat(repository.existsOverlapping(DOCTOR_A, newStart, newEnd)).isTrue();
    }

    @Test
    void existsOverlapping_false_when_adjacent_intervals() {
        // RDV existant : 10:00 → 10:30
        repository.save(buildAppointment(DOCTOR_A, BASE, 30, AppointmentStatus.PLANNED));

        // [10:30 → 11:00] = adjacent strict (pas de chevauchement)
        Instant adjacentStart = BASE.plus(30, ChronoUnit.MINUTES);
        Instant adjacentEnd = adjacentStart.plus(30, ChronoUnit.MINUTES);

        assertThat(repository.existsOverlapping(DOCTOR_A, adjacentStart, adjacentEnd)).isFalse();
    }

    @Test
    void existsOverlapping_ignores_other_doctors() {
        repository.save(buildAppointment(DOCTOR_A, BASE, 30, AppointmentStatus.CONFIRMED));

        // Même créneau pour DOCTOR_B → pas de conflit
        assertThat(repository.existsOverlapping(DOCTOR_B, BASE, BASE.plus(30, ChronoUnit.MINUTES)))
                .isFalse();
    }

    @Test
    void existsOverlapping_ignores_cancelled_and_completed() {
        repository.save(buildAppointment(DOCTOR_A, BASE, 30, AppointmentStatus.CANCELLED));
        repository.save(buildAppointment(DOCTOR_A, BASE, 30, AppointmentStatus.COMPLETED));
        repository.save(buildAppointment(DOCTOR_A, BASE, 30, AppointmentStatus.NO_SHOW));

        // Aucun RDV PLANNED/CONFIRMED → pas de conflit
        assertThat(repository.existsOverlapping(DOCTOR_A, BASE, BASE.plus(30, ChronoUnit.MINUTES)))
                .isFalse();
    }

    @Test
    void existsOverlappingExcluding_excludes_self() {
        Appointment saved = repository.save(buildAppointment(DOCTOR_A, BASE, 30, AppointmentStatus.CONFIRMED));

        // Vérifier que l'appointment lui-même n'est pas considéré comme un conflit
        assertThat(repository.existsOverlappingExcluding(
                DOCTOR_A, BASE, BASE.plus(30, ChronoUnit.MINUTES), saved.getId()))
                .isFalse();
    }

    @Test
    void existsOverlappingExcluding_finds_other_overlaps() {
        Appointment first = repository.save(buildAppointment(DOCTOR_A, BASE, 30, AppointmentStatus.CONFIRMED));
        Instant secondStart = BASE.plus(60, ChronoUnit.MINUTES);
        repository.save(buildAppointment(DOCTOR_A, secondStart, 30, AppointmentStatus.CONFIRMED));

        // Modifier le first pour empiéter sur le second → doit détecter le conflit
        Instant newStart = secondStart.plus(5, ChronoUnit.MINUTES);
        Instant newEnd = newStart.plus(30, ChronoUnit.MINUTES);

        assertThat(repository.existsOverlappingExcluding(DOCTOR_A, newStart, newEnd, first.getId()))
                .isTrue();
    }

    private Appointment buildAppointment(Long doctorId, Instant start, int durationMin, AppointmentStatus status) {
        return Appointment.builder()
                .patientId(99L)
                .doctorId(doctorId)
                .patientNameSnapshot("Patient Test")
                .doctorNameSnapshot("Doctor Test")
                .startTime(start)
                .endTime(start.plus(durationMin, ChronoUnit.MINUTES))
                .durationMinutes(durationMin)
                .status(status)
                .build();
    }
}
