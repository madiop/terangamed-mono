package com.terangamed.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Racine de toutes les exceptions métier de TerangaMed.
 *
 * <p>Chaque exception transporte :
 * <ul>
 *   <li>un statut HTTP attendu (mappé en réponse par {@link GlobalExceptionHandler})</li>
 *   <li>un code d'erreur stable, utilisable côté client pour afficher des messages localisés</li>
 * </ul>
 *
 * <p>Cette classe est abstraite — elle ne doit jamais être instanciée directement.
 * Préférer une sous-classe ({@link ResourceNotFoundException}, {@link BadRequestException}, …)
 * ou en créer une nouvelle par cas d'usage métier.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    protected BaseException(HttpStatus httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    protected BaseException(HttpStatus httpStatus, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }
}
