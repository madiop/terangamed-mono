package com.terangamed.common.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuditorAwareTest {

    private final JwtAuditorAware auditor = new JwtAuditorAware();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_return_principal_name_when_authenticated() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "drmartin", "n/a", AuthorityUtils.createAuthorityList("ROLE_DOCTOR"));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        Optional<String> result = auditor.getCurrentAuditor();

        assertThat(result).contains("drmartin");
    }

    @Test
    void should_return_empty_when_no_authentication() {
        SecurityContextHolder.clearContext();

        Optional<String> result = auditor.getCurrentAuditor();

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_when_authentication_is_anonymous() {
        AnonymousAuthenticationToken anon = new AnonymousAuthenticationToken(
                "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(anon);
        SecurityContextHolder.setContext(context);

        Optional<String> result = auditor.getCurrentAuditor();

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_when_authentication_not_marked_authenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        Optional<String> result = auditor.getCurrentAuditor();

        assertThat(result).isEmpty();
    }
}
