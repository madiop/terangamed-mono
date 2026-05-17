package com.terangamed.notification.config;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuration JPA isolée — pattern identique aux autres services.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = JpaAuditingAutoConfiguration.AUDITOR_AWARE_BEAN_NAME)
public class JpaConfig {
}
