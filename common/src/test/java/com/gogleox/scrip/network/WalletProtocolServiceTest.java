package com.gogleox.scrip.network;

import com.gogleox.scrip.account.*;
import com.gogleox.scrip.command.CommandService;
import com.gogleox.scrip.ledger.AccountLedger;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WalletProtocolServiceTest {
    @TempDir Path directory;
    private static final UUID ALICE=UUID.fromString("00000000-0000-0000-0000-000000000091");
    private static final UUID BOB=UUID.fromString("00000000-0000-0000-0000-000000000092");

    @Test void requiresNegotiationFiltersAccountsAndProtectsPreviewOwnership() {
        try (AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            AccountId alice=ledger.registerPlayer(ALICE,"Alice").toCompletableFuture().join().id();
            AccountId bob=ledger.registerPlayer(BOB,"Bob").toCompletableFuture().join().id();
            ledger.adjust(new AdjustmentRequest(UUID.randomUUID(),alice,3_000,"seed")).toCompletableFuture().join();
            ProtocolSessionRegistry sessions=new ProtocolSessionRegistry();
            WalletProtocolService service=new WalletProtocolService(sessions,new CommandService(ledger));

            assertEquals(WalletProtocol.Error.NOT_NEGOTIATED,service.handle(ALICE,WalletRequest.open(1),1,1).toCompletableFuture().join().error().orElseThrow());
            assertTrue(sessions.negotiate(ALICE,WalletProtocol.VERSION,0,1).isEmpty());
            assertEquals(3_000,service.handle(ALICE,WalletRequest.open(2),1,1).toCompletableFuture().join().account().orElseThrow().balance().units());
            WalletRequest denied=new WalletRequest(3,WalletProtocol.Request.SELECT_ACCOUNT,Optional.of(bob),Optional.empty(),0,Optional.empty(),0);
            assertEquals(WalletProtocol.Error.UNAUTHORIZED,service.handle(ALICE,denied,1,1).toCompletableFuture().join().error().orElseThrow());

            WalletRequest preview=new WalletRequest(4,WalletProtocol.Request.PREVIEW_TRANSFER,Optional.of(alice),Optional.of(bob),2_000,Optional.empty(),0);
            WalletResponse response=service.handle(ALICE,preview,1,1).toCompletableFuture().join();
            UUID nonce=response.preview().orElseThrow().nonce();
            WalletRequest wrongActorCancel=new WalletRequest(5,WalletProtocol.Request.CANCEL_PREVIEW,Optional.empty(),Optional.empty(),0,Optional.of(nonce),0);
            assertTrue(sessions.negotiate(BOB,WalletProtocol.VERSION,0,1).isEmpty());
            assertEquals("UNAUTHORIZED",service.handle(BOB,wrongActorCancel,1,1).toCompletableFuture().join().detail().orElseThrow());
            WalletRequest cancel=new WalletRequest(6,WalletProtocol.Request.CANCEL_PREVIEW,Optional.empty(),Optional.empty(),0,Optional.of(nonce),0);
            assertEquals("CANCELLED",service.handle(ALICE,cancel,1,1).toCompletableFuture().join().detail().orElseThrow());
            WalletResponse named=service.handle(ALICE,WalletRequest.previewByName(7,alice,"Bob",2_000),1,1).toCompletableFuture().join();
            assertEquals(WalletProtocol.Response.PREVIEW_RESULT,named.type());
            assertTrue(named.preview().isPresent());
            WalletResponse unknown=service.handle(ALICE,WalletRequest.previewByName(8,alice,"NotKnown",1),1,1).toCompletableFuture().join();
            assertEquals("UNKNOWN_ACCOUNT",unknown.detail().orElseThrow());
            WalletResponse committed=service.handle(ALICE,WalletRequest.previewByName(9,alice,"Bob",100),1,1).toCompletableFuture().join();
            assertEquals(WalletProtocol.Response.TRANSACTION_COMMITTED,committed.type());
            assertEquals(2_900,committed.account().orElseThrow().balance().units());
            WalletRequest historyRequest=new WalletRequest(10,WalletProtocol.Request.REQUEST_HISTORY,Optional.of(alice),Optional.empty(),0,Optional.empty(),100);
            assertTrue(service.handle(ALICE,historyRequest,1,1).toCompletableFuture().join().history().getFirst().description().contains("to Bob"));
        }
    }
}
