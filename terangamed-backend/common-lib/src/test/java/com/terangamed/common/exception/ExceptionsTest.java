package com.terangamed.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionsTest {

    @Test
    void resource_not_found_with_resource_and_id_should_carry_404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Patient", 42L);

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.getMessage()).contains("Patient", "42");
    }

    @Test
    void resource_not_found_with_custom_message() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Custom message");

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("Custom message");
    }

    @Test
    void bad_request_should_carry_400_with_default_code() {
        BadRequestException ex = new BadRequestException("Invalid input");

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getErrorCode()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void bad_request_should_accept_custom_code() {
        BadRequestException ex = new BadRequestException("INVALID_SORT_FIELD", "msg");

        assertThat(ex.getErrorCode()).isEqualTo("INVALID_SORT_FIELD");
    }

    @Test
    void conflict_should_carry_409() {
        ConflictException ex = new ConflictException("Duplicate entry");

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getErrorCode()).isEqualTo("CONFLICT");
    }

    @Test
    void conflict_should_accept_custom_code() {
        ConflictException ex = new ConflictException("APPOINTMENT_OVERLAP", "Time slot taken");

        assertThat(ex.getErrorCode()).isEqualTo("APPOINTMENT_OVERLAP");
    }

    @Test
    void forbidden_should_carry_403() {
        ForbiddenException ex = new ForbiddenException("Not your record");

        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getErrorCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void base_exception_should_propagate_cause() {
        Throwable cause = new IllegalStateException("root");
        BaseException ex = new BaseException(HttpStatus.BAD_GATEWAY, "GATEWAY", "msg", cause) {};

        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
