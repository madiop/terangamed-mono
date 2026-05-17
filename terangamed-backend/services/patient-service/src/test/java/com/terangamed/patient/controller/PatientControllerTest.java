package com.terangamed.patient.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.GlobalExceptionHandler;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.patient.dto.CreatePatientRequest;
import com.terangamed.patient.dto.PatientDto;
import com.terangamed.patient.dto.UpdatePatientRequest;
import com.terangamed.patient.entity.BloodGroup;
import com.terangamed.patient.entity.Civility;
import com.terangamed.patient.entity.Gender;
import com.terangamed.patient.entity.PatientStatus;
import com.terangamed.patient.service.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'intégration MockMvc du {@link PatientController}.
 *
 * <p><b>Setup</b> :
 * <ul>
 *   <li>{@code @WebMvcTest} — slice web (rapide, contexte minimal)</li>
 *   <li>{@link TestSecurityConfig} — chaîne de sécurité simplifiée avec
 *       {@code @EnableMethodSecurity} pour tester les {@code @PreAuthorize},
 *       HTTP Basic comme entry-point pour les 401, sans la complexité du
 *       Resource Server JWT (déjà testé en common-lib)</li>
 *   <li>{@code @WithMockUser(roles = "...")} — simule un utilisateur authentifié
 *       avec les rôles voulus, plus simple que mocker un JWT complet</li>
 *   <li>{@link GlobalExceptionHandler} importé — vérifie le mapping ResourceNotFound→404,
 *       Conflict→409, Validation→400</li>
 * </ul>
 *
 * <p>La sécurité « réelle » (Resource Server JWT, extraction des rôles Keycloak)
 * est testée en {@code common-lib/JwtAuthConverterTest} — pas besoin de la
 * répliquer ici.
 */
@WebMvcTest(controllers = PatientController.class)
@Import({PatientControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
class PatientControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PatientService service;

    /**
     * Configuration de sécurité de test : @EnableMethodSecurity (pour @PreAuthorize)
     * + chaîne minimale qui exige authentification + HTTP Basic comme entry-point
     * pour produire des 401 propres lors des tests sans @WithMockUser.
     */
    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }
    }

    // ─────────────────────────── GET /api/patients ───────────────────────────

    @Test
    void search_should_return_401_without_auth() throws Exception {
        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void search_should_return_200_with_authenticated_staff() throws Exception {
        when(service.search(any(), any())).thenReturn(
                PageResponse.<PatientDto>builder()
                        .content(List.of(samplePatientDto(1L, "Diop", "Fatou")))
                        .page(0).size(20).totalElements(1).totalPages(1).first(true).last(true)
                        .build());

        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lastName").value("Diop"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ─────────────────────────── GET /api/patients/{id} ───────────────────────────

    @Test
    @WithMockUser(roles = "DOCTOR")
    void findById_should_return_404_when_unknown() throws Exception {
        when(service.findById(999L)).thenThrow(new ResourceNotFoundException("Patient", 999L));

        mockMvc.perform(get("/api/patients/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/patients/999"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void findById_should_return_200_when_found() throws Exception {
        when(service.findById(42L)).thenReturn(samplePatientDto(42L, "Martin", "Jean"));

        mockMvc.perform(get("/api/patients/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.lastName").value("Martin"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void findByMrn_should_return_200() throws Exception {
        when(service.findByMedicalRecordNumber("MR-2026-00001"))
                .thenReturn(samplePatientDto(1L, "Diop", "Fatou"));

        mockMvc.perform(get("/api/patients/by-mrn/MR-2026-00001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.medicalRecordNumber").value("MR-2026-00001"));
    }

    // ─────────────────────────── POST /api/patients ───────────────────────────

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_should_return_201_with_location_header() throws Exception {
        PatientDto created = samplePatientDto(100L, "Sow", "Aminata");
        when(service.create(any(CreatePatientRequest.class))).thenReturn(created);

        CreatePatientRequest body = new CreatePatientRequest(
                Civility.MME, "Sow", "Aminata", LocalDate.of(1990, 5, 1), Gender.FEMALE,
                "0177000005", "aminata.sow@example.sn",
                null, null, null, "Dakar", "Sénégal", BloodGroup.O_POS,
                null, null, null);

        mockMvc.perform(post("/api/patients")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/patients/100"))
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_should_return_400_when_required_fields_missing() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_should_return_409_when_email_conflict() throws Exception {
        when(service.create(any(CreatePatientRequest.class)))
                .thenThrow(new ConflictException("PATIENT_EMAIL_DUPLICATE",
                        "Un patient avec l'email 'x@y.sn' existe déjà"));

        CreatePatientRequest body = new CreatePatientRequest(
                Civility.M, "Test", "User", LocalDate.of(1980, 1, 1), Gender.MALE,
                null, "x@y.sn", null, null, null, null, null, null, null, null, null);

        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATIENT_EMAIL_DUPLICATE"));
    }

    @Test
    void create_should_return_401_without_auth() throws Exception {
        mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────── PUT /api/patients/{id} ───────────────────────────

    @Test
    @WithMockUser(roles = "DOCTOR")
    void update_should_return_200() throws Exception {
        when(service.update(eq(5L), any(UpdatePatientRequest.class)))
                .thenReturn(samplePatientDto(5L, "Updated", "Name"));

        UpdatePatientRequest body = new UpdatePatientRequest(
                null, "Updated", null, null, null, "0100000000", null,
                null, null, null, null, null, null, null, null, null, null);

        mockMvc.perform(put("/api/patients/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Updated"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void update_should_return_404_when_unknown() throws Exception {
        when(service.update(eq(999L), any(UpdatePatientRequest.class)))
                .thenThrow(new ResourceNotFoundException("Patient", 999L));

        mockMvc.perform(put("/api/patients/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────── POST /api/patients/{id}/archive ───────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void archive_should_return_204_for_admin() throws Exception {
        mockMvc.perform(post("/api/patients/1/archive"))
                .andExpect(status().isNoContent());

        verify(service).archive(1L);
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void archive_should_return_204_for_receptionist() throws Exception {
        mockMvc.perform(post("/api/patients/1/archive"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void archive_should_return_403_for_doctor() throws Exception {
        mockMvc.perform(post("/api/patients/1/archive"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────── DELETE /api/patients/{id} ───────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_should_return_204_for_admin() throws Exception {
        mockMvc.perform(delete("/api/patients/1"))
                .andExpect(status().isNoContent());

        verify(service).delete(1L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void delete_should_return_403_for_doctor() throws Exception {
        mockMvc.perform(delete("/api/patients/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void delete_should_return_403_for_receptionist() throws Exception {
        mockMvc.perform(delete("/api/patients/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_should_return_404_when_unknown() throws Exception {
        doThrow(new ResourceNotFoundException("Patient", 999L)).when(service).delete(999L);

        mockMvc.perform(delete("/api/patients/999"))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static PatientDto samplePatientDto(Long id, String lastName, String firstName) {
        return new PatientDto(
                id, "MR-2026-%05d".formatted(id), Civility.M, lastName, firstName,
                LocalDate.of(1990, 1, 1), Gender.MALE,
                null, null, null, null, null, null, "Sénégal", BloodGroup.UNKNOWN,
                null, null, null, PatientStatus.ACTIVE,
                Instant.now(), Instant.now(), "system", "system", 0L);
    }
}
