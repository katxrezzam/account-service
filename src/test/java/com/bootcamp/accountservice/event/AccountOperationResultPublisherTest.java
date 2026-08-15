package com.bootcamp.accountservice.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class AccountOperationResultPublisherTest {

    @Mock
    private KafkaTemplate<String, AccountOperationResult> kafkaTemplate;

    private AccountOperationResultPublisher publisher;

    private AccountOperationResult result() {
        return new AccountOperationResult(
                "transfer1", Leg.OUT, true, new BigDecimal("90.00"), null, "key-1:out",
                Instant.now());
    }

    @Test
    void publish_envuelveElResultadoConLaClaveDeLaTransferencia() {
        publisher = new AccountOperationResultPublisher(kafkaTemplate);
        when(kafkaTemplate.send(eq(AccountOperationResultPublisher.TOPIC), eq("transfer1"), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publish(result());

        verify(kafkaTemplate)
                .send(eq(AccountOperationResultPublisher.TOPIC), eq("transfer1"), any());
    }

    @Test
    void publish_siKafkaFalla_noPropagaLaExcepcion() {
        publisher = new AccountOperationResultPublisher(kafkaTemplate);
        CompletableFuture<SendResult<String, AccountOperationResult>> failed =
                new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka caido"));
        when(kafkaTemplate.send(eq(AccountOperationResultPublisher.TOPIC), eq("transfer1"), any()))
                .thenReturn(failed);

        // no debe tirar: publish() es best-effort, un Kafka caido no puede tumbar la operacion
        // de negocio que lo dispara (ver comentario de la clase).
        publisher.publish(result());
    }
}
