package com.bootcamp.accountservice.exception;

/** Regla de negocio violada: tipo de cuenta no permitido para el cliente, limite de movimientos
 * mensual excedido, dia de movimiento invalido para plazo fijo, holders/signers invalidos, etc. */
public class InvalidBusinessRuleException extends RuntimeException {
    public InvalidBusinessRuleException(String message) {
        super(message);
    }
}
