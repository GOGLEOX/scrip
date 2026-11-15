package com.gogleox.scrip.tax;

import com.gogleox.scrip.account.*;
import com.gogleox.scrip.ledger.AccountLedger;
import com.gogleox.scrip.organization.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Simulated external tax authority; no ChunkLock implementation or classes are present. */
class TaxPaymentPortTest {
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID MANAGER=UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID COLLECTOR=UUID.fromString("00000000-0000-0000-0000-0000000000b3");

    @Test void paysInvoicesIdempotentlyRejectsConflictsAndSurvivesRestart() throws Exception {
        Path directory=Files.createTempDirectory("scrip-tax");
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            AccountId owner=register(ledger,OWNER,"Owner"),collector=register(ledger,COLLECTOR,"Collector");
            ledger.adjust(new AdjustmentRequest(UUID.randomUUID(),owner,100,"seed")).toCompletableFuture().join();
            TaxPaymentPort tax=new DefaultTaxPaymentPort(ledger);
            TaxPaymentRequest invoice=new TaxPaymentRequest(OWNER,owner,collector,"claim-7","2026-q1",30,true);
            assertEquals(TaxPaymentResult.Status.PAID,tax.pay(invoice).toCompletableFuture().join().status());
            assertEquals(TaxPaymentResult.Status.DUPLICATE,tax.pay(invoice).toCompletableFuture().join().status());
            assertEquals(TaxPaymentResult.Status.CONFLICT,tax.pay(new TaxPaymentRequest(OWNER,owner,collector,"claim-7","2026-q1",31,true)).toCompletableFuture().join().status());
            assertEquals(70,ledger.findAccount(owner).orElseThrow().balance().units());
        }
        try(AccountLedger replayed=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            AccountId owner=replayed.findPersonal(OWNER).orElseThrow().id(),collector=replayed.findPersonal(COLLECTOR).orElseThrow().id();
            TaxPaymentResult replay=new DefaultTaxPaymentPort(replayed).pay(new TaxPaymentRequest(OWNER,owner,collector,"claim-7","2026-q1",30,false)).toCompletableFuture().join();
            assertEquals(TaxPaymentResult.Status.DUPLICATE,replay.status());
            assertEquals(30,replayed.findAccount(collector).orElseThrow().balance().units());
        }
    }

    @Test void rejectsInsufficientFrozenAndUnconfirmedPayments() throws Exception {
        Path directory=Files.createTempDirectory("scrip-tax-auth");
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            AccountId owner=register(ledger,OWNER,"Owner"),collector=register(ledger,COLLECTOR,"Collector");TaxPaymentPort tax=new DefaultTaxPaymentPort(ledger);
            assertEquals(TaxPaymentResult.Status.INSUFFICIENT_FUNDS,tax.pay(new TaxPaymentRequest(OWNER,owner,collector,"claim-1","2026-q1",1,true)).toCompletableFuture().join().status());
            ledger.adjust(new AdjustmentRequest(UUID.randomUUID(),owner,2_000,"seed")).toCompletableFuture().join();
            assertEquals(TaxPaymentResult.Status.CONFIRMATION_REQUIRED,tax.pay(new TaxPaymentRequest(OWNER,owner,collector,"claim-2","2026-q1",2_000,false)).toCompletableFuture().join().status());
            ledger.setFrozen(UUID.randomUUID(),owner,true,AuditActor.console(),"test").toCompletableFuture().join();
            assertEquals(TaxPaymentResult.Status.FROZEN_ACCOUNT,tax.pay(new TaxPaymentRequest(OWNER,owner,collector,"claim-3","2026-q1",1,true)).toCompletableFuture().join().status());
        }
    }

    @Test void enforcesOrganizationAuthorityAndSupportsDisabledWiring() throws Exception {
        Path directory=Files.createTempDirectory("scrip-tax-org");
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            AccountId owner=register(ledger,OWNER,"Owner"),manager=register(ledger,MANAGER,"Manager"),collector=register(ledger,COLLECTOR,"Collector");
            ledger.adjust(new AdjustmentRequest(UUID.randomUUID(),owner,80,"seed")).toCompletableFuture().join();
            OrganizationSnapshot organization=ledger.createOrganization(UUID.randomUUID(),"Parcel Guild",OWNER).toCompletableFuture().join().organization().orElseThrow();
            ledger.inviteMember(UUID.randomUUID(),organization.id(),OWNER,MANAGER).toCompletableFuture().join();ledger.acceptInvitation(UUID.randomUUID(),organization.id(),MANAGER).toCompletableFuture().join();
            ledger.depositToOrganization(UUID.randomUUID(),organization.id(),OWNER,40).toCompletableFuture().join();
            TaxPaymentPort tax=new DefaultTaxPaymentPort(ledger);
            assertEquals(TaxPaymentResult.Status.UNAUTHORIZED,tax.pay(new TaxPaymentRequest(MANAGER,organization.accountId(),collector,"claim-4","2026-q1",10,true)).toCompletableFuture().join().status());
            ledger.changeRole(UUID.randomUUID(),organization.id(),OWNER,MANAGER,OrganizationRole.MANAGER).toCompletableFuture().join();
            assertEquals(TaxPaymentResult.Status.PAID,tax.pay(new TaxPaymentRequest(MANAGER,organization.accountId(),collector,"claim-4","2026-q1",10,true)).toCompletableFuture().join().status());
            assertEquals(TaxPaymentResult.Status.INTEGRATION_DISABLED,new DisabledTaxPaymentPort().pay(new TaxPaymentRequest(OWNER,owner,collector,"claim-5","2026-q1",1,true)).toCompletableFuture().join().status());
        }
    }

    private static AccountId register(AccountLedger ledger,UUID player,String name){return ledger.registerPlayer(player,name).toCompletableFuture().join().id();}
}
