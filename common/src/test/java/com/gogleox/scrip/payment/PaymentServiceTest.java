package com.gogleox.scrip.payment;

import com.gogleox.scrip.account.*;
import com.gogleox.scrip.ledger.AccountLedger;
import com.gogleox.scrip.organization.*;
import java.nio.file.Path;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {
    @TempDir Path directory;
    private static final UUID ALICE=UUID.fromString("00000000-0000-0000-0000-000000000031"), BOB=UUID.fromString("00000000-0000-0000-0000-000000000032"), MANAGER=UUID.fromString("00000000-0000-0000-0000-000000000033");
    @Test void appliesFlatAndRoundedPercentageFeesAndThreshold() {
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) { setup(ledger); seed(ledger,ALICE,10_000); PaymentService service=new PaymentService(ledger);
            PaymentPreview low=service.preview(ALICE,id(ledger,ALICE),id(ledger,BOB),1999).preview().orElseThrow(); assertFalse(low.confirmationRequired()); assertEquals(PaymentResult.Status.EXECUTED,service.executeWithoutConfirmation(ALICE,low).status());
            ledger.updatePaymentPolicy(UUID.randomUUID(),FeeMode.FLAT,7,300).toCompletableFuture().join(); PaymentPreview flat=service.preview(ALICE,id(ledger,ALICE),id(ledger,BOB),2000).preview().orElseThrow();assertTrue(flat.confirmationRequired());assertEquals(7,flat.fee());assertEquals(2007,flat.totalDebit());assertEquals(PaymentResult.Status.EXECUTED,service.confirm(ALICE,flat.nonce()).status());
            ledger.updatePaymentPolicy(UUID.randomUUID(),FeeMode.PERCENTAGE_BASIS_POINTS,125,300).toCompletableFuture().join();PaymentPreview percentage=service.preview(ALICE,id(ledger,ALICE),id(ledger,BOB),1).preview().orElseThrow();assertEquals(1,percentage.fee());assertEquals(PaymentResult.Status.EXECUTED,service.executeWithoutConfirmation(ALICE,percentage).status());assertEquals(8,ledger.feeReserve());
        }
    }
    @Test void rejectsInsufficientExpiredReusedChangedPolicyAndFrozenPreviews() {
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) { setup(ledger);seed(ledger,ALICE,2000);Clock clock=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);PaymentService service=new PaymentService(ledger,clock);
            ledger.updatePaymentPolicy(UUID.randomUUID(),FeeMode.FLAT,1,1).toCompletableFuture().join();assertEquals(PaymentResult.Status.INSUFFICIENT_FUNDS,service.preview(ALICE,id(ledger,ALICE),id(ledger,BOB),2000).status());
            seed(ledger,ALICE,10);PaymentPreview preview=service.preview(ALICE,id(ledger,ALICE),id(ledger,BOB),2000).preview().orElseThrow();ledger.updatePaymentPolicy(UUID.randomUUID(),FeeMode.FLAT,2,1).toCompletableFuture().join();assertEquals(PaymentResult.Status.POLICY_CHANGED,service.confirm(ALICE,preview.nonce()).status());assertEquals(PaymentResult.Status.NONCE_USED,service.confirm(ALICE,preview.nonce()).status());
            PaymentPreview frozen=service.preview(ALICE,id(ledger,ALICE),id(ledger,BOB),1).preview().orElseThrow();ledger.setFrozen(UUID.randomUUID(),id(ledger,ALICE),true,AuditActor.console(),"test").toCompletableFuture().join();assertEquals(PaymentResult.Status.UNAUTHORIZED,service.confirm(ALICE,frozen.nonce()).status());
        }
    }
    @Test void revalidatesOrganizationRoleAtConfirmation() {
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) { setup(ledger);OrganizationService orgs=new OrganizationService(ledger);OrganizationSnapshot org=orgs.create(UUID.randomUUID(),"Payments",ALICE).toCompletableFuture().join().organization().orElseThrow();orgs.invite(UUID.randomUUID(),org.id(),ALICE,MANAGER).toCompletableFuture().join();orgs.accept(UUID.randomUUID(),org.id(),MANAGER).toCompletableFuture().join();orgs.changeRole(UUID.randomUUID(),org.id(),ALICE,MANAGER,OrganizationRole.MANAGER).toCompletableFuture().join();seed(ledger,ALICE,100);orgs.deposit(UUID.randomUUID(),org.id(),ALICE,100).toCompletableFuture().join();PaymentService service=new PaymentService(ledger);PaymentPreview preview=service.preview(MANAGER,org.accountId(),id(ledger,BOB),10).preview().orElseThrow();orgs.remove(UUID.randomUUID(),org.id(),ALICE,MANAGER).toCompletableFuture().join();assertEquals(PaymentResult.Status.UNAUTHORIZED,service.confirm(MANAGER,preview.nonce()).status()); }
    }
    private void setup(AccountLedger ledger){ledger.registerPlayer(ALICE,"Alice").toCompletableFuture().join();ledger.registerPlayer(BOB,"Bob").toCompletableFuture().join();ledger.registerPlayer(MANAGER,"Manager").toCompletableFuture().join();}
    private void seed(AccountLedger ledger,UUID player,long amount){ledger.adjust(new AdjustmentRequest(UUID.randomUUID(),id(ledger,player),amount,"seed")).toCompletableFuture().join();}
    private AccountId id(AccountLedger ledger,UUID player){return ledger.findPersonal(player).orElseThrow().id();}
}
