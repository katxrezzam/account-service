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
}
