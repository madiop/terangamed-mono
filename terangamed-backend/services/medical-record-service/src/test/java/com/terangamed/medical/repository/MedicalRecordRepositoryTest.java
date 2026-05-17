package com.terangamed.medical.repository;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import com.terangamed.medical.AbstractPostgresIntegrationTest;
import com.terangamed.medical.config.JpaConfig;
import com.terangamed.medical.entity.BloodType;
import com.terangamed.medical.entity.MedicalRecord;
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
class MedicalRecordRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired MedicalRecordRepository repository;

    @BeforeEach
    void cleanup() { repository.deleteAll(); }

    @Test
    void should_persist_and_audit() {
        MedicalRecord saved = repository.save(sample(1001L));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getSoftDeleted()).isFalse();
    }

    @Test
    void should_find_by_patient_id() {
        repository.save(sample(2002L));
        assertThat(repository.findByPatientId(2002L)).isPresent();
        assertThat(repository.existsByPatientId(2002L)).isTrue();
        assertThat(repository.existsByPatientId(9999L)).isFalse();
    }

    @Test
    void optimistic_locking_increments_version() {
        MedicalRecord saved = repository.saveAndFlush(sample(3003L));
        saved.setNotes("updated");
        MedicalRecord updated = repository.saveAndFlush(saved);
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    private static MedicalRecord sample(Long patientId) {
        return MedicalRecord.builder()
                .patientId(patientId)
                .bloodType(BloodType.O_POS)
                .allergiesSummary("Pénicilline")
                .build();
    }
}
