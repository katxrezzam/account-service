package com.bootcamp.accountservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * DTO de actualizacion: solo permite modificar holders/signers. accountType y balance no son
 * editables por este endpoint (el balance solo cambia via depositos/retiros; el tipo de cuenta
 * no tiene sentido de negocio cambiarlo una vez creada la cuenta). El service vuelve a correr las
 * mismas reglas de D8 que en create() antes de aceptar el cambio.
 */
public record AccountUpdateRequest(
        @NotEmpty(message = "holders debe tener al menos un customerId")
        List<@NotBlank(message = "cada holder debe ser un customerId no vacio") String> holders,
        List<@NotBlank(message = "cada signer debe ser un customerId no vacio") String> signers) {
}
