package com.bootcamp.accountservice.client;

import com.bootcamp.accountservice.exception.CreditServiceUnavailableException;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Cliente REST hacia credit-service, protegido con circuit breaker + timeout de 2s (mismo
 * patron que {@link CardClient}/{@link CustomerClient}). Se usa para el bloqueo de alta de
 * productos nuevos por deuda vencida (D8, Fase III). Igual que {@code /cards/exists},
 * {@code /credits/customers/{id}/has-overdue-debt} no tiene un "404 de negocio" (siempre
 * responde true/false), asi que cualquier falla del circuito es indisponibilidad.
 */
@Component
public class CreditClient {

    private static final String CIRCUIT_BREAKER_ID = "credit-service";

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    public CreditClient(
            WebClient creditServiceWebClient,
            ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.webClient = creditServiceWebClient;
        this.circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
    }

    /** true si el cliente tiene alguna cuota vencida sin pagar en cualquiera de sus creditos. */
    public Mono<Boolean> hasOverdueDebt(String customerId) {
        Mono<Boolean> call = webClient.get()
                .uri("/credits/customers/{customerId}/has-overdue-debt", customerId)
                .retrieve()
                .bodyToMono(Boolean.class);

        return circuitBreaker.run(call, throwable ->
                Mono.error(new CreditServiceUnavailableException(customerId, throwable)));
    }
}
