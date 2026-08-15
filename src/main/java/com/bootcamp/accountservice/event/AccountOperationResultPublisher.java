package com.bootcamp.accountservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica a Kafka (topic yanki.account-operation-results) el resultado de ejecutar una pata de
 * operacion de cuenta pedida por yanki-service (Entrega 2, D8 Fase III). Efecto secundario
 * best-effort, no bloqueante, mismo criterio que CardEventPublisher/CustomerEventPublisher: si
 * Kafka esta caido, yanki-service no recibe el resultado y esa pata de la transferencia queda sin
 * confirmar - riesgo aceptado y documentado, no un Outbox transaccional.
 */
@Component
public class AccountOperationResultPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(AccountOperationResultPublisher.class);
    static final String TOPIC = "yanki.account-operation-results";

    private final KafkaTemplate<String, AccountOperationResult> kafkaTemplate;

    public AccountOperationResultPublisher(
            KafkaTemplate<String, AccountOperationResult> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /** La clave del mensaje es el transferId: ambas patas de una misma transferencia caen en la
     * misma particion y se procesan en orden. */
    public void publish(AccountOperationResult result) {
        kafkaTemplate.send(TOPIC, result.transferId(), result).whenComplete((sendResult, ex) -> {
            if (ex != null) {
                log.error("No se pudo publicar resultado de operacion transferId={} leg={}",
                        result.transferId(), result.leg(), ex);
            } else {
                log.info("Resultado publicado: transferId={} leg={} success={}",
                        result.transferId(), result.leg(), result.success());
            }
        });
    }
}
