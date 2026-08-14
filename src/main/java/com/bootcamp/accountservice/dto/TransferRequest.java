package com.bootcamp.accountservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * DTO de entrada para transferencias. destinationAccountId es el id de cuenta (el mismo
 * identificador que usa el resto de la API) - el enunciado no distingue mecanicamente entre
 * transferir a una cuenta propia o a un tercero del mismo banco, en los dos casos la cuenta
 * destino vive en account-service. La cuenta origen viene por path variable, igual que en
 * depositos/retiros.
 */
public record TransferRequest(
        @NotBlank(message = "destinationAccountId es obligatorio") String destinationAccountId,
        @NotNull(message = "amount es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount debe ser mayor a 0")
        BigDecimal amount) {
}
