package com.terangamed.appointment.controller;

import com.terangamed.appointment.service.AppointmentService;
import com.terangamed.common.exception.GlobalExceptionHandler;
import com.terangamed.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AppointmentController.class)
@Import({AppointmentControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
class AppointmentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AppointmentService service;

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
        mockMvc.perform(get("/api/appointments")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void findById_returns_404_when_unknown() throws Exception {
        doThrow(new ResourceNotFoundException("Appointment", 999L)).when(service).findById(999L);

        mockMvc.perform(get("/api/appointments/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void confirm_allowed_for_staff() throws Exception {
        mockMvc.perform(post("/api/appointments/1/confirm"))
                .andExpect(status().isNoContent());
        verify(service).confirm(1L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void complete_allowed_for_doctor() throws Exception {
        mockMvc.perform(post("/api/appointments/1/complete"))
                .andExpect(status().isNoContent());
        verify(service).complete(1L);
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void complete_forbidden_for_receptionist() throws Exception {
        mockMvc.perform(post("/api/appointments/1/complete"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void no_show_allowed_for_receptionist() throws Exception {
        mockMvc.perform(post("/api/appointments/1/no-show"))
                .andExpect(status().isNoContent());
        verify(service).markNoShow(1L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void no_show_forbidden_for_doctor() throws Exception {
        mockMvc.perform(post("/api/appointments/1/no-show"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void cancel_allowed_for_any_staff() throws Exception {
        mockMvc.perform(post("/api/appointments/1/cancel"))
                .andExpect(status().isNoContent());
        verify(service).cancel(1L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_allowed_for_admin() throws Exception {
        mockMvc.perform(delete("/api/appointments/1"))
                .andExpect(status().isNoContent());
        verify(service).delete(1L);
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void delete_forbidden_for_doctor() throws Exception {
        mockMvc.perform(delete("/api/appointments/1"))
                .andExpect(status().isForbidden());
    }
}
