package com.bootcamp.accountservice.client;

import com.bootcamp.accountservice.exception.CardServiceUnavailableException;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Cliente REST hacia card-service, protegido con circuit breaker + timeout de 2s (mismo patron
 * que {@link CustomerClient}). Se usa solo para el requisito de tarjeta de credito de los
 * perfiles VIP/PYME al crear una cuenta (D5/D8). A diferencia de CustomerClient,
 * GET /cards/exists no tiene un "404 de negocio" (siempre responde true/false), asi que no hace
 * falta distinguir una excepcion de negocio de una tecnica: cualquier falla del circuito es
 * indisponibilidad.
 */
@Component
public class CardClient {

    private static final String CIRCUIT_BREAKER_ID = "card-service";

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    public CardClient(
            WebClient cardServiceWebClient,
            ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.webClient = cardServiceWebClient;
        this.circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
    }

    /** true si el cliente ya tiene una tarjeta de credito con el banco. */
    public Mono<Boolean> hasCreditCard(String customerId) {
        Mono<Boolean> call = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/cards/exists")
                        .queryParam("customerId", customerId)
                        .build())
                .retrieve()
                .bodyToMono(Boolean.class);

        return circuitBreaker.run(call, throwable ->
                Mono.error(new CardServiceUnavailableException(customerId, throwable)));
    }
}
