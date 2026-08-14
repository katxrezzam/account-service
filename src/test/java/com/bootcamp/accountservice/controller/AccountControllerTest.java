package com.bootcamp.accountservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bootcamp.accountservice.dto.AccountRequest;
import com.bootcamp.accountservice.dto.AccountResponse;
import com.bootcamp.accountservice.dto.MovementRequest;
import com.bootcamp.accountservice.dto.MovementResponse;
import com.bootcamp.accountservice.exception.AccountNotFoundException;
import com.bootcamp.accountservice.exception.GlobalExceptionHandler;
import com.bootcamp.accountservice.model.AccountProfile;
import com.bootcamp.accountservice.model.AccountType;
import com.bootcamp.accountservice.model.MovementType;
import com.bootcamp.accountservice.service.AccountService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = AccountController.class)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AccountService accountService;

    private AccountResponse sampleResponse() {
        return new AccountResponse("acc1", AccountType.SAVINGS, AccountProfile.STANDARD,
                List.of("cust1"), List.of(), new BigDecimal("100.00"), null, Instant.now(), Instant.now());
    }

    @Test
    void post_requestValido_retorna201() {
        when(accountService.create(any(AccountRequest.class))).thenReturn(Mono.just(sampleResponse()));

        AccountRequest request = new AccountRequest(AccountType.SAVINGS, null, List.of("cust1"), List.of(), new BigDecimal("100.00"), null);

        webTestClient.post().uri("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("acc1");
    }

    @Test
    void get_inexistente_retorna404() {
        when(accountService.findById(eq("no-existe"))).thenReturn(Mono.error(new AccountNotFoundException("no-existe")));

        webTestClient.get().uri("/accounts/{id}", "no-existe")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.correlationId").exists();
    }

    @Test
    void deposit_sinIdempotencyKey_seEnviaComoNull_yElServiceDecideElError() {
        when(accountService.deposit(eq("acc1"), any(MovementRequest.class), eq(null)))
                .thenReturn(Mono.error(new com.bootcamp.accountservice.exception.InvalidBusinessRuleException(
                        "El header Idempotency-Key es obligatorio en depositos y retiros")));

        webTestClient.post().uri("/accounts/{id}/deposits", "acc1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MovementRequest(new BigDecimal("10")))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void deposit_conIdempotencyKey_retorna201() {
        MovementResponse response = new MovementResponse("mv1", "acc1", MovementType.DEPOSIT,
                new BigDecimal("10"), new BigDecimal("110"), Instant.now(), null);
        when(accountService.deposit(eq("acc1"), any(MovementRequest.class), eq("key-1")))
                .thenReturn(Mono.just(response));

        webTestClient.post().uri("/accounts/{id}/deposits", "acc1")
                .header("Idempotency-Key", "key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MovementRequest(new BigDecimal("10")))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isEqualTo("mv1");
    }

    @Test
    void getAll_retorna200() {
        when(accountService.findAll()).thenReturn(reactor.core.publisher.Flux.just(sampleResponse()));

        webTestClient.get().uri("/accounts")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(AccountResponse.class)
                .hasSize(1);
    }
}
