package com.bootcamp.accountservice.exception;

import com.bootcamp.accountservice.config.CorrelationIdWebFilter;
import java.time.Instant;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

/**
 * Traduce cada excepcion de dominio (y las de validacion de Bean Validation) a una respuesta HTTP
 * consistente, siempre con {@code correlationId} para poder auditar/rastrear el error.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex, ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), exchange);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerNotFound(
            CustomerNotFoundException ex, ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), exchange);
    }

    @ExceptionHandler(CustomerServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCustomerServiceUnavailable(
            CustomerServiceUnavailableException ex, ServerWebExchange exchange) {
        log.error("customer-service no disponible, correlationId={}", correlationId(exchange), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), exchange);
    }

    @ExceptionHandler(CardServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleCardServiceUnavailable(
            CardServiceUnavailableException ex, ServerWebExchange exchange) {
        log.error("card-service no disponible, correlationId={}", correlationId(exchange), ex);
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), exchange);
    }

    @ExceptionHandler(InvalidBusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRule(
            InvalidBusinessRuleException ex, ServerWebExchange exchange) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), exchange);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
            InsufficientFundsException ex, ServerWebExchange exchange) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), exchange);
    }

    /** Errores de Bean Validation (@Valid) sobre el body del request en un handler reactivo. */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            WebExchangeBindException ex, ServerWebExchange exchange) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, exchange);
    }

    /**
     * Excepciones propias de Spring que ya traen su propio status code correcto (ej:
     * NoResourceFoundException). Mismo bug encontrado y arreglado en customer-service via Newman.
     */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> handleErrorResponseException(
            ErrorResponseException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return build(status, ex.getMessage(), exchange);
    }

    /** Cualquier excepcion no prevista: no se filtra su mensaje interno al cliente, solo se
     * loguea. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, ServerWebExchange exchange) {
        log.error("Error no controlado, correlationId={}", correlationId(exchange), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrio un error inesperado", exchange);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, ServerWebExchange exchange) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                correlationId(exchange),
                exchange.getRequest().getPath().value());
        return ResponseEntity.status(status).body(body);
    }

    private String correlationId(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(CorrelationIdWebFilter.CORRELATION_ID_ATTRIBUTE);
        return value != null ? value.toString() : "unknown";
    }
}
