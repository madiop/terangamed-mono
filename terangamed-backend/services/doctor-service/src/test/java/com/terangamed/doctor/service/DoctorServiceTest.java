package com.terangamed.doctor.service;

import com.terangamed.common.exception.BadRequestException;
import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.finance.Currency;
import com.terangamed.common.finance.FinanceProperties;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.doctor.dto.CreateDoctorRequest;
import com.terangamed.doctor.dto.DoctorDto;
import com.terangamed.doctor.dto.DoctorSearchCriteria;
import com.terangamed.doctor.dto.UpdateDoctorRequest;
import com.terangamed.doctor.entity.Doctor;
import com.terangamed.doctor.entity.DoctorStatus;
import com.terangamed.doctor.entity.Specialty;
import com.terangamed.doctor.mapper.DoctorMapper;
import com.terangamed.doctor.repository.DoctorRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock DoctorRepository repository;
    @Mock FinanceProperties finance;
    @Mock com.terangamed.common.outbox.OutboxEventPublisher outboxPublisher;
    DoctorMapper mapper;
    DoctorService service;

    @BeforeEach
    void setUp() {
        mapper = Mappers.getMapper(DoctorMapper.class);
        service = new DoctorService(repository, mapper, finance, outboxPublisher);
    }

    @Test
    void search_paginates_and_maps() {
        Doctor d = doctorBuilder().id(1L).licenseNumber("MED-2026-00001")
                .lastName("Martin").firstName("Jean").build();
        Page<Doctor> page = new PageImpl<>(List.of(d), PageRequest.of(0, 10), 1);

        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        DoctorSearchCriteria criteria = new DoctorSearchCriteria(
                "Martin", null, null, null, null, null, null, null);

        PageResponse<DoctorDto> response = service.search(
                criteria, PageRequest.of(0, 10, Sort.by("lastName")));

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().get(0).lastName()).isEqualTo("Martin");
    }

    @Test
    void search_rejects_invalid_sort_field() {
        Pageable invalidSort = PageRequest.of(0, 10, Sort.by("password"));
        assertThatThrownBy(() -> service.search(null, invalidSort))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("password");
    }

    @Test
    void searchActive_combines_criteria_and_active_filter() {
        Page<Doctor> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        service.searchActive(null, PageRequest.of(0, 10));

        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void findById_returns_dto_when_present() {
        Doctor d = doctorBuilder().id(42L).lastName("Sall").build();
        when(repository.findById(42L)).thenReturn(Optional.of(d));

        DoctorDto dto = service.findById(42L);
        assertThat(dto.lastName()).isEqualTo("Sall");
    }

    @Test
    void findById_throws_when_not_found() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByLicenseNumber_returns_dto() {
        Doctor d = doctorBuilder().id(7L).licenseNumber("MED-2026-00007").build();
        when(repository.findByLicenseNumber("MED-2026-00007")).thenReturn(Optional.of(d));

        DoctorDto dto = service.findByLicenseNumber("MED-2026-00007");
        assertThat(dto.licenseNumber()).isEqualTo("MED-2026-00007");
    }

    @Test
    void findByLicenseNumber_throws_when_unknown() {
        when(repository.findByLicenseNumber("MED-9999-99999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByLicenseNumber("MED-9999-99999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_generates_license_and_sets_status_active() {
        when(repository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(7));
        when(repository.existsByLicenseNumber(anyString())).thenReturn(false);
        when(finance.getDefaultCurrency()).thenReturn(Currency.XOF);
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> {
            Doctor saved = inv.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        CreateDoctorRequest request = new CreateDoctorRequest(
                "Martin", "Jean", Specialty.GENERAL_MEDICINE,
                "j.martin@terangamed.local", "+221770100001", null,
                10, new BigDecimal("15000"), null, "bio", null);

        DoctorDto dto = service.create(request);

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(repository).save(captor.capture());
        Doctor saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(DoctorStatus.ACTIVE);
        assertThat(saved.getLicenseNumber()).matches("MED-\\d{4}-00008");
        assertThat(saved.getConsultationFeeCurrency()).isEqualTo(Currency.XOF);
        assertThat(dto.id()).isEqualTo(100L);
    }

    @Test
    void create_applies_default_currency_when_null() {
        when(repository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(0));
        when(repository.existsByLicenseNumber(anyString())).thenReturn(false);
        when(finance.getDefaultCurrency()).thenReturn(Currency.XOF);
        // simule l'attribution d'id par JPA pour que publishCreated() trouve un id non-null
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> {
            Doctor d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });

        CreateDoctorRequest request = new CreateDoctorRequest(
                "Diop", "Awa", Specialty.GENERAL_MEDICINE,
                "a.diop@terangamed.local", null, null,
                5, new BigDecimal("12000"), null, null, null);

        service.create(request);

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getConsultationFeeCurrency()).isEqualTo(Currency.XOF);
    }

    @Test
    void create_keeps_explicit_currency() {
        when(repository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(0));
        when(repository.existsByLicenseNumber(anyString())).thenReturn(false);
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> {
            Doctor d = inv.getArgument(0);
            d.setId(2L);
            return d;
        });

        CreateDoctorRequest request = new CreateDoctorRequest(
                "Ndiaye", "Cheikh", Specialty.GENERAL_MEDICINE,
                "c.ndiaye@terangamed.local", null, null,
                3, new BigDecimal("20"), Currency.EUR, null, null);

        service.create(request);

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(repository).save(captor.capture());
        // La devise explicite doit être conservée — finance.getDefaultCurrency() ne doit pas être appelé.
        assertThat(captor.getValue().getConsultationFeeCurrency()).isEqualTo(Currency.EUR);
        verify(finance, never()).getDefaultCurrency();
    }

    @Test
    void create_does_not_apply_currency_when_fee_is_null() {
        when(repository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(0));
        when(repository.existsByLicenseNumber(anyString())).thenReturn(false);
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> {
            Doctor d = inv.getArgument(0);
            d.setId(3L);
            return d;
        });

        CreateDoctorRequest request = new CreateDoctorRequest(
                "Sy", "Aminata", Specialty.GENERAL_MEDICINE,
                "a.sy@terangamed.local", null, null,
                0, null, null, null, null);

        service.create(request);

        ArgumentCaptor<Doctor> captor = ArgumentCaptor.forClass(Doctor.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getConsultationFee()).isNull();
        assertThat(captor.getValue().getConsultationFeeCurrency()).isNull();
        verify(finance, never()).getDefaultCurrency();
    }

    @Test
    void create_throws_when_email_duplicate() {
        when(repository.findByEmailIgnoreCase("dup@example.sn"))
                .thenReturn(Optional.of(doctorBuilder().id(1L).email("dup@example.sn").build()));

        CreateDoctorRequest request = new CreateDoctorRequest(
                "X", "Y", Specialty.OTHER, "dup@example.sn",
                null, null, 0, null, null, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("DOCTOR_EMAIL_DUPLICATE");
    }

    @Test
    void update_applies_partial_changes() {
        Doctor existing = doctorBuilder().id(5L).lastName("Old").firstName("Name")
                .yearsOfExperience(5).build();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateDoctorRequest request = new UpdateDoctorRequest(
                "NewName", null, null, null, null, null, 12, null, null, null, null, null);

        DoctorDto dto = service.update(5L, request);
        assertThat(dto.lastName()).isEqualTo("NewName");
        assertThat(dto.firstName()).isEqualTo("Name");
        assertThat(dto.yearsOfExperience()).isEqualTo(12);
    }

    @Test
    void update_throws_when_unknown() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        UpdateDoctorRequest request = new UpdateDoctorRequest(
                "X", null, null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.update(999L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_checks_email_only_when_changed() {
        Doctor existing = doctorBuilder().id(5L).email("same@example.sn").build();
        when(repository.findById(5L)).thenReturn(Optional.of(existing));
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateDoctorRequest request = new UpdateDoctorRequest(
                null, null, null, "SAME@example.sn", null, null, null, null, null, null, null, null);

        service.update(5L, request);
        verify(repository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void putOnLeave_changes_status_to_on_leave() {
        Doctor d = doctorBuilder().id(10L).status(DoctorStatus.ACTIVE).build();
        when(repository.findById(10L)).thenReturn(Optional.of(d));
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.putOnLeave(10L);
        verify(repository).save(d);
        assertThat(d.getStatus()).isEqualTo(DoctorStatus.ON_LEAVE);
    }

    @Test
    void putOnLeave_is_idempotent() {
        Doctor d = doctorBuilder().id(10L).status(DoctorStatus.ON_LEAVE).build();
        when(repository.findById(10L)).thenReturn(Optional.of(d));
        service.putOnLeave(10L);
        verify(repository, never()).save(any());
    }

    @Test
    void putOnLeave_rejects_retired_doctor() {
        Doctor d = doctorBuilder().id(10L).status(DoctorStatus.RETIRED).build();
        when(repository.findById(10L)).thenReturn(Optional.of(d));
        assertThatThrownBy(() -> service.putOnLeave(10L))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("DOCTOR_RETIRED");
    }

    @Test
    void retire_sets_status_retired() {
        Doctor d = doctorBuilder().id(10L).status(DoctorStatus.ACTIVE).build();
        when(repository.findById(10L)).thenReturn(Optional.of(d));
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.retire(10L);
        assertThat(d.getStatus()).isEqualTo(DoctorStatus.RETIRED);
    }

    @Test
    void retire_is_idempotent() {
        Doctor d = doctorBuilder().id(10L).status(DoctorStatus.RETIRED).build();
        when(repository.findById(10L)).thenReturn(Optional.of(d));
        service.retire(10L);
        verify(repository, never()).save(any());
    }

    @Test
    void reactivate_changes_status_to_active() {
        Doctor d = doctorBuilder().id(10L).status(DoctorStatus.ON_LEAVE).build();
        when(repository.findById(10L)).thenReturn(Optional.of(d));
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reactivate(10L);
        assertThat(d.getStatus()).isEqualTo(DoctorStatus.ACTIVE);
    }

    @Test
    void reactivate_from_retired_changes_status_to_active() {
        // Décision 9.7 : la sortie de retraite est autorisée via reactivate (UI ADMIN).
        Doctor d = doctorBuilder().id(10L).status(DoctorStatus.RETIRED).build();
        when(repository.findById(10L)).thenReturn(Optional.of(d));
        when(repository.save(any(Doctor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reactivate(10L);
        assertThat(d.getStatus()).isEqualTo(DoctorStatus.ACTIVE);
    }

    @Test
    void reactivate_is_idempotent_when_already_active() {
        Doctor d = doctorBuilder().id(10L).status(DoctorStatus.ACTIVE).build();
        when(repository.findById(10L)).thenReturn(Optional.of(d));
        service.reactivate(10L);
        verify(repository, never()).save(any());
    }

    @Test
    void delete_removes_doctor() {
        when(repository.existsById(1L)).thenReturn(true);
        service.delete(1L);
        verify(repository).deleteById(1L);
    }

    @Test
    void delete_throws_when_unknown() {
        when(repository.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateLicense_format_and_padding() {
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(7));
        when(repository.existsByLicenseNumber(anyString())).thenReturn(false);

        String license = service.generateLicenseNumber();
        assertThat(license).matches("MED-\\d{4}-00008");
    }

    @Test
    void generateLicense_starts_at_one_for_new_year() {
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.empty());
        when(repository.existsByLicenseNumber(anyString())).thenReturn(false);
        assertThat(service.generateLicenseNumber()).matches("MED-\\d{4}-00001");
    }

    @Test
    void generateLicense_throws_on_race_condition() {
        when(repository.findMaxSequenceForYear(anyString())).thenReturn(Optional.of(0));
        when(repository.existsByLicenseNumber(anyString())).thenReturn(true);
        assertThatThrownBy(() -> service.generateLicenseNumber())
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("LICENSE_GENERATION_CONFLICT");
    }

    /**
     * Builder par défaut — pré-rempli avec un {@code licenseNumber} bidon.
     *
     * <p>Le {@code licenseNumber} est nécessaire pour tous les flux qui
     * publient un événement Avro ({@code DoctorUpdated} / {@code DoctorStatusChanged})
     * — le schéma Avro refuse les valeurs null sur ce champ. Les tests qui
     * souhaitent un n° d'ordre spécifique le surchargent via
     * {@code .licenseNumber("MED-2026-XXXXX")}.
     */
    private static Doctor.DoctorBuilder doctorBuilder() {
        return Doctor.builder()
                .licenseNumber("MED-2026-99999")
                .lastName("Default")
                .firstName("Default")
                .specialty(Specialty.GENERAL_MEDICINE)
                .yearsOfExperience(0)
                .consultationFee(new BigDecimal("10000"))
                .consultationFeeCurrency(Currency.XOF)
                .status(DoctorStatus.ACTIVE)
                .version(0L);
    }
}
