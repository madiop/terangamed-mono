package com.terangamed.common.config;

import com.terangamed.common.audit.JwtAuditorAware;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Auto-configuration de l'auditing JPA TerangaMed.
 *
 * <p><b>Ce que cette classe fait</b> : enregistre un bean
 * {@link AuditorAware AuditorAware&lt;String&gt;} (par défaut {@link JwtAuditorAware})
 * que les services peuvent référencer dans leur annotation {@code @EnableJpaAuditing}.
 *
 * <p><b>Ce que cette classe NE fait PAS</b> : elle ne porte pas {@code @EnableJpaAuditing}.
 * <br>Pourquoi ? Parce que {@code @EnableJpaAuditing} déclenche la création immédiate
 * du {@code JpaMetamodelMappingContext} qui exige qu'au moins une entité JPA soit déclarée.
 * Dans une bibliothèque utilitaire (sans entités) ou dans des tests sans
 * {@code EntityManagerFactory}, cette annotation ferait échouer le démarrage avec
 * « JPA metamodel must not be empty ». Pour rester safe, on délègue l'activation
 * au service consommateur.
 *
 * <p><b>Comment activer l'auditing dans un microservice TerangaMed</b> :
 * <pre>
 * &#064;SpringBootApplication
 * &#064;EnableJpaAuditing(auditorAwareRef = "terangamedAuditorAware")
 * public class PatientServiceApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(PatientServiceApplication.class, args);
 *     }
 * }
 * </pre>
 *
 * <p><b>Désactivation</b> : positionner {@code terangamed.jpa.auditing.enabled=false}
 * (par exemple en environnement de test pour utiliser un autre {@code AuditorAware}).
 */
@AutoConfiguration
@ConditionalOnClass(AuditingEntityListener.class)
@ConditionalOnProperty(prefix = "terangamed.jpa.auditing", name = "enabled", matchIfMissing = true)
public class JpaAuditingAutoConfiguration {

    /**
     * Nom du bean utilisé comme {@code auditorAwareRef} dans
     * {@code @EnableJpaAuditing(auditorAwareRef = "terangamedAuditorAware")}.
     */
    public static final String AUDITOR_AWARE_BEAN_NAME = "terangamedAuditorAware";

    @Bean(name = AUDITOR_AWARE_BEAN_NAME)
    @ConditionalOnMissingBean(name = AUDITOR_AWARE_BEAN_NAME)
    public AuditorAware<String> terangamedAuditorAware() {
        return new JwtAuditorAware();
    }
}
