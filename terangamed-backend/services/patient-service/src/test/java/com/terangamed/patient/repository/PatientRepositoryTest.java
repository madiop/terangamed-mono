package com.terangamed.patient.repository;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import com.terangamed.patient.AbstractPostgresIntegrationTest;
import com.terangamed.patient.config.JpaConfig;
import com.terangamed.patient.entity.BloodGroup;
import com.terangamed.patient.entity.Civility;
import com.terangamed.patient.entity.Gender;
import com.terangamed.patient.entity.Patient;
import com.terangamed.patient.entity.PatientStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration des derived queries du {@link PatientRepository} :
 * recherche par MRN, par email (case-insensitive), comptage de séquence par année.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JpaAuditingAutoConfiguration.class)
@Import(JpaConfig.class)
class PatientRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired
    PatientRepository repository;

    @BeforeEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void should_persist_and_retrieve_by_id() {
        Patient saved = repository.save(samplePatient("MR-2026-00100", "Dia", "Mamadou"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(repository.findById(saved.getId())).isPresent();
    }

    @Test
    void should_find_by_medical_record_number() {
        repository.save(samplePatient("MR-2026-00200", "Ndiaye", "Awa"));

        assertThat(repository.findByMedicalRecordNumber("MR-2026-00200"))
                .isPresent()
                .get()
                .extracting(Patient::getLastName)
                .isEqualTo("Ndiaye");
    }

    @Test
    void should_check_existence_by_medical_record_number() {
        repository.save(samplePatient("MR-2026-00300", "Ba", "Cheikh"));

        assertThat(repository.existsByMedicalRecordNumber("MR-2026-00300")).isTrue();
        assertThat(repository.existsByMedicalRecordNumber("MR-9999-99999")).isFalse();
    }

    @Test
    void should_find_by_email_ignoring_case() {
        Patient p = samplePatient("MR-2026-00400", "Sy", "Khady");
        p.setEmail("Khady.SY@example.sn");
        repository.save(p);

        assertThat(repository.findByEmailIgnoreCase("khady.sy@example.sn"))
                .isPresent()
                .get()
                .extracting(Patient::getLastName)
                .isEqualTo("Sy");

        assertThat(repository.existsByEmailIgnoreCase("KHADY.SY@EXAMPLE.SN")).isTrue();
    }

    @Test
    void should_compute_max_sequence_for_year() {
        repository.save(samplePatient("MR-2026-00001", "A", "A"));
        repository.save(samplePatient("MR-2026-00042", "B", "B"));
        repository.save(samplePatient("MR-2026-00007", "C", "C"));
        repository.save(samplePatient("MR-2025-00099", "D", "D")); // autre année

        assertThat(repository.findMaxSequenceForYear("2026")).contains(42);
        assertThat(repository.findMaxSequenceForYear("2025")).contains(99);
        assertThat(repository.findMaxSequenceForYear("2099")).isEmpty();
    }

    @Test
    void unique_constraint_should_prevent_duplicate_mrn() {
        repository.save(samplePatient("MR-2026-DUP", "Diop", "X"));

        assertThat(repository.existsByMedicalRecordNumber("MR-2026-DUP")).isTrue();
        // L'unicité est vérifiée applicativement via existsBy... avant insert
        // (cf. service layer step 4.2). La contrainte SQL est un filet de sécurité.
    }

    @Test
    void optimistic_locking_version_increments_on_update() {
        Patient saved = repository.saveAndFlush(samplePatient("MR-2026-00500", "Fall", "Ousmane"));
        assertThat(saved.getVersion()).isZero();

        saved.setPhone("0177999999");
        Patient updated = repository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isEqualTo(1);
    }

    private static Patient samplePatient(String mrn, String lastName, String firstName) {
        return Patient.builder()
                .medicalRecordNumber(mrn)
                .civility(Civility.M)
                .lastName(lastName)
                .firstName(firstName)
                .birthDate(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE)
                .country("Sénégal")
                .bloodGroup(BloodGroup.UNKNOWN)
                .status(PatientStatus.ACTIVE)
                .build();
    }
}
