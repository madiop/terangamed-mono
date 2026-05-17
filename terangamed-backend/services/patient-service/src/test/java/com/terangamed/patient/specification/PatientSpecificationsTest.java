package com.terangamed.patient.specification;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import com.terangamed.patient.AbstractPostgresIntegrationTest;
import com.terangamed.patient.config.JpaConfig;
import com.terangamed.patient.dto.PatientSearchCriteria;
import com.terangamed.patient.entity.BloodGroup;
import com.terangamed.patient.entity.Civility;
import com.terangamed.patient.entity.Gender;
import com.terangamed.patient.entity.Patient;
import com.terangamed.patient.entity.PatientStatus;
import com.terangamed.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration des {@link PatientSpecifications} sur PostgreSQL réel
 * (via Testcontainers).
 *
 * <p><b>Annotations clés</b> :
 * <ul>
 *   <li>{@code @DataJpaTest} — slice JPA (rapide), exclut automatiquement web et security</li>
 *   <li>{@code @AutoConfigureTestDatabase(replace = NONE)} — désactive le replacement
 *       par H2 ; on utilise PostgreSQL Testcontainers</li>
 *   <li>{@code @ImportAutoConfiguration(JpaAuditingAutoConfiguration.class)} — force
 *       l'inclusion de l'auto-config d'auditing de {@code common-lib} (le slice
 *       {@code @DataJpaTest} ne la prend pas par défaut). Sans ça,
 *       {@code @EnableJpaAuditing} sur la classe Application ne trouve pas son
 *       {@code AuditorAware} et le contexte échoue au démarrage.</li>
 * </ul>
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ImportAutoConfiguration(JpaAuditingAutoConfiguration.class)
@Import(JpaConfig.class)
class PatientSpecificationsTest extends AbstractPostgresIntegrationTest {

    @Autowired
    PatientRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();

        repository.save(buildPatient("MR-2026-00001",
                "Diop", "Fatou", LocalDate.of(1985, 3, 12),
                Gender.FEMALE, BloodGroup.O_POS, "Dakar", PatientStatus.ACTIVE,
                "0177000001", "fatou.diop@example.sn"));

        repository.save(buildPatient("MR-2026-00002",
                "Martin", "Jean", LocalDate.of(1972, 11, 5),
                Gender.MALE, BloodGroup.A_NEG, "Saint-Louis", PatientStatus.ACTIVE,
                "0177000002", "jean.martin@example.sn"));

