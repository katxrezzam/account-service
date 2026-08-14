package com.bootcamp.accountservice.exception;

/** Se lanza cuando no existe una cuenta con el id pedido. Mapea a HTTP 404. */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String id) {
        super("No existe una cuenta con id " + id);
    }
}
