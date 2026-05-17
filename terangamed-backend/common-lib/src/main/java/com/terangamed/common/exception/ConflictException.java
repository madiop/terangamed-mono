package com.terangamed.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lever quand un état du serveur est en conflit avec la requête (mappé en HTTP 409).
 *
 * <p>Cas typiques :
 * <ul>
 *   <li>création d'une ressource avec une clé unique déjà utilisée (email patient, n° dossier)</li>
 *   <li>chevauchement de RDV pour un même médecin</li>
 *   <li>tentative de modification concurrente (optimistic lock)</li>
 * </ul>
 */
public class ConflictException extends BaseException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
