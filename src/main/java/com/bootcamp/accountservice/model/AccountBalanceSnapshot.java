package com.bootcamp.accountservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Saldo de fin de dia de una cuenta VIP. Alimentado por un job batch diario, separado del flujo
 * OLTP (ver D8/CONVENTIONS.md): calcular el promedio diario mensual exigido a las cuentas VIP no
 * es viable sobre los movimientos al momento de la consulta, hace falta este historico.
 *
 * <p>Coleccion propia ("account_balance_snapshots"), no embebida en {@link Account}, para no
 * inflar el documento principal de la cuenta con hasta 31 entradas por mes.
 */
@Document(collection = "account_balance_snapshots")
@CompoundIndex(
        name = "account_date_idx", def = "{'accountId': 1, 'snapshotDate': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountBalanceSnapshot {

    @Id
    private String id;

    private String accountId;

    private LocalDate snapshotDate;

    private BigDecimal balance;

    private Instant createdAt;
}
