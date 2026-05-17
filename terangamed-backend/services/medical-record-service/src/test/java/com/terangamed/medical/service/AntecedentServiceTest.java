package com.terangamed.medical.service;

import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.medical.dto.AntecedentDto;
import com.terangamed.medical.dto.CreateAntecedentRequest;
import com.terangamed.medical.dto.UpdateAntecedentRequest;
import com.terangamed.medical.entity.Antecedent;
import com.terangamed.medical.entity.AntecedentType;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.mapper.AntecedentMapper;
import com.terangamed.medical.repository.AntecedentRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AntecedentServiceTest {

    @Mock AntecedentRepository repository;
    @Mock MedicalRecordService medicalRecordService;

    AntecedentMapper mapper;
    AntecedentService service;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(AntecedentMapper.class);
        service = new AntecedentService(repository, mapper, medicalRecordService);
    }

    @Test
    void create_defaults_active_to_true() {
        when(medicalRecordService.findEntityById(1L))
                .thenReturn(MedicalRecord.builder().id(1L).build());
        when(repository.save(any(Antecedent.class))).thenAnswer(inv -> {
            Antecedent a = inv.getArgument(0);
            a.setId(7L);
            return a;
        });

        AntecedentDto dto = service.create(new CreateAntecedentRequest(
                1L, AntecedentType.ALLERGY, "Pénicilline", null, null, null));

        ArgumentCaptor<Antecedent> captor = ArgumentCaptor.forClass(Antecedent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getActive()).isTrue();
        assertThat(dto.id()).isEqualTo(7L);
    }

    @Test
    void create_validates_medical_record_exists() {
        when(medicalRecordService.findEntityById(99L))
                .thenThrow(new ResourceNotFoundException("MedicalRecord", 99L));

        assertThatThrownBy(() -> service.create(new CreateAntecedentRequest(
                99L, AntecedentType.SURGERY, "X", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_applies_partial() {
        Antecedent existing = Antecedent.builder()
                .id(7L).medicalRecordId(1L).type(AntecedentType.ALLERGY)
                .title("Old").active(true).build();
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Antecedent.class))).thenAnswer(inv -> inv.getArgument(0));

        AntecedentDto dto = service.update(7L, new UpdateAntecedentRequest(
                null, "New title", null, null, false));

        assertThat(dto.title()).isEqualTo("New title");
        assertThat(dto.active()).isFalse();
        assertThat(dto.type()).isEqualTo(AntecedentType.ALLERGY); // inchangé
    }

    @Test
    void delete_removes_entity() {
        Antecedent existing = Antecedent.builder().id(7L).medicalRecordId(1L).build();
        when(repository.findById(7L)).thenReturn(Optional.of(existing));

        service.delete(7L);
        verify(repository).delete(existing);
    }

    @Test
    void delete_throws_when_unknown() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
