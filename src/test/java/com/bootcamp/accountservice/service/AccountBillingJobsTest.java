package com.bootcamp.accountservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bootcamp.accountservice.model.Account;
import com.bootcamp.accountservice.model.AccountBalanceSnapshot;
import com.bootcamp.accountservice.model.AccountProfile;
import com.bootcamp.accountservice.model.AccountType;
import com.bootcamp.accountservice.repository.AccountBalanceSnapshotRepository;
import com.bootcamp.accountservice.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Los jobs son {@code void}/{@code @Scheduled} y disparan la pipeline reactiva con
 * {@code .subscribe()} (patron habitual para entrypoints no reactivos). Como todos los mocks
 * devuelven Mono/Flux ya resueltos (Mono.just/Flux.just), la suscripcion se completa en el mismo
 * hilo de la llamada - no hace falta esperar ni usar Awaitility.
 */
@ExtendWith(MockitoExtension.class)
class AccountBillingJobsTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private AccountBalanceSnapshotRepository snapshotRepository;
    @Mock
    private AccountFeeService accountFeeService;

    private static final BigDecimal VIP_MIN_DAILY_AVERAGE = new BigDecimal("500.00");
    private static final BigDecimal VIP_SHORTFALL_FEE = new BigDecimal("15.00");
    private static final BigDecimal CHECKING_MAINTENANCE_FEE = new BigDecimal("15.00");

    private AccountBillingJobs jobs;

    @BeforeEach
    void setUp() {
        jobs = new AccountBillingJobs(accountRepository, snapshotRepository, accountFeeService,
                VIP_MIN_DAILY_AVERAGE, VIP_SHORTFALL_FEE, CHECKING_MAINTENANCE_FEE);
    }

    private Account vipAccount() {
        return Account.builder().id("acc-vip").accountType(AccountType.SAVINGS)
                .profile(AccountProfile.VIP).balance(new BigDecimal("300.00")).build();
    }

    // ---------- snapshot diario ----------

    @Test
    void captureDailySnapshots_sinSnapshotHoy_loGuarda() {
        when(accountRepository.findByProfile(AccountProfile.VIP)).thenReturn(Flux.just(vipAccount()));
        when(snapshotRepository.existsByAccountIdAndSnapshotDate(eq("acc-vip"), any(LocalDate.class)))
                .thenReturn(Mono.just(false));
        when(snapshotRepository.save(any(AccountBalanceSnapshot.class)))
                .thenReturn(Mono.just(AccountBalanceSnapshot.builder().id("snap1").build()));

        jobs.captureDailySnapshots();

        verify(snapshotRepository).save(any(AccountBalanceSnapshot.class));
    }

    @Test
    void captureDailySnapshots_yaExisteSnapshotHoy_noDuplica() {
        when(accountRepository.findByProfile(AccountProfile.VIP)).thenReturn(Flux.just(vipAccount()));
        when(snapshotRepository.existsByAccountIdAndSnapshotDate(eq("acc-vip"), any(LocalDate.class)))
                .thenReturn(Mono.just(true));

        jobs.captureDailySnapshots();

        verify(snapshotRepository, never()).save(any(AccountBalanceSnapshot.class));
    }

    // ---------- evaluacion mensual VIP ----------

    @Test
    void evaluateVipMonthlyAverage_promedioPorDebajoDelMinimo_cobraComision() {
        Account vip = vipAccount();
        AccountBalanceSnapshot below = AccountBalanceSnapshot.builder()
                .accountId("acc-vip").balance(new BigDecimal("200.00")).build();
        when(accountRepository.findByProfile(AccountProfile.VIP)).thenReturn(Flux.just(vip));
        when(snapshotRepository.findByAccountIdAndSnapshotDateBetween(
                eq("acc-vip"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Flux.just(below));
        when(accountFeeService.chargeFee(eq("acc-vip"), eq(VIP_SHORTFALL_FEE), anyString(), anyString()))
                .thenReturn(Mono.empty());

        jobs.evaluateVipMonthlyAverage();

        verify(accountFeeService, times(1))
                .chargeFee(eq("acc-vip"), eq(VIP_SHORTFALL_FEE), anyString(), anyString());
    }

    @Test
    void evaluateVipMonthlyAverage_promedioSuficiente_noCobra() {
        Account vip = vipAccount();
        AccountBalanceSnapshot above = AccountBalanceSnapshot.builder()
                .accountId("acc-vip").balance(new BigDecimal("800.00")).build();
        when(accountRepository.findByProfile(AccountProfile.VIP)).thenReturn(Flux.just(vip));
        when(snapshotRepository.findByAccountIdAndSnapshotDateBetween(
                eq("acc-vip"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Flux.just(above));

        jobs.evaluateVipMonthlyAverage();

        verify(accountFeeService, never())
                .chargeFee(anyString(), any(BigDecimal.class), anyString(), anyString());
    }

    @Test
    void evaluateVipMonthlyAverage_sinSnapshotsDelMes_omiteSinCobrar() {
        Account vip = vipAccount();
        when(accountRepository.findByProfile(AccountProfile.VIP)).thenReturn(Flux.just(vip));
        when(snapshotRepository.findByAccountIdAndSnapshotDateBetween(
                eq("acc-vip"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Flux.empty());

        jobs.evaluateVipMonthlyAverage();

        verify(accountFeeService, never())
                .chargeFee(anyString(), any(BigDecimal.class), anyString(), anyString());
    }

    // ---------- comision de mantenimiento CHECKING ----------

    @Test
    void chargeCheckingMaintenanceFee_cobraATodasLasCuentasNoPyme() {
        Account checking1 = Account.builder().id("acc-chk1").accountType(AccountType.CHECKING)
                .profile(AccountProfile.STANDARD).balance(new BigDecimal("1000.00")).build();
        Account checking2 = Account.builder().id("acc-chk2").accountType(AccountType.CHECKING)
                .profile(AccountProfile.STANDARD).balance(new BigDecimal("2000.00")).build();
        when(accountRepository.findByAccountTypeAndProfileNot(AccountType.CHECKING, AccountProfile.PYME))
                .thenReturn(Flux.just(checking1, checking2));
        when(accountFeeService.chargeFee(anyString(), eq(CHECKING_MAINTENANCE_FEE), anyString(), anyString()))
                .thenReturn(Mono.empty());

        jobs.chargeCheckingMaintenanceFee();

        verify(accountFeeService).chargeFee(eq("acc-chk1"), eq(CHECKING_MAINTENANCE_FEE), anyString(), anyString());
        verify(accountFeeService).chargeFee(eq("acc-chk2"), eq(CHECKING_MAINTENANCE_FEE), anyString(), anyString());
    }
}
