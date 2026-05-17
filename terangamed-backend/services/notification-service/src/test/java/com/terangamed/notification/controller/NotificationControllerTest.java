package com.terangamed.notification.controller;

import com.terangamed.common.exception.GlobalExceptionHandler;
import com.terangamed.common.exception.ResourceNotFoundException;
import com.terangamed.common.pagination.PageResponse;
import com.terangamed.notification.dto.NotificationDto;
import com.terangamed.notification.entity.NotificationStatus;
import com.terangamed.notification.service.NotificationService;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationController.class)
@Import({NotificationControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean NotificationService service;

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

    private static NotificationDto sample() {
        return new NotificationDto(1L, UUID.randomUUID(), "terangamed.patient.events",
                "patient.created", "Patient", "42",
                "{\"patientId\":42}", NotificationStatus.RECEIVED,
                Instant.now(), null, null, Instant.now(), Instant.now(), 0L);
    }

    @Test
    void unauthenticated_returns_401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "DOCTOR")
    void doctor_role_is_forbidden() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_can_search() throws Exception {
        when(service.search(any(), any())).thenReturn(
                PageResponse.<NotificationDto>builder()
                        .content(List.of(sample())).page(0).size(20)
                        .totalElements(1).totalPages(1).first(true).last(true).build());

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void admin_can_get_by_id() throws Exception {
        when(service.findById(1L)).thenReturn(Optional.of(sample()));

        mockMvc.perform(get("/api/notifications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("patient.created"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void get_by_id_returns_404_when_unknown() throws Exception {
        when(service.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/notifications/999"))
                .andExpect(status().isNotFound());
    }
}
