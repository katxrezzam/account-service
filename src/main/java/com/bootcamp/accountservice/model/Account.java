package com.bootcamp.accountservice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cuenta bancaria pasiva. Persistida en la coleccion "accounts" de accountdb.
 *
 * <p>{@link #holders} siempre tiene al menos un elemento: para cuentas de cliente PERSONAL,
 * exactamente uno (el propio cliente); para BUSINESS, uno o mas titulares. {@link #signers} solo
 * aplica a cuentas empresariales (puede quedar vacia). {@link #allowedMovementDay} solo aplica a
 * {@link AccountType#FIXED_TERM}. El {@link #balance} nunca se modifica con save() directo desde
 * el service: las operaciones de deposito/retiro usan un update atomico en Mongo (ver
 * AccountServiceImpl) para no perder actualizaciones bajo concurrencia.
 *
 * <p>{@link #profile} es {@link AccountProfile#STANDARD} salvo que la cuenta se haya creado como
 * VIP o PYME (ver D8/CONVENTIONS.md); ambos exigen tarjeta de credito previa y habilitan
 * comisiones/requisitos propios que no aplican a una cuenta estandar del mismo tipo.
 */
@Document(collection = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    private String id;

    private AccountType accountType;

    @Builder.Default
    private AccountProfile profile = AccountProfile.STANDARD;

    private List<String> holders;

    private List<String> signers;

    private BigDecimal balance;

    private Integer allowedMovementDay;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
