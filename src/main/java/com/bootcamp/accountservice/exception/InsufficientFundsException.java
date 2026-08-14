package com.bootcamp.accountservice.exception;

/** Se lanza cuando un retiro superaria el saldo disponible. Mapea a HTTP 422. */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountId) {
        super("Saldo insuficiente en la cuenta " + accountId + " para el retiro solicitado");
    }
}
