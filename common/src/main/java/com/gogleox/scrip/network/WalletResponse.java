package com.gogleox.scrip.network;

import com.gogleox.scrip.account.AccountSnapshot;
import com.gogleox.scrip.account.TransactionRecord;
import com.gogleox.scrip.payment.PaymentPreview;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Permission-filtered response payload model. Loader adapters serialize only these immutable values. */
public record WalletResponse(long requestId, WalletProtocol.Response type, Optional<AccountSnapshot> account,
                             Optional<PaymentPreview> preview, List<TransactionRecord> history,
                             Optional<WalletProtocol.Error> error, Optional<String> detail) {
    public WalletResponse {
        Objects.requireNonNull(type, "type");
        account = account == null ? Optional.empty() : account;
        preview = preview == null ? Optional.empty() : preview;
        history = history == null ? List.of() : List.copyOf(history);
        error = error == null ? Optional.empty() : error;
        detail = detail == null ? Optional.empty() : detail;
    }
    public static WalletResponse error(long id, WalletProtocol.Error error) { return new WalletResponse(id, WalletProtocol.Response.ERROR_CODE, Optional.empty(), Optional.empty(), List.of(), Optional.of(error), Optional.empty()); }
}
