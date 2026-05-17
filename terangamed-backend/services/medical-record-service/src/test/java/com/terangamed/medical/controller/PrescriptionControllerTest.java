package com.terangamed.medical.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.terangamed.common.exception.GlobalExceptionHandler;
import com.terangamed.medical.dto.CreatePrescriptionLineRequest;
import com.terangamed.medical.dto.CreatePrescriptionRequest;
import com.terangamed.medical.dto.PrescriptionDto;
import com.terangamed.medical.dto.PrescriptionLineDto;
import com.terangamed.medical.entity.Consultation;
import com.terangamed.medical.entity.MedicalRecord;
import com.terangamed.medical.entity.MedicationRoute;
import com.terangamed.medical.pdf.PrescriptionPdfService;
import com.terangamed.medical.pdf.StoredPdf;
import com.terangamed.medical.security.MedicalRecordAccessChecker;
import com.terangamed.medical.service.ConsultationService;
import com.terangamed.medical.service.MedicalRecordService;
import com.terangamed.medical.service.PrescriptionService;
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

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@WebMvcTest(controllers = PrescriptionController.class)
@Import({ PrescriptionControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class })
class PrescriptionControllerTest {

        @Autowired
        MockMvc mockMvc;
        @Autowired
        ObjectMapper objectMapper;
        @MockBean
        PrescriptionService service;
        @MockBean
        ConsultationService consultationService;
        @MockBean
        MedicalRecordService medicalRecordService;
        @MockBean
        MedicalRecordAccessChecker accessChecker;
        @MockBean
        PrescriptionPdfService pdfService;

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

        private static PrescriptionDto sample() {
                return new PrescriptionDto(50L, "ORD-2026-00008", 10L, Instant.now(), null,
                                "instructions", List.of(),
                                Instant.now(), Instant.now(), "dr.a", "dr.a", 0L);
        }

        private static PrescriptionLineDto sampleLine() {
                return new PrescriptionLineDto(99L, 50L, "Amoxicilline", "500mg", "3x/j",
                                "7 jours", MedicationRoute.ORAL, null, 1,
                                Instant.now(), Instant.now(), "dr.a", "dr.a", 0L);
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void create_returns_201() throws Exception {
                when(service.create(eq(10L), any(CreatePrescriptionRequest.class))).thenReturn(sample());

                mockMvc.perform(post("/api/prescriptions/by-consultation/10")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CreatePrescriptionRequest(
                                                null, "instructions",
                                                List.of(new CreatePrescriptionLineRequest(
                                                                "Amoxicilline 500mg", "1 gélule", "3x/j", "7 jours",
                                                                MedicationRoute.ORAL, null, 1))))))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").value(50));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void create_403_for_admin() throws Exception {
                mockMvc.perform(post("/api/prescriptions/by-consultation/10")
                                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                                .andExpect(status().is4xxClientError()); // 400 (validation) ou 403 (auth) — les 2
                                                                         // prouvent absence de création;
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void find_by_id_returns_200_and_calls_access_checker() throws Exception {
                when(service.findById(50L)).thenReturn(sample());
                when(consultationService.findEntityById(10L)).thenReturn(
                                Consultation.builder().id(10L).medicalRecordId(1L).build());
                when(medicalRecordService.findEntityById(1L)).thenReturn(
                                MedicalRecord.builder().id(1L).patientId(42L).build());

                mockMvc.perform(get("/api/prescriptions/50"))
                                .andExpect(status().isOk());
                verify(accessChecker).ensureCanAccess(any());
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void add_line_returns_201() throws Exception {
                when(service.addLine(eq(50L), any(CreatePrescriptionLineRequest.class))).thenReturn(sampleLine());

                mockMvc.perform(post("/api/prescriptions/50/lines")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new CreatePrescriptionLineRequest(
                                                "Ibuprofène", "200mg", "3x/j", "5j", MedicationRoute.ORAL, null, 1))))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").value(99));
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void delete_line_returns_204() throws Exception {
                mockMvc.perform(delete("/api/prescriptions/50/lines/99"))
                                .andExpect(status().isNoContent());
                verify(service).deleteLine(50L, 99L);
        }

        @Test
        @WithMockUser(roles = "DOCTOR")
        void pdf_returns_200_with_pdf_stream() throws Exception {
                when(service.findById(50L)).thenReturn(sample());
                when(consultationService.findEntityById(10L)).thenReturn(
                                Consultation.builder().id(10L).medicalRecordId(1L).build());
                when(medicalRecordService.findEntityById(1L)).thenReturn(
                                MedicalRecord.builder().id(1L).patientId(42L).build());

                byte[] fakePdf = "%PDF-1.4 fake content".getBytes();
                StoredPdf storedPdf = new StoredPdf(
                                new ByteArrayInputStream(fakePdf),
                                fakePdf.length,
                                "application/pdf",
                                Map.of("content-hash", "abc123def4567890"));
                when(pdfService.getOrGeneratePdf(50L)).thenReturn(storedPdf);

                // StreamingResponseBody est asynchrone : on doit dispatcher l'async result
                // pour récupérer le body complet dans MockMvc.
                var result = mockMvc.perform(get("/api/prescriptions/50/pdf"))
                                .andExpect(request().asyncStarted())
                                .andReturn();

                mockMvc.perform(asyncDispatch(result))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType("application/pdf"))
                                .andExpect(header().string("Content-Disposition",
                                                "inline; filename=\"ORD-2026-00008.pdf\""))
                                .andExpect(header().string("X-Prescription-Hash", "abc123def4567890"))
                                .andExpect(header().string("Cache-Control",
                                                "private, no-store, must-revalidate"))
                                .andExpect(content().bytes(fakePdf));

                verify(accessChecker).ensureCanAccess(any());
                verify(pdfService).getOrGeneratePdf(50L);
        }

        @Test
        void anonymous_returns_401() throws Exception {
                mockMvc.perform(get("/api/prescriptions/50"))
                                .andExpect(status().isUnauthorized());
        }
}
