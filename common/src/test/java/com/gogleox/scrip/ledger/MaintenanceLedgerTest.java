package com.gogleox.scrip.ledger;

import com.gogleox.scrip.account.*;
import com.gogleox.scrip.maintenance.*;
import com.gogleox.scrip.organization.*;
import com.gogleox.scrip.storage.JournalFileLedgerStore;
import java.nio.file.Path;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MaintenanceLedgerTest {
    @TempDir Path directory;
    private static final UUID OLD=UUID.fromString("00000000-0000-0000-0000-000000000021"), POSITIVE=UUID.fromString("00000000-0000-0000-0000-000000000022"), FROZEN=UUID.fromString("00000000-0000-0000-0000-000000000023"), OWNER=UUID.fromString("00000000-0000-0000-0000-000000000024");
    private static final Instant CREATED=Instant.parse("2020-01-01T00:00:00Z"), CHECKED=Instant.parse("2021-01-01T00:00:00Z");

    @Test void persistsConfigurationAndPreviewDoesNotMutateState() {
        try(AccountLedger ledger=open(CREATED)) {
            register(ledger,OLD,"Old"); register(ledger,POSITIVE,"Positive"); register(ledger,FROZEN,"Frozen"); register(ledger,OWNER,"Owner");
            ledger.adjust(new AdjustmentRequest(UUID.randomUUID(),ledger.findPersonal(POSITIVE).orElseThrow().id(),1,"seed")).toCompletableFuture().join();
            ledger.setFrozen(UUID.randomUUID(),ledger.findPersonal(FROZEN).orElseThrow().id(),true,AuditActor.console(),"review").toCompletableFuture().join();
            OrganizationService organizations=new OrganizationService(ledger); OrganizationSnapshot organization=organizations.create(UUID.randomUUID(),"Maintenance Org",OWNER).toCompletableFuture().join().organization().orElseThrow();
            MaintenanceConfiguration saved=ledger.updateMaintenanceConfiguration(UUID.randomUUID(),new MaintenanceConfiguration(false,365,48,100)).toCompletableFuture().join();
            assertEquals(48,saved.intervalHours());
        }
        try(AccountLedger ledger=open(CHECKED)) {
            long before=ledger.journalMaintenancePreview().latestSequence();
            MaintenancePreview preview=ledger.previewMaintenance(365,java.util.Set.of());
            assertEquals(1,preview.eligibleAccounts()); assertEquals(1,preview.excludedByReason().get(MaintenanceExclusion.POSITIVE_BALANCE)); assertEquals(1,preview.excludedByReason().get(MaintenanceExclusion.FROZEN)); assertEquals(1,preview.excludedByReason().get(MaintenanceExclusion.ORGANIZATION_MEMBER));
            assertEquals(0,preview.excludedByReason().get(MaintenanceExclusion.PENDING_SETTLEMENT)); assertEquals(before,ledger.journalMaintenancePreview().latestSequence()); assertEquals(0,ledger.findPersonal(OLD).orElseThrow().balance().units());
            assertFalse(ledger.maintenanceConfiguration().enabled()); assertEquals(48,ledger.maintenanceConfiguration().intervalHours());
            assertEquals(1,ledger.previewMaintenance(365,java.util.Set.of()).eligibleAccounts());
        }
    }

    @Test void honorsBoundaryAndUsesBoundedLargeAccountScans() {
        Instant boundary=Instant.parse("2020-01-02T00:00:00Z");
        try(AccountLedger ledger=open(boundary)) {
            register(ledger,OLD,"Boundary");
        }
        try(AccountLedger ledger=open(CHECKED)) {
            assertEquals(0,ledger.previewMaintenance(365,java.util.Set.of()).eligibleAccounts());
        }
        try(AccountLedger ledger=open(CREATED)) {
            for(int index=0;index<110;index++) register(ledger,new UUID(0,index+100L),"P"+index);
            MaintenancePreview preview=ledger.previewMaintenance(365,java.util.Set.of());
            assertEquals(100,preview.scannedAccounts()); assertEquals(11,preview.unscannedAccounts());
        }
    }

    @Test void validatesConfigurationRangesAndJournalPreviewIsReadOnly() {
        assertThrows(IllegalArgumentException.class,()->new MaintenanceConfiguration(true,29,24,100));
        assertThrows(IllegalArgumentException.class,()->new MaintenanceConfiguration(true,365,169,100));
        try(AccountLedger ledger=open(CREATED)) {
            long sequence=ledger.journalMaintenancePreview().latestSequence(); JournalMaintenancePreview preview=ledger.journalMaintenancePreview();
            assertEquals(sequence,preview.latestSequence()); assertEquals(sequence,ledger.journalMaintenancePreview().latestSequence());
        }
    }

    private AccountLedger open(Instant instant) { return new AccountLedger(JournalFileLedgerStore.open(directory),CurrencyDefinition.defaultCurrency(),Clock.fixed(instant,ZoneOffset.UTC)); }
    private void register(AccountLedger ledger,UUID id,String name) { ledger.registerPlayer(id,name).toCompletableFuture().join(); }
}
