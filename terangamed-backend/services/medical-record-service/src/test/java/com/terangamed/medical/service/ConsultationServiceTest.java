package com.terangamed.medical.service;

import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ForbiddenException;
import com.terangamed.medical.dto.AppointmentSnapshotDto;
import com.terangamed.medical.dto.ConsultationDto;
import com.terangamed.medical.dto.CreateConsultationRequest;
import com.terangamed.medical.dto.DoctorSnapshotDto;
import com.terangamed.medical.dto.UpdateConsultationRequest;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.mapper.ConsultationMapper;
import com.terangamed.medical.mapper.VitalSignsMapper;
import com.terangamed.medical.mapper.VitalSignsMapperImpl;
import com.terangamed.medical.repository.ConsultationRepository;
import com.terangamed.medical.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationServiceTest {

    @Mock ConsultationRepository repository;
    @Mock MedicalRecordService medicalRecordService;
    @Mock RemoteLookupService remoteLookup;
    @Mock CurrentUserProvider currentUser;
    @Mock com.terangamed.common.outbox.OutboxEventPublisher outboxPublisher;

    ConsultationMapper mapper;
    ConsultationService service;

    @BeforeEach
    void setUp() {
        // Mappers Spring sont compilés à part — on les construit manuellement.
        // VitalSignsMapper est généré (suffix Impl).
        VitalSignsMapper vsm = new VitalSignsMapperImpl();
        mapper = new com.terangamed.medical.mapper.ConsultationMapperImpl();
        ReflectionTestUtils.setField(mapper, "vitalSignsMapper", vsm);

        service = new ConsultationService(repository, mapper, medicalRecordService,
                remoteLookup, currentUser, outboxPublisher);
    }

    private static CreateConsultationRequest sampleCreate(Long mrId, Long apptId) {
        return new CreateConsultationRequest(
                mrId, apptId, LocalDateTime.now(), "Toux",
                null, null, null, null, null, null);
    }

    @Test
    void create_succeeds_without_appointment() {
        when(medicalRecordService.findEntityById(1L))
                .thenReturn(MedicalRecord.builder().id(1L).patientId(42L).build());
        when(remoteLookup.fetchDoctor(101L)).thenReturn(
                new DoctorSnapshotDto(101L, "MED-X", "Sall", "Cheikh", "GENERAL_MEDICINE", "ACTIVE"));
        when(repository.save(any(Consultation.class))).thenAnswer(inv -> {
            Consultation c = inv.getArgument(0);
            c.setId(500L);
            return c;
        });

        ConsultationDto dto = service.create(sampleCreate(1L, null), 101L);

        assertThat(dto.id()).isEqualTo(500L);
        assertThat(dto.signed()).isFalse();
        assertThat(dto.softDeleted()).isFalse();
        assertThat(dto.doctorId()).isEqualTo(101L);
    }

    @Test
    void create_rejects_inactive_doctor() {
        when(medicalRecordService.findEntityById(1L))
                .thenReturn(MedicalRecord.builder().id(1L).patientId(42L).build());
        when(remoteLookup.fetchDoctor(101L)).thenReturn(
                new DoctorSnapshotDto(101L, "MED-X", "Sall", "Cheikh", "GENERAL_MEDICINE", "RETIRED"));

        assertThatThrownBy(() -> service.create(sampleCreate(1L, null), 101L))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("DOCTOR_NOT_ACTIVE");
    }

    @Test
    void create_with_appointment_validates_patient_match() {
        when(medicalRecordService.findEntityById(1L))
                .thenReturn(MedicalRecord.builder().id(1L).patientId(42L).build());
        when(remoteLookup.fetchDoctor(101L)).thenReturn(
                new DoctorSnapshotDto(101L, "X", "S", "C", "X", "ACTIVE"));
        when(remoteLookup.fetchAppointment(999L)).thenReturn(
                new AppointmentSnapshotDto(999L, 99L /* mauvais patient */, 101L,
                        LocalDateTime.now(), LocalDateTime.now().plusMinutes(30), "PLANNED"));

        assertThatThrownBy(() -> service.create(sampleCreate(1L, 999L), 101L))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("APPOINTMENT_PATIENT_MISMATCH");
    }

    @Test
    void create_with_appointment_validates_doctor_match() {
        when(medicalRecordService.findEntityById(1L))
                .thenReturn(MedicalRecord.builder().id(1L).patientId(42L).build());
        when(remoteLookup.fetchDoctor(101L)).thenReturn(
                new DoctorSnapshotDto(101L, "X", "S", "C", "X", "ACTIVE"));
        when(remoteLookup.fetchAppointment(999L)).thenReturn(
                new AppointmentSnapshotDto(999L, 42L, 222L /* mauvais médecin */,
                        LocalDateTime.now(), LocalDateTime.now().plusMinutes(30), "PLANNED"));

        assertThatThrownBy(() -> service.create(sampleCreate(1L, 999L), 101L))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("APPOINTMENT_DOCTOR_MISMATCH");
    }

    @Test
    void create_rejects_appointment_already_consumed() {
        when(medicalRecordService.findEntityById(1L))
                .thenReturn(MedicalRecord.builder().id(1L).patientId(42L).build());
        when(remoteLookup.fetchDoctor(101L)).thenReturn(
                new DoctorSnapshotDto(101L, "X", "S", "C", "X", "ACTIVE"));
        when(remoteLookup.fetchAppointment(999L)).thenReturn(
                new AppointmentSnapshotDto(999L, 42L, 101L,
                        LocalDateTime.now(), LocalDateTime.now().plusMinutes(30), "COMPLETED"));
        when(repository.existsByAppointmentId(999L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(sampleCreate(1L, 999L), 101L))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("CONSULTATION_FOR_APPOINTMENT_EXISTS");
    }

    @Test
    void update_blocked_when_signed() {
        Consultation signed = Consultation.builder()
                .id(5L).medicalRecordId(1L).doctorId(101L)
                .signed(true).softDeleted(false)
.build();
        signed.setCreatedBy("dr.a");
        when(repository.findById(5L)).thenReturn(Optional.of(signed));

        assertThatThrownBy(() -> service.update(5L, new UpdateConsultationRequest(
                null, "edit", null, null, null, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("CONSULTATION_SIGNED");
    }

    @Test
    void update_blocked_when_user_not_author() {
        Consultation c = Consultation.builder()
                .id(5L).medicalRecordId(1L).doctorId(101L)
                .signed(false).softDeleted(false)
.build();
        c.setCreatedBy("dr.a");
        when(repository.findById(5L)).thenReturn(Optional.of(c));
        when(currentUser.username()).thenReturn("dr.b");

        assertThatThrownBy(() -> service.update(5L, new UpdateConsultationRequest(
                null, "edit", null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_succeeds_when_author_and_not_signed() {
        Consultation c = Consultation.builder()
                .id(5L).medicalRecordId(1L).doctorId(101L)
                .motif("old").signed(false).softDeleted(false)
.build();
        c.setCreatedBy("dr.a");
        when(repository.findById(5L)).thenReturn(Optional.of(c));
        when(currentUser.username()).thenReturn("dr.a");
        when(repository.save(any(Consultation.class))).thenAnswer(inv -> inv.getArgument(0));

        ConsultationDto dto = service.update(5L, new UpdateConsultationRequest(
                null, "new motif", null, null, "Bronchite", null, null, null));

        assertThat(dto.motif()).isEqualTo("new motif");
        assertThat(dto.diagnostic()).isEqualTo("Bronchite");
    }

    @Test
    void sign_marks_signed_and_records_signer() {
        Consultation c = Consultation.builder()
                .id(5L).medicalRecordId(1L).doctorId(101L).signed(false).softDeleted(false)
                .build();
        c.setCreatedBy("dr.a");
        when(repository.findById(5L)).thenReturn(Optional.of(c));
        when(medicalRecordService.findEntityById(1L))
                .thenReturn(MedicalRecord.builder().id(1L).patientId(42L).build());
        when(currentUser.username()).thenReturn("dr.a");
        when(repository.save(any(Consultation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.sign(5L);

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(repository).save(captor.capture());
        Consultation saved = captor.getValue();
        assertThat(saved.getSigned()).isTrue();
        assertThat(saved.getSignedAt()).isNotNull();
        assertThat(saved.getSignedBy()).isEqualTo("dr.a");
    }

    @Test
    void sign_is_idempotent() {
        Consultation alreadySigned = Consultation.builder()
                .id(5L).signed(true).softDeleted(false).build();
        alreadySigned.setCreatedBy("dr.a");
        when(repository.findById(5L)).thenReturn(Optional.of(alreadySigned));
        when(currentUser.username()).thenReturn("dr.a");

        service.sign(5L);
        verify(repository, never()).save(any());
    }

    @Test
    void sign_blocked_for_non_author() {
        Consultation c = Consultation.builder()
                .id(5L).signed(false).softDeleted(false).build();
        c.setCreatedBy("dr.a");
        when(repository.findById(5L)).thenReturn(Optional.of(c));
        when(currentUser.username()).thenReturn("dr.evil");

        assertThatThrownBy(() -> service.sign(5L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void soft_delete_marks_entity() {
        Consultation c = Consultation.builder()
                .id(5L).signed(true).softDeleted(false).build();
        c.setCreatedBy("dr.a");
        when(repository.findById(5L)).thenReturn(Optional.of(c));
        when(currentUser.username()).thenReturn("admin");
        when(repository.save(any(Consultation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(5L);

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSoftDeleted()).isTrue();
        assertThat(captor.getValue().getDeletedBy()).isEqualTo("admin");
    }
}
