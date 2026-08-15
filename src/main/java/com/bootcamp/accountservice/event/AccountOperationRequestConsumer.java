package com.bootcamp.accountservice.event;

import com.bootcamp.accountservice.dto.MovementRequest;
import com.bootcamp.accountservice.dto.MovementResponse;
import com.bootcamp.accountservice.service.AccountService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Consume yanki.account-operation-requests y ejecuta la pata pedida reutilizando directamente los
 * metodos publicos ya existentes de AccountService (deposit/withdraw): ya aplican reglas de
 * negocio, saldo atomico e idempotencia (Idempotency-Key), asi que no hizo falta exponer ningun
 * metodo interno nuevo (Entrega 2, D8 Fase III). Se bloquea deliberadamente ({@code .block()}),
 * mismo criterio que CardEventConsumer/CustomerEventConsumer en yanki-service: el offset de
 * Kafka se confirma recien cuando el metodo retorna. El resultado SIEMPRE se publica (exito o
 * fallo, capturando cualquier Throwable) para que la maquina de estados de la transferencia en
 * yanki-service nunca quede esperando una pata indefinidamente.
 *
 * <p>Una solicitud de compensacion (idempotencyKey terminada en {@value #COMPENSATION_SUFFIX} -
 * mismo criterio que yanki-service usa para distinguirla de una confirmacion normal) se enruta a
 * {@link AccountService#reverseWithdrawal} en vez de a {@code deposit()}: revertir un retiro NO
 * es un movimiento nuevo del cliente, asi que no debe contar para el limite/comision por exceso
 * ni disparar una comision propia (hallazgo 8.7, PLAN-DE-ACCION.md).
 */
@Component
public class AccountOperationRequestConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(AccountOperationRequestConsumer.class);
    private static final String COMPENSATION_SUFFIX = ":compensation";

    private final AccountService accountService;
    private final AccountOperationResultPublisher resultPublisher;

    public AccountOperationRequestConsumer(
            AccountService accountService, AccountOperationResultPublisher resultPublisher) {
        this.accountService = accountService;
        this.resultPublisher = resultPublisher;
    }

    /** Escucha yanki.account-operation-requests y ejecuta el deposito/retiro/reversion
     * correspondiente. */
    @KafkaListener(topics = "yanki.account-operation-requests", groupId = "account-service")
    public void onAccountOperationRequest(AccountOperationRequest request) {
        MovementRequest movementRequest = new MovementRequest(request.amount());
        resolveOperation(request, movementRequest)
                .map(response -> success(request, response))
                .onErrorResume(ex -> Mono.just(failure(request, ex)))
                .doOnNext(resultPublisher::publish)
                .block();
    }

    private Mono<MovementResponse> resolveOperation(
            AccountOperationRequest request, MovementRequest movementRequest) {
        if (isCompensation(request)) {
            String originalWithdrawalIdempotencyKey = stripCompensationSuffix(
                    request.idempotencyKey());
            return accountService.reverseWithdrawal(
                    request.accountId(), movementRequest, request.idempotencyKey(),
                    originalWithdrawalIdempotencyKey);
        }
        return request.operationType() == OperationType.DEPOSIT
                ? accountService.deposit(
                        request.accountId(), movementRequest, request.idempotencyKey())
                : accountService.withdraw(
                        request.accountId(), movementRequest, request.idempotencyKey());
    }

    private boolean isCompensation(AccountOperationRequest request) {
        return request.idempotencyKey() != null
                && request.idempotencyKey().endsWith(COMPENSATION_SUFFIX);
    }

    private String stripCompensationSuffix(String idempotencyKey) {
        return idempotencyKey.substring(0, idempotencyKey.length() - COMPENSATION_SUFFIX.length());
    }

    private AccountOperationResult success(
            AccountOperationRequest request, MovementResponse response) {
        log.info("Operacion {} ejecutada para transferId={} leg={} accountId={}",
                request.operationType(), request.transferId(), request.leg(), request.accountId());
        return new AccountOperationResult(request.transferId(), request.leg(), true,
                response.balanceAfter(), null, request.idempotencyKey(), Instant.now());
    }

    private AccountOperationResult failure(AccountOperationRequest request, Throwable ex) {
        log.warn("Operacion {} fallo para transferId={} leg={} accountId={}: {}",
                request.operationType(), request.transferId(), request.leg(), request.accountId(),
                ex.getMessage());
        return new AccountOperationResult(request.transferId(), request.leg(), false,
                null, ex.getMessage(), request.idempotencyKey(), Instant.now());
    }
}
