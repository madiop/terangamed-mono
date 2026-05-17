package com.terangamed.medical.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.GlobalExceptionHandler;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.medical.dto.ConsultationDto;
import com.terangamed.medical.dto.CreateConsultationRequest;
import com.terangamed.medical.dto.DoctorSnapshotDto;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.security.CurrentUserProvider;
import com.terangamed.medical.security.MedicalRecordAccessChecker;
import com.terangamed.medical.service.ConsultationService;
import com.terangamed.medical.service.MedicalRecordService;
import com.terangamed.medical.service.RemoteLookupService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ConsultationController.class)
@Import({ ConsultationControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class })
class ConsultationControllerTest {

        @Autowired
        MockMvc mockMvc;
        @Autowired
        ObjectMapper objectMapper;
        @MockBean
        ConsultationService service;
        @MockBean
        MedicalRecordService medicalRecordService;
        @MockBean
        RemoteLookupService remoteLookup;
        @MockBean
        CurrentUserProvider currentUser;
        @MockBean
        MedicalRecordAccessChecker accessChecker;

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

                @Bean
                ObjectMapper objectMapper() {
                        return new ObjectMapper().registerModule(new JavaTimeModule());
                }
        }

        private static ConsultationDto sample() {
                return new ConsultationDto(5L, 1L, 101L, null, LocalDateTime.now(),
                                "Toux", null, null, "Bronchite", null, null,
                                null, false, null, null, false, null, null,
                                Instant.now(), Instant.now(), "dr.a", "dr.a", 0L);
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void search_returns_200() throws Exception {
                when(service.search(any(), any())).thenReturn(
                                PageResponse.<ConsultationDto>builder()
                                                .content(List.of(sample())).page(0).size(20)
                                                .totalElements(1).totalPages(1).first(true).last(true).build());

                mockMvc.perform(get("/api/consultations"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content[0].id").value(5));
        }

        @Test
        @WithMockUser(roles = "PATIENT")
        void search_403_for_patient() throws Exception {
                mockMvc.perform(get("/api/consultations"))
                                .andExpect(status().isForbidden());
        }

        private static final UUID DR_SALL_SUB = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaa00000099");

        private static String body() throws Exception {
                return new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(
                                new CreateConsultationRequest(1L, null, LocalDateTime.now(), "Toux",
                                                null, null, null, null, null, null));
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void create_201_when_jwt_subject_resolves_doctor() throws Exception {
                when(currentUser.subject()).thenReturn(DR_SALL_SUB.toString());
                when(remoteLookup.fetchDoctorByKeycloakSubject(DR_SALL_SUB)).thenReturn(
                                new DoctorSnapshotDto(101L, "MED", "Sall", "Cheikh", "X", "ACTIVE"));
                when(service.create(any(CreateConsultationRequest.class), eq(101L))).thenReturn(sample());

                mockMvc.perform(post("/api/consultations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body()))
                                .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void create_409_when_jwt_subject_missing() throws Exception {
                when(currentUser.subject()).thenReturn(null);

                mockMvc.perform(post("/api/consultations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body()))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("JWT_SUBJECT_MISSING"));
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void create_409_when_jwt_subject_not_uuid() throws Exception {
                when(currentUser.subject()).thenReturn("not-a-uuid");

                mockMvc.perform(post("/api/consultations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body()))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("JWT_SUBJECT_INVALID"));
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void create_404_when_no_doctor_linked_to_keycloak_account() throws Exception {
                when(currentUser.subject()).thenReturn(DR_SALL_SUB.toString());
                when(remoteLookup.fetchDoctorByKeycloakSubject(DR_SALL_SUB))
                                .thenThrow(new ResourceNotFoundException(
                                                "Aucun médecin lié au compte Keycloak '" + DR_SALL_SUB + "'"));

                mockMvc.perform(post("/api/consultations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body()))
                                .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void create_409_when_doctor_inactive() throws Exception {
                when(currentUser.subject()).thenReturn(DR_SALL_SUB.toString());
                when(remoteLookup.fetchDoctorByKeycloakSubject(DR_SALL_SUB)).thenReturn(
                                new DoctorSnapshotDto(101L, "MED", "X", "Y", "Z", "RETIRED"));

                mockMvc.perform(post("/api/consultations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body()))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("DOCTOR_NOT_ACTIVE"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void create_403_for_admin() throws Exception {
                mockMvc.perform(post("/api/consultations")
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andExpect(status().is4xxClientError()); // 400 (validation) ou 403 (auth) — les 2
                                                                         // prouvent absence de création;
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void update_propagates_conflict_when_signed() throws Exception {
                when(service.update(eq(5L), any())).thenThrow(
                                new ConflictException("CONSULTATION_SIGNED", "signed"));

                mockMvc.perform(put("/api/consultations/5")
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code").value("CONSULTATION_SIGNED"));
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void sign_returns_200() throws Exception {
                when(service.sign(5L)).thenReturn(sample());

                mockMvc.perform(post("/api/consultations/5/sign"))
                                .andExpect(status().isOk());
                verify(service).sign(5L);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void delete_returns_204_for_admin() throws Exception {
                mockMvc.perform(delete("/api/consultations/5"))
                                .andExpect(status().isNoContent());
                verify(service).softDelete(5L);
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void delete_403_for_doctor() throws Exception {
                mockMvc.perform(delete("/api/consultations/5"))
                                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "PATIENT")
        void find_by_id_with_patient_role_calls_access_checker() throws Exception {
                Consultation c = Consultation.builder()
                                .id(5L).medicalRecordId(1L).build();
                MedicalRecord record = MedicalRecord.builder().id(1L).patientId(42L).build();
                when(service.findEntityById(5L)).thenReturn(c);
                when(medicalRecordService.findEntityById(1L)).thenReturn(record);
                when(service.findById(5L)).thenReturn(sample());

                mockMvc.perform(get("/api/consultations/5"))
                                .andExpect(status().isOk());

                verify(accessChecker).ensureCanAccess(record);
        }
}
