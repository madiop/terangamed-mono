package com.terangamed.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lever quand l'utilisateur authentifié n'a pas les droits pour l'opération
 * (mappé en HTTP 403).
 *
 * <p>À distinguer de {@link org.springframework.security.access.AccessDeniedException}
 * que Spring Security lève automatiquement pour les @PreAuthorize qui échouent —
 * cette exception est destinée aux refus métier post-authentification
 * (ex: un médecin tente de modifier un dossier qui ne lui appartient pas).
 */
public class ForbiddenException extends BaseException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
