package com.bootcamp.accountservice.service;

import com.bootcamp.accountservice.client.CustomerClient;
import com.bootcamp.accountservice.dto.AccountMapper;
import com.bootcamp.accountservice.dto.AccountRequest;
import com.bootcamp.accountservice.dto.AccountResponse;
import com.bootcamp.accountservice.dto.AccountUpdateRequest;
import com.bootcamp.accountservice.dto.CustomerInfo;
import com.bootcamp.accountservice.dto.MovementMapper;
import com.bootcamp.accountservice.dto.MovementRequest;
import com.bootcamp.accountservice.dto.MovementResponse;
import com.bootcamp.accountservice.exception.AccountNotFoundException;
import com.bootcamp.accountservice.exception.CustomerNotFoundException;
import com.bootcamp.accountservice.exception.InsufficientFundsException;
import com.bootcamp.accountservice.exception.InvalidBusinessRuleException;
import com.bootcamp.accountservice.model.Account;
import com.bootcamp.accountservice.model.AccountType;
import com.bootcamp.accountservice.model.CustomerType;
import com.bootcamp.accountservice.model.Movement;
import com.bootcamp.accountservice.model.MovementType;
import com.bootcamp.accountservice.repository.AccountRepository;
import com.bootcamp.accountservice.repository.MovementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementacion de {@link AccountService}. Las validaciones de negocio que pueden fallar van
 * siempre envueltas en Mono.defer (misma leccion aprendida que customer-service). El cambio de
 * saldo en deposito/retiro usa un update atomico de Mongo (findAndModify con $inc), no
 * read-modify-write en Java: sin eso, dos requests concurrentes sobre la misma cuenta podrian
 * perder una actualizacion (lost update) - inaceptable con dinero real.
 */
