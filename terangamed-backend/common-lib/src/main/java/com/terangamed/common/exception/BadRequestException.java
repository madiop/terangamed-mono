package com.terangamed.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lever quand la requête est mal formée ou viole une règle métier (mappé en HTTP 400).
 *
 * <p>Pour les erreurs de validation Bean Validation (annotations @NotBlank, @Email, …),
 * Spring lève automatiquement une {@code MethodArgumentNotValidException} —
 * inutile de la convertir manuellement, {@link GlobalExceptionHandler} s'en charge.
 */
public class BadRequestException extends BaseException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public BadRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
