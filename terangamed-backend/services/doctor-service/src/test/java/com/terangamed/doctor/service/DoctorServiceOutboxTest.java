package com.terangamed.doctor.service;

import com.terangamed.common.finance.Currency;
import com.terangamed.common.finance.FinanceProperties;
import com.terangamed.common.kafka.TerangaMedTopics;
import com.terangamed.common.outbox.OutboxEventPublisher;
import com.terangamed.doctor.dto.CreateDoctorRequest;
import com.terangamed.doctor.dto.UpdateDoctorRequest;
import com.terangamed.doctor.entity.Doctor;
import com.terangamed.doctor.entity.DoctorStatus;
import com.terangamed.doctor.entity.Specialty;
import com.terangamed.doctor.event.DoctorCreated;
import com.terangamed.doctor.event.DoctorStatusChanged;
import com.terangamed.doctor.event.DoctorUpdated;
import com.terangamed.doctor.mapper.DoctorMapper;
import com.terangamed.doctor.repository.DoctorRepository;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vérifie l'émission des 3 événements doctor-service via l'Outbox :
 *  - doctor.created (sur create)
 *  - doctor.updated (sur update)
 *  - doctor.status-changed (sur putOnLeave / retire / reactivate)
 */
@ExtendWith(MockitoExtension.class)
class DoctorServiceOutboxTest {

    @Mock DoctorRepository repository;
    @Mock FinanceProperties finance;
    @Mock OutboxEventPublisher outboxPublisher;

    DoctorMapper mapper;
    DoctorService service;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(DoctorMapper.class);
        service = new DoctorService(repository, mapper, finance, outboxPublisher);
    }

    private static Doctor.DoctorBuilder doctorBuilder() {
        return Doctor.builder()
                .lastName("Sall").firstName("Cheikh")
                .specialty(Specialty.GENERAL_MEDICINE)
                .yearsOfExperience(10)
                .consultationFee(new BigDecimal("15000"))
                .consultationFeeCurrency(Currency.XOF)
                .status(DoctorStatus.ACTIVE)
                .version(0L);
    }

    @Test
    void create_publishes_doctor_created_event() {
        when(repository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(0));
        when(repository.existsByLicenseNumber(anyString())).thenReturn(false);
        when(finance.getDefaultCurrency()).thenReturn(Currency.XOF);
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> {
            Doctor d = inv.getArgument(0);
            d.setId(101L);
            return d;
        });

        service.create(new CreateDoctorRequest(
                "Sall", "Cheikh", Specialty.GENERAL_MEDICINE,
                "c.sall@terangamed.local", "+221770100001", null,
                10, new BigDecimal("15000"), null, "bio", null));

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.DOCTOR_EVENTS), eq("101"),
                eq("Doctor"), eq("101"),
                eq("doctor.created"), payload.capture());

        DoctorCreated event = (DoctorCreated) payload.getValue();
        assertThat(event.getDoctorId()).isEqualTo(101L);
        assertThat(event.getLicenseNumber()).matches("MED-\\d{4}-00001");
        assertThat(event.getLastName()).isEqualTo("Sall");
        assertThat(event.getSpecialty()).isEqualTo("GENERAL_MEDICINE");
    }

    @Test
    void update_publishes_doctor_updated_event() {
        Doctor existing = doctorBuilder().id(101L).licenseNumber("MED-2026-00001").build();
        when(repository.findById(101L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(101L, new UpdateDoctorRequest(
                "NewName", null, null, null, null, null, 15, null, null, null, null, null));

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.DOCTOR_EVENTS), eq("101"),
                eq("Doctor"), eq("101"),
                eq("doctor.updated"), payload.capture());

        DoctorUpdated event = (DoctorUpdated) payload.getValue();
        assertThat(event.getDoctorId()).isEqualTo(101L);
        assertThat(event.getLastName()).isEqualTo("NewName");
        assertThat(event.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void put_on_leave_publishes_status_changed_event() {
        Doctor d = doctorBuilder().id(101L).licenseNumber("MED-2026-00001")
                .status(DoctorStatus.ACTIVE).build();
        when(repository.findById(101L)).thenReturn(Optional.of(d));
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.putOnLeave(101L);

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.DOCTOR_EVENTS), eq("101"),
                eq("Doctor"), eq("101"),
                eq("doctor.status-changed"), payload.capture());

        DoctorStatusChanged event = (DoctorStatusChanged) payload.getValue();
        assertThat(event.getPreviousStatus()).isEqualTo("ACTIVE");
        assertThat(event.getNewStatus()).isEqualTo("ON_LEAVE");
    }

    @Test
    void retire_publishes_status_changed_event() {
        Doctor d = doctorBuilder().id(101L).licenseNumber("MED-2026-00001")
                .status(DoctorStatus.ACTIVE).build();
        when(repository.findById(101L)).thenReturn(Optional.of(d));
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.retire(101L);

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.DOCTOR_EVENTS), eq("101"),
                eq("Doctor"), eq("101"),
                eq("doctor.status-changed"), payload.capture());

        DoctorStatusChanged event = (DoctorStatusChanged) payload.getValue();
        assertThat(event.getPreviousStatus()).isEqualTo("ACTIVE");
        assertThat(event.getNewStatus()).isEqualTo("RETIRED");
    }

    @Test
    void reactivate_publishes_status_changed_event() {
        Doctor d = doctorBuilder().id(101L).licenseNumber("MED-2026-00001")
                .status(DoctorStatus.ON_LEAVE).build();
        when(repository.findById(101L)).thenReturn(Optional.of(d));
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reactivate(101L);

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.DOCTOR_EVENTS), eq("101"),
                eq("Doctor"), eq("101"),
                eq("doctor.status-changed"), payload.capture());

        DoctorStatusChanged event = (DoctorStatusChanged) payload.getValue();
        assertThat(event.getPreviousStatus()).isEqualTo("ON_LEAVE");
        assertThat(event.getNewStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void put_on_leave_idempotent_does_not_publish_event() {
        Doctor alreadyOnLeave = doctorBuilder().id(101L).status(DoctorStatus.ON_LEAVE).build();
        when(repository.findById(101L)).thenReturn(Optional.of(alreadyOnLeave));

        service.putOnLeave(101L);

        verify(outboxPublisher, never()).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                any(SpecificRecord.class));
    }

    @Test
    void retire_idempotent_does_not_publish_event() {
        Doctor alreadyRetired = doctorBuilder().id(101L).status(DoctorStatus.RETIRED).build();
        when(repository.findById(101L)).thenReturn(Optional.of(alreadyRetired));

        service.retire(101L);

        verify(outboxPublisher, never()).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                any(SpecificRecord.class));
    }
}
