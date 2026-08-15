package com.bootcamp.accountservice.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload publicado al topic yanki.account-operation-results tras ejecutar (o fallar en
 * ejecutar) una pata de operacion de cuenta pedida por yanki-service. transferId, leg e
 * idempotencyKey viajan igual que en la solicitud para que el consumidor en yanki-service
 * correlacione la respuesta con la transferencia y la pata que la origino - idempotencyKey en
 * particular es lo que le permite distinguir la confirmacion de una pata normal (sufijo
 * ":out"/":in") de la confirmacion de una compensacion (sufijo ":out:compensation"), que llega
 * con el mismo leg que la pata original.
 */
public record AccountOperationResult(
        String transferId,
        Leg leg,
        boolean success,
        BigDecimal balanceAfter,
        String errorMessage,
        String idempotencyKey,
        Instant timestamp) {
}
