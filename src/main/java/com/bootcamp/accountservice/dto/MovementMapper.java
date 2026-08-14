package com.bootcamp.accountservice.dto;

import com.bootcamp.accountservice.model.Movement;

public final class MovementMapper {

    private MovementMapper() {
    }

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
