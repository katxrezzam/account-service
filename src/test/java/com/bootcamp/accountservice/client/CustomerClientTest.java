package com.bootcamp.accountservice.client;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bootcamp.accountservice.dto.CustomerInfo;
import com.bootcamp.accountservice.exception.CustomerNotFoundException;
import com.bootcamp.accountservice.model.CustomerType;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Cubre el cache-aside nuevo (D8, Fase III): hit/miss/cliente inexistente/Redis caido. El
 * circuit breaker se mockea replicando su comportamiento real en estado cerrado (pasa el exito,
 * aplica el fallback ante error) - no hace falta un ReactiveResilience4JCircuitBreakerFactory
 * real, que exige registries y un ReactiveResilience4jBulkheadProvider para construirse.
 */
@ExtendWith(MockitoExtension.class)
class CustomerClientTest {

    private static final String KEY = "customer:cust1";

    @Mock
    private ReactiveCircuitBreakerFactory circuitBreakerFactory;
    @Mock
    private ReactiveCircuitBreaker circuitBreaker;
    @Mock
    private ReactiveRedisTemplate<String, CustomerInfo> redisTemplate;
    @Mock
    private ReactiveValueOperations<String, CustomerInfo> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(circuitBreakerFactory.create("customer-service")).thenReturn(circuitBreaker);
        // any(Mono.class) en vez de any(): con any() a secas, run(Mono,Function<..,Mono>) y
        // run(Flux,Function<..,Flux>) quedan ambiguos para el compilador. lenient(): el cache
        // hit no llega a usar el circuit breaker, no vale la pena un stub por test.
        lenient().when(circuitBreaker.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> {
                    Mono<Object> call = invocation.getArgument(0);
                    Function<Throwable, Mono<Object>> fallback = invocation.getArgument(1);
                    return call.onErrorResume(fallback);
                });
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private CustomerClient client(WebClient webClient) {
        return new CustomerClient(webClient, circuitBreakerFactory, redisTemplate, 60L);
    }

    private WebClient webClientRespondingOk() {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"id\":\"cust1\",\"customerType\":\"PERSONAL\"}")
                        .build()))
                .build();
    }

    private WebClient webClientRespondingNotFound() {
        return WebClient.builder()
                .exchangeFunction(request ->
                        Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).build()))
                .build();
    }

    private WebClient webClientThatShouldNotBeCalled() {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.error(new AssertionError(
                        "no deberia llamar a customer-service en un cache hit")))
                .build();
    }

    @Test
    void getCustomer_cacheHit_noLlamaAWebClient() {
        CustomerInfo cached = new CustomerInfo("cust1", CustomerType.PERSONAL);
        when(valueOperations.get(KEY)).thenReturn(Mono.just(cached));

        StepVerifier.create(client(webClientThatShouldNotBeCalled()).getCustomer("cust1"))
                .expectNext(cached)
                .verifyComplete();
    }

    @Test
    void getCustomer_cacheMiss_llamaAWebClientYCachea() {
        when(valueOperations.get(KEY)).thenReturn(Mono.empty());
        when(valueOperations.set(eq(KEY), any(CustomerInfo.class), any(Duration.class)))
                .thenReturn(Mono.just(true));

        StepVerifier.create(client(webClientRespondingOk()).getCustomer("cust1"))
                .expectNextMatches(info -> "cust1".equals(info.id())
                        && info.customerType() == CustomerType.PERSONAL)
                .verifyComplete();

        verify(valueOperations).set(eq(KEY), any(CustomerInfo.class), any(Duration.class));
    }

    @Test
    void getCustomer_clienteInexistente_noLoCacheaYPropagaElError() {
        when(valueOperations.get(KEY)).thenReturn(Mono.empty());

        StepVerifier.create(client(webClientRespondingNotFound()).getCustomer("cust1"))
                .expectError(CustomerNotFoundException.class)
                .verify();

        verify(valueOperations, never())
                .set(any(String.class), any(CustomerInfo.class), any(Duration.class));
    }

    @Test
    void getCustomer_redisCaidoAlLeer_igualFuncionaPorRest() {
        when(valueOperations.get(KEY))
                .thenReturn(Mono.error(new RuntimeException("redis down")));
        when(valueOperations.set(eq(KEY), any(CustomerInfo.class), any(Duration.class)))
                .thenReturn(Mono.error(new RuntimeException("redis down")));

        StepVerifier.create(client(webClientRespondingOk()).getCustomer("cust1"))
                .expectNextMatches(info -> "cust1".equals(info.id()))
                .verifyComplete();
    }
}
