package com.bootcamp.accountservice.controller;

import com.bootcamp.accountservice.dto.AccountRequest;
import com.bootcamp.accountservice.dto.AccountResponse;
import com.bootcamp.accountservice.dto.AccountUpdateRequest;
import com.bootcamp.accountservice.dto.MovementRequest;
import com.bootcamp.accountservice.dto.MovementResponse;
import com.bootcamp.accountservice.dto.TransferRequest;
import com.bootcamp.accountservice.dto.TransferResponse;
import com.bootcamp.accountservice.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** CRUD de cuentas + depositos/retiros. Delega toda regla de negocio en {@link AccountService}. */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AccountResponse> create(@Valid @RequestBody AccountRequest request) {
        return accountService.create(request);
    }

    @GetMapping
    public Flux<AccountResponse> findAll() {
        return accountService.findAll();
    }

    @GetMapping("/{id}")
    public Mono<AccountResponse> findById(@PathVariable String id) {
        return accountService.findById(id);
    }

    @PutMapping("/{id}")
    public Mono<AccountResponse> update(
            @PathVariable String id, @Valid @RequestBody AccountUpdateRequest request) {
        return accountService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String id) {
        return accountService.delete(id);
    }

    @GetMapping("/{id}/movements")
    public Flux<MovementResponse> findMovements(@PathVariable String id) {
        return accountService.findMovements(id);
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MovementResponse> deposit(
            @PathVariable String id,
            @Valid @RequestBody MovementRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return accountService.deposit(id, request, idempotencyKey);
    }

    @PostMapping("/{id}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MovementResponse> withdraw(
            @PathVariable String id,
            @Valid @RequestBody MovementRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return accountService.withdraw(id, request, idempotencyKey);
    }

    @PostMapping("/{id}/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<TransferResponse> transfer(
            @PathVariable String id,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return accountService.transfer(id, request, idempotencyKey);
    }
}
