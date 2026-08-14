package com.bootcamp.accountservice.dto;

import com.bootcamp.accountservice.model.CustomerType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Subconjunto de la respuesta de GET /customers/{id} en customer-service que a account-service le
 * interesa. Jackson por defecto falla ante propiedades desconocidas; @JsonIgnoreProperties hace
 * que ignore el resto (firstName, businessName, documentNumber, etc.) en vez de fallar solo por
 * no necesitarlos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerInfo(String id, CustomerType customerType) {
}
