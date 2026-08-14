package com.bootcamp.accountservice.exception;

/**
 * credit-service no respondio (caido, timeout, error 5xx, circuito abierto) mientras se
 * validaba deuda vencida (D8, Fase III) antes de dar de alta una cuenta. Mismo criterio que
 * CardServiceUnavailableException: se traduce a un 503 claro en vez de colgar la request.
 */
public class CreditServiceUnavailableException extends RuntimeException {
    public CreditServiceUnavailableException(String customerId, Throwable cause) {
        super("No se pudo validar la deuda vencida del cliente " + customerId
                + " contra credit-service", cause);
    }
}
