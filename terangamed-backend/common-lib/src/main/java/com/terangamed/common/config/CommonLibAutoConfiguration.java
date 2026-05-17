package com.terangamed.common.config;

import com.terangamed.common.exception.GlobalExceptionHandler;
import com.terangamed.common.finance.FinanceProperties;
import com.terangamed.common.security.JwtAuthConverterProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * Auto-configuration de {@code common-lib}.
 *
 * <p>Les composants exposés ne sont enregistrés que si les conditions sont remplies :
 * <ul>
 *   <li>{@link GlobalExceptionHandler} : uniquement si le module dépend de Spring MVC</li>
 *   <li>{@link JwtAuthConverterProperties} : toujours (lecture du préfixe {@code terangamed.security.jwt})</li>
 * </ul>
 *
 * <p>Référencée par :
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 */
@AutoConfiguration
@EnableConfigurationProperties({JwtAuthConverterProperties.class, FinanceProperties.class})
public class CommonLibAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(DispatcherServlet.class)
    public GlobalExceptionHandler terangamedGlobalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
