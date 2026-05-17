package com.terangamed.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Lever quand une ressource demandée n'existe pas (mappé en HTTP 404).
 *
 * <pre>
 *   throw new ResourceNotFoundException("Patient", id);
 * </pre>
 */
public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "%s with id %s not found".formatted(resource, id));
    }

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }
}
