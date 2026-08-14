package com.bootcamp.accountservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bootcamp.accountservice.model.Account;
import com.bootcamp.accountservice.model.Movement;
import com.bootcamp.accountservice.repository.MovementRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AccountFeeServiceTest {

    @Mock
    private ReactiveMongoTemplate mongoTemplate;
    @Mock
    private MovementRepository movementRepository;

    private AccountFeeService service;

    @BeforeEach
    void setUp() {
        service = new AccountFeeService(mongoTemplate, movementRepository);
    }

    @Test
    void chargeFee_yaCobrada_noVuelveACobrar() {
        Movement existing = Movement.builder().id("mv1").accountId("acc1")
                .idempotencyKey("maintenance:acc1:2026-08").build();
        when(movementRepository.findByIdempotencyKey("maintenance:acc1:2026-08"))
                .thenReturn(Mono.just(existing));

        StepVerifier.create(service.chargeFee(
                        "acc1", new BigDecimal("15.00"), "maintenance:acc1:2026-08", "Mantenimiento"))
                .verifyComplete();

        verify(mongoTemplate, never())
                .findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Account.class));
    }

    @Test
    void chargeFee_nuevoCobra_actualizaBalanceYRegistraMovimientoFee() {
        Account updated = Account.builder().id("acc1").balance(new BigDecimal("85.00")).build();
        when(movementRepository.findByIdempotencyKey("excess-fee:key-1")).thenReturn(Mono.empty());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Account.class)))
                .thenReturn(Mono.just(updated));
        when(movementRepository.save(any(Movement.class))).thenReturn(Mono.just(
                Movement.builder().id("mv2").accountId("acc1").build()));

        StepVerifier.create(service.chargeFee(
                        "acc1", new BigDecimal("5.00"), "excess-fee:key-1", "Comision por exceso"))
                .verifyComplete();

        verify(movementRepository).save(any(Movement.class));
    }

    @Test
    void chargeFee_cuentaInexistente_noPropagaError() {
        when(movementRepository.findByIdempotencyKey("maintenance:acc-borrada:2026-08")).thenReturn(Mono.empty());
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Account.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.chargeFee(
                        "acc-borrada", new BigDecimal("15.00"), "maintenance:acc-borrada:2026-08", "Mantenimiento"))
                .verifyComplete();

        verify(movementRepository, never()).save(any(Movement.class));
    }
}
