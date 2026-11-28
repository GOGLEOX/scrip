package com.gogleox.scrip.network;

import com.gogleox.scrip.account.AccountId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable optional-client request. The actor identity is always supplied by the server transport. */
public record WalletRequest(long requestId, WalletProtocol.Request type, Optional<AccountId> account,
                            Optional<AccountId> recipient, Optional<String> recipientName, long amount, Optional<UUID> confirmationNonce,
                            int historyLimit) {
    public WalletRequest {
        Objects.requireNonNull(type, "type");
        account = account == null ? Optional.empty() : account;
        recipient = recipient == null ? Optional.empty() : recipient;
        recipientName = recipientName == null ? Optional.empty() : recipientName.filter(value -> value.length() <= 16 && value.matches("[A-Za-z0-9_]+"));
        confirmationNonce = confirmationNonce == null ? Optional.empty() : confirmationNonce;
    }
    public WalletRequest(long requestId, WalletProtocol.Request type, Optional<AccountId> account, Optional<AccountId> recipient, long amount, Optional<UUID> confirmationNonce, int historyLimit) { this(requestId,type,account,recipient,Optional.empty(),amount,confirmationNonce,historyLimit); }
    public static WalletRequest open(long requestId) { return new WalletRequest(requestId, WalletProtocol.Request.OPEN_WALLET, Optional.empty(), Optional.empty(), Optional.empty(), 0, Optional.empty(), 0); }
    public static WalletRequest previewByName(long requestId, AccountId source, String recipientName, long amount) { return new WalletRequest(requestId,WalletProtocol.Request.PREVIEW_TRANSFER,Optional.of(source),Optional.empty(),Optional.ofNullable(recipientName),amount,Optional.empty(),0); }
}
