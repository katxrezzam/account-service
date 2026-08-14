package com.bootcamp.accountservice.exception;

/**
 * customer-service no respondio (caido, timeout, error 5xx). Sin Resilience4j todavia (Fase 2):
 * por ahora esto simplemente se traduce a un 503 claro en vez de colgar la request.
 */
public class CustomerServiceUnavailableException extends RuntimeException {
    public CustomerServiceUnavailableException(String customerId, Throwable cause) {
        super("No se pudo validar el cliente " + customerId + " contra customer-service", cause);
    }
}
