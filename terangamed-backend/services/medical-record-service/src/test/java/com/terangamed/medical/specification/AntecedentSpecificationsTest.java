package com.terangamed.medical.specification;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import com.terangamed.medical.AbstractPostgresIntegrationTest;
import com.terangamed.medical.config.JpaConfig;
import com.terangamed.medical.entity.Antecedent;
import com.terangamed.medical.entity.AntecedentType;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.repository.AntecedentRepository;
import com.terangamed.medical.repository.MedicalRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JpaAuditingAutoConfiguration.class)
@Import(JpaConfig.class)
class AntecedentSpecificationsTest extends AbstractPostgresIntegrationTest {

    @Autowired MedicalRecordRepository medicalRecordRepository;
    @Autowired AntecedentRepository repository;

    Long mr1, mr2;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        medicalRecordRepository.deleteAll();

        mr1 = medicalRecordRepository.saveAndFlush(MedicalRecord.builder().patientId(1L).build()).getId();
        mr2 = medicalRecordRepository.saveAndFlush(MedicalRecord.builder().patientId(2L).build()).getId();

        repository.save(Antecedent.builder().medicalRecordId(mr1).type(AntecedentType.ALLERGY)
                .title("Pénicilline").active(true).build());
        repository.save(Antecedent.builder().medicalRecordId(mr1).type(AntecedentType.MEDICAL_CONDITION)
                .title("Diabète type 2").active(true).build());
        repository.save(Antecedent.builder().medicalRecordId(mr1).type(AntecedentType.SURGERY)
                .title("Appendicectomie 2018").active(false).build());
        repository.save(Antecedent.builder().medicalRecordId(mr2).type(AntecedentType.ALLERGY)
                .title("Aspirine").active(true).build());
    }

    @Test
    void filter_by_medical_record() {
        var results = repository.findAll(AntecedentSpecifications.byMedicalRecordId(mr1));
        assertThat(results).hasSize(3);
    }

    @Test
    void filter_by_type_combined() {
        var results = repository.findAll(
                AntecedentSpecifications.byMedicalRecordId(mr1)
                        .and(AntecedentSpecifications.byType(AntecedentType.ALLERGY)));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitle()).isEqualTo("Pénicilline");
    }

    @Test
    void only_active_filters_inactive() {
        var results = repository.findAll(
                AntecedentSpecifications.byMedicalRecordId(mr1)
                        .and(AntecedentSpecifications.onlyActive()));
        assertThat(results).hasSize(2).allMatch(Antecedent::getActive);
    }

    @Test
    void utility_class_should_not_be_instantiable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            var ctor = AntecedentSpecifications.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
