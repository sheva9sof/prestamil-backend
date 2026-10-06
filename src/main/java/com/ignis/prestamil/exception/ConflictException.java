package com.ignis.prestamil.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 409 — Conflicto con el estado actual del recurso. Se usa cuando la operación es válida pero
 * una precondición dependiente del tiempo no se cumple (p. ej. pedir el PDF de reposición sin
 * un movimiento RE del día; C-02).
 */
@ResponseStatus(value = HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
