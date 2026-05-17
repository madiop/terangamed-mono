package com.terangamed.appointment.service;

import com.terangamed.appointment.dto.CreateAppointmentRequest;
import com.terangamed.appointment.dto.DoctorSnapshotDto;
import com.terangamed.appointment.dto.PatientSnapshotDto;
import com.terangamed.appointment.entity.Appointment;
import com.terangamed.appointment.entity.AppointmentStatus;
import com.terangamed.appointment.event.AppointmentCancelled;
import com.terangamed.appointment.event.AppointmentCompleted;
import com.terangamed.appointment.event.AppointmentConfirmed;
import com.terangamed.appointment.event.AppointmentNoShow;
import com.terangamed.appointment.event.AppointmentScheduled;
import com.terangamed.appointment.mapper.AppointmentMapper;
import com.terangamed.appointment.repository.AppointmentRepository;
import com.terangamed.common.kafka.TerangaMedTopics;
import com.terangamed.common.outbox.OutboxEventPublisher;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vérifie l'émission des 5 événements RDV via l'Outbox.
 */
@ExtendWith(MockitoExtension.class)
class AppointmentServiceOutboxTest {

    @Mock AppointmentRepository repository;
    @Mock RemoteLookupService remoteLookup;
    @Mock OutboxEventPublisher outboxPublisher;

    AppointmentMapper mapper;
    AppointmentService service;

    private static final Instant FUTURE = Instant.parse("2027-06-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(AppointmentMapper.class);
        service = new AppointmentService(repository, mapper, remoteLookup, outboxPublisher);
    }

    private static Appointment plannedAppointment() {
        return Appointment.builder()
                .id(10L)
                .patientId(1L).doctorId(2L)
                .patientNameSnapshot("Diop Fatou").doctorNameSnapshot("Sall Cheikh")
                .startTime(FUTURE).endTime(FUTURE.plusSeconds(30 * 60))
                .durationMinutes(30)
                .status(AppointmentStatus.PLANNED)
                .reason("Contrôle annuel")
                .build();
    }

    @Test
    void create_publishes_appointment_scheduled() {
        when(remoteLookup.fetchPatient(1L)).thenReturn(
                new PatientSnapshotDto(1L, "MR", "Diop", "Fatou", null, "ACTIVE"));
        when(remoteLookup.fetchDoctor(2L)).thenReturn(
                new DoctorSnapshotDto(2L, "MED", "Sall", "Cheikh", "GENERAL_MEDICINE", "ACTIVE"));
        when(repository.existsOverlapping(eq(2L), any(Instant.class), any(Instant.class)))
                .thenReturn(false);
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(10L);
            return a;
        });

        service.create(new CreateAppointmentRequest(1L, 2L, FUTURE, 30, "Contrôle", null));

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.APPOINTMENT_EVENTS), eq("10"),
                eq("Appointment"), eq("10"),
                eq("appointment.scheduled"), payload.capture());

        AppointmentScheduled event = (AppointmentScheduled) payload.getValue();
        assertThat(event.getAppointmentId()).isEqualTo(10L);
        assertThat(event.getPatientId()).isEqualTo(1L);
        assertThat(event.getDoctorId()).isEqualTo(2L);
        assertThat(event.getDurationMinutes()).isEqualTo(30);
    }

    @Test
    void confirm_publishes_appointment_confirmed() {
        Appointment a = plannedAppointment();
        when(repository.findById(10L)).thenReturn(Optional.of(a));
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirm(10L);

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.APPOINTMENT_EVENTS), eq("10"),
                eq("Appointment"), eq("10"),
                eq("appointment.confirmed"), payload.capture());

        AppointmentConfirmed event = (AppointmentConfirmed) payload.getValue();
        assertThat(event.getAppointmentId()).isEqualTo(10L);
    }

    @Test
    void complete_publishes_appointment_completed() {
        Appointment a = plannedAppointment();
        a.setStatus(AppointmentStatus.CONFIRMED);
        when(repository.findById(10L)).thenReturn(Optional.of(a));
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.complete(10L);

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.APPOINTMENT_EVENTS), eq("10"),
                eq("Appointment"), eq("10"),
                eq("appointment.completed"), payload.capture());

        AppointmentCompleted event = (AppointmentCompleted) payload.getValue();
        assertThat(event.getAppointmentId()).isEqualTo(10L);
    }

    @Test
    void cancel_publishes_appointment_cancelled_with_original_start() {
        Appointment a = plannedAppointment();
        when(repository.findById(10L)).thenReturn(Optional.of(a));
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancel(10L);

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.APPOINTMENT_EVENTS), eq("10"),
                eq("Appointment"), eq("10"),
                eq("appointment.cancelled"), payload.capture());

        AppointmentCancelled event = (AppointmentCancelled) payload.getValue();
        assertThat(event.getAppointmentId()).isEqualTo(10L);
        assertThat(event.getOriginalStartTime()).isEqualTo(FUTURE);
    }

    @Test
    void no_show_publishes_appointment_no_show() {
        Appointment a = plannedAppointment();
        a.setStatus(AppointmentStatus.CONFIRMED);
        when(repository.findById(10L)).thenReturn(Optional.of(a));
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markNoShow(10L);

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.APPOINTMENT_EVENTS), eq("10"),
                eq("Appointment"), eq("10"),
                eq("appointment.no-show"), payload.capture());

        AppointmentNoShow event = (AppointmentNoShow) payload.getValue();
        assertThat(event.getAppointmentId()).isEqualTo(10L);
        assertThat(event.getMissedAt()).isNotNull();
    }
}
