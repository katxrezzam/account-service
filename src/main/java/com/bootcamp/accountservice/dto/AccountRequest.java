package com.bootcamp.accountservice.dto;

import com.bootcamp.accountservice.model.AccountProfile;
import com.bootcamp.accountservice.model.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO de entrada para crear una cuenta. Las reglas cruzadas (tipo de cliente vs accountType,
 * cantidad de holders, obligatoriedad de allowedMovementDay en FIXED_TERM) se validan en la capa
 * de servicio, porque dependen de una llamada a customer-service y de una combinacion de campos.
 * Cada elemento de holders/signers se valida no-blank aca (constraint sobre el tipo del elemento
 * de la lista); que sea un customerId real se valida en el service contra customer-service.
 *
 * <p>{@link #profile} es opcional: si viene null, se asume {@link AccountProfile#STANDARD}. Si
 * se pide VIP o PYME, el service valida la combinacion con accountType/customerType y el
 * requisito de tarjeta de credito contra card-service (D5/D8) antes de crear la cuenta.
 */
public record AccountRequest(
        @NotNull(message = "accountType es obligatorio") AccountType accountType,
        AccountProfile profile,
        @NotEmpty(message = "holders debe tener al menos un customerId")
        List<@NotBlank(message = "cada holder debe ser un customerId no vacio") String> holders,
        List<@NotBlank(message = "cada signer debe ser un customerId no vacio") String> signers,
        @NotNull(message = "openingAmount es obligatorio")
        @DecimalMin(value = "0.0", message = "openingAmount no puede ser negativo")
        BigDecimal openingAmount,
        @Min(value = 1, message = "allowedMovementDay debe estar entre 1 y 28")
        @Max(value = 28, message = "allowedMovementDay debe estar entre 1 y 28")
        Integer allowedMovementDay) {
}
