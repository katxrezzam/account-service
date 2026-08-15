package com.bootcamp.accountservice.client;

import com.bootcamp.accountservice.dto.CustomerInfo;
import com.bootcamp.accountservice.exception.CustomerNotFoundException;
import com.bootcamp.accountservice.exception.CustomerServiceUnavailableException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
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
 *
 * <p>Cache-aside en Redis (D8, Fase III: "datos catalogados o maestros"), TTL corto
 * (cache.customer.ttl-seconds) y sin invalidacion activa - el dato cacheado (nombre/tipo de
 * documento/tipo de cliente) no es sensible a un desfasaje de un minuto. Redis es cache, no
 * fuente de verdad: si falla (lectura o escritura), el flujo cae a la llamada REST normal en vez
 * de romper, nunca al reves.
 */
@Component
public class CustomerClient {

    private static final String CIRCUIT_BREAKER_ID = "customer-service";
    private static final String CACHE_KEY_PREFIX = "customer:";

    private static final Logger log = LoggerFactory.getLogger(CustomerClient.class);

    private final WebClient webClient;
    private final ReactiveCircuitBreaker circuitBreaker;
    private final ReactiveRedisTemplate<String, CustomerInfo> redisTemplate;
    private final Duration cacheTtl;

    /** Inyecta el WebClient, la fabrica de circuit breaker, el template de Redis para el cache
     * de clientes y el TTL configurado (cache.customer.ttl-seconds). */
    public CustomerClient(
            WebClient customerServiceWebClient,
            ReactiveCircuitBreakerFactory circuitBreakerFactory,
            ReactiveRedisTemplate<String, CustomerInfo> customerRedisTemplate,
            @Value("${cache.customer.ttl-seconds}") long ttlSeconds) {
        this.webClient = customerServiceWebClient;
        this.circuitBreaker = circuitBreakerFactory.create(CIRCUIT_BREAKER_ID);
        this.redisTemplate = customerRedisTemplate;
        this.cacheTtl = Duration.ofSeconds(ttlSeconds);
    }

    /** Trae el cliente por id: cache-hit devuelve directo, cache-miss (o Redis caido) llama a
     * customer-service y cachea el resultado. Emite CustomerNotFoundException (404) o
     * CustomerServiceUnavailableException (circuito abierto/timeout/otro error). */
    public Mono<CustomerInfo> getCustomer(String customerId) {
        String key = CACHE_KEY_PREFIX + customerId;
        return redisTemplate.opsForValue().get(key)
                .doOnNext(cached -> log.debug("Cache hit de cliente id={}", customerId))
                .onErrorResume(ex -> {
                    log.warn("Redis no disponible al leer el cache de cliente id={}, "
                            + "se sigue por REST", customerId, ex);
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> fetchAndCache(customerId, key)));
    }

    private Mono<CustomerInfo> fetchAndCache(String customerId, String key) {
        Mono<CustomerInfo> call = webClient.get()
                .uri("/customers/{id}", customerId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        response -> Mono.error(new CustomerNotFoundException(customerId)))
                .bodyToMono(CustomerInfo.class);

        return circuitBreaker.run(call, throwable -> mapFallback(customerId, throwable))
                .flatMap(info -> cacheAndReturn(key, info));
    }

    private Mono<CustomerInfo> cacheAndReturn(String key, CustomerInfo info) {
        return redisTemplate.opsForValue().set(key, info, cacheTtl)
                .onErrorResume(ex -> {
                    log.warn("Redis no disponible al escribir el cache de cliente key={}, "
                            + "se ignora", key, ex);
                    return Mono.just(false);
                })
                .thenReturn(info);
    }

    private Mono<CustomerInfo> mapFallback(String customerId, Throwable throwable) {
        if (throwable instanceof CustomerNotFoundException) {
            return Mono.error(throwable);
        }
        return Mono.error(new CustomerServiceUnavailableException(customerId, throwable));
    }
}
