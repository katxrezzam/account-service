package com.bootcamp.accountservice.exception;

/**
 * card-service no respondio (caido, timeout, error 5xx, circuito abierto) mientras se validaba el
 * requisito de tarjeta de credito de un perfil VIP/PYME. Mismo criterio que
 * CustomerServiceUnavailableException: se traduce a un 503 claro en vez de colgar la request.
 */
public class CardServiceUnavailableException extends RuntimeException {
    public CardServiceUnavailableException(String customerId, Throwable cause) {
        super("No se pudo validar la tarjeta de credito del cliente " + customerId
                + " contra card-service", cause);
    }
}
