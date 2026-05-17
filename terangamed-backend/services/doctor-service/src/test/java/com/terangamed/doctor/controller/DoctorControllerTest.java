package com.terangamed.doctor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terangamed.common.exception.ConflictException;
import com.terangamed.common.exception.GlobalExceptionHandler;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.finance.Currency;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.doctor.dto.CreateDoctorRequest;
import com.terangamed.doctor.dto.DoctorDto;
import com.terangamed.doctor.dto.UpdateDoctorRequest;
import com.terangamed.doctor.entity.DoctorStatus;
import com.terangamed.doctor.entity.Specialty;
import com.terangamed.doctor.service.DoctorService;
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

import java.math.BigDecimal;
import java.time.Instant;
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

@WebMvcTest(controllers = DoctorController.class)
@Import({DoctorControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
class DoctorControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean DoctorService service;

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

    @Test
    void search_returns_401_without_auth() throws Exception {
        mockMvc.perform(get("/api/doctors")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void search_returns_200_for_staff() throws Exception {
        when(service.search(any(), any())).thenReturn(
                PageResponse.<DoctorDto>builder()
                        .content(List.of(sampleDto(1L, "Martin", "Jean")))
                        .page(0).size(20).totalElements(1).totalPages(1).first(true).last(true).build());

        mockMvc.perform(get("/api/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lastName").value("Martin"));
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void searchActive_returns_200() throws Exception {
        when(service.searchActive(any(), any())).thenReturn(
                PageResponse.<DoctorDto>builder()
                        .content(List.of()).page(0).size(20).totalElements(0).totalPages(0)
                        .first(true).last(true).build());

        mockMvc.perform(get("/api/doctors/active"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void findById_returns_404_when_unknown() throws Exception {
        when(service.findById(999L)).thenThrow(new ResourceNotFoundException("Doctor", 999L));

        mockMvc.perform(get("/api/doctors/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void findById_returns_200() throws Exception {
        when(service.findById(42L)).thenReturn(sampleDto(42L, "Sall", "Cheikh"));

        mockMvc.perform(get("/api/doctors/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns_201_with_location() throws Exception {
        DoctorDto created = sampleDto(100L, "Diallo", "Mariama");
        when(service.create(any(CreateDoctorRequest.class))).thenReturn(created);

        CreateDoctorRequest body = new CreateDoctorRequest(
                "Diallo", "Mariama", Specialty.PEDIATRICS,
                "m.diallo@terangamed.local", "+221770100002", null,
                12, new BigDecimal("20000"), Currency.XOF, null, null);

        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/doctors/100"))
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void create_returns_403_for_non_admin() throws Exception {
        CreateDoctorRequest body = new CreateDoctorRequest(
                "X", "Y", Specialty.OTHER, null, null, null, 0, null, null, null, null);

        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns_400_for_validation_error() throws Exception {
        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void create_returns_409_for_email_conflict() throws Exception {
        when(service.create(any(CreateDoctorRequest.class)))
                .thenThrow(new ConflictException("DOCTOR_EMAIL_DUPLICATE", "duplicate"));

        CreateDoctorRequest body = new CreateDoctorRequest(
                "X", "Y", Specialty.OTHER, "x@y.sn", null, null, 0, null, null, null, null);

        mockMvc.perform(post("/api/doctors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCTOR_EMAIL_DUPLICATE"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_returns_200() throws Exception {
        when(service.update(eq(5L), any(UpdateDoctorRequest.class)))
                .thenReturn(sampleDto(5L, "Updated", "Name"));

        UpdateDoctorRequest body = new UpdateDoctorRequest(
                "Updated", null, null, null, null, null, null, null, null, null, null, null);

        mockMvc.perform(put("/api/doctors/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void update_returns_403_for_non_admin() throws Exception {
        mockMvc.perform(put("/api/doctors/5")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void putOnLeave_returns_204() throws Exception {
        mockMvc.perform(post("/api/doctors/1/leave"))
                .andExpect(status().isNoContent());
        verify(service).putOnLeave(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void retire_returns_204() throws Exception {
        mockMvc.perform(post("/api/doctors/1/retire"))
                .andExpect(status().isNoContent());
        verify(service).retire(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reactivate_returns_204() throws Exception {
        mockMvc.perform(post("/api/doctors/1/reactivate"))
                .andExpect(status().isNoContent());
        verify(service).reactivate(1L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void status_endpoints_return_403_for_non_admin() throws Exception {
        mockMvc.perform(post("/api/doctors/1/leave")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/doctors/1/retire")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/doctors/1/reactivate")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns_204() throws Exception {
        mockMvc.perform(delete("/api/doctors/1"))
                .andExpect(status().isNoContent());
        verify(service).delete(1L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void delete_returns_403_for_non_admin() throws Exception {
        mockMvc.perform(delete("/api/doctors/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_returns_404_when_unknown() throws Exception {
        doThrow(new ResourceNotFoundException("Doctor", 999L)).when(service).delete(999L);
        mockMvc.perform(delete("/api/doctors/999")).andExpect(status().isNotFound());
    }

    private static DoctorDto sampleDto(Long id, String lastName, String firstName) {
        return new DoctorDto(
                id, "MED-2026-%05d".formatted(id), lastName, firstName,
                Specialty.GENERAL_MEDICINE,
                "%s.%s@terangamed.local".formatted(firstName.toLowerCase(), lastName.toLowerCase()),
                null, null, 10, new BigDecimal("15000"), Currency.XOF, null,
                DoctorStatus.ACTIVE, null, Instant.now(), Instant.now(), "system", "system", 0L);
    }
}
