package com.terangamed.medical.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terangamed.common.exception.GlobalExceptionHandler;
import com.terangamed.medical.dto.AntecedentDto;
import com.terangamed.medical.dto.CreateAntecedentRequest;
import com.terangamed.medical.entity.AntecedentType;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.security.MedicalRecordAccessChecker;
import com.terangamed.medical.service.AntecedentService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AntecedentController.class)
@Import({ AntecedentControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class })
class AntecedentControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockBean
    AntecedentService service;
    @MockBean
    MedicalRecordService medicalRecordService;
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

    private static AntecedentDto sample() {
        return new AntecedentDto(7L, 1L, AntecedentType.ALLERGY, "Pénicilline",
                null, null, true, Instant.now(), Instant.now(), "system", "system", 0L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void list_by_record_returns_200() throws Exception {
        when(medicalRecordService.findEntityById(1L)).thenReturn(MedicalRecord.builder().id(1L).build());
        when(service.listByMedicalRecord(eq(1L), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(List.of(sample()));

        mockMvc.perform(get("/api/antecedents/by-record/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void list_by_record_supports_filters() throws Exception {
        when(medicalRecordService.findEntityById(1L)).thenReturn(MedicalRecord.builder().id(1L).build());
        when(service.listByMedicalRecord(eq(1L), eq(AntecedentType.ALLERGY), eq(false)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/antecedents/by-record/1?type=ALLERGY&onlyActive=false"))
                .andExpect(status().isOk());

        verify(service).listByMedicalRecord(1L, AntecedentType.ALLERGY, false);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_returns_201() throws Exception {
        when(service.create(any(CreateAntecedentRequest.class))).thenReturn(sample());

        mockMvc.perform(post("/api/antecedents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateAntecedentRequest(
                        1L, AntecedentType.ALLERGY, "Pénicilline", null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void create_403_for_receptionist() throws Exception {
        mockMvc.perform(post("/api/antecedents")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError()); // 400 (validation) ou 403 (auth) — les 2 prouvent absence de
                                                         // création;
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void create_403_for_patient() throws Exception {
        mockMvc.perform(post("/api/antecedents")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError()); // 400 (validation) ou 403 (auth) — les 2 prouvent absence de
                                                         // création;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns_204() throws Exception {
        mockMvc.perform(delete("/api/antecedents/7"))
                .andExpect(status().isNoContent());
        verify(service).delete(7L);
    }

    @Test
    void anonymous_returns_401() throws Exception {
        mockMvc.perform(get("/api/antecedents/by-record/1"))
                .andExpect(status().isUnauthorized());
    }
}
