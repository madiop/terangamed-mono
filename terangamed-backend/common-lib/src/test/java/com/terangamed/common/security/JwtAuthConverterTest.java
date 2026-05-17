package com.terangamed.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthConverterTest {

    private JwtAuthConverter converter;

    @BeforeEach
    void setUp() {
        JwtAuthConverterProperties props = new JwtAuthConverterProperties();
        props.setResourceId("terangamed-backend");
        props.setPrincipalAttribute("preferred_username");
        converter = new JwtAuthConverter(props);
    }

    @Test
    void should_extract_realm_roles_with_role_prefix() {
        Jwt jwt = baseJwt()
                .claim("realm_access", Map.of("roles", List.of("ADMIN", "DOCTOR")))
                .build();

        JwtAuthenticationToken token = converter.convert(jwt);

        assertThat(token).isNotNull();
        assertThat(token.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN", "ROLE_DOCTOR");
    }

    @Test
    void should_extract_client_roles_when_resource_id_set() {
        Jwt jwt = baseJwt()
                .claim("resource_access", Map.of(
                        "terangamed-backend", Map.of("roles", List.of("RECEPTIONIST"))))
                .build();

        JwtAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting("authority")
                .contains("ROLE_RECEPTIONIST");
    }

    @Test
    void should_ignore_client_roles_for_other_clients() {
        Jwt jwt = baseJwt()
                .claim("resource_access", Map.of(
                        "another-client", Map.of("roles", List.of("X"))))
                .build();

        JwtAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting("authority")
                .doesNotContain("ROLE_X");
    }

    @Test
    void should_combine_realm_and_client_roles() {
        Jwt jwt = baseJwt()
                .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                .claim("resource_access", Map.of(
                        "terangamed-backend", Map.of("roles", List.of("DOCTOR"))))
                .build();

        JwtAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN", "ROLE_DOCTOR");
    }

    @Test
    void should_use_principal_attribute_when_present() {
        Jwt jwt = baseJwt()
                .claim("preferred_username", "drmartin")
                .build();

        JwtAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getName()).isEqualTo("drmartin");
    }

    @Test
    void should_fall_back_to_subject_when_principal_attribute_missing() {
        JwtAuthConverterProperties props = new JwtAuthConverterProperties();
        props.setPrincipalAttribute("nonexistent");
        Jwt jwt = baseJwt().build();

        JwtAuthenticationToken token = new JwtAuthConverter(props).convert(jwt);

        assertThat(token.getName()).isEqualTo("user-uuid");
    }

    @Test
    void should_handle_jwt_without_role_claims() {
        Jwt jwt = baseJwt().build();

        JwtAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .noneMatch(a -> a.getAuthority().startsWith("ROLE_"));
    }

    @Test
    void should_skip_realm_roles_with_invalid_structure() {
        Jwt jwt = baseJwt()
                .claim("realm_access", Map.of("roles", "not-a-list"))
                .build();

        JwtAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .noneMatch(a -> a.getAuthority().startsWith("ROLE_"));
    }

    @Test
    void should_skip_blank_role_strings() {
        Jwt jwt = baseJwt()
                .claim("realm_access", Map.of("roles", List.of("ADMIN", "", "  ")))
                .build();

        JwtAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN")
                .doesNotContain("ROLE_", "ROLE_  ");
    }

    @Test
    void should_extract_scopes_with_scope_prefix() {
        Jwt jwt = baseJwt()
                .claim("scope", "openid profile email")
                .build();

        JwtAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting("authority")
                .contains("SCOPE_openid", "SCOPE_profile", "SCOPE_email");
    }

    @Test
    void should_skip_client_roles_when_resource_id_empty() {
        JwtAuthConverterProperties props = new JwtAuthConverterProperties();
        props.setResourceId(""); // explicitement vide
        JwtAuthConverter c = new JwtAuthConverter(props);
        Jwt jwt = baseJwt()
                .claim("resource_access", Map.of(
                        "terangamed-backend", Map.of("roles", List.of("DOCTOR"))))
                .build();

        JwtAuthenticationToken token = c.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting("authority")
                .doesNotContain("ROLE_DOCTOR");
    }

    private Jwt.Builder baseJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
    }
}
