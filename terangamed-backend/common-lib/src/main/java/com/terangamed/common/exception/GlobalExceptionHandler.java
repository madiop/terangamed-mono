package com.terangamed.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Handler global d'exceptions pour tous les microservices TerangaMed.
 *
 * <p>Mappe les exceptions techniques et métier vers le format unifié {@link ApiError}.
 * Auto-enregistré via {@link com.terangamed.common.config.CommonLibAutoConfiguration}.
 *
 * <p>Stratégie de log :
 * <ul>
 *   <li>{@link BaseException} → {@code WARN} (erreur attendue, ne nécessite pas d'investigation)</li>
 *   <li>{@code Exception} générique → {@code ERROR} avec stack trace complète</li>
 *   <li>les erreurs de validation et 4xx ne sont pas loggées (bruit inutile)</li>
 * </ul>
 *
 * <p>Le {@code correlationId} est récupéré du MDC, alimenté en amont par un filtre
 * (typiquement au niveau Gateway) et propagé via le header {@code X-Correlation-Id}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String CORRELATION_ID_KEY = "correlationId";

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiError> handleBase(BaseException ex, HttpServletRequest req) {
        log.warn("Business exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return build(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldViolation)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", req, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex,
                                                     HttpServletRequest req) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(this::toFieldViolation)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Validation failed", req, violations);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        String message = "Parameter '%s' has invalid value '%s'".formatted(ex.getName(), ex.getValue());
        return build(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", message, req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on path {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred", req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest req,
                                           List<ApiError.FieldViolation> violations) {
        ApiError body = ApiError.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .code(code)
                .message(message)
                .path(req.getRequestURI())
                .correlationId(MDC.get(CORRELATION_ID_KEY))
                .violations(violations)
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private ApiError.FieldViolation toFieldViolation(FieldError err) {
        return ApiError.FieldViolation.builder()
                .field(err.getField())
                .rejectedValue(err.getRejectedValue())
                .message(err.getDefaultMessage())
                .build();
    }

    private ApiError.FieldViolation toFieldViolation(ConstraintViolation<?> v) {
        return ApiError.FieldViolation.builder()
                .field(v.getPropertyPath().toString())
                .rejectedValue(v.getInvalidValue())
                .message(v.getMessage())
                .build();
    }
}
