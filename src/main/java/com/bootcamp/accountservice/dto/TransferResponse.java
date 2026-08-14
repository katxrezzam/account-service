package com.bootcamp.accountservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO de salida de una transferencia. Resume las dos patas (retiro en origen + deposito en
 * destino) en una sola respuesta; el detalle de cada movimiento (incluida una eventual comision
 * por exceso en cualquiera de las dos cuentas) sigue disponible via
 * GET /accounts/{id}/movements de cada cuenta, con counterpartyAccountId apuntando a la otra.
 */
public record TransferResponse(
        String sourceAccountId,
        String destinationAccountId,
        BigDecimal amount,
        BigDecimal sourceBalanceAfter,
        BigDecimal destinationBalanceAfter,
        Instant timestamp) {
}
