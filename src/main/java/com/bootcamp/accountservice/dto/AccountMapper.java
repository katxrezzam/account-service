package com.bootcamp.accountservice.dto;

import com.bootcamp.accountservice.model.Account;
import com.bootcamp.accountservice.model.AccountProfile;
import java.math.BigDecimal;

/** Mapeo manual entidad&lt;-&gt;DTO (mismo criterio que customer-service: sin MapStruct
 * todavia). */
public final class AccountMapper {

    private AccountMapper() {
    }

    /** profile resuelto (nunca null: si el request no lo trae, ya vino default a STANDARD desde
     * el service - ver AccountServiceImpl.resolveProfile) para no duplicar esa regla aca. */
    public static Account toEntity(AccountRequest request, AccountProfile profile) {
        BigDecimal openingAmount = request.openingAmount() != null
                ? request.openingAmount() : BigDecimal.ZERO;
        return Account.builder()
                .accountType(request.accountType())
                .profile(profile)
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
                account.getProfile(),
                account.getHolders(),
                account.getSigners(),
                account.getBalance(),
                account.getAllowedMovementDay(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
