package com.terangamed.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest req;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void should_handle_base_exception() {
        ResponseEntity<ApiError> resp = handler.handleBase(
                new ResourceNotFoundException("Patient", 1L), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(resp.getBody().path()).isEqualTo("/api/test");
        assertThat(resp.getBody().status()).isEqualTo(404);
        assertThat(resp.getBody().timestamp()).isNotNull();
    }

    @Test
    void should_handle_method_argument_not_valid() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "patient");
        result.addError(new FieldError(
                "patient", "email", "not-an-email", false,
                null, null, "must be a well-formed email"));

        Method method = SampleController.class.getMethod("post", Object.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new MethodParameter(method, 0), result);

        ResponseEntity<ApiError> resp = handler.handleValidation(ex, req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().violations()).hasSize(1);
        assertThat(resp.getBody().violations().get(0).field()).isEqualTo("email");
        assertThat(resp.getBody().violations().get(0).rejectedValue()).isEqualTo("not-an-email");
    }

    @Test
    void should_handle_constraint_violation_from_real_validator() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<TestBean>> violations = validator.validate(new TestBean(""));
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        ResponseEntity<ApiError> resp = handler.handleConstraint(ex, req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().violations()).isNotEmpty();
        assertThat(resp.getBody().violations().get(0).field()).isEqualTo("name");
    }

    @Test
    void should_handle_type_mismatch() throws Exception {
        Method method = SampleController.class.getMethod("get", Long.class);
        MethodParameter param = new MethodParameter(method, 0);

        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", param, new RuntimeException("not a number"));

        ResponseEntity<ApiError> resp = handler.handleTypeMismatch(ex, req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().code()).isEqualTo("TYPE_MISMATCH");
        assertThat(resp.getBody().message()).contains("id", "abc");
    }

    @Test
    void should_handle_access_denied() {
        ResponseEntity<ApiError> resp = handler.handleAccessDenied(
                new AccessDeniedException("nope"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().code()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void should_handle_authentication_exception() {
        ResponseEntity<ApiError> resp = handler.handleAuth(
                new BadCredentialsException("bad creds"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().code()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void should_handle_generic_exception_as_500() {
        ResponseEntity<ApiError> resp = handler.handleGeneric(new RuntimeException("boom"), req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    // Stubs pour récupérer un MethodParameter — Spring exige un Method réel
    @SuppressWarnings("unused")
    static class SampleController {
        public void post(Object body) {
        }

        public String get(Long id) {
            return String.valueOf(id);
        }
    }

    record TestBean(@NotBlank String name) {
    }
}
