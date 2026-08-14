package com.bootcamp.accountservice.exception;

import java.time.Instant;

/** Cuerpo de error uniforme para toda respuesta no exitosa. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String correlationId,
        String path) {
}
