package com.terangamed.patient.service;

import com.terangamed.common.kafka.TerangaMedTopics;
import com.terangamed.common.outbox.OutboxEventPublisher;
import com.terangamed.patient.dto.CreatePatientRequest;
import com.terangamed.patient.dto.UpdatePatientRequest;
import com.terangamed.patient.entity.BloodGroup;
import com.terangamed.patient.entity.Civility;
import com.terangamed.patient.entity.Gender;
import com.terangamed.patient.entity.Patient;
import com.terangamed.patient.entity.PatientStatus;
import com.terangamed.patient.event.PatientArchived;
import com.terangamed.patient.event.PatientCreated;
import com.terangamed.patient.event.PatientUpdated;
import com.terangamed.patient.mapper.PatientMapper;
import com.terangamed.patient.repository.PatientRepository;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests dédiés à l'émission des événements Kafka via Outbox.
 *
 * <p>On vérifie que :
 * <ul>
 *   <li>Les 3 opérations métier (create, update, archive) appellent bien
 *       {@link OutboxEventPublisher#publish}</li>
 *   <li>Le bon topic est ciblé ({@link TerangaMedTopics#PATIENT_EVENTS})</li>
 *   <li>Le bon eventType est utilisé pour chaque opération</li>
 *   <li>Le payload Avro contient les bonnes données de l'aggregate</li>
 *   <li>L'archivage idempotent N'émet PAS de doublon</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PatientServiceOutboxTest {

    @Mock PatientRepository repository;
    @Mock OutboxEventPublisher outboxPublisher;

    PatientMapper mapper;
    PatientService service;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(PatientMapper.class);
        service = new PatientService(repository, mapper, outboxPublisher);
    }

    @Test
    void create_publishes_patient_created_event() {
        when(repository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(0));
        when(repository.existsByMedicalRecordNumber(anyString())).thenReturn(false);
        when(repository.save(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(42L);
            return p;
        });

        service.create(new CreatePatientRequest(
                Civility.M, "Diop", "Aliou", LocalDate.of(1990, 1, 1),
                Gender.MALE, "+221770000000", "aliou@example.sn",
                null, null, null, null, null,
                BloodGroup.O_POS, null, null, null));

        ArgumentCaptor<SpecificRecord> payloadCaptor = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.PATIENT_EVENTS),
                eq("42"),
                eq("Patient"), eq("42"),
                eq("patient.created"),
                payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).isInstanceOf(PatientCreated.class);
        PatientCreated event = (PatientCreated) payloadCaptor.getValue();
        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getLastName()).isEqualTo("Diop");
        assertThat(event.getFirstName()).isEqualTo("Aliou");
        assertThat(event.getEmail()).isEqualTo("aliou@example.sn");
        assertThat(event.getMedicalRecordNumber()).matches("MR-\\d{4}-00001");
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void update_publishes_patient_updated_event_with_new_state() {
        Patient existing = patientBuilder().id(5L).medicalRecordNumber("MR-2026-00005")
                .lastName("Old").firstName("Name").status(PatientStatus.ACTIVE).build();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(5L, new UpdatePatientRequest(
                null,           // civility
                "NewLast",      // lastName
                "NewFirst",     // firstName
                null,           // birthDate
                null,           // gender
                null,           // phone
                null,           // email
                null,           // addressLine1
                null,           // addressLine2
                null,           // postalCode
                null,           // city
                null,           // country
                null,           // bloodGroup
                null,           // allergies
                null,           // emergencyContactName
                null,           // emergencyContactPhone
                null            // status
        ));

        ArgumentCaptor<SpecificRecord> payloadCaptor = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.PATIENT_EVENTS),
                eq("5"),
                eq("Patient"), eq("5"),
                eq("patient.updated"),
                payloadCaptor.capture());

        PatientUpdated event = (PatientUpdated) payloadCaptor.getValue();
        assertThat(event.getPatientId()).isEqualTo(5L);
        assertThat(event.getLastName()).isEqualTo("NewLast");
        assertThat(event.getFirstName()).isEqualTo("NewFirst");
        assertThat(event.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void archive_publishes_patient_archived_event() {
        Patient existing = patientBuilder().id(7L).medicalRecordNumber("MR-2026-00007")
                .status(PatientStatus.ACTIVE).build();
        when(repository.findById(7L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        service.archive(7L);

        ArgumentCaptor<SpecificRecord> payloadCaptor = ArgumentCaptor.forClass(SpecificRecord.class);
        verify(outboxPublisher).publish(
                eq(TerangaMedTopics.PATIENT_EVENTS),
                eq("7"),
                eq("Patient"), eq("7"),
                eq("patient.archived"),
                payloadCaptor.capture());

        PatientArchived event = (PatientArchived) payloadCaptor.getValue();
        assertThat(event.getPatientId()).isEqualTo(7L);
        assertThat(event.getMedicalRecordNumber()).isEqualTo("MR-2026-00007");
    }

    @Test
    void archive_idempotent_does_not_publish_duplicate_event() {
        Patient already = patientBuilder().id(7L).status(PatientStatus.ARCHIVED).build();
        when(repository.findById(7L)).thenReturn(Optional.of(already));

        service.archive(7L);

        verify(outboxPublisher, never()).publish(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                any(SpecificRecord.class));
    }

    private static Patient.PatientBuilder patientBuilder() {
        return Patient.builder()
                .civility(Civility.M)
                .lastName("Default")
                .firstName("Default")
                .gender(Gender.MALE)
                .birthDate(LocalDate.of(1990, 1, 1))
                .status(PatientStatus.ACTIVE)
                .version(0L);
    }
}
