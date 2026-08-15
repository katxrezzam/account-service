package com.bootcamp.accountservice.service;

import com.bootcamp.accountservice.dto.AccountRequest;
import com.bootcamp.accountservice.dto.AccountResponse;
import com.bootcamp.accountservice.dto.AccountUpdateRequest;
import com.bootcamp.accountservice.dto.MovementRequest;
import com.bootcamp.accountservice.dto.MovementResponse;
import com.bootcamp.accountservice.dto.TransferRequest;
import com.bootcamp.accountservice.dto.TransferResponse;
import java.time.Instant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Casos de uso de negocio sobre cuentas: CRUD + depositos/retiros. */
public interface AccountService {

    Mono<AccountResponse> create(AccountRequest request);

    /** holderId es opcional: si viene, filtra a las cuentas de ese cliente (usado por
     * report-service); si es null, se comporta como antes (todas las cuentas). */
    Flux<AccountResponse> findAll(String holderId);

    Mono<AccountResponse> findById(String id);

    Mono<AccountResponse> update(String id, AccountUpdateRequest request);

    Mono<Void> delete(String id);

    /** from/to son opcionales: si ambos vienen null, se comporta como antes (todo el
     * historial); usado por report-service para el reporte general en un intervalo. */
    Flux<MovementResponse> findMovements(String accountId, Instant from, Instant to);

    Mono<MovementResponse> deposit(
            String accountId, MovementRequest request, String idempotencyKey);

    Mono<MovementResponse> withdraw(
            String accountId, MovementRequest request, String idempotencyKey);

    /**
     * Revierte un retiro ya aplicado cuya operacion (transferencia local, o pedida por otro
     * servicio via Kafka) termino fallando en una pata posterior - a diferencia de
     * {@link #deposit}, NO cuenta como un movimiento nuevo a los fines del limite/comision por
     * exceso ni del bloqueo de dia de una cuenta a plazo fijo (revertir un intento no es un
     * movimiento nuevo del cliente), y devuelve cualquier comision que se haya cobrado
     * especificamente por el retiro que se esta revirtiendo. originalWithdrawalIdempotencyKey es
     * la Idempotency-Key de ESE retiro (para localizar su comision, si la tuvo) - ver hallazgo 8.7
     * en PLAN-DE-ACCION.md.
     */
    Mono<MovementResponse> reverseWithdrawal(
            String accountId, MovementRequest request, String idempotencyKey,
            String originalWithdrawalIdempotencyKey);

    /** Transferencia entre dos cuentas (propias o a un tercero del mismo banco - mecanicamente
     * identicas, ambas viven en account-service). sourceAccountId sale por path variable. */
    Mono<TransferResponse> transfer(
            String sourceAccountId, TransferRequest request, String idempotencyKey);
}
