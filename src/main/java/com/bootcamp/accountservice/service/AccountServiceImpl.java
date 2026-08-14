package com.bootcamp.accountservice.service;

import com.bootcamp.accountservice.client.CardClient;
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
import com.bootcamp.accountservice.model.AccountProfile;
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
    private final CardClient cardClient;
    private final AccountFeeService accountFeeService;
    private final int movementsFreeMonthlyLimit;
    private final BigDecimal movementsExcessFee;

    /** Inyeccion por constructor; los limites/montos de comisiones vienen de la config
     * externalizada (D8: ninguno esta en el enunciado, son parametros de negocio). */
    public AccountServiceImpl(
            AccountRepository accountRepository,
            MovementRepository movementRepository,
            ReactiveMongoTemplate mongoTemplate,
            CustomerClient customerClient,
            CardClient cardClient,
            AccountFeeService accountFeeService,
            @Value("${bank.accounts.movements.free-monthly-limit}")
            int movementsFreeMonthlyLimit,
            @Value("${bank.accounts.movements.excess-fee}")
            BigDecimal movementsExcessFee) {
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
        this.mongoTemplate = mongoTemplate;
        this.customerClient = customerClient;
        this.cardClient = cardClient;
        this.accountFeeService = accountFeeService;
        this.movementsFreeMonthlyLimit = movementsFreeMonthlyLimit;
        this.movementsExcessFee = movementsExcessFee;
    }

    @Override
    public Mono<AccountResponse> create(AccountRequest request) {
        AccountProfile profile = resolveProfile(request.profile());
        return validateHoldersAndSigners(request.holders(), request.signers())
                .flatMap(primaryHolderInfo -> validateFixedTermDay(
                                request.accountType(), request.allowedMovementDay())
                        .then(validateHolderStructure(
                                request.accountType(),
                                request.holders(),
                                request.signers(),
                                primaryHolderInfo.customerType()))
                        // profile antes de type-uniqueness: es una validacion local (sin I/O
                        // salvo el chequeo de tarjeta), tiene sentido descartar una combinacion
                        // invalida antes de pagar una consulta a Mongo.
                        .then(Mono.defer(() -> validateProfileRequirements(
                                request.accountType(),
                                profile,
                                primaryHolderInfo.customerType(),
                                request.holders().get(0))))
                        .then(Mono.defer(() -> validateTypeUniqueness(
                                request.holders().get(0),
                                request.accountType(),
                                primaryHolderInfo.customerType(),
                                null))))
                .then(Mono.defer(() ->
                        accountRepository.save(AccountMapper.toEntity(request, profile))))
                .doOnNext(saved -> log.info("Cuenta creada id={} accountType={} profile={}",
                        saved.getId(), saved.getAccountType(), saved.getProfile()))
                .map(AccountMapper::toResponse);
    }

    private AccountProfile resolveProfile(AccountProfile requested) {
        return requested != null ? requested : AccountProfile.STANDARD;
    }

    /**
     * VIP solo aplica a SAVINGS de clientes personales; PYME solo a CHECKING de clientes
     * empresariales (D8). Ambos exigen que el cliente ya tenga una tarjeta de credito con el
     * banco al momento de la creacion (D5) - se valida contra card-service via CardClient, mismo
     * patron de circuit breaker + timeout que CustomerClient.
     */
    private Mono<Void> validateProfileRequirements(
            AccountType accountType,
            AccountProfile profile,
            CustomerType customerType,
            String primaryHolderId) {
        return Mono.defer(() -> {
            if (profile == AccountProfile.VIP && accountType != AccountType.SAVINGS) {
                return Mono.error(new InvalidBusinessRuleException(
                        "El perfil VIP solo aplica a cuentas de ahorro"));
            }
            if (profile == AccountProfile.PYME
                    && (accountType != AccountType.CHECKING
                            || customerType != CustomerType.BUSINESS)) {
                return Mono.error(new InvalidBusinessRuleException(
                        "El perfil PYME solo aplica a cuentas corrientes de clientes "
                                + "empresariales"));
            }
            if (profile == AccountProfile.STANDARD) {
                return Mono.empty();
            }
            return cardClient.hasCreditCard(primaryHolderId)
                    .flatMap(hasCard -> hasCard
                            ? Mono.<Void>empty()
                            : Mono.error(new InvalidBusinessRuleException(
                                    "El perfil " + profile + " requiere que el cliente ya tenga "
                                            + "una tarjeta de credito con el banco")));
        });
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
                                        existing.getAccountType(),
                                        request.holders(),
                                        request.signers(),
                                        primaryHolderInfo.customerType())
                                .then(Mono.defer(() -> validateTypeUniqueness(
                                        request.holders().get(0),
                                        existing.getAccountType(),
                                        primaryHolderInfo.customerType(),
                                        id))))
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
                    boolean hasBalance = account.getBalance() != null
                            && account.getBalance().compareTo(BigDecimal.ZERO) != 0;
                    if (hasBalance) {
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
    public Mono<MovementResponse> deposit(
            String accountId, MovementRequest request, String idempotencyKey) {
        return processMovement(accountId, request, idempotencyKey, MovementType.DEPOSIT);
    }

    @Override
    public Mono<MovementResponse> withdraw(
            String accountId, MovementRequest request, String idempotencyKey) {
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
    private Mono<CustomerInfo> validateHoldersAndSigners(
            List<String> holders, List<String> signers) {
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
                        .orElseGet(() ->
                                Mono.error(new CustomerNotFoundException(primaryHolderId))));
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
            AccountType accountType,
            List<String> holders,
            List<String> signers,
            CustomerType customerType) {
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
            String customerId,
            AccountType accountType,
            CustomerType customerType,
            String excludeAccountId) {
        if (customerType != CustomerType.PERSONAL) {
            return Mono.empty();
        }
        Mono<Boolean> existsMono = excludeAccountId == null
                ? accountRepository.existsByHoldersAndAccountType(customerId, accountType)
                : accountRepository.existsByHoldersAndAccountTypeAndIdNot(
                        customerId, accountType, excludeAccountId);
        return existsMono.flatMap(exists -> exists
                ? Mono.<Void>error(new InvalidBusinessRuleException(
                        "El cliente ya tiene una cuenta de tipo " + accountType))
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
                .switchIfEmpty(Mono.defer(
                        () -> executeMovement(accountId, request, idempotencyKey, type)));
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
                .flatMap(account -> resolveMovementRules(account)
                        .flatMap(feeApplies -> applyBalanceChange(accountId, request.amount(), type)
                                .flatMap(updatedAccount -> recordMovement(
                                                updatedAccount, type, request.amount(),
                                                idempotencyKey)
                                        .flatMap(movement -> feeApplies
                                                ? chargeExcessFee(accountId, idempotencyKey)
                                                        .thenReturn(movement)
                                                : Mono.just(movement)))))
                .doOnNext(movement -> log.info(
                        "Movimiento {} registrado accountId={}", type, accountId))
                .map(MovementMapper::toResponse);
    }

    /**
     * SAVINGS y CHECKING comparten el mismo mecanismo (D8, Parte II): hasta
     * movementsFreeMonthlyLimit por mes sin comision; superado, el movimiento se permite igual
     * pero se cobra bank.accounts.movements.excess-fee (devuelve true). FIXED_TERM no usa este
     * mecanismo, sigue con su bloqueo duro de producto (1 movimiento/mes, dia pactado) - su
     * propia regla ya es mas estricta que el limite de comision, nunca lo alcanzaria.
     */
    private Mono<Boolean> resolveMovementRules(Account account) {
        Instant now = Instant.now();
        if (account.getAccountType() == AccountType.FIXED_TERM) {
            return Mono.defer(() -> {
                int today = LocalDate.ofInstant(now, ZoneOffset.UTC).getDayOfMonth();
                boolean wrongDay = account.getAllowedMovementDay() == null
                        || today != account.getAllowedMovementDay();
                if (wrongDay) {
                    return Mono.<Boolean>error(new InvalidBusinessRuleException(
                            "La cuenta a plazo fijo solo admite movimientos el dia "
                                    + account.getAllowedMovementDay() + " del mes"));
                }
                return countMovementsThisMonth(account.getId(), now)
                        .flatMap(count -> count > 0
                                ? Mono.<Boolean>error(new InvalidBusinessRuleException(
                                        "La cuenta a plazo fijo ya tuvo su movimiento de este mes"))
                                : Mono.just(false));
            });
        }
        return countMovementsThisMonth(account.getId(), now)
                .map(count -> count >= movementsFreeMonthlyLimit);
    }

    private Mono<Void> chargeExcessFee(String accountId, String idempotencyKey) {
        return accountFeeService.chargeFee(
                accountId,
                movementsExcessFee,
                idempotencyKey + ":excess-fee",
                "Comision por exceso de movimientos mensuales");
    }

    private Mono<Long> countMovementsThisMonth(String accountId, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Instant start = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = today.withDayOfMonth(1).plusMonths(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        return movementRepository.countByAccountIdAndTimestampBetween(accountId, start, end);
    }

    /**
     * Update atomico: para retiros, el filtro exige balance &gt;= amount, asi que si dos retiros
     * concurrentes dejarian la cuenta en negativo, el segundo simplemente no encuentra el
     * documento (no hay lectura+escritura separada que pueda pisarse).
     */
    private Mono<Account> applyBalanceChange(
            String accountId, BigDecimal amount, MovementType type) {
        BigDecimal delta = type == MovementType.DEPOSIT ? amount : amount.negate();
        Query query = Query.query(Criteria.where("_id").is(accountId));
        if (type == MovementType.WITHDRAWAL) {
            query.addCriteria(Criteria.where("balance").gte(amount));
        }
        Update update = new Update().inc("balance", delta).currentDate("updatedAt");
        return mongoTemplate
                .findAndModify(
                        query, update,
                        FindAndModifyOptions.options().returnNew(true), Account.class)
                .switchIfEmpty(Mono.defer(() -> type == MovementType.WITHDRAWAL
                        ? Mono.error(new InsufficientFundsException(accountId))
                        : Mono.error(new AccountNotFoundException(accountId))));
    }

    private Mono<Movement> recordMovement(
            Account account, MovementType type, BigDecimal amount, String idempotencyKey) {
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
