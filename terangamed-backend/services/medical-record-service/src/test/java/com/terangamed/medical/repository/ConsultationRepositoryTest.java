package com.terangamed.medical.repository;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import com.terangamed.medical.AbstractPostgresIntegrationTest;
import com.terangamed.medical.config.JpaConfig;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.entity.VitalSigns;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JpaAuditingAutoConfiguration.class)
@Import(JpaConfig.class)
class ConsultationRepositoryTest extends AbstractPostgresIntegrationTest {

    @Autowired MedicalRecordRepository medicalRecordRepository;
    @Autowired ConsultationRepository repository;

    Long medicalRecordId;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        medicalRecordRepository.deleteAll();
        MedicalRecord mr = medicalRecordRepository.saveAndFlush(
                MedicalRecord.builder().patientId(8001L).build());
        medicalRecordId = mr.getId();
    }

    @Test
    void should_persist_consultation_with_jsonb_vital_signs() {
        VitalSigns vs = VitalSigns.builder()
                .weightKg(new BigDecimal("72.5"))
                .heightCm(new BigDecimal("178"))
                .bloodPressureSystolic(125)
                .bloodPressureDiastolic(82)
                .heartRateBpm(76)
                .temperatureCelsius(new BigDecimal("36.8"))
                .oxygenSaturationPercent(98)
                .build();

        Consultation saved = repository.saveAndFlush(Consultation.builder()
                .medicalRecordId(medicalRecordId)
                .doctorId(101L)
                .consultationDate(LocalDateTime.now())
                .motif("Contrôle annuel")
                .vitalSigns(vs)
                .diagnostic("RAS")
                .build());

        // Reload pour vérifier la désérialisation depuis JSONB
        Consultation reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getVitalSigns()).isNotNull();
        assertThat(reloaded.getVitalSigns().getWeightKg()).isEqualByComparingTo("72.5");
        assertThat(reloaded.getVitalSigns().getBloodPressureSystolic()).isEqualTo(125);
        assertThat(reloaded.getVitalSigns().getOxygenSaturationPercent()).isEqualTo(98);
        assertThat(reloaded.getSigned()).isFalse();
        assertThat(reloaded.getSoftDeleted()).isFalse();
    }

    @Test
    void should_find_by_appointment_id() {
        repository.save(Consultation.builder()
                .medicalRecordId(medicalRecordId)
                .doctorId(101L)
                .appointmentId(555L)
                .consultationDate(LocalDateTime.now())
                .motif("RDV").build());

        assertThat(repository.findByAppointmentId(555L)).isPresent();
        assertThat(repository.existsByAppointmentId(555L)).isTrue();
        assertThat(repository.existsByAppointmentId(999L)).isFalse();
    }
}
