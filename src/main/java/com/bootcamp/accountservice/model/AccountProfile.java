package com.bootcamp.accountservice.model;

/**
 * Perfil de la cuenta. STANDARD no tiene requisitos ni comisiones extra sobre las ya definidas por
 * {@link AccountType}. VIP solo aplica a cuentas {@link AccountType#SAVINGS} de clientes
 * {@link CustomerType#PERSONAL}; PYME solo a {@link AccountType#CHECKING} de clientes
 * {@link CustomerType#BUSINESS} (ver reglas D8 en CONVENTIONS.md). Ambos exigen que el cliente ya
 * tenga una tarjeta de crédito con el banco al momento de la creación de la cuenta.
 */
public enum AccountProfile {
    STANDARD,
    VIP,
    PYME
}
