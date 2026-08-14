package com.bootcamp.accountservice.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountId) {
        super("Saldo insuficiente en la cuenta " + accountId + " para el retiro solicitado");
    }
}
