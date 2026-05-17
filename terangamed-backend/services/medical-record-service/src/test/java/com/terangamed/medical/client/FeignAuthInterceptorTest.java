package com.terangamed.medical.client;

import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class FeignAuthInterceptorTest {

    private final FeignAuthInterceptor interceptor = new FeignAuthInterceptor();

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void no_request_context_does_nothing() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);
        assertThat(template.headers()).isEmpty();
    }

    @Test
    void propagates_authorization_header_when_present() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer abc.def.ghi");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer abc.def.ghi");
    }

    @Test
    void no_authorization_header_does_not_set_anything() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }

    @Test
    void blank_authorization_header_does_not_set_anything() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getHeader("Authorization")).thenReturn("   ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }
}
