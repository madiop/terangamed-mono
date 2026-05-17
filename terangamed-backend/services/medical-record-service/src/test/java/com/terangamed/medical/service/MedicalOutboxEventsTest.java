package com.terangamed.medical.service;

import com.terangamed.common.kafka.TerangaMedTopics;
import com.terangamed.common.outbox.OutboxEventPublisher;
import com.terangamed.medical.dto.CreateConsultationRequest;
import com.terangamed.medical.dto.CreatePrescriptionLineRequest;
import com.terangamed.medical.dto.CreatePrescriptionRequest;
import com.terangamed.medical.dto.DoctorSnapshotDto;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.entity.MedicationRoute;
import com.terangamed.medical.entity.Prescription;
import com.terangamed.medical.entity.PrescriptionLine;
import com.terangamed.medical.event.ConsultationCreated;
import com.terangamed.medical.event.ConsultationSigned;
import com.terangamed.medical.event.PrescriptionCreated;
import com.terangamed.medical.mapper.ConsultationMapper;
import com.terangamed.medical.mapper.ConsultationMapperImpl;
import com.terangamed.medical.mapper.PrescriptionMapper;
import com.terangamed.medical.mapper.PrescriptionMapperImpl;
import com.terangamed.medical.mapper.VitalSignsMapper;
import com.terangamed.medical.mapper.VitalSignsMapperImpl;
import com.terangamed.medical.repository.ConsultationRepository;
import com.terangamed.medical.repository.PrescriptionLineRepository;
import com.terangamed.medical.repository.PrescriptionRepository;
import com.terangamed.medical.security.CurrentUserProvider;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vérifie l'émission des 3 événements medical-record-service via l'Outbox :
 *  - consultation.created
 *  - consultation.signed
 *  - prescription.created
 */
@ExtendWith(MockitoExtension.class)
class MedicalOutboxEventsTest {

    @Mock ConsultationRepository consultationRepository;
    @Mock PrescriptionRepository prescriptionRepository;
    @Mock PrescriptionLineRepository lineRepository;
    @Mock MedicalRecordService medicalRecordService;
    @Mock RemoteLookupService remoteLookup;
    @Mock CurrentUserProvider currentUser;
    @Mock OutboxEventPublisher outboxPublisher;

    ConsultationService consultationService;
    PrescriptionService prescriptionService;

    @BeforeEach
    void setUp() {
        VitalSignsMapper vsm = new VitalSignsMapperImpl();
        ConsultationMapper consultationMapper = new ConsultationMapperImpl();
        ReflectionTestUtils.setField(consultationMapper, "vitalSignsMapper", vsm);

        consultationService = new ConsultationService(
                consultationRepository, consultationMapper, medicalRecordService,
                remoteLookup, currentUser, outboxPublisher);

        PrescriptionMapper prescriptionMapper = new PrescriptionMapperImpl();
        prescriptionService = new PrescriptionService(
                prescriptionRepository, lineRepository, prescriptionMapper,
                consultationService, medicalRecordService, currentUser, outboxPublisher);
    }

    @Test
    void create_consultation_publishes_consultation_created_event() {
        when(medicalRecordService.findEntityById(1L))
                .thenReturn(MedicalRecord.builder().id(1L).patientId(42L).build());
        when(remoteLookup.fetchDoctor(101L)).thenReturn(
                new DoctorSnapshotDto(101L, "MED", "Sall", "Cheikh", "GENERAL", "ACTIVE"));
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(inv -> {
            Consultation c = inv.getArgument(0);
            c.setId(500L);
            return c;
        });
        when(currentUser.username()).thenReturn("dr.sall");

        CreateConsultationRequest request = new CreateConsultationRequest(
                1L, null, LocalDateTime.now(), "Toux",
                null, null, null, null, null, null);

        consultationService.create(request, 101L);

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.MEDICAL_EVENTS), eq("500"),
                eq("Consultation"), eq("500"),
                eq("consultation.created"), payload.capture());

        ConsultationCreated event = (ConsultationCreated) payload.getValue();
        assertThat(event.getConsultationId()).isEqualTo(500L);
        assertThat(event.getMedicalRecordId()).isEqualTo(1L);
        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getDoctorId()).isEqualTo(101L);
    }

    @Test
    void sign_consultation_publishes_consultation_signed_event() {
        Consultation c = Consultation.builder()
                .id(500L).medicalRecordId(1L).doctorId(101L)
                .signed(false).softDeleted(false)
                .diagnostic("Bronchite virale").build();
        c.setCreatedBy("dr.sall");
        when(consultationRepository.findById(500L)).thenReturn(Optional.of(c));
        when(currentUser.username()).thenReturn("dr.sall");
        when(consultationRepository.save(any(Consultation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(medicalRecordService.findEntityById(1L))
                .thenReturn(MedicalRecord.builder().id(1L).patientId(42L).build());

        consultationService.sign(500L);

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.MEDICAL_EVENTS), eq("500"),
                eq("Consultation"), eq("500"),
                eq("consultation.signed"), payload.capture());

        ConsultationSigned event = (ConsultationSigned) payload.getValue();
        assertThat(event.getConsultationId()).isEqualTo(500L);
        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getDoctorId()).isEqualTo(101L);
        assertThat(event.getSignedBy()).isEqualTo("dr.sall");
        assertThat(event.getDiagnostic()).isEqualTo("Bronchite virale");
    }

    @Test
    void create_prescription_publishes_prescription_created_event() {
        Consultation parent = Consultation.builder()
                .id(500L).medicalRecordId(1L).doctorId(101L)
                .signed(false).softDeleted(false).build();
        parent.setCreatedBy("dr.sall");
        // consultationService est le VRAI service (instancié en setUp), pas un mock —
        // on doit donc mocker son repository sous-jacent.
        when(consultationRepository.findById(500L)).thenReturn(Optional.of(parent));
        when(medicalRecordService.findEntityById(1L))
                .thenReturn(MedicalRecord.builder().id(1L).patientId(42L).build());
        when(currentUser.username()).thenReturn("dr.sall");
        when(prescriptionRepository.existsByConsultationId(500L)).thenReturn(false);
        when(prescriptionRepository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(0));
        when(prescriptionRepository.existsByPrescriptionNumber(anyString())).thenReturn(false);
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> {
            Prescription p = inv.getArgument(0);
            p.setId(50L);
            return p;
        });
        when(lineRepository.save(any(PrescriptionLine.class))).thenAnswer(inv -> {
            PrescriptionLine pl = inv.getArgument(0);
            pl.setId(System.nanoTime());
            return pl;
        });

        prescriptionService.create(500L, new CreatePrescriptionRequest(
                null, "À prendre avant les repas",
                List.of(
                        new CreatePrescriptionLineRequest("Amoxicilline 500mg", "1 cp",
                                "3x/j", "7j", MedicationRoute.ORAL, null, 1),
                        new CreatePrescriptionLineRequest("Paracétamol 1g", "1 cp",
                                "si fièvre", "5j", MedicationRoute.ORAL, null, 1))));

        ArgumentCaptor<SpecificRecord> payload = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.MEDICAL_EVENTS), eq("50"),
                eq("Prescription"), eq("50"),
                eq("prescription.created"), payload.capture());

        PrescriptionCreated event = (PrescriptionCreated) payload.getValue();
        assertThat(event.getPrescriptionId()).isEqualTo(50L);
        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getDoctorId()).isEqualTo(101L);
        assertThat(event.getLineCount()).isEqualTo(2);
        assertThat(event.getPrescriptionNumber()).matches("ORD-\\d{4}-00001");
    }
}
