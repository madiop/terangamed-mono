package com.terangamed.patient.config;

import com.terangamed.common.config.JpaAuditingAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Configuration JPA du patient-service.
 *
 * <p><b>Pourquoi cette classe (et pas {@code @EnableJpaAuditing} sur la classe
 * {@code PatientServiceApplication}) ?</b><br>
 * Lorsque {@code @EnableJpaAuditing} est sur la classe principale, il est traité
 * par TOUS les contextes de test, y compris ceux des slices comme
 * {@code @WebMvcTest} qui ne chargent PAS l'infrastructure JPA. Le bean
 * {@code jpaAuditingHandler} tente alors de wire {@code jpaMappingContext}
 * inexistant → contexte fail au démarrage.
 *
 * <p>En isolant {@code @EnableJpaAuditing} dans une classe {@code @Configuration}
 * dédiée :
 * <ul>
 *   <li>en prod / {@code @SpringBootTest} → cette config est chargée par le
 *       component scan, l'auditing est actif</li>
 *   <li>en {@code @DataJpaTest} → ajouter {@code @Import(JpaConfig.class)} pour
 *       activer l'auditing avec un contexte JPA présent</li>
 *   <li>en {@code @WebMvcTest} → la classe est filtrée par le slice (pas une
 *       classe web), donc {@code @EnableJpaAuditing} n'est pas traité — pas
 *       d'erreur sur {@code jpaMappingContext}</li>
 * </ul>
 *
 * <p>Le bean {@code terangamedAuditorAware} référencé est fourni par
 * {@link JpaAuditingAutoConfiguration} de {@code common-lib}.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = JpaAuditingAutoConfiguration.AUDITOR_AWARE_BEAN_NAME)
public class JpaConfig {
}
