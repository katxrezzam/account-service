package com.bootcamp.accountservice.service;

import com.bootcamp.accountservice.exception.AccountNotFoundException;
import com.bootcamp.accountservice.model.Account;
import com.bootcamp.accountservice.model.Movement;
import com.bootcamp.accountservice.model.MovementType;
import com.bootcamp.accountservice.repository.MovementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Cobro atomico de comisiones sobre una cuenta, compartido por {@link AccountServiceImpl}
 * (comision por exceso de movimientos) y los jobs batch de {@link AccountBillingJobs} (comision
 * de mantenimiento de CHECKING y comision por incumplimiento de promedio VIP). Centralizado aca
 * para no duplicar la logica de update atomico + registro de movimiento en tres lugares.
 *
 * <p>idempotencyKey es obligatoria y debe ser deterministica para el evento que cobra (ej.
 * "{@code excess-fee:<idempotencyKey del movimiento que la origino>}" o
 * "{@code maintenance:<accountId>:<yyyy-MM>}"): asi un reintento de la operacion que la origino,
 * o una reejecucion del job mensual, no duplica el cobro.
 */
@Service
public class AccountFeeService {

    private static final Logger log = LoggerFactory.getLogger(AccountFeeService.class);

    private final ReactiveMongoTemplate mongoTemplate;
    private final MovementRepository movementRepository;

    public AccountFeeService(
            ReactiveMongoTemplate mongoTemplate, MovementRepository movementRepository) {
        this.mongoTemplate = mongoTemplate;
        this.movementRepository = movementRepository;
    }

    /** Cobra la comision si no se cobro ya con esta idempotencyKey. No falla si la cuenta ya no
     * existe al momento del cobro (puede pasar en un job batch si se borro entretanto) - solo lo
     * loguea, para no tumbar el resto de la corrida. */
    public Mono<Void> chargeFee(
            String accountId, BigDecimal amount, String idempotencyKey, String description) {
        return movementRepository.findByIdempotencyKey(idempotencyKey)
                .hasElement()
                .flatMap(alreadyCharged -> alreadyCharged
                        ? Mono.empty()
                        : doCharge(accountId, amount, idempotencyKey, description))
                .onErrorResume(AccountNotFoundException.class, ex -> {
                    log.warn("No se pudo cobrar comision, cuenta inexistente accountId={}",
                            accountId);
                    return Mono.empty();
                });
    }

    private Mono<Void> doCharge(
            String accountId, BigDecimal amount, String idempotencyKey, String description) {
        Query query = Query.query(Criteria.where("_id").is(accountId));
        Update update = new Update().inc("balance", amount.negate()).currentDate("updatedAt");
        return mongoTemplate
                .findAndModify(
                        query, update,
                        FindAndModifyOptions.options().returnNew(true), Account.class)
                .switchIfEmpty(Mono.defer(() ->
                        Mono.error(new AccountNotFoundException(accountId))))
                .flatMap(updated -> movementRepository.save(Movement.builder()
                        .accountId(accountId)
                        .type(MovementType.FEE)
                        .amount(amount)
                        .balanceAfter(updated.getBalance())
                        .timestamp(Instant.now())
                        .idempotencyKey(idempotencyKey)
                        .description(description)
                        .build()))
                .doOnNext(movement -> log.info(
                        "Comision cobrada accountId={} amount={} desc={}",
                        accountId, amount, description))
                .then();
    }
}
