package com.terangamed.doctor.config;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuration JPA isolée — voir patient-service/JpaConfig pour la justification
 * (éviter que {@code @EnableJpaAuditing} sur la classe Application fasse crasher
 * les slices {@code @WebMvcTest} sur le bean {@code jpaMappingContext}).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = JpaAuditingAutoConfiguration.AUDITOR_AWARE_BEAN_NAME)
public class JpaConfig {
}
