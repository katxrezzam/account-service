package com.bootcamp.accountservice.model;

/**
 * Tipo de cuenta pasiva. SAVINGS y FIXED_TERM son exclusivas de clientes personales; BUSINESS
 * solo puede tener CHECKING (ver D8 / CONVENTIONS.md).
 */
public enum AccountType {
    SAVINGS,
    CHECKING,
    FIXED_TERM
}
