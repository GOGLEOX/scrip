package com.gogleox.scrip.command;

import com.gogleox.scrip.account.AccountControlOutcome;
import com.gogleox.scrip.account.AuditActor;
import com.gogleox.scrip.account.CurrencyDefinition;
import com.gogleox.scrip.account.TransferOutcome;
import com.gogleox.scrip.ledger.AccountLedger;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CommandServiceTest {
    @TempDir Path directory;
    private static final UUID ALICE=UUID.fromString("00000000-0000-0000-0000-000000000001"), BOB=UUID.fromString("00000000-0000-0000-0000-000000000002"), ADMIN=UUID.fromString("00000000-0000-0000-0000-000000000003");
    @Test void resolvesPreviouslyJoinedUuidAfterRenameAndAuditsAdminActions() {
        try(AccountLedger ledger=AccountLedger.open(directory, CurrencyDefinition.defaultCurrency())) {
            CommandService commands=new CommandService(ledger); ledger.registerPlayer(ALICE,"Alice").toCompletableFuture().join(); ledger.registerPlayer(BOB,"Bob").toCompletableFuture().join(); ledger.registerPlayer(BOB,"RenamedBob").toCompletableFuture().join();
            assertTrue(commands.balanceByVerifiedPlayer(BOB).isPresent());
            assertEquals(TransferOutcome.Status.SUCCESS,commands.adjust(ALICE,100,"seed",AuditActor.player(ADMIN,"Admin"),UUID.randomUUID()).status());
            assertEquals(AccountControlOutcome.Status.SUCCESS,commands.setFrozen(ALICE,true,"review",AuditActor.player(ADMIN,"Admin"),UUID.randomUUID()).status());
            assertEquals(TransferOutcome.Status.FROZEN_ACCOUNT,commands.pay(ALICE,BOB,10,UUID.randomUUID()).status());
            assertEquals(2,commands.audit(ALICE).size());
            assertEquals(TransferOutcome.Status.UNKNOWN_ACCOUNT,commands.pay(ALICE,UUID.randomUUID(),10,UUID.randomUUID()).status());
        }
    }
    @Test void rejectsInvalidAliasesAndKeepsExistingRoots() {
        assertEquals(List.of("money","coins"),CommandAliasPolicy.configuredAliases("money, scrip, MONEY, bad alias, coins"));
        assertEquals(List.of("money"),CommandAliasPolicy.withoutConflicts(List.of("money","coins"),List.of("coins","scrip")));
    }
}
