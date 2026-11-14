package com.gogleox.scrip.organization;

import com.gogleox.scrip.account.AccountId;
import com.gogleox.scrip.account.AccountType;
import java.util.Objects;
import java.util.Optional;

/** Server-derived account option for a future GUI; it grants no independent authority. */
public record AccountAccess(AccountId accountId, AccountType type, Optional<OrganizationId> organizationId,
                            Optional<OrganizationRole> organizationRole) {
    public AccountAccess {
        Objects.requireNonNull(accountId, "accountId"); Objects.requireNonNull(type, "type");
        organizationId = organizationId == null ? Optional.empty() : organizationId;
        organizationRole = organizationRole == null ? Optional.empty() : organizationRole;
        if ((type == AccountType.ORGANIZATION) != organizationId.isPresent()) throw new IllegalArgumentException("Invalid account access type");
    }
}
