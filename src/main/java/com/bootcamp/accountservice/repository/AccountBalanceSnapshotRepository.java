package com.bootcamp.accountservice.repository;

import com.bootcamp.accountservice.model.AccountBalanceSnapshot;
import java.time.LocalDate;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Repositorio reactivo de {@link AccountBalanceSnapshot}, usado por los jobs batch de
 * facturacion VIP (ver AccountBillingJobs). */
public interface AccountBalanceSnapshotRepository
        extends ReactiveMongoRepository<AccountBalanceSnapshot, String> {

    /** Evita duplicar el snapshot si el job diario se reintenta el mismo dia. */
    Mono<Boolean> existsByAccountIdAndSnapshotDate(String accountId, LocalDate snapshotDate);

    /** Snapshots del mes a evaluar (start/end inclusive), para calcular el promedio diario VIP. */
    Flux<AccountBalanceSnapshot> findByAccountIdAndSnapshotDateBetween(
            String accountId, LocalDate start, LocalDate end);
}
