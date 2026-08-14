package com.bootcamp.accountservice.service;

import com.bootcamp.accountservice.model.Account;
import com.bootcamp.accountservice.model.AccountBalanceSnapshot;
import com.bootcamp.accountservice.model.AccountProfile;
import com.bootcamp.accountservice.model.AccountType;
import com.bootcamp.accountservice.repository.AccountBalanceSnapshotRepository;
import com.bootcamp.accountservice.repository.AccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Jobs batch de facturacion de cuentas, separados del flujo OLTP (D8/CONVENTIONS.md):
 * <ol>
 *   <li>snapshot diario de saldo de las cuentas VIP (no alcanza con calcular el promedio sobre
 *       movimientos al momento de la consulta);</li>
 *   <li>evaluacion mensual del promedio VIP contra el minimo configurado, con cobro de comision
 *       si no se alcanza (sin perder el estatus VIP);</li>
 *   <li>comision de mantenimiento mensual de las cuentas CHECKING que no son PYME (las PYME
 *       estan exentas).</li>
 * </ol>
 *
 * <p>{@code @EnableScheduling} vive en {@link com.bootcamp.accountservice.config.SchedulingConfig}
 * separado de la clase principal, no aca. Los tres jobs corren en la misma instancia de
 * account-service; si en algun momento hay mas de una replica hace falta un lock distribuido
 * (ej. ShedLock) para que no se disparen duplicados - no aplica todavia (una sola instancia
 * local), queda anotado para cuando haya Docker/K8s con replicas.
 */
@Component
public class AccountBillingJobs {

    private static final Logger log = LoggerFactory.getLogger(AccountBillingJobs.class);

    private final AccountRepository accountRepository;
    private final AccountBalanceSnapshotRepository snapshotRepository;
    private final AccountFeeService accountFeeService;
    private final BigDecimal vipMinDailyAverage;
    private final BigDecimal vipAvgShortfallFee;
    private final BigDecimal checkingMaintenanceFee;

    /** Inyeccion por constructor; los montos de comisiones y el minimo VIP vienen de la config
     * externalizada (D8: ninguno esta en el enunciado). */
    public AccountBillingJobs(
            AccountRepository accountRepository,
            AccountBalanceSnapshotRepository snapshotRepository,
            AccountFeeService accountFeeService,
            @Value("${bank.accounts.savings.vip.min-daily-average}")
            BigDecimal vipMinDailyAverage,
            @Value("${bank.accounts.savings.vip.avg-shortfall-fee}")
            BigDecimal vipAvgShortfallFee,
            @Value("${bank.accounts.checking.maintenance-fee}")
            BigDecimal checkingMaintenanceFee) {
        this.accountRepository = accountRepository;
        this.snapshotRepository = snapshotRepository;
        this.accountFeeService = accountFeeService;
        this.vipMinDailyAverage = vipMinDailyAverage;
        this.vipAvgShortfallFee = vipAvgShortfallFee;
        this.checkingMaintenanceFee = checkingMaintenanceFee;
    }

    /** 01:00 todos los dias: snapshot de saldo de cada cuenta VIP. Idempotente por el indice
     * unico accountId+snapshotDate de AccountBalanceSnapshot - un reintento el mismo dia no
     * duplica el registro. */
    @Scheduled(cron = "0 0 1 * * *")
    public void captureDailySnapshots() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        accountRepository.findByProfile(AccountProfile.VIP)
                .flatMap(account -> saveSnapshotIfMissing(account, today)
                        .onErrorResume(ex -> {
                            log.error("Fallo el snapshot diario de la cuenta VIP {}",
                                    account.getId(), ex);
                            return Mono.empty();
                        }))
                .subscribe();
    }

    private Mono<Void> saveSnapshotIfMissing(Account account, LocalDate today) {
        return snapshotRepository.existsByAccountIdAndSnapshotDate(account.getId(), today)
                .flatMap(exists -> exists
                        ? Mono.<Void>empty()
                        : snapshotRepository.save(AccountBalanceSnapshot.builder()
                                        .accountId(account.getId())
                                        .snapshotDate(today)
                                        .balance(account.getBalance())
                                        .createdAt(Instant.now())
                                        .build())
                                .then());
    }

    /** 01:30 el dia 1 de cada mes: evalua el promedio diario del mes recien cerrado de cada
     * cuenta VIP; si no llega a vipMinDailyAverage, cobra vipAvgShortfallFee sin quitar el
     * estatus VIP (D8). Idempotente via la idempotencyKey derivada que arma AccountFeeService. */
    @Scheduled(cron = "0 30 1 1 * *")
    public void evaluateVipMonthlyAverage() {
        YearMonth closedMonth = YearMonth.now(ZoneOffset.UTC).minusMonths(1);
        accountRepository.findByProfile(AccountProfile.VIP)
                .flatMap(account -> evaluateAccount(account, closedMonth)
                        .onErrorResume(ex -> {
                            log.error("Fallo la evaluacion de promedio VIP de la cuenta {}",
                                    account.getId(), ex);
                            return Mono.empty();
                        }))
                .subscribe();
    }

    private Mono<Void> evaluateAccount(Account account, YearMonth closedMonth) {
        LocalDate start = closedMonth.atDay(1);
        LocalDate end = closedMonth.atEndOfMonth();
        return snapshotRepository
                .findByAccountIdAndSnapshotDateBetween(account.getId(), start, end)
                .map(AccountBalanceSnapshot::getBalance)
                .collectList()
                .flatMap(balances -> {
                    if (balances.isEmpty()) {
                        log.warn("Cuenta VIP {} sin snapshots del mes {}, se omite la evaluacion",
                                account.getId(), closedMonth);
                        return Mono.empty();
                    }
                    BigDecimal average = balances.stream()
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .divide(BigDecimal.valueOf(balances.size()), 2, RoundingMode.HALF_UP);
                    if (average.compareTo(vipMinDailyAverage) >= 0) {
                        return Mono.empty();
                    }
                    String idempotencyKey = "vip-shortfall:" + account.getId() + ":" + closedMonth;
                    return accountFeeService.chargeFee(
                            account.getId(),
                            vipAvgShortfallFee,
                            idempotencyKey,
                            "Comision por no alcanzar el promedio diario minimo VIP en "
                                    + closedMonth);
                });
    }

    /** 02:00 el dia 1 de cada mes: cobra la comision de mantenimiento a toda cuenta CHECKING que
     * no sea PYME (D8: PYME esta exenta). Idempotente via la idempotencyKey derivada. */
    @Scheduled(cron = "0 0 2 1 * *")
    public void chargeCheckingMaintenanceFee() {
        YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
        accountRepository
                .findByAccountTypeAndProfileNot(AccountType.CHECKING, AccountProfile.PYME)
                .flatMap(account -> {
                    String idempotencyKey = "maintenance:" + account.getId() + ":" + currentMonth;
                    return accountFeeService.chargeFee(
                                    account.getId(),
                                    checkingMaintenanceFee,
                                    idempotencyKey,
                                    "Comision de mantenimiento de cuenta corriente " + currentMonth)
                            .onErrorResume(ex -> {
                                log.error("Fallo el cobro de mantenimiento de la cuenta {}",
                                        account.getId(), ex);
                                return Mono.empty();
                            });
                })
                .subscribe();
    }
}
