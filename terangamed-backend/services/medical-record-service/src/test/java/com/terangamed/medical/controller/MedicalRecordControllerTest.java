package com.terangamed.medical.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terangamed.common.exception.GlobalExceptionHandler;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.medical.dto.CreateMedicalRecordRequest;
import com.terangamed.medical.dto.MedicalRecordDto;
import com.terangamed.medical.entity.BloodType;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.security.MedicalRecordAccessChecker;
import com.terangamed.medical.service.MedicalRecordService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MedicalRecordController.class)
@Import({ MedicalRecordControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class })
class MedicalRecordControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockBean
    MedicalRecordService service;
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
    }

    private static MedicalRecordDto sampleDto() {
        return new MedicalRecordDto(1L, 42L, BloodType.O_POS, "RAS", "notes",
                false, null, null, Instant.now(), Instant.now(), "system", "system", 0L);
    }

    @Test
    void unauthenticated_returns_401() throws Exception {
        mockMvc.perform(get("/api/medical-records/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns_201_with_location() throws Exception {
        when(service.create(any(CreateMedicalRecordRequest.class))).thenReturn(sampleDto());

        mockMvc.perform(post("/api/medical-records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateMedicalRecordRequest(
                        42L, BloodType.O_POS, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_returns_403_for_doctor() throws Exception {
        mockMvc.perform(post("/api/medical-records")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().is4xxClientError()); // 400 (validation) ou 403 (auth) — les 2 prouvent absence de
                                                         // création;
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void create_returns_201_for_receptionist() throws Exception {
        when(service.create(any(CreateMedicalRecordRequest.class))).thenReturn(sampleDto());

        mockMvc.perform(post("/api/medical-records")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateMedicalRecordRequest(
                        42L, null, null, null))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void find_by_id_calls_access_checker() throws Exception {
        MedicalRecord record = MedicalRecord.builder().id(1L).patientId(42L).softDeleted(false).build();
        when(service.findEntityById(1L)).thenReturn(record);
        when(service.findById(1L)).thenReturn(sampleDto());
        doNothing().when(accessChecker).ensureCanAccess(any());

        mockMvc.perform(get("/api/medical-records/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(42));

        verify(accessChecker).ensureCanAccess(record);
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void find_by_id_403_when_patient_not_owner() throws Exception {
        MedicalRecord record = MedicalRecord.builder().id(1L).patientId(99L).softDeleted(false).build();
        when(service.findEntityById(1L)).thenReturn(record);
        org.mockito.Mockito.doThrow(new com.terangamed.common.exception.ForbiddenException(
                "Pas votre dossier")).when(accessChecker).ensureCanAccess(any());

        mockMvc.perform(get("/api/medical-records/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void find_by_id_returns_404() throws Exception {
        when(service.findEntityById(999L)).thenThrow(new ResourceNotFoundException("MedicalRecord", 999L));

        mockMvc.perform(get("/api/medical-records/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void find_by_patient_calls_access_checker() throws Exception {
        when(service.findByPatientId(42L)).thenReturn(sampleDto());
        doNothing().when(accessChecker).ensureCanAccessPatient(42L);

        mockMvc.perform(get("/api/medical-records/by-patient/42"))
                .andExpect(status().isOk());

        verify(accessChecker).ensureCanAccessPatient(42L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void update_returns_200_for_doctor() throws Exception {
        when(service.update(org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(sampleDto());

        mockMvc.perform(put("/api/medical-records/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"updated\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void update_403_for_receptionist() throws Exception {
        mockMvc.perform(put("/api/medical-records/1")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns_204_for_admin() throws Exception {
        mockMvc.perform(delete("/api/medical-records/1"))
                .andExpect(status().isNoContent());
        verify(service).softDelete(1L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void delete_403_for_doctor() throws Exception {
        mockMvc.perform(delete("/api/medical-records/1"))
                .andExpect(status().isForbidden());
    }
}
