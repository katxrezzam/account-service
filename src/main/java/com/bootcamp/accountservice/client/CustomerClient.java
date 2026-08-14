package com.bootcamp.accountservice.client;

import com.bootcamp.accountservice.dto.CustomerInfo;
import com.bootcamp.accountservice.exception.CustomerNotFoundException;
import com.bootcamp.accountservice.exception.CustomerServiceUnavailableException;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Cliente REST hacia customer-service, protegido con circuit breaker + timeout de 2s
 * (Resilience4j via Spring Cloud CircuitBreaker). El id "customer-service" mapea a
 * resilience4j.circuitbreaker.instances.customer-service / .timelimiter.instances.customer-service
 * en la config (bootcamp-config-repo/account-service.yml). CustomerNotFoundException (404 de
 * negocio, cliente inexistente) esta declarada como ignore-exception ahi: no debe contar como
 * falla tecnica y abrir el circuito - si abriera por 404s, un cliente inventado bloquearia
 * llamadas legitimas de otros clientes reales.
 */
@Component
public class CustomerClient {

    private static final String CIRCUIT_BREAKER_ID = "customer-service";

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;

    public CustomerClient(WebClient customerServiceWebClient, ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.webClient = customerServiceWebClient;
        this.circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
    }

    /** Trae el cliente por id. Emite CustomerNotFoundException (404) o CustomerServiceUnavailableException (circuito abierto/timeout/otro error). */
    public Mono<CustomerInfo> getCustomer(String customerId) {
        Mono<CustomerInfo> call = webClient.get()
                .uri("/customers/{id}", customerId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        response -> Mono.error(new CustomerNotFoundException(customerId)))
                .bodyToMono(CustomerInfo.class);

        return circuitBreaker.run(call, throwable -> mapFallback(customerId, throwable));
    }

    private Mono<CustomerInfo> mapFallback(String customerId, Throwable throwable) {
        if (throwable instanceof CustomerNotFoundException) {
            return Mono.error(throwable);
        }
        return Mono.error(new CustomerServiceUnavailableException(customerId, throwable));
    }
}
