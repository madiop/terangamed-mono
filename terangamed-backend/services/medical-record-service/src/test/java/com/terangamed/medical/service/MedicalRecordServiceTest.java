package com.terangamed.medical.service;

import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.medical.dto.CreateMedicalRecordRequest;
import com.terangamed.medical.dto.MedicalRecordDto;
import com.terangamed.medical.dto.PatientSnapshotDto;
import com.terangamed.medical.dto.UpdateMedicalRecordRequest;
import com.terangamed.medical.entity.BloodType;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.mapper.MedicalRecordMapper;
import com.terangamed.medical.repository.MedicalRecordRepository;
import com.terangamed.medical.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceTest {

    @Mock MedicalRecordRepository repository;
    @Mock RemoteLookupService remoteLookup;
    @Mock CurrentUserProvider currentUser;

    MedicalRecordMapper mapper;
    MedicalRecordService service;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(MedicalRecordMapper.class);
        service = new MedicalRecordService(repository, mapper, remoteLookup, currentUser);
    }

    @Test
    void create_validates_patient_exists_and_persists() {
        when(remoteLookup.fetchPatient(42L)).thenReturn(
                new PatientSnapshotDto(42L, "MRN-1", "Diop", "Awa", null, "ACTIVE", null));
        when(repository.existsByPatientId(42L)).thenReturn(false);
        when(repository.save(any(MedicalRecord.class))).thenAnswer(inv -> {
            MedicalRecord mr = inv.getArgument(0);
            mr.setId(100L);
            return mr;
        });

        MedicalRecordDto dto = service.create(new CreateMedicalRecordRequest(
                42L, BloodType.O_POS, "Pénicilline", "Notes"));

        assertThat(dto.id()).isEqualTo(100L);
        assertThat(dto.patientId()).isEqualTo(42L);
        verify(remoteLookup).fetchPatient(42L);
    }

    @Test
    void create_throws_when_record_already_exists_for_patient() {
        when(remoteLookup.fetchPatient(42L)).thenReturn(
                new PatientSnapshotDto(42L, "MRN-1", "X", "Y", null, "ACTIVE", null));
        when(repository.existsByPatientId(42L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateMedicalRecordRequest(
                42L, null, null, null)))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("MEDICAL_RECORD_DUPLICATE");

        verify(repository, never()).save(any());
    }

    @Test
    void create_propagates_remote_lookup_failure() {
        when(remoteLookup.fetchPatient(99L))
                .thenThrow(new ResourceNotFoundException("Patient", 99L));

        assertThatThrownBy(() -> service.create(new CreateMedicalRecordRequest(
                99L, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_throws_when_soft_deleted() {
        MedicalRecord deleted = MedicalRecord.builder().id(1L).patientId(42L)
                .softDeleted(true).build();
        when(repository.findById(1L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.findById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByPatientId_skips_soft_deleted() {
        MedicalRecord deleted = MedicalRecord.builder().patientId(42L).softDeleted(true).build();
        when(repository.findByPatientId(42L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.findByPatientId(42L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_applies_partial_changes() {
        MedicalRecord existing = MedicalRecord.builder()
                .id(5L).patientId(7L).bloodType(BloodType.A_POS)
                .notes("old").softDeleted(false).build();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(MedicalRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        MedicalRecordDto dto = service.update(5L, new UpdateMedicalRecordRequest(
                BloodType.B_NEG, null, "new notes"));

        assertThat(dto.bloodType()).isEqualTo(BloodType.B_NEG);
        assertThat(dto.notes()).isEqualTo("new notes");
    }

    @Test
    void soft_delete_marks_entity_and_persists() {
        MedicalRecord existing = MedicalRecord.builder()
                .id(5L).patientId(7L).softDeleted(false).build();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(currentUser.username()).thenReturn("admin");
        when(repository.save(any(MedicalRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(5L);

        ArgumentCaptor<MedicalRecord> captor = ArgumentCaptor.forClass(MedicalRecord.class);
        verify(repository).save(captor.capture());
        MedicalRecord saved = captor.getValue();
        assertThat(saved.getSoftDeleted()).isTrue();
        assertThat(saved.getDeletedAt()).isNotNull();
        assertThat(saved.getDeletedBy()).isEqualTo("admin");
    }

    @Test
    void soft_delete_is_idempotent() {
        MedicalRecord deleted = MedicalRecord.builder().id(5L).softDeleted(true).build();
        when(repository.findById(5L)).thenReturn(Optional.of(deleted));

        service.softDelete(5L);
        verify(repository, never()).save(any());
    }

    @Test
    void soft_delete_throws_when_unknown() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDelete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
