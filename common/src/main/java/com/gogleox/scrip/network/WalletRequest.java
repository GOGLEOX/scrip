package com.gogleox.scrip.network;

import com.gogleox.scrip.account.AccountId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable optional-client request. The actor identity is always supplied by the server transport. */
public record WalletRequest(long requestId, WalletProtocol.Request type, Optional<AccountId> account,
                            Optional<AccountId> recipient, long amount, Optional<UUID> confirmationNonce,
                            int historyLimit) {
    public WalletRequest {
        Objects.requireNonNull(type, "type");
        account = account == null ? Optional.empty() : account;
        recipient = recipient == null ? Optional.empty() : recipient;
        confirmationNonce = confirmationNonce == null ? Optional.empty() : confirmationNonce;
    }
    public static WalletRequest open(long requestId) { return new WalletRequest(requestId, WalletProtocol.Request.OPEN_WALLET, Optional.empty(), Optional.empty(), 0, Optional.empty(), 0); }
}
