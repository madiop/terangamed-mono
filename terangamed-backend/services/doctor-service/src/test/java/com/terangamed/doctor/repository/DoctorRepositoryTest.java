package com.terangamed.doctor.repository;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import com.terangamed.common.finance.Currency;
import com.terangamed.doctor.AbstractPostgresIntegrationTest;
import com.terangamed.doctor.config.JpaConfig;
import com.terangamed.doctor.entity.Doctor;
import com.terangamed.doctor.entity.DoctorStatus;
import com.terangamed.doctor.entity.Specialty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JpaAuditingAutoConfiguration.class)
@Import(JpaConfig.class)
class DoctorRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired DoctorRepository repository;

    @BeforeEach
    void cleanup() { repository.deleteAll(); }

    @Test
    void should_persist_and_retrieve() {
        Doctor saved = repository.save(sampleDoctor("MED-2026-00100", "Dia"));
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(repository.findById(saved.getId())).isPresent();
    }

    @Test
    void should_find_by_license_number() {
        repository.save(sampleDoctor("MED-2026-00200", "Ndiaye"));
        assertThat(repository.findByLicenseNumber("MED-2026-00200"))
                .isPresent()
                .get()
                .extracting(Doctor::getLastName).isEqualTo("Ndiaye");
    }

    @Test
    void should_check_existence_by_license() {
        repository.save(sampleDoctor("MED-2026-00300", "Ba"));
        assertThat(repository.existsByLicenseNumber("MED-2026-00300")).isTrue();
        assertThat(repository.existsByLicenseNumber("MED-9999-99999")).isFalse();
    }

    @Test
    void should_find_by_email_ignoring_case() {
        Doctor d = sampleDoctor("MED-2026-00400", "Sy");
        d.setEmail("Sy.MED@terangamed.local");
        repository.save(d);

        assertThat(repository.findByEmailIgnoreCase("sy.med@terangamed.local")).isPresent();
        assertThat(repository.existsByEmailIgnoreCase("SY.MED@TERANGAMED.LOCAL")).isTrue();
    }

    @Test
    void should_compute_max_sequence_for_year() {
        repository.save(sampleDoctor("MED-2026-00001", "A"));
        repository.save(sampleDoctor("MED-2026-00042", "B"));
        repository.save(sampleDoctor("MED-2025-00099", "D"));

        assertThat(repository.findMaxSequenceForYear("2026")).contains(42);
        assertThat(repository.findMaxSequenceForYear("2025")).contains(99);
        assertThat(repository.findMaxSequenceForYear("2099")).isEmpty();
    }

    @Test
    void optimistic_locking_increments_version() {
        Doctor saved = repository.saveAndFlush(sampleDoctor("MED-2026-00500", "Fall"));
        saved.setPhone("+221770999999");
        Doctor updated = repository.saveAndFlush(saved);
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    private static Doctor sampleDoctor(String licenseNumber, String lastName) {
        return Doctor.builder()
                .licenseNumber(licenseNumber)
                .lastName(lastName)
                .firstName("Test")
                .specialty(Specialty.GENERAL_MEDICINE)
                .yearsOfExperience(5)
                .consultationFee(new BigDecimal("10000"))
                .consultationFeeCurrency(Currency.XOF)
                .status(DoctorStatus.ACTIVE)
                .build();
    }
}
