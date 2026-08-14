package com.bootcamp.accountservice.exception;

/** El customerId de un holder/signer no corresponde a ningun cliente real en customer-service. */
public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String customerId) {
        super("No existe un cliente con id " + customerId + " en customer-service");
    }
}
