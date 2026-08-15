package com.bootcamp.accountservice.model;

/** Tipo de movimiento sobre una cuenta. FEE es cualquier comision cobrada (exceso de
 * movimientos, mantenimiento de CHECKING, incumplimiento de promedio VIP) - ver
 * {@link com.bootcamp.accountservice.service.AccountFeeService}. FEE_REVERSAL es la devolucion de
 * una FEE que se cobro por un movimiento que despues se revirtio por una compensacion de Saga
 * (D6/D7, hallazgo 8.7 en PLAN-DE-ACCION.md). */
public enum MovementType {
    DEPOSIT,
    WITHDRAWAL,
    FEE,
    FEE_REVERSAL
}
