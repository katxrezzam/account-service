package com.bootcamp.accountservice.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bootcamp.accountservice.dto.MovementRequest;
import com.bootcamp.accountservice.dto.MovementResponse;
import com.bootcamp.accountservice.exception.InsufficientFundsException;
import com.bootcamp.accountservice.model.MovementType;
import com.bootcamp.accountservice.service.AccountService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AccountOperationRequestConsumerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private AccountOperationResultPublisher resultPublisher;

    private AccountOperationRequestConsumer consumer;

    private MovementResponse movementResponse(BigDecimal balanceAfter) {
        return new MovementResponse("mov1", "acc1", MovementType.WITHDRAWAL, BigDecimal.TEN,
                balanceAfter, Instant.now(), "yanki transfer", null);
    }

    @Test
    void onAccountOperationRequest_withdrawalExitoso_publicaResultadoConExito() {
        consumer = new AccountOperationRequestConsumer(accountService, resultPublisher);
        AccountOperationRequest request = new AccountOperationRequest("transfer1", Leg.OUT,
                "acc1", OperationType.WITHDRAWAL, BigDecimal.TEN, "key-1", Instant.now());
        when(accountService.withdraw(eq("acc1"), any(MovementRequest.class), eq("key-1")))
                .thenReturn(Mono.just(movementResponse(new BigDecimal("90.00"))));

        consumer.onAccountOperationRequest(request);

        verify(accountService).withdraw(eq("acc1"), any(MovementRequest.class), eq("key-1"));
        verify(accountService, never()).deposit(any(), any(), any());
        verify(resultPublisher).publish(argThat(result -> "transfer1".equals(result.transferId())
                && result.leg() == Leg.OUT
                && result.success()
                && new BigDecimal("90.00").equals(result.balanceAfter())
                && result.errorMessage() == null
                && "key-1".equals(result.idempotencyKey())));
    }

    @Test
    void onAccountOperationRequest_depositoExitoso_publicaResultadoConExito() {
        consumer = new AccountOperationRequestConsumer(accountService, resultPublisher);
        AccountOperationRequest request = new AccountOperationRequest("transfer1", Leg.IN,
                "acc2", OperationType.DEPOSIT, BigDecimal.TEN, "key-2", Instant.now());
        when(accountService.deposit(eq("acc2"), any(MovementRequest.class), eq("key-2")))
                .thenReturn(Mono.just(movementResponse(new BigDecimal("110.00"))));

        consumer.onAccountOperationRequest(request);

        verify(accountService).deposit(eq("acc2"), any(MovementRequest.class), eq("key-2"));
        verify(accountService, never()).withdraw(any(), any(), any());
        verify(resultPublisher).publish(argThat(result -> result.leg() == Leg.IN
                && result.success()
                && new BigDecimal("110.00").equals(result.balanceAfter())));
    }

    @Test
    void onAccountOperationRequest_fallaDeNegocio_publicaResultadoSinExitoConMensaje() {
        consumer = new AccountOperationRequestConsumer(accountService, resultPublisher);
        AccountOperationRequest request = new AccountOperationRequest("transfer1", Leg.OUT,
                "acc1", OperationType.WITHDRAWAL, BigDecimal.TEN, "key-3", Instant.now());
        when(accountService.withdraw(eq("acc1"), any(MovementRequest.class), eq("key-3")))
                .thenReturn(Mono.error(new InsufficientFundsException("acc1")));

        // no debe propagar: el resultado (exito o fallo) siempre se publica, nunca se deja la
        // pata sin respuesta (ver comentario de la clase).
        consumer.onAccountOperationRequest(request);

        verify(resultPublisher).publish(argThat(result -> "transfer1".equals(result.transferId())
                && result.leg() == Leg.OUT
                && !result.success()
                && result.balanceAfter() == null
                && result.errorMessage() != null));
    }

    // ---------- compensacion (idempotencyKey terminada en :compensation) ----------

    @Test
    void onAccountOperationRequest_esCompensacion_seEnrutaAReverseWithdrawalConLaClaveOriginal() {
        consumer = new AccountOperationRequestConsumer(accountService, resultPublisher);
        AccountOperationRequest request = new AccountOperationRequest("transfer1", Leg.OUT,
                "acc1", OperationType.DEPOSIT, BigDecimal.TEN, "key-4:out:compensation", Instant.now());
        when(accountService.reverseWithdrawal(
                        eq("acc1"), any(MovementRequest.class), eq("key-4:out:compensation"),
                        eq("key-4:out")))
                .thenReturn(Mono.just(movementResponse(new BigDecimal("100.00"))));

        consumer.onAccountOperationRequest(request);

        verify(accountService).reverseWithdrawal(
                eq("acc1"), any(MovementRequest.class), eq("key-4:out:compensation"), eq("key-4:out"));
        verify(accountService, never()).deposit(any(), any(), any());
        verify(accountService, never()).withdraw(any(), any(), any());
        verify(resultPublisher).publish(argThat(result -> result.leg() == Leg.OUT
                && result.success()
                && new BigDecimal("100.00").equals(result.balanceAfter())));
    }

    @Test
    void onAccountOperationRequest_compensacionFalla_publicaResultadoSinExito() {
        consumer = new AccountOperationRequestConsumer(accountService, resultPublisher);
        AccountOperationRequest request = new AccountOperationRequest("transfer1", Leg.OUT,
                "acc1", OperationType.DEPOSIT, BigDecimal.TEN, "key-5:out:compensation", Instant.now());
        when(accountService.reverseWithdrawal(
                        eq("acc1"), any(MovementRequest.class), eq("key-5:out:compensation"),
                        eq("key-5:out")))
                .thenReturn(Mono.error(new RuntimeException("cuenta caida")));

        consumer.onAccountOperationRequest(request);

        verify(resultPublisher).publish(argThat(result -> !result.success()
                && result.errorMessage() != null));
    }
}
