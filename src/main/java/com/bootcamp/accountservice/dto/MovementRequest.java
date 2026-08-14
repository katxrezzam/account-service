package com.bootcamp.accountservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO de entrada para depositos/retiros. La clave de idempotencia va por header
 * (Idempotency-Key), no en el body: es metadata del intento de request, no del movimiento en si.
 */
public record MovementRequest(
        @NotNull(message = "amount es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount debe ser mayor a 0")
        BigDecimal amount) {
}
