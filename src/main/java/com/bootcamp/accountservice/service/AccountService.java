package com.bootcamp.accountservice.service;

import com.bootcamp.accountservice.dto.AccountRequest;
import com.bootcamp.accountservice.dto.AccountResponse;
import com.bootcamp.accountservice.dto.AccountUpdateRequest;
import com.bootcamp.accountservice.dto.MovementRequest;
import com.bootcamp.accountservice.dto.MovementResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AccountService {

    Mono<AccountResponse> create(AccountRequest request);

    Flux<AccountResponse> findAll();

    Mono<AccountResponse> findById(String id);

    Mono<AccountResponse> update(String id, AccountUpdateRequest request);

    Mono<Void> delete(String id);

    Flux<MovementResponse> findMovements(String accountId);

    Mono<MovementResponse> deposit(String accountId, MovementRequest request, String idempotencyKey);

    Mono<MovementResponse> withdraw(String accountId, MovementRequest request, String idempotencyKey);
}
