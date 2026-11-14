package com.gogleox.scrip.api;

import com.gogleox.scrip.account.*;
import com.gogleox.scrip.ledger.AccountLedger;
import com.gogleox.scrip.organization.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Sample integration consumer: it receives only the public API, never AccountLedger. */
class EconomyApiIntegrationTest {
    private static final UUID ALICE=UUID.fromString("00000000-0000-0000-0000-000000000071"), BOB=UUID.fromString("00000000-0000-0000-0000-000000000072"), MANAGER=UUID.fromString("00000000-0000-0000-0000-000000000073");
    @Test void consumerUsesApiForTransferAuthorizationAndPostCommitEvents() throws Exception {
        Path dir=Files.createTempDirectory("scrip-api");try(AccountLedger ledger=AccountLedger.open(dir,CurrencyDefinition.defaultCurrency())){
            AccountId alice=ledger.registerPlayer(ALICE,"Alice").toCompletableFuture().join().id(),bob=ledger.registerPlayer(BOB,"Bob").toCompletableFuture().join().id();ledger.adjust(new AdjustmentRequest(UUID.randomUUID(),alice,100,"seed")).toCompletableFuture().join();
            DefaultEconomyService api=api(ledger);List<EconomyPostCommitEvent> events=new ArrayList<>();api.addPostCommitListener(events::add);Consumer consumer=new Consumer(api);
            TransferPreviewResult preview=consumer.preview(ALICE,alice,bob,25);assertEquals(TransferPreviewResult.Status.READY,preview.status());assertEquals(TransferOutcome.Status.SUCCESS,consumer.execute(ALICE,preview.preview().orElseThrow()).status());assertEquals(75,consumer.balance(ALICE));assertEquals(25,consumer.balance(BOB));assertEquals(1,events.size());
            assertFalse(api.authorize(BOB,alice,EconomyPermission.TRANSFER).toCompletableFuture().join().allowed());ledger.setFrozen(UUID.randomUUID(),alice,true,AuditActor.console(),"test").toCompletableFuture().join();assertEquals(TransferPreviewResult.Status.UNAUTHORIZED,consumer.preview(ALICE,alice,bob,1).status());
        }
    }
    @Test void trustedRewardsAreIdempotentConflictingAndReplaySafe() throws Exception {
        Path dir=Files.createTempDirectory("scrip-api-reward");try(AccountLedger ledger=AccountLedger.open(dir,CurrencyDefinition.defaultCurrency())){
            ledger.registerPlayer(ALICE,"Alice").toCompletableFuture().join();Consumer consumer=new Consumer(api(ledger));assertEquals(IntegrationRewardResponse.Status.ISSUED,consumer.reward("bridge","quest-1",ALICE,12).status());assertEquals(IntegrationRewardResponse.Status.DUPLICATE,consumer.reward("bridge","quest-1",ALICE,12).status());assertEquals(IntegrationRewardResponse.Status.CONFLICT,consumer.reward("bridge","quest-1",ALICE,13).status());assertEquals(IntegrationRewardResponse.Status.UNAUTHORIZED_SOURCE,consumer.reward("unknown","quest-2",ALICE,1).status());assertEquals(12,consumer.balance(ALICE));
        }try(AccountLedger ledger=AccountLedger.open(dir,CurrencyDefinition.defaultCurrency())){assertEquals(IntegrationRewardResponse.Status.DUPLICATE,new Consumer(api(ledger)).reward("bridge","quest-1",ALICE,12).status());assertEquals(12,ledger.findPersonal(ALICE).orElseThrow().balance().units());}
    }
    @Test void organizationPermissionsAndAdministrativePolicyAreEnforced() throws Exception {
        Path dir=Files.createTempDirectory("scrip-api-org");try(AccountLedger ledger=AccountLedger.open(dir,CurrencyDefinition.defaultCurrency())){
            ledger.registerPlayer(ALICE,"Alice").toCompletableFuture().join();ledger.registerPlayer(MANAGER,"Manager").toCompletableFuture().join();OrganizationSnapshot org=ledger.createOrganization(UUID.randomUUID(),"Guild",ALICE).toCompletableFuture().join().organization().orElseThrow();ledger.inviteMember(UUID.randomUUID(),org.id(),ALICE,MANAGER).toCompletableFuture().join();ledger.acceptInvitation(UUID.randomUUID(),org.id(),MANAGER).toCompletableFuture().join();ledger.changeRole(UUID.randomUUID(),org.id(),ALICE,MANAGER,OrganizationRole.MANAGER).toCompletableFuture().join();DefaultEconomyService api=api(ledger);
            assertTrue(api.authorize(MANAGER,org.accountId(),EconomyPermission.TRANSFER).toCompletableFuture().join().allowed());assertTrue(api.organization(org.id()).toCompletableFuture().join().isPresent());assertEquals(TransferOutcome.Status.UNAUTHORIZED,api.adjust(new AdministrativeAdjustmentRequest(UUID.randomUUID(),MANAGER,org.accountId(),1,"no")).toCompletableFuture().join().status());
        }
    }
    @Test void concurrentIdenticalIntegrationRequestsIssueOnlyOnce() throws Exception {
        Path dir=Files.createTempDirectory("scrip-api-concurrent");try(AccountLedger ledger=AccountLedger.open(dir,CurrencyDefinition.defaultCurrency())){
            ledger.registerPlayer(ALICE,"Alice").toCompletableFuture().join();DefaultEconomyService api=api(ledger);List<CompletableFuture<IntegrationRewardResponse>> calls=new ArrayList<>();for(int i=0;i<20;i++)calls.add(api.issueReward(new IntegrationRewardRequest("bridge","same-claim",ALICE,5)).toCompletableFuture());CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).join();assertEquals(1,calls.stream().map(CompletableFuture::join).filter(result->result.status()==IntegrationRewardResponse.Status.ISSUED).count());assertEquals(19,calls.stream().map(CompletableFuture::join).filter(result->result.status()==IntegrationRewardResponse.Status.DUPLICATE).count());assertEquals(5,ledger.findPersonal(ALICE).orElseThrow().balance().units());
        }
    }
    private static DefaultEconomyService api(AccountLedger ledger){TrustedRewardSourceRegistry registry=new TrustedRewardSourceRegistry();registry.register(new TrustedRewardSource("bridge",100,true));return new DefaultEconomyService(ledger,registry,id->false);}
    private static final class Consumer { private final EconomyService api; Consumer(EconomyService api){this.api=api;} long balance(UUID player){return api.personalAccount(player).toCompletableFuture().join().orElseThrow().balance().units();} TransferPreviewResult preview(UUID actor,AccountId from,AccountId to,long amount){return api.previewTransfer(new TransferPreviewRequest(actor,from,to,amount)).toCompletableFuture().join();} EconomyTransactionResult execute(UUID actor,com.gogleox.scrip.payment.PaymentPreview preview){return api.executeTransfer(new TransferExecutionRequest(actor,preview)).toCompletableFuture().join();} IntegrationRewardResponse reward(String source,String claim,UUID player,long amount){return api.issueReward(new IntegrationRewardRequest(source,claim,player,amount)).toCompletableFuture().join();} }
}
