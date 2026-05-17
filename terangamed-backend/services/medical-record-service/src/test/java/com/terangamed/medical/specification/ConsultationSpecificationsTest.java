package com.terangamed.medical.specification;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import com.terangamed.medical.AbstractPostgresIntegrationTest;
import com.terangamed.medical.config.JpaConfig;
import com.terangamed.medical.dto.ConsultationSearchCriteria;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.repository.ConsultationRepository;
import com.terangamed.medical.repository.MedicalRecordRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JpaAuditingAutoConfiguration.class)
@Import(JpaConfig.class)
class ConsultationSpecificationsTest extends AbstractPostgresIntegrationTest {

    @Autowired MedicalRecordRepository medicalRecordRepository;
    @Autowired ConsultationRepository repository;

    Long mrPatient1;
    Long mrPatient2;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        medicalRecordRepository.deleteAll();

        mrPatient1 = medicalRecordRepository.saveAndFlush(
                MedicalRecord.builder().patientId(1L).build()).getId();
        mrPatient2 = medicalRecordRepository.saveAndFlush(
                MedicalRecord.builder().patientId(2L).build()).getId();

        repository.save(Consultation.builder()
                .medicalRecordId(mrPatient1).doctorId(10L)
                .consultationDate(LocalDateTime.of(2026, 3, 1, 10, 0))
                .motif("Toux persistante").diagnostic("Bronchite")
                .build());
        repository.save(Consultation.builder()
                .medicalRecordId(mrPatient1).doctorId(20L)
                .consultationDate(LocalDateTime.of(2026, 4, 5, 14, 0))
                .motif("Maux de tête").diagnostic("Migraine")
                .signed(true).signedAt(Instant.now()).signedBy("dr.test")
                .build());
        repository.save(Consultation.builder()
                .medicalRecordId(mrPatient2).doctorId(10L)
                .consultationDate(LocalDateTime.of(2026, 4, 10, 9, 0))
                .motif("Bilan annuel").diagnostic("RAS")
                .build());
        // Une consultation soft-deleted — doit toujours être exclue
        repository.save(Consultation.builder()
                .medicalRecordId(mrPatient1).doctorId(10L)
                .consultationDate(LocalDateTime.of(2025, 12, 15, 11, 0))
                .motif("Ancien").softDeleted(true)
                .deletedAt(Instant.now()).deletedBy("admin")
                .build());
    }

    @Test
    void empty_criteria_excludes_soft_deleted() {
        var results = repository.findAll(ConsultationSpecifications.withCriteria(
                new ConsultationSearchCriteria(null, null, null, null, null, null)));
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(c -> !c.getSoftDeleted());
    }

    @Test
    void null_criteria_returns_active_only() {
        assertThat(repository.findAll(ConsultationSpecifications.withCriteria(null))).hasSize(3);
    }

    @Test
    void filter_by_patient_id_uses_subquery() {
        var results = repository.findAll(ConsultationSpecifications.withCriteria(
                new ConsultationSearchCriteria(1L, null, null, null, null, null)));
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(c -> c.getMedicalRecordId().equals(mrPatient1));
    }

    @Test
    void filter_by_doctor_id() {
        var results = repository.findAll(ConsultationSpecifications.withCriteria(
                new ConsultationSearchCriteria(null, 10L, null, null, null, null)));
        assertThat(results).hasSize(2).allMatch(c -> c.getDoctorId().equals(10L));
    }

    @Test
    void filter_by_date_range() {
        var results = repository.findAll(ConsultationSpecifications.withCriteria(
                new ConsultationSearchCriteria(null, null,
                        LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null, null)));
        assertThat(results).hasSize(2);
    }

    @Test
    void filter_by_signed_status() {
        var signed = repository.findAll(ConsultationSpecifications.withCriteria(
                new ConsultationSearchCriteria(null, null, null, null, true, null)));
        assertThat(signed).hasSize(1).allMatch(Consultation::getSigned);

        var notSigned = repository.findAll(ConsultationSpecifications.withCriteria(
                new ConsultationSearchCriteria(null, null, null, null, false, null)));
        assertThat(notSigned).hasSize(2);
    }

    @Test
    void filter_by_keyword_matches_motif_or_diagnostic() {
        var byMotif = repository.findAll(ConsultationSpecifications.withCriteria(
                new ConsultationSearchCriteria(null, null, null, null, null, "TOUX")));
        assertThat(byMotif).hasSize(1);

        var byDiag = repository.findAll(ConsultationSpecifications.withCriteria(
                new ConsultationSearchCriteria(null, null, null, null, null, "migraine")));
        assertThat(byDiag).hasSize(1);
    }

    @Test
    void blank_keyword_treated_as_no_filter() {
        var results = repository.findAll(ConsultationSpecifications.withCriteria(
                new ConsultationSearchCriteria(null, null, null, null, null, "  ")));
        assertThat(results).hasSize(3);
    }

    @Test
    void by_medical_record_id_helper() {
        List<Consultation> results = repository.findAll(
                ConsultationSpecifications.byMedicalRecordId(mrPatient1)
                        .and(ConsultationSpecifications.notSoftDeleted()));
        assertThat(results).hasSize(2);
    }

    @Test
    void utility_class_should_not_be_instantiable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            var ctor = ConsultationSpecifications.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
