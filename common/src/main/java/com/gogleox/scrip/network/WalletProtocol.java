package com.gogleox.scrip.network;

/** Loader-neutral protocol identifiers and hard inbound bounds for optional clients. */
public final class WalletProtocol {
    public static final int VERSION = 3;
    public static final int MAX_PACKET_BYTES = 8_192;
    public static final int MAX_REQUESTS_PER_WINDOW = 20;
    public static final long RATE_WINDOW_MILLIS = 10_000L;
    private WalletProtocol() { }
    public enum Request { OPEN_WALLET, PREVIEW_TRANSFER, CONFIRM_TRANSFER, CANCEL_PREVIEW, REQUEST_HISTORY, SELECT_ACCOUNT, UPDATE_PREFERENCE }
    public enum Response { WALLET_SNAPSHOT, PREVIEW_RESULT, TRANSACTION_COMMITTED, HISTORY_PAGE, NOTIFICATION, ERROR_CODE, SERVER_CONFIG_SUMMARY }
    public enum Error { INCOMPATIBLE_PROTOCOL, MALFORMED, OVERSIZED, RATE_LIMITED, REPLAYED, UNAUTHORIZED, NOT_NEGOTIATED }
}
