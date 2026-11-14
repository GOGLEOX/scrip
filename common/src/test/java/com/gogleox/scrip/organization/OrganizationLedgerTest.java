package com.gogleox.scrip.organization;

import com.gogleox.scrip.account.*;
import com.gogleox.scrip.ledger.AccountLedger;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OrganizationLedgerTest {
    @TempDir Path directory;
    private static final UUID OWNER=UUID.fromString("00000000-0000-0000-0000-000000000011"), MANAGER=UUID.fromString("00000000-0000-0000-0000-000000000012"), MEMBER=UUID.fromString("00000000-0000-0000-0000-000000000013"), OUTSIDER=UUID.fromString("00000000-0000-0000-0000-000000000014");

    @Test void createsInvitesDepositsAndAuthorizesOrganizationSpending() {
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            register(ledger); OrganizationService service=new OrganizationService(ledger);
            OrganizationSnapshot organization=create(service,"Builders"); assertEquals(OWNER,organization.ownerId()); assertEquals(1,organization.memberCount());
            assertEquals(OrganizationOutcome.Status.SUCCESS,service.invite(UUID.randomUUID(),organization.id(),OWNER,MANAGER).toCompletableFuture().join().status());
            assertEquals(OrganizationOutcome.Status.SUCCESS,service.accept(UUID.randomUUID(),organization.id(),MANAGER).toCompletableFuture().join().status());
            assertEquals(OrganizationOutcome.Status.SUCCESS,service.changeRole(UUID.randomUUID(),organization.id(),OWNER,MANAGER,OrganizationRole.MANAGER).toCompletableFuture().join().status());
            assertEquals(OrganizationOutcome.Status.SUCCESS,service.invite(UUID.randomUUID(),organization.id(),MANAGER,MEMBER).toCompletableFuture().join().status());
            assertEquals(OrganizationOutcome.Status.SUCCESS,service.accept(UUID.randomUUID(),organization.id(),MEMBER).toCompletableFuture().join().status());
            seed(ledger,MEMBER,100); assertEquals(TransferOutcome.Status.SUCCESS,service.deposit(UUID.randomUUID(),organization.id(),MEMBER,60).toCompletableFuture().join().status());
            assertEquals(60,ledger.findAccount(organization.accountId()).orElseThrow().balance().units());
            assertEquals(TransferOutcome.Status.UNAUTHORIZED,service.spend(UUID.randomUUID(),organization.id(),MEMBER,ledger.findPersonal(MEMBER).orElseThrow().id(),1).toCompletableFuture().join().status());
            assertEquals(TransferOutcome.Status.SUCCESS,service.spend(UUID.randomUUID(),organization.id(),MANAGER,ledger.findPersonal(OWNER).orElseThrow().id(),20).toCompletableFuture().join().status());
            assertEquals(40,ledger.findAccount(organization.accountId()).orElseThrow().balance().units());
            assertEquals(TransferOutcome.Status.UNAUTHORIZED,ledger.transfer(new TransferRequest(UUID.randomUUID(),organization.accountId(),ledger.findPersonal(OUTSIDER).orElseThrow().id(),1)).toCompletableFuture().join().status());
            assertTrue(service.accessibleAccounts(MEMBER).stream().anyMatch(access -> access.organizationId().equals(Optional.of(organization.id())) && access.organizationRole().equals(Optional.of(OrganizationRole.MEMBER))));
        }
    }

    @Test void enforcesRoleBoundariesAndPersistsMembershipAfterRestart() {
        OrganizationId id;
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            register(ledger); OrganizationService service=new OrganizationService(ledger); OrganizationSnapshot organization=create(service,"Guild"); id=organization.id();
            join(service,id,OWNER,MANAGER); join(service,id,OWNER,MEMBER);
            assertEquals(OrganizationOutcome.Status.UNAUTHORIZED,service.changeRole(UUID.randomUUID(),id,MANAGER,MEMBER,OrganizationRole.MANAGER).toCompletableFuture().join().status());
            assertEquals(OrganizationOutcome.Status.SUCCESS,service.changeRole(UUID.randomUUID(),id,OWNER,MANAGER,OrganizationRole.MANAGER).toCompletableFuture().join().status());
            assertEquals(OrganizationOutcome.Status.OWNER_PROTECTED,service.remove(UUID.randomUUID(),id,MANAGER,OWNER).toCompletableFuture().join().status());
            assertEquals(OrganizationOutcome.Status.SUCCESS,service.changeRole(UUID.randomUUID(),id,OWNER,MANAGER,OrganizationRole.MEMBER).toCompletableFuture().join().status());
            assertEquals(OrganizationOutcome.Status.SUCCESS,service.remove(UUID.randomUUID(),id,OWNER,MANAGER).toCompletableFuture().join().status());
            assertEquals(OrganizationOutcome.Status.POLICY_UNRESOLVED,service.requestOwnershipTransfer(UUID.randomUUID(),id,OWNER,MEMBER).status());
            assertEquals(OrganizationRole.OWNER,ledger.organizationRole(id,OWNER).orElseThrow());
            assertEquals(1,ledger.organizationAudit(id,OWNER).stream().filter(record -> record.action()==OrganizationAuditAction.MEMBER_REMOVED).count());
        }
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            assertEquals(OrganizationRole.OWNER,ledger.organizationRole(id,OWNER).orElseThrow());
            assertEquals(OrganizationRole.MEMBER,ledger.organizationRole(id,MEMBER).orElseThrow());
            assertTrue(ledger.organizationRole(id,MANAGER).isEmpty());
            assertEquals(8,ledger.organizationAudit(id,OWNER).size());
        }
    }

    @Test void serializesConcurrentOrganizationPaymentsAndPreservesBalancesOnFailure() throws Exception {
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            register(ledger); OrganizationService service=new OrganizationService(ledger); OrganizationSnapshot organization=create(service,"Commerce"); join(service,organization.id(),OWNER,MANAGER); service.changeRole(UUID.randomUUID(),organization.id(),OWNER,MANAGER,OrganizationRole.MANAGER).toCompletableFuture().join(); seed(ledger,OWNER,100);
            assertEquals(TransferOutcome.Status.SUCCESS,service.deposit(UUID.randomUUID(),organization.id(),OWNER,100).toCompletableFuture().join().status());
            List<CompletableFuture<TransferOutcome>> payments=new ArrayList<>();
            for(int index=0;index<20;index++) payments.add(service.spend(UUID.randomUUID(),organization.id(),MANAGER,ledger.findPersonal(MANAGER).orElseThrow().id(),10).toCompletableFuture());
            CompletableFuture.allOf(payments.toArray(CompletableFuture[]::new)).get(10,TimeUnit.SECONDS);
            assertEquals(10,payments.stream().filter(payment -> payment.join().status()==TransferOutcome.Status.SUCCESS).count());
            assertEquals(0,ledger.findAccount(organization.accountId()).orElseThrow().balance().units());
            assertEquals(100,ledger.findPersonal(MANAGER).orElseThrow().balance().units());
            assertEquals(TransferOutcome.Status.INSUFFICIENT_FUNDS,service.spend(UUID.randomUUID(),organization.id(),MANAGER,ledger.findPersonal(MEMBER).orElseThrow().id(),1).toCompletableFuture().join().status());
            assertEquals(0,ledger.findAccount(organization.accountId()).orElseThrow().balance().units());
        }
    }

    private void register(AccountLedger ledger) { ledger.registerPlayer(OWNER,"Owner").toCompletableFuture().join();ledger.registerPlayer(MANAGER,"Manager").toCompletableFuture().join();ledger.registerPlayer(MEMBER,"Member").toCompletableFuture().join();ledger.registerPlayer(OUTSIDER,"Outsider").toCompletableFuture().join(); }
    private OrganizationSnapshot create(OrganizationService service,String name) { return service.create(UUID.randomUUID(),name,OWNER).toCompletableFuture().join().organization().orElseThrow(); }
    private void join(OrganizationService service,OrganizationId id,UUID inviter,UUID invitee) { assertEquals(OrganizationOutcome.Status.SUCCESS,service.invite(UUID.randomUUID(),id,inviter,invitee).toCompletableFuture().join().status());assertEquals(OrganizationOutcome.Status.SUCCESS,service.accept(UUID.randomUUID(),id,invitee).toCompletableFuture().join().status()); }
    private void seed(AccountLedger ledger,UUID player,long amount) { ledger.adjust(new AdjustmentRequest(UUID.randomUUID(),ledger.findPersonal(player).orElseThrow().id(),amount,"seed")).toCompletableFuture().join(); }
}
