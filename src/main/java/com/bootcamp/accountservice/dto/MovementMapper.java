package com.bootcamp.accountservice.dto;

import com.bootcamp.accountservice.model.Movement;

/** Mapeo manual entidad Movement -&gt; DTO de salida. */
public final class MovementMapper {

    private MovementMapper() {
    }

    /** Convierte la entidad al DTO de salida. */
    public static MovementResponse toResponse(Movement movement) {
        return new MovementResponse(
                movement.getId(),
                movement.getAccountId(),
                movement.getType(),
                movement.getAmount(),
                movement.getBalanceAfter(),
                movement.getTimestamp());
    }
}
