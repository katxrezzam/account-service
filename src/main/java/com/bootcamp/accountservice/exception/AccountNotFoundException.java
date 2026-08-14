package com.bootcamp.accountservice.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String id) {
        super("No existe una cuenta con id " + id);
    }
}
