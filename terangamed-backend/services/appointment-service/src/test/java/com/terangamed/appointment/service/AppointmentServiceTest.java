package com.terangamed.appointment.service;

import com.terangamed.appointment.dto.AppointmentDto;
import com.terangamed.appointment.dto.CreateAppointmentRequest;
import com.terangamed.appointment.dto.DoctorSnapshotDto;
import com.terangamed.appointment.dto.PatientSnapshotDto;
import com.terangamed.appointment.dto.UpdateAppointmentRequest;
import com.terangamed.appointment.entity.Appointment;
import com.terangamed.appointment.entity.AppointmentStatus;
import com.terangamed.appointment.mapper.AppointmentMapper;
import com.terangamed.appointment.repository.AppointmentRepository;
import com.terangamed.common.exception.BadRequestException;
import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.outbox.OutboxEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock AppointmentRepository repository;
    @Mock RemoteLookupService remoteLookup;
    @Mock OutboxEventPublisher outboxPublisher;

    AppointmentMapper mapper;
    AppointmentService service;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(AppointmentMapper.class);
        service = new AppointmentService(repository, mapper, remoteLookup, outboxPublisher);
    }

    private static final Instant FUTURE = Instant.parse("2027-06-15T10:00:00Z");

    private static PatientSnapshotDto activePatient() {
        return new PatientSnapshotDto(1L, "MR-2026-00001", "Diop", "Fatou", "f@example.sn", "ACTIVE");
    }

    private static DoctorSnapshotDto activeDoctor() {
        return new DoctorSnapshotDto(1L, "MED-2026-00001", "Martin", "Jean", "GENERAL_MEDICINE", "ACTIVE");
    }

    @Test
    void create_validates_patient_doctor_overlap_then_persists() {
        when(remoteLookup.fetchPatient(1L)).thenReturn(activePatient());
        when(remoteLookup.fetchDoctor(1L)).thenReturn(activeDoctor());
        when(repository.existsOverlapping(eq(1L), any(), any())).thenReturn(false);
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                1L, 1L, FUTURE, 30, "Consultation suivi", null);

        AppointmentDto dto = service.create(request);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(repository).save(captor.capture());
        Appointment saved = captor.getValue();

        assertThat(saved.getPatientId()).isEqualTo(1L);
        assertThat(saved.getDoctorId()).isEqualTo(1L);
        assertThat(saved.getPatientNameSnapshot()).isEqualTo("Diop Fatou");
        assertThat(saved.getDoctorNameSnapshot()).isEqualTo("Martin Jean");
        assertThat(saved.getStatus()).isEqualTo(AppointmentStatus.PLANNED);
        assertThat(saved.getEndTime()).isEqualTo(FUTURE.plus(30, ChronoUnit.MINUTES));
        assertThat(dto.id()).isEqualTo(100L);
    }

    @Test
    void create_rejects_inactive_doctor() {
        when(remoteLookup.fetchPatient(1L)).thenReturn(activePatient());
        DoctorSnapshotDto inactiveDoctor = new DoctorSnapshotDto(
                1L, "MED-2026-00001", "Martin", "Jean", "GENERAL_MEDICINE", "ON_LEAVE");
        when(remoteLookup.fetchDoctor(1L)).thenReturn(inactiveDoctor);

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                1L, 1L, FUTURE, 30, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("DOCTOR_NOT_ACTIVE");

        verify(repository, never()).save(any());
    }

    @Test
    void create_rejects_overlapping_appointment() {
        when(remoteLookup.fetchPatient(1L)).thenReturn(activePatient());
        when(remoteLookup.fetchDoctor(1L)).thenReturn(activeDoctor());
        when(repository.existsOverlapping(eq(1L), any(), any())).thenReturn(true);

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                1L, 1L, FUTURE, 30, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("APPOINTMENT_OVERLAP");

        verify(repository, never()).save(any());
    }

    @Test
    void create_propagates_patient_not_found() {
        when(remoteLookup.fetchPatient(1L)).thenThrow(new ResourceNotFoundException("Patient", 1L));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                1L, 1L, FUTURE, 30, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(remoteLookup, never()).fetchDoctor(anyLong());
        verify(repository, never()).save(any());
    }

    @Test
    void update_changes_time_and_rechecks_overlap() {
        Appointment existing = sampleAppointment(5L, AppointmentStatus.CONFIRMED);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.existsOverlappingExcluding(eq(1L), any(), any(), eq(5L))).thenReturn(false);
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant newStart = FUTURE.plus(2, ChronoUnit.HOURS);
        UpdateAppointmentRequest request = new UpdateAppointmentRequest(newStart, 45, null, null);

        AppointmentDto dto = service.update(5L, request);

        assertThat(dto.startTime()).isEqualTo(newStart);
        assertThat(dto.durationMinutes()).isEqualTo(45);
        assertThat(dto.endTime()).isEqualTo(newStart.plus(45, ChronoUnit.MINUTES));
    }

    @Test
    void update_rejects_overlap() {
        Appointment existing = sampleAppointment(5L, AppointmentStatus.CONFIRMED);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.existsOverlappingExcluding(eq(1L), any(), any(), eq(5L))).thenReturn(true);

        UpdateAppointmentRequest request = new UpdateAppointmentRequest(
                FUTURE.plus(2, ChronoUnit.HOURS), 30, null, null);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("APPOINTMENT_OVERLAP");
    }

    @Test
    void update_rejects_terminal_status() {
        Appointment existing = sampleAppointment(5L, AppointmentStatus.COMPLETED);
        when(repository.findById(5L)).thenReturn(Optional.of(existing));

        UpdateAppointmentRequest request = new UpdateAppointmentRequest(null, null, "new reason", null);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("APPOINTMENT_TERMINAL_STATUS");
    }

    @Test
    void confirm_works_only_from_planned() {
        Appointment a = sampleAppointment(1L, AppointmentStatus.PLANNED);
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirm(1L);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void confirm_rejects_invalid_state() {
        Appointment a = sampleAppointment(1L, AppointmentStatus.CONFIRMED);
        when(repository.findById(1L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.confirm(1L))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void complete_works_only_from_confirmed() {
        Appointment a = sampleAppointment(1L, AppointmentStatus.CONFIRMED);
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.complete(1L);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void cancel_works_from_planned() {
        Appointment a = sampleAppointment(1L, AppointmentStatus.PLANNED);
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancel(1L);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void cancel_rejects_terminal() {
        Appointment a = sampleAppointment(1L, AppointmentStatus.COMPLETED);
        when(repository.findById(1L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.cancel(1L))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("APPOINTMENT_TERMINAL_STATUS");
    }

    @Test
    void mark_no_show_works_only_from_confirmed() {
        Appointment a = sampleAppointment(1L, AppointmentStatus.CONFIRMED);
        when(repository.findById(1L)).thenReturn(Optional.of(a));
        when(repository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markNoShow(1L);

        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    void delete_throws_when_unknown() {
        when(repository.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Appointment sampleAppointment(Long id, AppointmentStatus status) {
        return Appointment.builder()
                .id(id)
                .patientId(1L)
                .doctorId(1L)
                .patientNameSnapshot("Diop Fatou")
                .doctorNameSnapshot("Martin Jean")
                .startTime(FUTURE)
                .endTime(FUTURE.plus(30, ChronoUnit.MINUTES))
                .durationMinutes(30)
                .status(status)
                .version(0L)
                .build();
    }
}
