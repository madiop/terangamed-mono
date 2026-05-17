package com.terangamed.common.config;

import com.terangamed.common.exception.GlobalExceptionHandler;
import com.terangamed.common.security.JwtAuthConverterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CommonLibAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonLibAutoConfiguration.class));

    @Test
    void should_register_global_exception_handler_when_dispatcher_servlet_present() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(ctx).hasSingleBean(JwtAuthConverterProperties.class);
        });
    }

    @Test
    void should_bind_jwt_auth_converter_properties_from_config() {
        runner.withPropertyValues(
                        "terangamed.security.jwt.principal-attribute=email",
                        "terangamed.security.jwt.resource-id=my-client")
                .run(ctx -> {
                    JwtAuthConverterProperties props = ctx.getBean(JwtAuthConverterProperties.class);
                    assertThat(props.getPrincipalAttribute()).isEqualTo("email");
                    assertThat(props.getResourceId()).isEqualTo("my-client");
                });
    }

    @Test
    void should_use_defaults_when_properties_not_set() {
        runner.run(ctx -> {
            JwtAuthConverterProperties props = ctx.getBean(JwtAuthConverterProperties.class);
            assertThat(props.getPrincipalAttribute()).isEqualTo("preferred_username");
            assertThat(props.getResourceId()).isNull();
        });
    }

    @Test
    void should_back_off_when_user_already_provides_handler() {
        runner.withUserConfiguration(CustomHandlerConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(ctx.getBean(GlobalExceptionHandler.class))
                            .isInstanceOf(CustomHandlerConfig.MyHandler.class);
                });
    }

    static class CustomHandlerConfig {
        @org.springframework.context.annotation.Bean
        GlobalExceptionHandler myHandler() {
            return new MyHandler();
        }

        static class MyHandler extends GlobalExceptionHandler {
        }
    }

    @org.junit.jupiter.api.Nested
    class JpaAuditingAutoConfig {

        private final ApplicationContextRunner jpaRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JpaAuditingAutoConfiguration.class));

        @Test
        void should_register_default_auditor_aware_with_expected_bean_name() {
            jpaRunner.run(ctx -> {
                assertThat(ctx).hasSingleBean(org.springframework.data.domain.AuditorAware.class);
                assertThat(ctx.containsBean(JpaAuditingAutoConfiguration.AUDITOR_AWARE_BEAN_NAME))
                        .isTrue();
            });
        }

        @Test
        void should_back_off_when_disabled_by_property() {
            jpaRunner.withPropertyValues("terangamed.jpa.auditing.enabled=false")
                    .run(ctx -> assertThat(ctx)
                            .doesNotHaveBean(org.springframework.data.domain.AuditorAware.class));
        }

        @Test
        void should_back_off_when_user_provides_their_own_auditor_aware() {
            jpaRunner.withUserConfiguration(CustomAuditorConfig.class)
                    .run(ctx -> {
                        assertThat(ctx).hasSingleBean(org.springframework.data.domain.AuditorAware.class);
                        org.springframework.data.domain.AuditorAware<?> aw = ctx
                                .getBean(JpaAuditingAutoConfiguration.AUDITOR_AWARE_BEAN_NAME,
                                        org.springframework.data.domain.AuditorAware.class);
                        assertThat(aw.getCurrentAuditor()).isEqualTo(java.util.Optional.of("system"));
                    });
        }
    }

    static class CustomAuditorConfig {
        @org.springframework.context.annotation.Bean(
                name = JpaAuditingAutoConfiguration.AUDITOR_AWARE_BEAN_NAME)
        org.springframework.data.domain.AuditorAware<String> myAuditor() {
            return () -> java.util.Optional.of("system");
        }
    }
}
