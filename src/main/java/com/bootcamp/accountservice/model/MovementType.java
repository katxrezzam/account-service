package com.bootcamp.accountservice.model;

/** Tipo de movimiento sobre una cuenta. FEE es cualquier comision cobrada (exceso de
 * movimientos, mantenimiento de CHECKING, incumplimiento de promedio VIP) - ver
 * {@link com.bootcamp.accountservice.service.AccountFeeService}. */
public enum MovementType {
    DEPOSIT,
    WITHDRAWAL,
    FEE
}
