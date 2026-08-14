package com.bootcamp.accountservice.dto;

import com.bootcamp.accountservice.model.Account;
import java.math.BigDecimal;

/** Mapeo manual entidad&lt;-&gt;DTO (mismo criterio que customer-service: sin MapStruct todavia). */
public final class AccountMapper {

    private AccountMapper() {
    }

    public static Account toEntity(AccountRequest request) {
        return Account.builder()
                .accountType(request.accountType())
                .holders(request.holders())
                .signers(request.signers())
                .balance(request.openingAmount() != null ? request.openingAmount() : BigDecimal.ZERO)
                .allowedMovementDay(request.allowedMovementDay())
                .build();
    }

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
