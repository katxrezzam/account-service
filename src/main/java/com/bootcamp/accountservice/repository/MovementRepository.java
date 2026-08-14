package com.bootcamp.accountservice.repository;

import com.bootcamp.accountservice.model.Movement;
import java.time.Instant;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Repositorio reactivo de {@link Movement}. */
public interface MovementRepository extends ReactiveMongoRepository<Movement, String> {

    Flux<Movement> findByAccountId(String accountId);

    /** Usado por report-service (via GET /accounts/{id}/movements?from=&to=) para el reporte
     * general por cliente en un intervalo. */
    Flux<Movement> findByAccountIdAndTimestampBetween(String accountId, Instant start, Instant end);

    /** Cuenta movimientos del mes en curso, usado para el limite mensual de ahorro y el "un
     * movimiento por mes" de plazo fijo. */
    Mono<Long> countByAccountIdAndTimestampBetween(String accountId, Instant start, Instant end);

    /** Idempotencia: si ya existe un movimiento con esta clave, se devuelve tal cual en vez de
     * volver a aplicar el efecto sobre el saldo. */
    Mono<Movement> findByIdempotencyKey(String idempotencyKey);
}
