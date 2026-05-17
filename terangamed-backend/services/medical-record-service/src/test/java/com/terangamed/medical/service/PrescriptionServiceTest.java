package com.terangamed.medical.service;

import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ForbiddenException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.medical.dto.CreatePrescriptionLineRequest;
import com.terangamed.medical.dto.CreatePrescriptionRequest;
import com.terangamed.medical.dto.PrescriptionDto;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicationRoute;
import com.terangamed.medical.entity.Prescription;
import com.terangamed.medical.entity.PrescriptionLine;
import com.terangamed.medical.mapper.PrescriptionMapper;
import com.terangamed.medical.mapper.PrescriptionMapperImpl;
import com.terangamed.medical.repository.PrescriptionLineRepository;
import com.terangamed.medical.repository.PrescriptionRepository;
import com.terangamed.medical.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

    @Mock PrescriptionRepository repository;
    @Mock PrescriptionLineRepository lineRepository;
    @Mock ConsultationService consultationService;
    @Mock MedicalRecordService medicalRecordService;
    @Mock CurrentUserProvider currentUser;
    @Mock com.terangamed.common.outbox.OutboxEventPublisher outboxPublisher;

    PrescriptionMapper mapper;
    PrescriptionService service;

    @BeforeEach
    void setUp() {
        mapper = new PrescriptionMapperImpl();
        service = new PrescriptionService(repository, lineRepository, mapper,
                consultationService, medicalRecordService, currentUser, outboxPublisher);
    }

    private Consultation activeConsultation(Long id, String createdBy) {
        Consultation c = Consultation.builder()
                .id(id).medicalRecordId(1L).doctorId(101L)
                .signed(false).softDeleted(false)
                .build();
        c.setCreatedBy(createdBy);
        return c;
    }

    @Test
    void create_generates_number_and_persists_lines() {
        Consultation c = activeConsultation(10L, "dr.a");
        when(consultationService.findEntityById(10L)).thenReturn(c);
        when(medicalRecordService.findEntityById(1L))
                .thenReturn(com.terangamed.medical.entity.MedicalRecord.builder()
                        .id(1L).patientId(42L).build());
        when(currentUser.username()).thenReturn("dr.a");
        when(repository.existsByConsultationId(10L)).thenReturn(false);
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(7));
        when(repository.existsByPrescriptionNumber(anyString())).thenReturn(false);
        when(repository.save(any(Prescription.class))).thenAnswer(inv -> {
            Prescription p = inv.getArgument(0);
            p.setId(50L);
            return p;
        });
        when(lineRepository.save(any(PrescriptionLine.class))).thenAnswer(inv -> {
            PrescriptionLine pl = inv.getArgument(0);
            pl.setId(System.nanoTime());
            return pl;
        });

        PrescriptionDto dto = service.create(10L, new CreatePrescriptionRequest(
                null, "Avant les repas", List.of(
                        new CreatePrescriptionLineRequest("Amoxicilline 500mg",
                                "1 gélule", "3x/j", "7 jours", MedicationRoute.ORAL, null, 1),
                        new CreatePrescriptionLineRequest("Paracétamol 1g",
                                "1 cp", "si fièvre", "5 jours", MedicationRoute.ORAL, null, 1)
                )));

        assertThat(dto.prescriptionNumber()).matches("ORD-\\d{4}-00008");
        assertThat(dto.lines()).hasSize(2);
        verify(lineRepository, org.mockito.Mockito.times(2)).save(any(PrescriptionLine.class));
    }

    @Test
    void create_blocked_when_consultation_signed() {
        Consultation signed = Consultation.builder()
                .id(10L).signed(true).softDeleted(false).build();
        signed.setCreatedBy("dr.a");
        when(consultationService.findEntityById(10L)).thenReturn(signed);
        when(currentUser.username()).thenReturn("dr.a");

        assertThatThrownBy(() -> service.create(10L, new CreatePrescriptionRequest(
                null, null, List.of(new CreatePrescriptionLineRequest(
                        "X", null, null, null, null, null, null)))))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("CONSULTATION_SIGNED");
    }

    @Test
    void create_blocked_when_not_author() {
        Consultation c = activeConsultation(10L, "dr.a");
        when(consultationService.findEntityById(10L)).thenReturn(c);
        when(currentUser.username()).thenReturn("dr.evil");

        assertThatThrownBy(() -> service.create(10L, new CreatePrescriptionRequest(
                null, null, List.of(new CreatePrescriptionLineRequest(
                        "X", null, null, null, null, null, null)))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_blocked_when_already_exists() {
        Consultation c = activeConsultation(10L, "dr.a");
        when(consultationService.findEntityById(10L)).thenReturn(c);
        when(currentUser.username()).thenReturn("dr.a");
        when(repository.existsByConsultationId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(10L, new CreatePrescriptionRequest(
                null, null, List.of(new CreatePrescriptionLineRequest(
                        "X", null, null, null, null, null, null)))))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("PRESCRIPTION_EXISTS");
    }

    @Test
    void generate_number_starts_at_one_for_new_year() {
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.empty());
        when(repository.existsByPrescriptionNumber(anyString())).thenReturn(false);

        assertThat(service.generatePrescriptionNumber()).matches("ORD-\\d{4}-00001");
    }

    @Test
    void generate_number_throws_on_race_condition() {
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(0));
        when(repository.existsByPrescriptionNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.generatePrescriptionNumber())
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("PRESCRIPTION_NUMBER_CONFLICT");
    }

    @Test
    void delete_removes_when_author_and_consultation_active() {
        Prescription p = Prescription.builder().id(50L).consultationId(10L)
                .prescriptionNumber("ORD-2026-00008").build();
        when(repository.findById(50L)).thenReturn(Optional.of(p));
        when(consultationService.findEntityById(10L)).thenReturn(activeConsultation(10L, "dr.a"));
        when(currentUser.username()).thenReturn("dr.a");

        service.delete(50L);
        verify(repository).delete(p);
    }

    @Test
    void delete_blocked_when_consultation_signed() {
        Prescription p = Prescription.builder().id(50L).consultationId(10L)
                .prescriptionNumber("ORD-2026-00008").build();
        when(repository.findById(50L)).thenReturn(Optional.of(p));
        Consultation signed = Consultation.builder()
                .id(10L).signed(true).softDeleted(false).build();
        signed.setCreatedBy("dr.a");
        when(consultationService.findEntityById(10L)).thenReturn(signed);

        assertThatThrownBy(() -> service.delete(50L))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).delete(any());
    }

    @Test
    void add_line_succeeds_when_author_active() {
        Prescription p = Prescription.builder().id(50L).consultationId(10L).build();
        when(repository.findById(50L)).thenReturn(Optional.of(p));
        when(consultationService.findEntityById(10L)).thenReturn(activeConsultation(10L, "dr.a"));
        when(currentUser.username()).thenReturn("dr.a");
        when(lineRepository.save(any(PrescriptionLine.class))).thenAnswer(inv -> {
            PrescriptionLine pl = inv.getArgument(0);
            pl.setId(99L);
            return pl;
        });

        var dto = service.addLine(50L, new CreatePrescriptionLineRequest(
                "Ibuprofène 200mg", "1 cp", "3x/j", "5 jours",
                MedicationRoute.ORAL, null, 1));

        assertThat(dto.id()).isEqualTo(99L);
        assertThat(dto.medicationName()).isEqualTo("Ibuprofène 200mg");
    }

    @Test
    void update_line_throws_when_line_belongs_to_other_prescription() {
        Prescription p = Prescription.builder().id(50L).consultationId(10L).build();
        when(repository.findById(50L)).thenReturn(Optional.of(p));
        when(consultationService.findEntityById(10L)).thenReturn(activeConsultation(10L, "dr.a"));
        when(currentUser.username()).thenReturn("dr.a");
        PrescriptionLine other = PrescriptionLine.builder()
                .id(99L).prescriptionId(999L).medicationName("X").build();
        when(lineRepository.findById(99L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateLine(50L, 99L,
                new com.terangamed.medical.dto.UpdatePrescriptionLineRequest(
                        "Y", null, null, null, null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
