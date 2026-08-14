package com.bootcamp.accountservice.dto;

import com.bootcamp.accountservice.model.MovementType;
import java.math.BigDecimal;
import java.time.Instant;

/** DTO de salida de un movimiento (deposito/retiro). */
public record MovementResponse(
        String id,
        String accountId,
        MovementType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant timestamp) {
}
