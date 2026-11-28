package com.gogleox.scrip.network;

import com.gogleox.scrip.account.AccountId;
import com.gogleox.scrip.account.AccountSnapshot;
import com.gogleox.scrip.command.CommandService;
import com.gogleox.scrip.payment.PaymentResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Server-side protocol dispatcher. It accepts only transport-authenticated actors and performs no client-authoritative mutation. */
public final class WalletProtocolService {
    private final ProtocolSessionRegistry sessions;
    private final CommandService commands;

    public WalletProtocolService(ProtocolSessionRegistry sessions, CommandService commands) {
        this.sessions = Objects.requireNonNull(sessions); this.commands = Objects.requireNonNull(commands);
    }
    public CompletionStage<WalletResponse> handle(UUID actor, WalletRequest request, int encodedBytes, long now) {
        Objects.requireNonNull(actor); Objects.requireNonNull(request);
        Optional<WalletProtocol.Error> accepted = sessions.accept(actor, request.requestId(), encodedBytes, now);
        if (accepted.isPresent()) return CompletableFuture.completedFuture(WalletResponse.error(request.requestId(), accepted.get()));
        return CompletableFuture.completedFuture(dispatch(actor, request));
    }
    private WalletResponse dispatch(UUID actor, WalletRequest request) {
        return switch (request.type()) {
            case OPEN_WALLET -> new WalletResponse(request.requestId(), WalletProtocol.Response.WALLET_SNAPSHOT, commands.balance(actor), Optional.empty(), List.of(), commands.accessibleAccounts(actor), Optional.empty(), Optional.empty());
            case SELECT_ACCOUNT -> account(request, actor);
            case REQUEST_HISTORY -> history(request, actor);
            case PREVIEW_TRANSFER -> preview(request, actor);
            case CONFIRM_TRANSFER -> confirm(request, actor);
            case CANCEL_PREVIEW -> cancel(request, actor);
            // Preferences are retained by the optional client, not sent to the ledger or shared with other players.
            case UPDATE_PREFERENCE -> new WalletResponse(request.requestId(), WalletProtocol.Response.NOTIFICATION, Optional.empty(), Optional.empty(), List.of(), commands.accessibleAccounts(actor), Optional.empty(), Optional.of("ACCEPTED"));
        };
    }
    private WalletResponse account(WalletRequest request, UUID actor) {
        return request.account().map(account -> commands.accountForActor(actor, account)
                .map(value -> new WalletResponse(request.requestId(), WalletProtocol.Response.WALLET_SNAPSHOT, Optional.of(value), Optional.empty(), List.of(), commands.accessibleAccounts(actor), Optional.empty(), Optional.empty()))
                .orElseGet(() -> WalletResponse.error(request.requestId(), WalletProtocol.Error.UNAUTHORIZED)))
                .orElseGet(() -> WalletResponse.error(request.requestId(), WalletProtocol.Error.MALFORMED));
    }
    private WalletResponse history(WalletRequest request, UUID actor) {
        if (request.account().isEmpty() || request.historyLimit() < 1 || request.historyLimit() > 100) return WalletResponse.error(request.requestId(), WalletProtocol.Error.MALFORMED);
        AccountId account = request.account().get();
        if (commands.accountForActor(actor, account).isEmpty()) return WalletResponse.error(request.requestId(), WalletProtocol.Error.UNAUTHORIZED);
        return new WalletResponse(request.requestId(), WalletProtocol.Response.HISTORY_PAGE, Optional.empty(), Optional.empty(), commands.historyForActor(actor, account, request.historyLimit()), commands.accessibleAccounts(actor), Optional.empty(), Optional.empty());
    }
    private WalletResponse preview(WalletRequest request, UUID actor) {
        if (request.account().isEmpty() || request.amount() <= 0 || (request.recipient().isPresent()==request.recipientName().isPresent())) return WalletResponse.error(request.requestId(), WalletProtocol.Error.MALFORMED);
        Optional<AccountId> destination=request.recipient().isPresent()?request.recipient():request.recipientName().flatMap(name -> commands.balanceByVerifiedName(name).map(AccountSnapshot::id));
        if(destination.isEmpty()) return new WalletResponse(request.requestId(),WalletProtocol.Response.PREVIEW_RESULT,Optional.empty(),Optional.empty(),List.of(),commands.accessibleAccounts(actor),Optional.empty(),Optional.of(PaymentResult.Status.UNKNOWN_ACCOUNT.name()));
        PaymentResult result = commands.previewPayment(actor, request.account().get(), destination.get(), request.amount());
        if (result.status()==PaymentResult.Status.PREVIEW_READY && result.preview().isPresent() && !result.preview().get().confirmationRequired()) {
            PaymentResult executed=commands.executePreview(actor,result.preview().get());
            return new WalletResponse(request.requestId(), WalletProtocol.Response.TRANSACTION_COMMITTED, commands.accountForActor(actor, request.account().get()), executed.preview(), List.of(), commands.accessibleAccounts(actor), Optional.empty(), Optional.of(executed.status().name()));
        }
        return new WalletResponse(request.requestId(), WalletProtocol.Response.PREVIEW_RESULT, Optional.empty(), result.preview(), List.of(), commands.accessibleAccounts(actor), Optional.empty(), Optional.of(result.status().name()));
    }
    private WalletResponse confirm(WalletRequest request, UUID actor) {
        if (request.confirmationNonce().isEmpty()) return WalletResponse.error(request.requestId(), WalletProtocol.Error.MALFORMED);
        PaymentResult result = commands.confirmPayment(actor, request.confirmationNonce().get());
        return new WalletResponse(request.requestId(), WalletProtocol.Response.TRANSACTION_COMMITTED, result.preview().flatMap(value -> commands.accountForActor(actor, value.source())), result.preview(), List.of(), commands.accessibleAccounts(actor), Optional.empty(), Optional.of(result.status().name()));
    }
    private WalletResponse cancel(WalletRequest request, UUID actor) {
        if (request.confirmationNonce().isEmpty()) return WalletResponse.error(request.requestId(), WalletProtocol.Error.MALFORMED);
        PaymentResult result = commands.cancelPaymentPreview(actor, request.confirmationNonce().get());
        return new WalletResponse(request.requestId(), WalletProtocol.Response.NOTIFICATION, Optional.empty(), result.preview(), List.of(), commands.accessibleAccounts(actor), Optional.empty(), Optional.of(result.status().name()));
    }
}
