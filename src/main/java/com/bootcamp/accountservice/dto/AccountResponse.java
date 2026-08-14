package com.bootcamp.accountservice.dto;

import com.bootcamp.accountservice.model.AccountType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AccountResponse(
        String id,
        AccountType accountType,
        List<String> holders,
        List<String> signers,
        BigDecimal balance,
        Integer allowedMovementDay,
        Instant createdAt,
        Instant updatedAt) {
}
