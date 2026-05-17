package com.terangamed.medical.repository;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import com.terangamed.medical.AbstractPostgresIntegrationTest;
import com.terangamed.medical.config.JpaConfig;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.entity.Prescription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JpaAuditingAutoConfiguration.class)
@Import(JpaConfig.class)
class PrescriptionRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired MedicalRecordRepository medicalRecordRepository;
    @Autowired ConsultationRepository consultationRepository;
    @Autowired PrescriptionRepository repository;

    Long consultationId;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        consultationRepository.deleteAll();
        medicalRecordRepository.deleteAll();

        MedicalRecord mr = medicalRecordRepository.saveAndFlush(
                MedicalRecord.builder().patientId(7001L).build());
        Consultation c = consultationRepository.saveAndFlush(Consultation.builder()
                .medicalRecordId(mr.getId())
                .doctorId(202L)
                .consultationDate(LocalDateTime.now())
                .motif("Toux")
                .build());
        consultationId = c.getId();
    }

    @Test
    void should_persist_prescription_unique_per_consultation() {
        Prescription saved = repository.saveAndFlush(Prescription.builder()
                .prescriptionNumber("ORD-2026-00001")
                .consultationId(consultationId)
                .issuedAt(Instant.now())
                .validUntil(LocalDate.now().plusMonths(3))
                .build());

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findByConsultationId(consultationId)).isPresent();
        assertThat(repository.findByPrescriptionNumber("ORD-2026-00001")).isPresent();
        assertThat(repository.existsByPrescriptionNumber("ORD-2026-00001")).isTrue();
    }

    @Test
    void should_find_max_sequence_for_year() {
        Long c1 = consultationRepository.saveAndFlush(Consultation.builder()
                .medicalRecordId(consultationRepository.findById(consultationId).orElseThrow().getMedicalRecordId())
                .doctorId(202L).consultationDate(LocalDateTime.now()).motif("x").build()).getId();
        Long c2 = consultationRepository.saveAndFlush(Consultation.builder()
                .medicalRecordId(consultationRepository.findById(consultationId).orElseThrow().getMedicalRecordId())
                .doctorId(202L).consultationDate(LocalDateTime.now()).motif("y").build()).getId();

        repository.saveAndFlush(Prescription.builder()
                .prescriptionNumber("ORD-2026-00001").consultationId(consultationId)
                .issuedAt(Instant.now()).build());
        repository.saveAndFlush(Prescription.builder()
                .prescriptionNumber("ORD-2026-00042").consultationId(c1)
                .issuedAt(Instant.now()).build());
        repository.saveAndFlush(Prescription.builder()
                .prescriptionNumber("ORD-2025-00099").consultationId(c2)
                .issuedAt(Instant.now()).build());

        assertThat(repository.findMaxSequenceForYear("2026")).contains(42);
        assertThat(repository.findMaxSequenceForYear("2025")).contains(99);
        assertThat(repository.findMaxSequenceForYear("2099")).isEmpty();
    }
}
