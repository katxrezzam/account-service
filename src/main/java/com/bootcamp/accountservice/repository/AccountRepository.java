package com.bootcamp.accountservice.repository;

import com.bootcamp.accountservice.model.Account;
import com.bootcamp.accountservice.model.AccountProfile;
import com.bootcamp.accountservice.model.AccountType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Repositorio reactivo de {@link Account}. */
public interface AccountRepository extends ReactiveMongoRepository<Account, String> {

    /**
     * MongoDB compara igualdad contra un campo array como pertenencia (holders contiene
     * customerId), sin necesidad de "Containing" en el nombre del metodo. Se usa para el limite
     * de "maximo 1 cuenta de cada tipo" de clientes personales (D8).
     */
    Mono<Boolean> existsByHoldersAndAccountType(String customerId, AccountType accountType);

    /** Misma idea, pero excluyendo una cuenta puntual - usado en update() para no autobloquear
     * la cuenta que se esta editando contra si misma. */
    Mono<Boolean> existsByHoldersAndAccountTypeAndIdNot(
            String customerId, AccountType accountType, String excludedId);

    /** Usado por el job diario de snapshot y el job mensual de evaluacion VIP
     * (ver AccountBillingJobs). */
    Flux<Account> findByProfile(AccountProfile profile);

    /** Usado por el job mensual de comision de mantenimiento: todas las CHECKING salvo las
     * PYME, que estan exentas (D8). */
    Flux<Account> findByAccountTypeAndProfileNot(AccountType accountType, AccountProfile profile);
}