        repository.save(buildPatient("MR-2024-00050",
                "Sow", "Aminata", LocalDate.of(2005, 6, 20),
                Gender.FEMALE, BloodGroup.B_POS, "Dakar", PatientStatus.ARCHIVED,
                "0177000003", "aminata.sow@example.sn"));
    }

    @Test
    @DisplayName("Critères vides → tous les patients")
    void empty_criteria_should_return_all_patients() {
        PatientSearchCriteria criteria = new PatientSearchCriteria(
                null, null, null, null, null, null, null, null, null, null, null);

        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(criteria));

        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("Critères null → tous les patients (Specifications retourne where(null))")
    void null_criteria_should_return_all_patients() {
        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(null));

        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("likeIgnoreCase sur nom — recherche partielle insensible à la casse")
    void filter_by_partial_last_name_case_insensitive() {
        PatientSearchCriteria criteria = criteriaBuilder().lastName("DIO").build();

        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(criteria));

        assertThat(results)
                .extracting(Patient::getLastName)
                .containsExactly("Diop");
    }

    @Test
    @DisplayName("Champs blancs (whitespace) → ignorés (pas de filtre appliqué)")
    void blank_value_should_be_treated_as_no_filter() {
        PatientSearchCriteria criteria = criteriaBuilder().lastName("   ").build();

        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(criteria));

        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("equalsField sur statut — match exact")
    void filter_by_status() {
        PatientSearchCriteria criteria = criteriaBuilder().status(PatientStatus.ARCHIVED).build();

        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(criteria));

        assertThat(results)
                .extracting(Patient::getMedicalRecordNumber)
                .containsExactly("MR-2024-00050");
    }

    @Test
    @DisplayName("equalsField sur groupe sanguin")
    void filter_by_blood_group() {
        PatientSearchCriteria criteria = criteriaBuilder().bloodGroup(BloodGroup.A_NEG).build();

        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(criteria));

        assertThat(results)
                .extracting(Patient::getLastName)
                .containsExactly("Martin");
    }

    @Test
    @DisplayName("Combinaison nom + statut + ville")
    void combined_criteria() {
        PatientSearchCriteria criteria = criteriaBuilder()
                .lastName("dio")
                .status(PatientStatus.ACTIVE)
                .city("dakar")
                .build();

        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(criteria));

        assertThat(results)
                .hasSize(1)
                .extracting(Patient::getLastName)
                .containsExactly("Diop");
    }

    @Test
    @DisplayName("birthDateBetween — borne basse uniquement")
    void filter_by_birth_date_from_only() {
        PatientSearchCriteria criteria = criteriaBuilder()
                .birthDateFrom(LocalDate.of(1980, 1, 1))
                .build();

        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(criteria));

        assertThat(results)
                .extracting(Patient::getLastName)
                .containsExactlyInAnyOrder("Diop", "Sow");
    }

    @Test
    @DisplayName("birthDateBetween — borne haute uniquement")
    void filter_by_birth_date_to_only() {
        PatientSearchCriteria criteria = criteriaBuilder()
                .birthDateTo(LocalDate.of(1990, 12, 31))
                .build();

        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(criteria));

        assertThat(results)
                .extracting(Patient::getLastName)
                .containsExactlyInAnyOrder("Diop", "Martin");
    }

    @Test
    @DisplayName("birthDateBetween — fenêtre fermée")
    void filter_by_birth_date_range() {
        PatientSearchCriteria criteria = criteriaBuilder()
                .birthDateFrom(LocalDate.of(1980, 1, 1))
                .birthDateTo(LocalDate.of(2000, 12, 31))
                .build();

        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(criteria));

        assertThat(results)
                .extracting(Patient::getLastName)
                .containsExactly("Diop");
    }

    @Test
    @DisplayName("Email partiel insensible à la casse")
    void filter_by_partial_email() {
        PatientSearchCriteria criteria = criteriaBuilder().email("@EXAMPLE.SN").build();

        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(criteria));

        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("Aucun match → liste vide (pas d'erreur SQL)")
    void no_match_returns_empty_list() {
        PatientSearchCriteria criteria = criteriaBuilder().lastName("ZZZINEXISTANT").build();

        List<Patient> results = repository.findAll(PatientSpecifications.withCriteria(criteria));

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Specification 'notArchived' exclut les dossiers archivés")
    void not_archived_excludes_archived_patients() {
        List<Patient> results = repository.findAll(PatientSpecifications.notArchived());

        assertThat(results)
                .extracting(Patient::getStatus)
                .doesNotContain(PatientStatus.ARCHIVED);
    }

    @Test
    @DisplayName("Classe utilitaire — constructeur privé non instanciable")
    void utility_class_should_not_be_instantiable() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            var ctor = PatientSpecifications.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
        }).hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    // ─────────────────────────── Helpers de test ───────────────────────────

    private static Patient buildPatient(String mrn, String lastName, String firstName,
                                        LocalDate birthDate, Gender gender, BloodGroup blood,
                                        String city, PatientStatus status,
                                        String phone, String email) {
        return Patient.builder()
                .medicalRecordNumber(mrn)
                .civility(gender == Gender.FEMALE ? Civility.MME : Civility.M)
                .lastName(lastName)
                .firstName(firstName)
                .birthDate(birthDate)
                .gender(gender)
                .phone(phone)
                .email(email)
                .city(city)
                .country("Sénégal")
                .bloodGroup(blood)
                .status(status)
                .build();
    }

    private static CriteriaBuilder criteriaBuilder() {
        return new CriteriaBuilder();
    }

    private static class CriteriaBuilder {
        String lastName, firstName, mrn, phone, email, city;
        PatientStatus status;
        Gender gender;
        BloodGroup bloodGroup;
        LocalDate birthDateFrom, birthDateTo;

        CriteriaBuilder lastName(String v)        { this.lastName = v; return this; }
        CriteriaBuilder firstName(String v)       { this.firstName = v; return this; }
        CriteriaBuilder phone(String v)           { this.phone = v; return this; }
        CriteriaBuilder email(String v)           { this.email = v; return this; }
        CriteriaBuilder city(String v)            { this.city = v; return this; }
        CriteriaBuilder status(PatientStatus v)   { this.status = v; return this; }
        CriteriaBuilder gender(Gender v)          { this.gender = v; return this; }
        CriteriaBuilder bloodGroup(BloodGroup v)  { this.bloodGroup = v; return this; }
        CriteriaBuilder birthDateFrom(LocalDate v){ this.birthDateFrom = v; return this; }
        CriteriaBuilder birthDateTo(LocalDate v)  { this.birthDateTo = v; return this; }

        PatientSearchCriteria build() {
            return new PatientSearchCriteria(lastName, firstName, mrn, phone, email,
                    status, gender, bloodGroup, birthDateFrom, birthDateTo, city);
        }
    }
}
