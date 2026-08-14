package com.bootcamp.accountservice.exception;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String correlationId,
        String path) {
}
