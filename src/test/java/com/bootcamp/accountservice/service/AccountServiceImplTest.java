package com.bootcamp.accountservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bootcamp.accountservice.client.CardClient;
import com.bootcamp.accountservice.client.CustomerClient;
import com.bootcamp.accountservice.dto.AccountRequest;
import com.bootcamp.accountservice.dto.CustomerInfo;
import com.bootcamp.accountservice.dto.MovementRequest;
import com.bootcamp.accountservice.exception.AccountNotFoundException;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private MovementRepository movementRepository;
    @Mock
    private ReactiveMongoTemplate mongoTemplate;
    @Mock
    private CustomerClient customerClient;
    @Mock
    private CardClient cardClient;
    @Mock
    private AccountFeeService accountFeeService;

    private AccountServiceImpl service;

    private static final int MAX_MONTHLY_SAVINGS = 5;
    private static final BigDecimal EXCESS_FEE = new BigDecimal("5.00");

    @BeforeEach
    void setUp() {
        service = new AccountServiceImpl(accountRepository, movementRepository, mongoTemplate,
                customerClient, cardClient, accountFeeService, MAX_MONTHLY_SAVINGS, EXCESS_FEE);
    }

    private Account savingsAccount() {
        return Account.builder()
                .id("acc1")
                .accountType(AccountType.SAVINGS)
                .profile(AccountProfile.STANDARD)
                .holders(List.of("cust1"))
                .signers(List.of())
                .balance(new BigDecimal("100.00"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ---------- create ----------

    @Test
    void create_personalValido_creaLaCuenta() {
        AccountRequest request = new AccountRequest(AccountType.SAVINGS, null, List.of("cust1"), List.of(), new BigDecimal("50.00"), null);
        when(customerClient.getCustomer("cust1")).thenReturn(Mono.just(new CustomerInfo("cust1", CustomerType.PERSONAL)));
        when(accountRepository.existsByHoldersAndAccountType("cust1", AccountType.SAVINGS)).thenReturn(Mono.just(false));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(savingsAccount()));

        StepVerifier.create(service.create(request))
                .expectNextMatches(response -> "acc1".equals(response.id()) && response.accountType() == AccountType.SAVINGS)
                .verifyComplete();
    }

    @Test
    void create_personalYaTieneCuentaDeEseTipo_falla() {
        AccountRequest request = new AccountRequest(AccountType.SAVINGS, null, List.of("cust1"), List.of(), new BigDecimal("50.00"), null);
        when(customerClient.getCustomer("cust1")).thenReturn(Mono.just(new CustomerInfo("cust1", CustomerType.PERSONAL)));
        when(accountRepository.existsByHoldersAndAccountType("cust1", AccountType.SAVINGS)).thenReturn(Mono.just(true));

        StepVerifier.create(service.create(request))
                .expectError(InvalidBusinessRuleException.class)
                .verify();
    }

    @Test
    void create_personalConMultiplesHolders_falla() {
        AccountRequest request = new AccountRequest(AccountType.SAVINGS, null, List.of("cust1", "cust2"), List.of(), new BigDecimal("50.00"), null);
        when(customerClient.getCustomer("cust1")).thenReturn(Mono.just(new CustomerInfo("cust1", CustomerType.PERSONAL)));
        when(customerClient.getCustomer("cust2")).thenReturn(Mono.just(new CustomerInfo("cust2", CustomerType.PERSONAL)));

        StepVerifier.create(service.create(request))
                .expectError(InvalidBusinessRuleException.class)
                .verify();
    }

    @Test
    void create_businessConCuentaAhorro_falla() {
        AccountRequest request = new AccountRequest(AccountType.SAVINGS, null, List.of("bus1"), List.of(), new BigDecimal("50.00"), null);
        when(customerClient.getCustomer("bus1")).thenReturn(Mono.just(new CustomerInfo("bus1", CustomerType.BUSINESS)));

        StepVerifier.create(service.create(request))
                .expectError(InvalidBusinessRuleException.class)
                .verify();
    }

    @Test
    void create_businessConCorriente_creaLaCuenta() {
        AccountRequest request = new AccountRequest(AccountType.CHECKING, null, List.of("bus1", "bus2"), List.of("signer1"), new BigDecimal("0"), null);
        Account saved = Account.builder().id("acc2").accountType(AccountType.CHECKING).profile(AccountProfile.STANDARD).holders(List.of("bus1", "bus2")).signers(List.of("signer1")).balance(BigDecimal.ZERO).build();
        // ahora se valida CADA holder y signer contra customer-service, no solo el primero
        when(customerClient.getCustomer("bus1")).thenReturn(Mono.just(new CustomerInfo("bus1", CustomerType.BUSINESS)));
        when(customerClient.getCustomer("bus2")).thenReturn(Mono.just(new CustomerInfo("bus2", CustomerType.BUSINESS)));
        when(customerClient.getCustomer("signer1")).thenReturn(Mono.just(new CustomerInfo("signer1", CustomerType.PERSONAL)));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(service.create(request))
                .expectNextMatches(response -> response.accountType() == AccountType.CHECKING)
                .verifyComplete();
    }

    @Test
    void create_signerInexistente_falla() {
        AccountRequest request = new AccountRequest(AccountType.CHECKING, null, List.of("bus1"), List.of("signer-fantasma"), new BigDecimal("0"), null);
        when(customerClient.getCustomer("bus1")).thenReturn(Mono.just(new CustomerInfo("bus1", CustomerType.BUSINESS)));
        when(customerClient.getCustomer("signer-fantasma"))
                .thenReturn(Mono.error(new com.bootcamp.accountservice.exception.CustomerNotFoundException("signer-fantasma")));

        StepVerifier.create(service.create(request))
                .expectError(com.bootcamp.accountservice.exception.CustomerNotFoundException.class)
                .verify();
    }

    @Test
    void create_plazoFijoSinDia_falla() {
        AccountRequest request = new AccountRequest(AccountType.FIXED_TERM, null, List.of("cust1"), List.of(), new BigDecimal("50.00"), null);
        when(customerClient.getCustomer("cust1")).thenReturn(Mono.just(new CustomerInfo("cust1", CustomerType.PERSONAL)));

        StepVerifier.create(service.create(request))
                .expectError(InvalidBusinessRuleException.class)
                .verify();
    }

    // ---------- create: perfiles VIP / PYME (D8) ----------

    @Test
    void create_vipConTarjeta_creaLaCuenta() {
        AccountRequest request = new AccountRequest(AccountType.SAVINGS, AccountProfile.VIP, List.of("cust1"), List.of(), new BigDecimal("500.00"), null);
        Account saved = Account.builder().id("acc-vip").accountType(AccountType.SAVINGS).profile(AccountProfile.VIP).holders(List.of("cust1")).signers(List.of()).balance(new BigDecimal("500.00")).build();
        when(customerClient.getCustomer("cust1")).thenReturn(Mono.just(new CustomerInfo("cust1", CustomerType.PERSONAL)));
        when(accountRepository.existsByHoldersAndAccountType("cust1", AccountType.SAVINGS)).thenReturn(Mono.just(false));
        when(cardClient.hasCreditCard("cust1")).thenReturn(Mono.just(true));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(service.create(request))
                .expectNextMatches(response -> response.profile() == AccountProfile.VIP)
                .verifyComplete();
    }

    @Test
    void create_vipSinTarjeta_falla() {
        AccountRequest request = new AccountRequest(AccountType.SAVINGS, AccountProfile.VIP, List.of("cust1"), List.of(), new BigDecimal("500.00"), null);
        when(customerClient.getCustomer("cust1")).thenReturn(Mono.just(new CustomerInfo("cust1", CustomerType.PERSONAL)));
        when(cardClient.hasCreditCard("cust1")).thenReturn(Mono.just(false));

        StepVerifier.create(service.create(request))
                .expectError(InvalidBusinessRuleException.class)
                .verify();
    }

    @Test
    void create_vipEnCuentaCorriente_falla() {
        AccountRequest request = new AccountRequest(AccountType.CHECKING, AccountProfile.VIP, List.of("cust1"), List.of(), new BigDecimal("500.00"), null);
        when(customerClient.getCustomer("cust1")).thenReturn(Mono.just(new CustomerInfo("cust1", CustomerType.PERSONAL)));

        StepVerifier.create(service.create(request))
                .expectError(InvalidBusinessRuleException.class)
                .verify();
    }

    @Test
    void create_pymeEmpresarialConTarjeta_creaLaCuenta() {
        AccountRequest request = new AccountRequest(AccountType.CHECKING, AccountProfile.PYME, List.of("bus1"), List.of(), new BigDecimal("0"), null);
        Account saved = Account.builder().id("acc-pyme").accountType(AccountType.CHECKING).profile(AccountProfile.PYME).holders(List.of("bus1")).signers(List.of()).balance(BigDecimal.ZERO).build();
        when(customerClient.getCustomer("bus1")).thenReturn(Mono.just(new CustomerInfo("bus1", CustomerType.BUSINESS)));
        when(cardClient.hasCreditCard("bus1")).thenReturn(Mono.just(true));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(service.create(request))
                .expectNextMatches(response -> response.profile() == AccountProfile.PYME)
                .verifyComplete();
    }

    @Test
    void create_pymeClientePersonal_falla() {
        AccountRequest request = new AccountRequest(AccountType.CHECKING, AccountProfile.PYME, List.of("cust1"), List.of(), new BigDecimal("0"), null);
        when(customerClient.getCustomer("cust1")).thenReturn(Mono.just(new CustomerInfo("cust1", CustomerType.PERSONAL)));

        StepVerifier.create(service.create(request))
                .expectError(InvalidBusinessRuleException.class)
                .verify();
    }

    // ---------- update ----------

    @Test
    void update_valido_reejecutaValidacionYActualiza() {
        Account existing = savingsAccount();
        Account updated = savingsAccount();
        updated.setHolders(List.of("cust1"));
        com.bootcamp.accountservice.dto.AccountUpdateRequest request =
                new com.bootcamp.accountservice.dto.AccountUpdateRequest(List.of("cust1"), List.of());

        when(accountRepository.findById("acc1")).thenReturn(Mono.just(existing));
        when(customerClient.getCustomer("cust1")).thenReturn(Mono.just(new CustomerInfo("cust1", CustomerType.PERSONAL)));
        when(accountRepository.existsByHoldersAndAccountTypeAndIdNot("cust1", AccountType.SAVINGS, "acc1"))
                .thenReturn(Mono.just(false));
        when(accountRepository.save(existing)).thenReturn(Mono.just(updated));

        StepVerifier.create(service.update("acc1", request))
                .expectNextMatches(response -> response.holders().contains("cust1"))
                .verifyComplete();
    }

    @Test
    void update_nuevoHolderYaTieneOtraCuentaDeEseTipo_falla() {
        Account existing = savingsAccount();
        com.bootcamp.accountservice.dto.AccountUpdateRequest request =
                new com.bootcamp.accountservice.dto.AccountUpdateRequest(List.of("cust2"), List.of());

        when(accountRepository.findById("acc1")).thenReturn(Mono.just(existing));
        when(customerClient.getCustomer("cust2")).thenReturn(Mono.just(new CustomerInfo("cust2", CustomerType.PERSONAL)));
        // cust2 ya tiene OTRA cuenta de ahorro (distinta de "acc1", que es la que se esta editando)
        when(accountRepository.existsByHoldersAndAccountTypeAndIdNot("cust2", AccountType.SAVINGS, "acc1"))
                .thenReturn(Mono.just(true));

        StepVerifier.create(service.update("acc1", request))
                .expectError(InvalidBusinessRuleException.class)
                .verify();
    }

    @Test
    void update_agregaSegundoHolderPersonal_falla() {
        Account existing = savingsAccount();
        com.bootcamp.accountservice.dto.AccountUpdateRequest request =
                new com.bootcamp.accountservice.dto.AccountUpdateRequest(List.of("cust1", "cust2"), List.of());

        when(accountRepository.findById("acc1")).thenReturn(Mono.just(existing));
        when(customerClient.getCustomer("cust1")).thenReturn(Mono.just(new CustomerInfo("cust1", CustomerType.PERSONAL)));
        when(customerClient.getCustomer("cust2")).thenReturn(Mono.just(new CustomerInfo("cust2", CustomerType.PERSONAL)));

        StepVerifier.create(service.update("acc1", request))
                .expectError(InvalidBusinessRuleException.class)
                .verify();
    }

    // ---------- findById / delete ----------

    @Test
    void findById_inexistente_falla() {
        when(accountRepository.findById("no-existe")).thenReturn(Mono.empty());

        StepVerifier.create(service.findById("no-existe"))
                .expectError(AccountNotFoundException.class)
                .verify();
    }

    @Test
    void delete_conSaldoDistintoDeCero_falla() {
        when(accountRepository.findById("acc1")).thenReturn(Mono.just(savingsAccount()));

        StepVerifier.create(service.delete("acc1"))
                .expectError(InvalidBusinessRuleException.class)
                .verify();
    }

    @Test
    void delete_conSaldoCero_seElimina() {
        Account zeroBalance = savingsAccount();
        zeroBalance.setBalance(BigDecimal.ZERO);
        when(accountRepository.findById("acc1")).thenReturn(Mono.just(zeroBalance));
        when(accountRepository.delete(zeroBalance)).thenReturn(Mono.empty());

        StepVerifier.create(service.delete("acc1")).verifyComplete();
    }

    // ---------- deposit / withdraw ----------

    @Test
    void deposit_sinIdempotencyKey_falla() {
        StepVerifier.create(service.deposit("acc1", new MovementRequest(new BigDecimal("10")), null))
                .expectError(InvalidBusinessRuleException.class)
                .verify();
    }

    @Test
    void deposit_conClaveYaUsada_devuelveElMovimientoExistenteSinReaplicar() {
        Movement existing = Movement.builder().id("mv1").accountId("acc1").type(MovementType.DEPOSIT)
                .amount(new BigDecimal("10")).balanceAfter(new BigDecimal("110")).timestamp(Instant.now())
                .idempotencyKey("key-1").build();
        when(movementRepository.findByIdempotencyKey("key-1")).thenReturn(Mono.just(existing));

        StepVerifier.create(service.deposit("acc1", new MovementRequest(new BigDecimal("10")), "key-1"))
                .expectNextMatches(response -> "mv1".equals(response.id()))
                .verifyComplete();

        verify(accountFeeService, never()).chargeFee(anyString(), any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void withdraw_saldoInsuficiente_falla() {
        Account account = savingsAccount();
        when(movementRepository.findByIdempotencyKey("key-2")).thenReturn(Mono.empty());
        when(accountRepository.findById("acc1")).thenReturn(Mono.just(account));
        when(movementRepository.countByAccountIdAndTimestampBetween(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just(0L));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), org.mockito.ArgumentMatchers.eq(Account.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.withdraw("acc1", new MovementRequest(new BigDecimal("99999")), "key-2"))
                .expectError(InsufficientFundsException.class)
                .verify();
    }

    @Test
    void withdraw_valido_actualizaSaldoYRegistraMovimiento() {
        Account account = savingsAccount();
        Account updated = savingsAccount();
        updated.setBalance(new BigDecimal("90.00"));
        Movement saved = Movement.builder().id("mv2").accountId("acc1").type(MovementType.WITHDRAWAL)
                .amount(new BigDecimal("10")).balanceAfter(new BigDecimal("90.00")).timestamp(Instant.now())
                .idempotencyKey("key-3").build();

        when(movementRepository.findByIdempotencyKey("key-3")).thenReturn(Mono.empty());
        when(accountRepository.findById("acc1")).thenReturn(Mono.just(account));
        when(movementRepository.countByAccountIdAndTimestampBetween(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just(0L));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), org.mockito.ArgumentMatchers.eq(Account.class)))
                .thenReturn(Mono.just(updated));
        when(movementRepository.save(any(Movement.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(service.withdraw("acc1", new MovementRequest(new BigDecimal("10")), "key-3"))
                .expectNextMatches(response -> "mv2".equals(response.id()) && response.balanceAfter().compareTo(new BigDecimal("90.00")) == 0)
                .verifyComplete();

        verify(accountFeeService, never()).chargeFee(anyString(), any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void deposit_ahorroSuperaLimiteMensual_sePermiteYCobraComision() {
        // D8 (2026-08-14): antes esto rechazaba el movimiento; ahora se permite y se cobra
        // bank.accounts.movements.excess-fee (Parte II del enunciado).
        Account account = savingsAccount();
        Account updated = savingsAccount();
        updated.setBalance(new BigDecimal("110.00"));
        Movement saved = Movement.builder().id("mv4").accountId("acc1").type(MovementType.DEPOSIT)
                .amount(new BigDecimal("10")).balanceAfter(new BigDecimal("110.00")).timestamp(Instant.now())
                .idempotencyKey("key-4").build();

        when(movementRepository.findByIdempotencyKey("key-4")).thenReturn(Mono.empty());
        when(accountRepository.findById("acc1")).thenReturn(Mono.just(account));
        when(movementRepository.countByAccountIdAndTimestampBetween(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just((long) MAX_MONTHLY_SAVINGS));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), org.mockito.ArgumentMatchers.eq(Account.class)))
                .thenReturn(Mono.just(updated));
        when(movementRepository.save(any(Movement.class))).thenReturn(Mono.just(saved));
        when(accountFeeService.chargeFee(anyString(), any(BigDecimal.class), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.deposit("acc1", new MovementRequest(new BigDecimal("10")), "key-4"))
                .expectNextMatches(response -> "mv4".equals(response.id()))
                .verifyComplete();

        verify(accountFeeService, times(1))
                .chargeFee("acc1", EXCESS_FEE, "key-4:excess-fee",
                        "Comision por exceso de movimientos mensuales");
    }

    @Test
    void deposit_checkingSuperaLimiteMensual_sePermiteYCobraComision() {
        // Parte II amplio el mecanismo de comision a CHECKING (Fase 1 la dejaba "sin limite").
        Account account = Account.builder().id("acc-chk").accountType(AccountType.CHECKING)
                .profile(AccountProfile.STANDARD).holders(List.of("bus1")).signers(List.of())
                .balance(new BigDecimal("1000.00")).build();
        Account updated = Account.builder().id("acc-chk").accountType(AccountType.CHECKING)
                .profile(AccountProfile.STANDARD).holders(List.of("bus1")).signers(List.of())
                .balance(new BigDecimal("1010.00")).build();
        Movement saved = Movement.builder().id("mv5").accountId("acc-chk").type(MovementType.DEPOSIT)
                .amount(new BigDecimal("10")).balanceAfter(new BigDecimal("1010.00")).timestamp(Instant.now())
                .idempotencyKey("key-5").build();

        when(movementRepository.findByIdempotencyKey("key-5")).thenReturn(Mono.empty());
        when(accountRepository.findById("acc-chk")).thenReturn(Mono.just(account));
        when(movementRepository.countByAccountIdAndTimestampBetween(anyString(), any(Instant.class), any(Instant.class)))
                .thenReturn(Mono.just((long) MAX_MONTHLY_SAVINGS));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), org.mockito.ArgumentMatchers.eq(Account.class)))
                .thenReturn(Mono.just(updated));
        when(movementRepository.save(any(Movement.class))).thenReturn(Mono.just(saved));
        when(accountFeeService.chargeFee(anyString(), any(BigDecimal.class), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.deposit("acc-chk", new MovementRequest(new BigDecimal("10")), "key-5"))
                .expectNextMatches(response -> "mv5".equals(response.id()))
                .verifyComplete();

        verify(accountFeeService, times(1))
                .chargeFee("acc-chk", EXCESS_FEE, "key-5:excess-fee",
                        "Comision por exceso de movimientos mensuales");
    }

    @Test
    void findMovements_cuentaExistente_retornaLista() {
        when(accountRepository.findById("acc1")).thenReturn(Mono.just(savingsAccount()));
        Movement m = Movement.builder().id("mv1").accountId("acc1").type(MovementType.DEPOSIT)
                .amount(BigDecimal.TEN).balanceAfter(new BigDecimal("110")).timestamp(Instant.now()).build();
        when(movementRepository.findByAccountId("acc1")).thenReturn(Flux.just(m));

        StepVerifier.create(service.findMovements("acc1"))
                .expectNextCount(1)
                .verifyComplete();
    }
}
