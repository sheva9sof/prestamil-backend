package com.ignis.prestamil.exception;

/**
 * Autenticado pero sin permiso para la operación. Se traduce a HTTP 403 en {@code GlobalExceptionHandler}.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
