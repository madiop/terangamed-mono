package com.terangamed.medical.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setJwt(String preferredUsername, String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("preferred_username", preferredUsername)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        var authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, authorities));
    }

    @Test
    void username_returns_preferred_username() {
        setJwt("dr.sall", "kc-sub-101");
        assertThat(provider.username()).isEqualTo("dr.sall");
    }

    @Test
    void subject_returns_jwt_sub() {
        setJwt("x", "kc-sub-101");
        assertThat(provider.subject()).isEqualTo("kc-sub-101");
    }

    @Test
    void username_falls_back_to_subject_when_preferred_username_missing() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none").subject("kc-sub-x")
                .claim("foo", "bar") // pas de preferred_username
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(30))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));

        assertThat(provider.username()).isEqualTo("kc-sub-x");
    }

    @Test
    void username_returns_anonymous_when_no_authentication() {
        SecurityContextHolder.clearContext();
        assertThat(provider.username()).isEqualTo("anonymous");
    }

    @Test
    void subject_returns_null_when_no_authentication() {
        SecurityContextHolder.clearContext();
        assertThat(provider.subject()).isNull();
    }

    @Test
    void has_role_matches_role_authority() {
        setJwt("u", "s", "ROLE_DOCTOR", "ROLE_USER");
        assertThat(provider.hasRole("DOCTOR")).isTrue();
        assertThat(provider.hasRole("ADMIN")).isFalse();
    }

    @Test
    void has_role_matches_unprefixed_authority_too() {
        setJwt("u", "s", "ADMIN");
        assertThat(provider.hasRole("ADMIN")).isTrue();
    }

    @Test
    void has_role_returns_false_when_no_auth() {
        SecurityContextHolder.clearContext();
        assertThat(provider.hasRole("ANY")).isFalse();
    }

    @Test
    void username_with_non_jwt_principal_returns_anonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "x",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        assertThat(provider.username()).isEqualTo("anonymous");
    }

    @Test
    void anonymous_token_returns_anonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anon",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(provider.username()).isEqualTo("anonymous");
        // hasRole avec ROLE_ANONYMOUS
        assertThat(provider.hasRole("ANONYMOUS")).isTrue();
    }

    @Test
    void jwt_with_object_preferred_username_uses_to_string() {
        // Test la branche `username.toString()` quand le claim n'est pas une String
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("s")
                .claim("preferred_username", Map.of("nested", "value"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(30))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of()));

        assertThat(provider.username()).contains("nested");
    }
}
