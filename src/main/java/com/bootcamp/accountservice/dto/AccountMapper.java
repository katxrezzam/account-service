package com.bootcamp.accountservice.dto;

import com.bootcamp.accountservice.model.Account;
import java.math.BigDecimal;

/** Mapeo manual entidad&lt;-&gt;DTO (mismo criterio que customer-service: sin MapStruct
 * todavia). */
public final class AccountMapper {

    private AccountMapper() {
    }

    /** Arma una entidad nueva (sin id/auditoria) desde el request de creacion. */
    public static Account toEntity(AccountRequest request) {
        BigDecimal openingAmount = request.openingAmount() != null
                ? request.openingAmount() : BigDecimal.ZERO;
        return Account.builder()
                .accountType(request.accountType())
                .holders(request.holders())
                .signers(request.signers())
                .balance(openingAmount)
                .allowedMovementDay(request.allowedMovementDay())
                .build();
    }

    /** Convierte la entidad al DTO de salida. */
    public static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountType(),
                account.getHolders(),
                account.getSigners(),
                account.getBalance(),
                account.getAllowedMovementDay(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
