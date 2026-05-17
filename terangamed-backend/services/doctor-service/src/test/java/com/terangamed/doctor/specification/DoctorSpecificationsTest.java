package com.terangamed.doctor.specification;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import com.terangamed.common.finance.Currency;
import com.terangamed.doctor.AbstractPostgresIntegrationTest;
import com.terangamed.doctor.config.JpaConfig;
import com.terangamed.doctor.dto.DoctorSearchCriteria;
import com.terangamed.doctor.entity.Doctor;
import com.terangamed.doctor.entity.DoctorStatus;
import com.terangamed.doctor.entity.Specialty;
import com.terangamed.doctor.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JpaAuditingAutoConfiguration.class)
@Import(JpaConfig.class)
class DoctorSpecificationsTest extends AbstractPostgresIntegrationTest {

    @Autowired DoctorRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.save(buildDoctor("MED-2026-00001", "Martin", "Jean",
                Specialty.GENERAL_MEDICINE, DoctorStatus.ACTIVE, 18, "15000"));
        repository.save(buildDoctor("MED-2026-00002", "Diallo", "Mariama",
                Specialty.PEDIATRICS, DoctorStatus.ACTIVE, 12, "20000"));
        repository.save(buildDoctor("MED-2026-00003", "Sall", "Cheikh",
                Specialty.CARDIOLOGY, DoctorStatus.ON_LEAVE, 22, "30000"));
    }

    @Test
    void empty_criteria_returns_all() {
        var results = repository.findAll(DoctorSpecifications.withCriteria(
                new DoctorSearchCriteria(null, null, null, null, null, null, null, null)));
        assertThat(results).hasSize(3);
    }

    @Test
    void null_criteria_returns_all() {
        assertThat(repository.findAll(DoctorSpecifications.withCriteria(null))).hasSize(3);
    }

    @Test
    void filter_by_partial_last_name_case_insensitive() {
        var results = repository.findAll(DoctorSpecifications.withCriteria(
                new DoctorSearchCriteria("DIA", null, null, null, null, null, null, null)));
        assertThat(results).extracting(Doctor::getLastName).containsExactly("Diallo");
    }

    @Test
    void filter_by_specialty() {
        var results = repository.findAll(DoctorSpecifications.withCriteria(
                new DoctorSearchCriteria(null, null, null, null,
                        Specialty.CARDIOLOGY, null, null, null)));
        assertThat(results).extracting(Doctor::getLastName).containsExactly("Sall");
    }

    @Test
    void filter_by_status() {
        var results = repository.findAll(DoctorSpecifications.withCriteria(
                new DoctorSearchCriteria(null, null, null, null, null,
                        DoctorStatus.ON_LEAVE, null, null)));
        assertThat(results).hasSize(1);
    }

    @Test
    void filter_by_min_years_of_experience() {
        var results = repository.findAll(DoctorSpecifications.withCriteria(
                new DoctorSearchCriteria(null, null, null, null, null, null, 15, null)));
        assertThat(results).extracting(Doctor::getLastName).containsExactlyInAnyOrder("Martin", "Sall");
    }

    @Test
    void filter_by_max_consultation_fee() {
        var results = repository.findAll(DoctorSpecifications.withCriteria(
                new DoctorSearchCriteria(null, null, null, null, null, null, null,
                        new BigDecimal("20000"))));
        assertThat(results).extracting(Doctor::getLastName).containsExactlyInAnyOrder("Martin", "Diallo");
    }

    @Test
    void blank_value_treated_as_no_filter() {
        var results = repository.findAll(DoctorSpecifications.withCriteria(
                new DoctorSearchCriteria("   ", null, null, null, null, null, null, null)));
        assertThat(results).hasSize(3);
    }

    @Test
    void only_active_excludes_on_leave_and_retired() {
        List<Doctor> results = repository.findAll(DoctorSpecifications.onlyActive());
        assertThat(results).extracting(Doctor::getStatus).allMatch(s -> s == DoctorStatus.ACTIVE);
    }

    @Test
    void no_match_returns_empty_list() {
        var results = repository.findAll(DoctorSpecifications.withCriteria(
                new DoctorSearchCriteria("ZZZ_INEXISTANT", null, null, null, null, null, null, null)));
        assertThat(results).isEmpty();
    }

    @Test
    void utility_class_should_not_be_instantiable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            var ctor = DoctorSpecifications.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    private static Doctor buildDoctor(String licenseNumber, String lastName, String firstName,
                                      Specialty specialty, DoctorStatus status, int years, String fee) {
        return Doctor.builder()
                .licenseNumber(licenseNumber)
                .lastName(lastName)
                .firstName(firstName)
                .specialty(specialty)
                .yearsOfExperience(years)
                .consultationFee(new BigDecimal(fee))
                .consultationFeeCurrency(Currency.XOF)
                .status(status)
                .build();
    }
}
