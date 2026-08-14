package com.bootcamp.accountservice.service;

import com.bootcamp.accountservice.dto.AccountRequest;
import com.bootcamp.accountservice.dto.AccountResponse;
import com.bootcamp.accountservice.dto.AccountUpdateRequest;
import com.bootcamp.accountservice.dto.MovementRequest;
import com.bootcamp.accountservice.dto.MovementResponse;
import com.bootcamp.accountservice.dto.TransferRequest;
import com.bootcamp.accountservice.dto.TransferResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Casos de uso de negocio sobre cuentas: CRUD + depositos/retiros. */
public interface AccountService {

    Mono<AccountResponse> create(AccountRequest request);

    Flux<AccountResponse> findAll();

    Mono<AccountResponse> findById(String id);

    Mono<AccountResponse> update(String id, AccountUpdateRequest request);

    Mono<Void> delete(String id);

    Flux<MovementResponse> findMovements(String accountId);

    Mono<MovementResponse> deposit(
            String accountId, MovementRequest request, String idempotencyKey);

    Mono<MovementResponse> withdraw(
            String accountId, MovementRequest request, String idempotencyKey);

    /** Transferencia entre dos cuentas (propias o a un tercero del mismo banco - mecanicamente
     * identicas, ambas viven en account-service). sourceAccountId sale por path variable. */
    Mono<TransferResponse> transfer(
            String sourceAccountId, TransferRequest request, String idempotencyKey);
}
