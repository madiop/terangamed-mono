package com.terangamed.patient.service;

import com.terangamed.common.exception.BadRequestException;
import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.outbox.OutboxEventPublisher;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.patient.dto.CreatePatientRequest;
import com.terangamed.patient.dto.PatientDto;
import com.terangamed.patient.dto.PatientSearchCriteria;
import com.terangamed.patient.dto.UpdatePatientRequest;
import com.terangamed.patient.entity.BloodGroup;
import com.terangamed.patient.entity.Civility;
import com.terangamed.patient.entity.Gender;
import com.terangamed.patient.entity.Patient;
import com.terangamed.patient.entity.PatientStatus;
import com.terangamed.patient.mapper.PatientMapper;
import com.terangamed.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link PatientService} avec :
 * <ul>
 *   <li>Repository mocké via Mockito (pas de DB)</li>
 *   <li>Mapper réel obtenu via {@code Mappers.getMapper()} — exercer le code MapStruct
 *       réel rend les tests plus fidèles à la prod sans complexité supplémentaire</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    PatientRepository repository;

    @Mock
    OutboxEventPublisher outboxPublisher;

    PatientMapper mapper;
    PatientService service;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(PatientMapper.class);
        service = new PatientService(repository, mapper, outboxPublisher);
    }

    // ─────────────────────────── search ───────────────────────────

    @Test
    @DisplayName("search applique Specifications, mappe les entités et préserve la pagination")
    void search_should_paginate_and_map_entities() {
        Patient p1 = patientBuilder().id(1L).medicalRecordNumber("MR-2026-00001")
                .lastName("Diop").firstName("Fatou").build();
        Page<Patient> page = new PageImpl<>(List.of(p1), PageRequest.of(0, 10), 1);

        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        PatientSearchCriteria criteria = new PatientSearchCriteria(
                "Diop", null, null, null, null, null, null, null, null, null, null);

        PageResponse<PatientDto> response = service.search(
                criteria, PageRequest.of(0, 10, Sort.by("lastName")));

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content())
                .singleElement()
                .extracting(PatientDto::lastName)
                .isEqualTo("Diop");
    }

    @Test
    @DisplayName("search rejette un tri sur un champ non whitelisté (BadRequestException)")
    void search_should_reject_invalid_sort_field() {
        Pageable invalidSort = PageRequest.of(0, 10, Sort.by("password"));

        assertThatThrownBy(() -> service.search(null, invalidSort))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("password");
    }

    // ─────────────────────────── findById ───────────────────────────

    @Test
    void findById_should_return_dto_when_present() {
        Patient p = patientBuilder().id(42L).medicalRecordNumber("MR-2026-00042")
                .lastName("Martin").firstName("Jean").build();
        when(repository.findById(42L)).thenReturn(Optional.of(p));

        PatientDto dto = service.findById(42L);

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.lastName()).isEqualTo("Martin");
    }

    @Test
    void findById_should_throw_when_not_found() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Patient")
                .hasMessageContaining("999");
    }

    // ─────────────────────────── findByMedicalRecordNumber ───────────────────────────

    @Test
    void findByMedicalRecordNumber_should_return_dto() {
        Patient p = patientBuilder().id(7L).medicalRecordNumber("MR-2026-00007").build();
        when(repository.findByMedicalRecordNumber("MR-2026-00007")).thenReturn(Optional.of(p));

        PatientDto dto = service.findByMedicalRecordNumber("MR-2026-00007");

        assertThat(dto.medicalRecordNumber()).isEqualTo("MR-2026-00007");
    }

    @Test
    void findByMedicalRecordNumber_should_throw_when_not_found() {
        when(repository.findByMedicalRecordNumber("MR-9999-99999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByMedicalRecordNumber("MR-9999-99999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("MR-9999-99999");
    }

    // ─────────────────────────── create ───────────────────────────

    @Test
    @DisplayName("create génère un MRN, fixe le statut ACTIVE et persiste")
    void create_should_generate_mrn_and_persist() {
        when(repository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(41));
        when(repository.existsByMedicalRecordNumber(anyString())).thenReturn(false);
        when(repository.save(any(Patient.class))).thenAnswer(inv -> {
            Patient saved = inv.getArgument(0);
            saved.setId(100L); // simule l'attribution d'ID par la DB
            return saved;
        });

        CreatePatientRequest request = new CreatePatientRequest(
                Civility.MME, "Diop", "Fatou", LocalDate.of(1985, 3, 12), Gender.FEMALE,
                "0177000001", "fatou.diop@example.sn",
                "12 rue de la Paix", null, "10000", "Dakar", "Sénégal",
                BloodGroup.O_POS, null, null, null);

        PatientDto dto = service.create(request);

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(repository).save(captor.capture());
        Patient saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(PatientStatus.ACTIVE);
        assertThat(saved.getMedicalRecordNumber()).matches("MR-\\d{4}-00042");
        assertThat(dto.id()).isEqualTo(100L);
    }

    @Test
    @DisplayName("create lève ConflictException si l'email est déjà pris")
    void create_should_throw_when_email_already_used() {
        Patient existing = patientBuilder().id(99L).email("fatou.diop@example.sn").build();
        when(repository.findByEmailIgnoreCase("fatou.diop@example.sn"))
                .thenReturn(Optional.of(existing));

        CreatePatientRequest request = new CreatePatientRequest(
                Civility.MME, "Diop", "Fatou", LocalDate.of(1985, 3, 12), Gender.FEMALE,
                null, "fatou.diop@example.sn", null, null, null, null, null,
                null, null, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("fatou.diop@example.sn")
                .extracting("errorCode")
                .isEqualTo("PATIENT_EMAIL_DUPLICATE");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("create accepte un email vide sans conflit")
    void create_should_skip_email_check_when_blank() {
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.empty());
        when(repository.existsByMedicalRecordNumber(anyString())).thenReturn(false);
        // simule l'attribution d'id par JPA pour que publishCreated() trouve un id non-null
        when(repository.save(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        CreatePatientRequest request = new CreatePatientRequest(
                Civility.M, "Sow", "Cheikh", LocalDate.of(1990, 1, 1), Gender.MALE,
                null, null, null, null, null, null, null, null, null, null, null);

        service.create(request);

        verify(repository, never()).findByEmailIgnoreCase(anyString());
    }

    // ─────────────────────────── update ───────────────────────────

    @Test
    @DisplayName("update applique partial update et préserve les champs non fournis")
    void update_should_apply_partial_changes() {
        Patient existing = patientBuilder().id(5L).lastName("Old").firstName("Name")
                .phone("0100000000").email("old@example.sn").build();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdatePatientRequest request = new UpdatePatientRequest(
                null, "NewName", null, null, null, "0177999999", null,
                null, null, null, null, null, null, null, null, null, null);

        PatientDto dto = service.update(5L, request);

        assertThat(dto.lastName()).isEqualTo("NewName");
        assertThat(dto.firstName()).isEqualTo("Name");      // préservé
        assertThat(dto.phone()).isEqualTo("0177999999");
        assertThat(dto.email()).isEqualTo("old@example.sn"); // préservé
    }

    @Test
    void update_should_throw_when_id_unknown() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        UpdatePatientRequest request = new UpdatePatientRequest(
                null, "X", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(999L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update vérifie l'unicité email seulement si l'email change")
    void update_should_check_email_uniqueness_only_when_changed() {
        Patient existing = patientBuilder().id(5L).email("same@example.sn").build();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        // Email identique (mais case différent) → pas de check
        UpdatePatientRequest request = new UpdatePatientRequest(
                null, null, null, null, null, null, "SAME@example.sn",
                null, null, null, null, null, null, null, null, null, null);

        service.update(5L, request);

        verify(repository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("update lève ConflictException si le nouvel email appartient à un autre patient")
    void update_should_throw_when_new_email_taken_by_another_patient() {
        Patient currentPatient = patientBuilder().id(5L).email("old@example.sn").build();
        Patient otherPatient = patientBuilder().id(99L).email("taken@example.sn").build();

        when(repository.findById(5L)).thenReturn(Optional.of(currentPatient));
        when(repository.findByEmailIgnoreCase("taken@example.sn")).thenReturn(Optional.of(otherPatient));

        UpdatePatientRequest request = new UpdatePatientRequest(
                null, null, null, null, null, null, "taken@example.sn",
                null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(5L, request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("PATIENT_EMAIL_DUPLICATE");
    }

    // ─────────────────────────── archive ───────────────────────────

    @Test
    void archive_should_set_status_archived() {
        Patient p = patientBuilder().id(10L).status(PatientStatus.ACTIVE).build();
        when(repository.findById(10L)).thenReturn(Optional.of(p));
        when(repository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));

        service.archive(10L);

        verify(repository).save(p);
        assertThat(p.getStatus()).isEqualTo(PatientStatus.ARCHIVED);
    }

    @Test
    @DisplayName("archive est idempotent : pas de save si déjà ARCHIVED")
    void archive_should_be_idempotent() {
        Patient p = patientBuilder().id(10L).status(PatientStatus.ARCHIVED).build();
        when(repository.findById(10L)).thenReturn(Optional.of(p));

        service.archive(10L);

        verify(repository, never()).save(any());
    }

    @Test
    void archive_should_throw_when_not_found() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─────────────────────────── delete ───────────────────────────

    @Test
    void delete_should_remove_patient_when_exists() {
        when(repository.existsById(10L)).thenReturn(true);

        service.delete(10L);

        verify(repository).deleteById(10L);
    }

    @Test
    void delete_should_throw_when_id_unknown() {
        when(repository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).deleteById(any());
    }

    // ─────────────────────────── generateMedicalRecordNumber ───────────────────────────

    @Test
    void generateMrn_should_use_format_with_padded_sequence() {
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(7));
        when(repository.existsByMedicalRecordNumber(anyString())).thenReturn(false);

        String mrn = service.generateMedicalRecordNumber();

        assertThat(mrn).matches("MR-\\d{4}-00008");
    }

    @Test
    void generateMrn_should_start_at_one_when_no_existing_for_year() {
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.empty());
        when(repository.existsByMedicalRecordNumber(anyString())).thenReturn(false);

        String mrn = service.generateMedicalRecordNumber();

        assertThat(mrn).matches("MR-\\d{4}-00001");
    }

    @Test
    @DisplayName("generateMrn lève ConflictException si race condition détectée")
    void generateMrn_should_throw_on_race_condition() {
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(0));
        when(repository.existsByMedicalRecordNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.generateMedicalRecordNumber())
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("MRN_GENERATION_CONFLICT");
    }

    // ─────────────────────────── Helpers ───────────────────────────

    /**
     * Builder par défaut — pré-rempli avec un {@code medicalRecordNumber} bidon.
     *
     * <p>Le {@code medicalRecordNumber} est nécessaire pour tous les flux qui
     * publient un événement Avro ({@code PatientUpdated} / {@code PatientArchived}) —
     * le schéma Avro refuse les valeurs null sur ce champ. Les tests qui veulent
     * un MRN spécifique le surchargent via {@code .medicalRecordNumber("MR-...")}.
     */
    private static Patient.PatientBuilder patientBuilder() {
        return Patient.builder()
                .medicalRecordNumber("MR-2026-99999")
                .civility(Civility.M)
                .lastName("Default")
                .firstName("Default")
                .birthDate(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE)
                .country("Sénégal")
                .bloodGroup(BloodGroup.UNKNOWN)
                .status(PatientStatus.ACTIVE)
                .version(0L);
    }
}
