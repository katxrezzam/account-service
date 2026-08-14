package com.bootcamp.accountservice.client;

import com.bootcamp.accountservice.dto.CustomerInfo;
import com.bootcamp.accountservice.exception.CustomerNotFoundException;
import com.bootcamp.accountservice.exception.CustomerServiceUnavailableException;
import java.time.Duration;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Cliente REST hacia customer-service. Sin Resilience4j todavia (llega en Fase 2): timeout fijo
 * corto y errores traducidos a excepciones de dominio propias, para que quien llame no dependa de
 * los detalles de WebClient.
 */
@Component
public class CustomerClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final WebClient webClient;

    public CustomerClient(WebClient customerServiceWebClient) {
        this.webClient = customerServiceWebClient;
    }

    /** Trae el cliente por id. Emite CustomerNotFoundException (404) o CustomerServiceUnavailableException (otro error/timeout). */
    public Mono<CustomerInfo> getCustomer(String customerId) {
        return webClient.get()
                .uri("/customers/{id}", customerId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        response -> Mono.error(new CustomerNotFoundException(customerId)))
                .bodyToMono(CustomerInfo.class)
                .timeout(TIMEOUT)
                .onErrorMap(
                        ex -> !(ex instanceof CustomerNotFoundException),
                        ex -> new CustomerServiceUnavailableException(customerId, ex));
    }
}
