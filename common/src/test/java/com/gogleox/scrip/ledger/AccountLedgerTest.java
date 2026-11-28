package com.gogleox.scrip.ledger;

import com.gogleox.scrip.account.*;
import com.gogleox.scrip.storage.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AccountLedgerTest {
    @TempDir Path directory;
    private static final UUID ALICE=UUID.fromString("00000000-0000-0000-0000-000000000001"), BOB=UUID.fromString("00000000-0000-0000-0000-000000000002");
    @Test void createsAccountsAndPreservesIdentityAfterRenameAndRestart() {
        try(AccountLedger ledger=AccountLedger.open(directory,new CurrencyDefinition("Coins"))){ assertEquals(ALICE,ledger.registerPlayer(ALICE,"Alice").toCompletableFuture().join().ownerId()); ledger.registerPlayer(ALICE,"Alicia").toCompletableFuture().join(); assertTrue(ledger.findKnownPlayer("Alicia").isPresent()); }
        try(AccountLedger ledger=AccountLedger.open(directory,new CurrencyDefinition("Coins"))){ assertEquals(ALICE,ledger.findPersonal(ALICE).orElseThrow().ownerId()); assertEquals("Alicia",ledger.findKnownPlayer("Alicia").orElseThrow().lastKnownName()); }
    }
    @Test void validatesTransfersAndMakesRetriesIdempotent() {
        try(AccountLedger l=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) { AccountId a=l.registerPlayer(ALICE,"Alice").toCompletableFuture().join().id(), b=l.registerPlayer(BOB,"Bob").toCompletableFuture().join().id(); l.adjust(new AdjustmentRequest(UUID.randomUUID(),a,100,"test")).toCompletableFuture().join(); UUID op=UUID.randomUUID(); assertEquals(TransferOutcome.Status.SUCCESS,l.transfer(new TransferRequest(op,a,b,60)).toCompletableFuture().join().status()); assertTrue(l.transfer(new TransferRequest(op,a,b,60)).toCompletableFuture().join().idempotent()); assertEquals(TransferOutcome.Status.OPERATION_CONFLICT,l.transfer(new TransferRequest(op,a,b,61)).toCompletableFuture().join().status()); assertEquals(40,l.findAccount(a).orElseThrow().balance().units()); assertEquals(60,l.findAccount(b).orElseThrow().balance().units()); assertEquals(TransferOutcome.Status.INSUFFICIENT_FUNDS,l.transfer(new TransferRequest(UUID.randomUUID(),a,b,41)).toCompletableFuture().join().status()); assertEquals(TransferOutcome.Status.INVALID_AMOUNT,l.transfer(new TransferRequest(UUID.randomUUID(),a,b,0)).toCompletableFuture().join().status()); }
    }
    @Test void protectsOverflowBoundsHistoryAndClaims() {
        try(AccountLedger l=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) { AccountId a=l.registerPlayer(ALICE,"Alice").toCompletableFuture().join().id(), b=l.registerPlayer(BOB,"Bob").toCompletableFuture().join().id(); l.adjust(new AdjustmentRequest(UUID.randomUUID(),a,Long.MAX_VALUE,"seed")).toCompletableFuture().join(); assertEquals(TransferOutcome.Status.DESTINATION_OVERFLOW,l.adjust(new AdjustmentRequest(UUID.randomUUID(),a,1,"overflow")).toCompletableFuture().join().status()); for(int i=0;i<101;i++)l.adjust(new AdjustmentRequest(UUID.randomUUID(),b,1,"entry")).toCompletableFuture().join(); assertEquals(100,l.history(b).size()); assertTrue(l.recordPermanentClaim("reward:"+ALICE).toCompletableFuture().join()); assertFalse(l.recordPermanentClaim("reward:"+ALICE).toCompletableFuture().join()); assertTrue(l.hasPermanentClaim("reward:"+ALICE)); }
    }
    @Test void serializesConcurrentTransfersWithoutChangingTotal() throws Exception {
        try(AccountLedger l=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) { AccountId a=l.registerPlayer(ALICE,"Alice").toCompletableFuture().join().id(), b=l.registerPlayer(BOB,"Bob").toCompletableFuture().join().id();l.adjust(new AdjustmentRequest(UUID.randomUUID(),a,100,"seed")).toCompletableFuture().join(); List<CompletableFuture<TransferOutcome>> futures=new ArrayList<>();for(int i=0;i<20;i++)futures.add(l.transfer(new TransferRequest(UUID.randomUUID(),a,b,10)).toCompletableFuture());CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10,TimeUnit.SECONDS);assertEquals(10,futures.stream().filter(f->f.join().status()==TransferOutcome.Status.SUCCESS).count());assertEquals(100,l.findAccount(a).orElseThrow().balance().units()+l.findAccount(b).orElseThrow().balance().units()); }
    }
    @Test void uncertainForceFailureNeverPublishesHalfTransfer() {
        java.util.concurrent.atomic.AtomicBoolean fail=new java.util.concurrent.atomic.AtomicBoolean();
        FaultInjector injector=point->{if(fail.get()&&point==FaultPoint.BEFORE_FORCE)throw new java.io.IOException("injected");};
        try(AccountLedger l=AccountLedger.open(JournalFileLedgerStore.open(directory,128,injector),CurrencyDefinition.defaultCurrency())) { AccountId a=l.registerPlayer(ALICE,"Alice").toCompletableFuture().join().id(),b=l.registerPlayer(BOB,"Bob").toCompletableFuture().join().id();l.adjust(new AdjustmentRequest(UUID.randomUUID(),a,10,"seed")).toCompletableFuture().join();fail.set(true);assertEquals(TransferOutcome.Status.STORAGE_FAILURE,l.transfer(new TransferRequest(UUID.randomUUID(),a,b,10)).toCompletableFuture().join().status());assertEquals(10,l.findAccount(a).orElseThrow().balance().units());assertEquals(0,l.findAccount(b).orElseThrow().balance().units()); }
        try(AccountLedger l=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) { assertEquals(10,l.findPersonal(ALICE).orElseThrow().balance().units()+l.findPersonal(BOB).orElseThrow().balance().units()); }
    }
    @Test void freezesOutgoingTransfersAndPreservesAdministrativeAuditAcrossRestart() {
        UUID admin=UUID.fromString("00000000-0000-0000-0000-000000000003");
        try(AccountLedger l=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            AccountId a=l.registerPlayer(ALICE,"Alice").toCompletableFuture().join().id(), b=l.registerPlayer(BOB,"Bob").toCompletableFuture().join().id();
            l.adjust(new AdjustmentRequest(UUID.randomUUID(),a,100,"seed")).toCompletableFuture().join();
            assertEquals(AccountControlOutcome.Status.SUCCESS,l.setFrozen(UUID.randomUUID(),a,true,AuditActor.player(admin,"Admin"),"review").toCompletableFuture().join().status());
            assertEquals(TransferOutcome.Status.FROZEN_ACCOUNT,l.transfer(new TransferRequest(UUID.randomUUID(),a,b,1)).toCompletableFuture().join().status());
            assertEquals(TransferOutcome.Status.SUCCESS,l.adjustAdministratively(new AdminAdjustmentRequest(UUID.randomUUID(),a,5,"correction",AuditActor.player(admin,"Admin"))).toCompletableFuture().join().status());
            assertEquals(2,l.administrativeAudit(a).size());
        }
        try(AccountLedger l=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            assertEquals(AccountLifecycle.FROZEN,l.findPersonal(ALICE).orElseThrow().lifecycle());
            assertEquals(105,l.findPersonal(ALICE).orElseThrow().balance().units());
            assertEquals(2,l.administrativeAudit().size());
        }
    }
    @Test void checkpointReplaysAccountsClaimsAndPostCheckpointOperations() {
        UUID claimOwner=UUID.fromString("00000000-0000-0000-0000-000000000004");
        try(AccountLedger l=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            AccountId alice=l.registerPlayer(ALICE,"Alice").toCompletableFuture().join().id();
            l.registerPlayer(BOB,"Bob").toCompletableFuture().join();
            l.adjust(new AdjustmentRequest(UUID.randomUUID(),alice,75,"seed")).toCompletableFuture().join();
            assertTrue(l.recordPermanentClaim("starter_grant:"+claimOwner).toCompletableFuture().join());
            long checkpoint=l.checkpoint().toCompletableFuture().join().sequence();
            assertTrue(checkpoint>0);
            l.adjust(new AdjustmentRequest(UUID.randomUUID(),alice,25,"after checkpoint")).toCompletableFuture().join();
        }
        try(AccountLedger l=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            assertEquals(100,l.findPersonal(ALICE).orElseThrow().balance().units());
            assertTrue(l.hasPermanentClaim("starter_grant:"+claimOwner));
            assertTrue(l.findPersonal(BOB).isPresent());
        }
    }
}
