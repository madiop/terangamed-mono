package com.terangamed.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Représentation standardisée d'une erreur HTTP renvoyée par toutes les APIs TerangaMed.
 *
 * <p>Format JSON (exemple) :
 * <pre>
 * {
 *   "timestamp": "2026-04-30T14:22:31.123+00:00",
 *   "status": 404,
 *   "error": "Not Found",
 *   "code": "RESOURCE_NOT_FOUND",
 *   "message": "Patient with id 42 not found",
 *   "path": "/api/patients/42",
 *   "correlationId": "5f1d9b94-..."
 * }
 * </pre>
 *
 * <p>En cas d'erreur de validation, le tableau {@code violations} liste les champs invalides.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> violations
) {

    @Builder
    public record FieldViolation(String field, Object rejectedValue, String message) {
    }
}