@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    private final AccountRepository accountRepository;
    private final MovementRepository movementRepository;
    private final ReactiveMongoTemplate mongoTemplate;
    private final CustomerClient customerClient;
    private final int maxMonthlySavingsMovements;

    public AccountServiceImpl(
            AccountRepository accountRepository,
            MovementRepository movementRepository,
            ReactiveMongoTemplate mongoTemplate,
            CustomerClient customerClient,
            @Value("${bank.accounts.savings.max-monthly-movements}") int maxMonthlySavingsMovements) {
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.mongoTemplate = mongoTemplate;
        this.customerClient = customerClient;
        this.maxMonthlySavingsMovements = maxMonthlySavingsMovements;
    }

    @Override
    public Mono<AccountResponse> create(AccountRequest request) {
        return validateHoldersAndSigners(request.holders(), request.signers())
                .flatMap(primaryHolderInfo -> validateFixedTermDay(request.accountType(), request.allowedMovementDay())
                        .then(validateHolderStructure(
                                request.accountType(), request.holders(), request.signers(), primaryHolderInfo.customerType()))
                        .then(Mono.defer(() -> validateTypeUniqueness(
                                request.holders().get(0), request.accountType(), primaryHolderInfo.customerType(), null))))
                .then(Mono.defer(() -> accountRepository.save(AccountMapper.toEntity(request))))
                .doOnNext(saved -> log.info("Cuenta creada id={} accountType={}", saved.getId(), saved.getAccountType()))
                .map(AccountMapper::toResponse);
    }

    @Override
    public Flux<AccountResponse> findAll() {
        return accountRepository.findAll().map(AccountMapper::toResponse);
    }

    @Override
    public Mono<AccountResponse> findById(String id) {
        return findEntityById(id).map(AccountMapper::toResponse);
    }

    @Override
    public Mono<AccountResponse> update(String id, AccountUpdateRequest request) {
        return findEntityById(id)
                .flatMap(existing -> validateHoldersAndSigners(request.holders(), request.signers())
                        .flatMap(primaryHolderInfo -> validateHolderStructure(
                                        existing.getAccountType(), request.holders(), request.signers(), primaryHolderInfo.customerType())
                                .then(Mono.defer(() -> validateTypeUniqueness(
                                        request.holders().get(0), existing.getAccountType(), primaryHolderInfo.customerType(), id))))
                        .then(Mono.defer(() -> {
                            existing.setHolders(request.holders());
                            existing.setSigners(request.signers());
                            return accountRepository.save(existing);
                        })))
                .doOnNext(saved -> log.info("Cuenta actualizada id={}", saved.getId()))
                .map(AccountMapper::toResponse);
    }

    @Override
    public Mono<Void> delete(String id) {
        return findEntityById(id)
                .flatMap(account -> {
                    if (account.getBalance() != null && account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                        return Mono.<Account>error(new InvalidBusinessRuleException(
                                "No se puede eliminar una cuenta con saldo distinto de cero"));
                    }
                    return Mono.just(account);
                })
                .flatMap(accountRepository::delete)
                .doOnSuccess(v -> log.info("Cuenta eliminada id={}", id));
    }

    @Override
    public Flux<MovementResponse> findMovements(String accountId) {
        return findEntityById(accountId)
                .flatMapMany(account -> movementRepository.findByAccountId(accountId))
                .map(MovementMapper::toResponse);
    }

    @Override
    public Mono<MovementResponse> deposit(String accountId, MovementRequest request, String idempotencyKey) {
        return processMovement(accountId, request, idempotencyKey, MovementType.DEPOSIT);
    }

    @Override
    public Mono<MovementResponse> withdraw(String accountId, MovementRequest request, String idempotencyKey) {
        return processMovement(accountId, request, idempotencyKey, MovementType.WITHDRAWAL);
    }

    private Mono<Account> findEntityById(String id) {
        return accountRepository.findById(id)
                .switchIfEmpty(Mono.defer(() -> Mono.error(new AccountNotFoundException(id))));
    }

    /**
     * Valida que TODOS los holders y signers existan como clientes reales en customer-service
     * (no solo el primero - hueco encontrado y cerrado tras el smoke test manual: un signer
     * vacio/inventado pasaba sin chequeo). Devuelve el CustomerInfo del primer holder, que es el
     * que determina las reglas de tipo de cuenta (D8).
     */
    private Mono<CustomerInfo> validateHoldersAndSigners(List<String> holders, List<String> signers) {
        List<String> allIds = new ArrayList<>(holders);
        if (signers != null) {
            allIds.addAll(signers);
        }
        String primaryHolderId = holders.get(0);
        return Flux.fromIterable(allIds)
                .distinct()
                .flatMap(customerClient::getCustomer)
                .collectList()
                .flatMap(infos -> infos.stream()
                        .filter(info -> info.id().equals(primaryHolderId))
                        .findFirst()
                        .map(Mono::just)
                        .orElseGet(() -> Mono.error(new CustomerNotFoundException(primaryHolderId))));
    }

    /** Solo aplica en create(): FIXED_TERM exige allowedMovementDay, no es editable despues. */
    private Mono<Void> validateFixedTermDay(AccountType accountType, Integer allowedMovementDay) {
        if (accountType == AccountType.FIXED_TERM && allowedMovementDay == null) {
            return Mono.error(new InvalidBusinessRuleException(
                    "Cuenta a plazo fijo requiere allowedMovementDay (1-28)"));
        }
        return Mono.empty();
    }

    /**
     * Reglas de D8 sobre holders/signers segun el tipo de cliente. Se usa tanto en create() como
     * en update() (si cambian los holders, se vuelven a validar contra el accountType existente).
     */
    private Mono<Void> validateHolderStructure(
            AccountType accountType, List<String> holders, List<String> signers, CustomerType customerType) {
        return Mono.defer(() -> {
            if (customerType == CustomerType.PERSONAL) {
                if (holders.size() != 1) {
                    return Mono.error(new InvalidBusinessRuleException(
                            "Cuenta de cliente personal debe tener exactamente 1 titular"));
                }
                if (signers != null && !signers.isEmpty()) {
                    return Mono.error(new InvalidBusinessRuleException(
                            "Cuenta de cliente personal no admite firmantes autorizados"));
                }
            } else if (accountType != AccountType.CHECKING) {
                return Mono.error(new InvalidBusinessRuleException(
                        "Cliente empresarial solo puede tener cuentas corrientes"));
            }
            return Mono.empty();
        });
    }

    /**
     * D8: personal no puede repetir accountType. excludeAccountId es null en create(); en
     * update() es el id de la propia cuenta, para no autobloquearse contra si misma.
     */
    private Mono<Void> validateTypeUniqueness(
            String customerId, AccountType accountType, CustomerType customerType, String excludeAccountId) {
        if (customerType != CustomerType.PERSONAL) {
            return Mono.empty();
        }
        Mono<Boolean> existsMono = excludeAccountId == null
                ? accountRepository.existsByHoldersAndAccountType(customerId, accountType)
                : accountRepository.existsByHoldersAndAccountTypeAndIdNot(customerId, accountType, excludeAccountId);
        return existsMono.flatMap(exists -> exists
                ? Mono.<Void>error(new InvalidBusinessRuleException("El cliente ya tiene una cuenta de tipo " + accountType))
                : Mono.empty());
    }

    private Mono<MovementResponse> processMovement(
            String accountId, MovementRequest request, String idempotencyKey, MovementType type) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Mono.error(new InvalidBusinessRuleException(
                    "El header Idempotency-Key es obligatorio en depositos y retiros"));
        }
        return findExistingMovement(idempotencyKey)
                .map(MovementMapper::toResponse)
                .switchIfEmpty(Mono.defer(() -> executeMovement(accountId, request, idempotencyKey, type)));
    }

    private Mono<Movement> findExistingMovement(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Mono.empty();
        }
        return movementRepository.findByIdempotencyKey(idempotencyKey);
    }

    private Mono<MovementResponse> executeMovement(
            String accountId, MovementRequest request, String idempotencyKey, MovementType type) {
        return findEntityById(accountId)
                .flatMap(account -> validateMovementRules(account, type)
                        .then(Mono.defer(() -> applyBalanceChange(accountId, request.amount(), type))))
                .flatMap(updatedAccount -> recordMovement(updatedAccount, type, request.amount(), idempotencyKey))
                .doOnNext(movement -> log.info("Movimiento {} registrado accountId={}", type, accountId))
                .map(MovementMapper::toResponse);
    }

    /** SAVINGS: limite mensual configurable. FIXED_TERM: solo el dia pactado, 1 vez por mes. CHECKING: sin limite en Fase 1. */
    private Mono<Void> validateMovementRules(Account account, MovementType type) {
        Instant now = Instant.now();
        if (account.getAccountType() == AccountType.SAVINGS) {
            return countMovementsThisMonth(account.getId(), now)
                    .flatMap(count -> count >= maxMonthlySavingsMovements
                            ? Mono.<Void>error(new InvalidBusinessRuleException(
                                    "La cuenta de ahorro alcanzo el limite de " + maxMonthlySavingsMovements
                                            + " movimientos mensuales"))
                            : Mono.empty());
        }
        if (account.getAccountType() == AccountType.FIXED_TERM) {
            return Mono.defer(() -> {
                int today = LocalDate.ofInstant(now, ZoneOffset.UTC).getDayOfMonth();
                if (account.getAllowedMovementDay() == null || today != account.getAllowedMovementDay()) {
                    return Mono.<Void>error(new InvalidBusinessRuleException(
                            "La cuenta a plazo fijo solo admite movimientos el dia "
                                    + account.getAllowedMovementDay() + " del mes"));
                }
                return countMovementsThisMonth(account.getId(), now)
                        .flatMap(count -> count > 0
                                ? Mono.<Void>error(new InvalidBusinessRuleException(
                                        "La cuenta a plazo fijo ya tuvo su movimiento de este mes"))
                                : Mono.empty());
            });
        }
        return Mono.empty();
    }

    private Mono<Long> countMovementsThisMonth(String accountId, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Instant start = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return movementRepository.countByAccountIdAndTimestampBetween(accountId, start, end);
    }

    /**
     * Update atomico: para retiros, el filtro exige balance &gt;= amount, asi que si dos retiros
     * concurrentes dejarian la cuenta en negativo, el segundo simplemente no encuentra el
     * documento (no hay lectura+escritura separada que pueda pisarse).
     */
    private Mono<Account> applyBalanceChange(String accountId, BigDecimal amount, MovementType type) {
        BigDecimal delta = type == MovementType.DEPOSIT ? amount : amount.negate();
        Query query = Query.query(Criteria.where("_id").is(accountId));
        if (type == MovementType.WITHDRAWAL) {
            query.addCriteria(Criteria.where("balance").gte(amount));
        }
        Update update = new Update().inc("balance", delta).currentDate("updatedAt");
        return mongoTemplate
                .findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Account.class)
                .switchIfEmpty(Mono.defer(() -> type == MovementType.WITHDRAWAL
                        ? Mono.error(new InsufficientFundsException(accountId))
                        : Mono.error(new AccountNotFoundException(accountId))));
    }

    private Mono<Movement> recordMovement(Account account, MovementType type, BigDecimal amount, String idempotencyKey) {
        Movement movement = Movement.builder()
                .accountId(account.getId())
                .type(type)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .timestamp(Instant.now())
                .idempotencyKey(idempotencyKey)
                .build();
        return movementRepository.save(movement);
    }
}
