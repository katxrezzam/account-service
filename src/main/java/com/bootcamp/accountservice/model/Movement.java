package com.bootcamp.accountservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Movimiento (deposito/retiro) de una cuenta. Vive en account-service, no en un servicio de
 * movimientos aparte (decision D4: cada servicio dueño guarda sus propios movimientos en I/II).
 */
@Document(collection = "movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movement {

    @Id
    private String id;

    private String accountId;

    private MovementType type;

    private BigDecimal amount;

    /** Saldo de la cuenta inmediatamente despues de aplicar este movimiento (para auditoria). */
    private BigDecimal balanceAfter;

    private Instant timestamp;

    /** Clave de idempotencia provista por el cliente (header Idempotency-Key). Unica. */
    @Indexed(unique = true)
    private String idempotencyKey;

    private String correlationId;

    /** Solo se usa en movimientos tipo FEE, para dejar auditable el motivo del cobro (ej.
     * "Comision por exceso de movimientos mensuales"). Null en DEPOSIT/WITHDRAWAL. */
    private String description;

    /** Solo se usa en movimientos que forman parte de una transferencia (deposito/retiro con su
     * contraparte, y el deposito de compensacion si la transferencia fallo a mitad de camino):
     * el id de la otra cuenta involucrada. Null en depositos/retiros sueltos y en comisiones. */
    private String counterpartyAccountId;
}
