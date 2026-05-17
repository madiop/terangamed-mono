package com.terangamed.appointment.feign;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeignAuthInterceptorTest {

    private final FeignAuthInterceptor interceptor = new FeignAuthInterceptor();

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void should_propagate_authorization_header() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addHeader("Authorization", "Bearer test-jwt-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).containsKey("Authorization");
        assertThat(template.headers().get("Authorization"))
                .containsExactly("Bearer test-jwt-token");
    }

    @Test
    void should_skip_when_no_request_context() {
        // RequestContextHolder vide (cas job batch / scheduler)
        RequestContextHolder.resetRequestAttributes();

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void should_skip_when_authorization_header_absent() {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        // pas d'header Authorization
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void should_skip_when_authorization_header_blank() {
        HttpServletRequest mockRequest = mock(HttpServletRequest.class);
        when(mockRequest.getHeader("Authorization")).thenReturn("   ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey("Authorization");
    }
}
