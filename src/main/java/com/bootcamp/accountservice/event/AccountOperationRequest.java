package com.bootcamp.accountservice.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Copia local (bounded context propio) del payload que yanki-service publica en
 * yanki.account-operation-requests para pedir que account-service ejecute una pata de una
 * transferencia entre monederos con cuenta vinculada (Entrega 2, D8 Fase III). account-service no
 * conoce el modelo de Wallet/WalletTransfer de yanki-service: transferId es solo un identificador
 * opaco que se devuelve tal cual en el resultado, para que yanki-service correlacione la
 * respuesta con la transferencia que la origino.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountOperationRequest(
        String transferId,
        Leg leg,
        String accountId,
        OperationType operationType,
        BigDecimal amount,
        String idempotencyKey,
        Instant timestamp) {
}
