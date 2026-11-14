package com.gogleox.scrip.command;

import com.gogleox.scrip.account.CurrencyDefinition;
import com.gogleox.scrip.api.IntegrationRewardResponse;
import com.gogleox.scrip.ledger.AccountLedger;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FtbQuestsCommandBridgeTest {
    private static final UUID PLAYER=UUID.fromString("00000000-0000-0000-0000-000000000091");
    @Test void onceOnlyQuestClaimBindsSourcePlayerQuestAndRewardAcrossRestart() throws Exception {
        Path dir=Files.createTempDirectory("scrip-ftb-quests");try(AccountLedger ledger=AccountLedger.open(dir,CurrencyDefinition.defaultCurrency())){
            ledger.registerPlayer(PLAYER,"Player").toCompletableFuture().join();CommandService commands=new CommandService(ledger);String identity="570F25BE1914AE96:0F1E2D3C4B5A6978";
            assertEquals(IntegrationRewardResponse.Status.UNAUTHORIZED_SOURCE,commands.ftbQuestReward(PLAYER,PLAYER,identity,25).status());commands.setFtbQuestsCompatible(true);assertEquals(IntegrationRewardResponse.Status.UNAUTHORIZED_SOURCE,commands.ftbQuestReward(UUID.randomUUID(),PLAYER,identity,25).status());assertEquals(IntegrationRewardResponse.Status.UNAUTHORIZED_SOURCE,commands.ftbQuestReward(PLAYER,PLAYER,"not-an-id",25).status());assertEquals(IntegrationRewardResponse.Status.ISSUED,commands.ftbQuestReward(PLAYER,PLAYER,identity,25).status());assertEquals(IntegrationRewardResponse.Status.DUPLICATE,commands.ftbQuestReward(PLAYER,PLAYER,identity,25).status());assertEquals(25,ledger.findPersonal(PLAYER).orElseThrow().balance().units());
        }try(AccountLedger replayed=AccountLedger.open(dir,CurrencyDefinition.defaultCurrency())){CommandService commands=new CommandService(replayed);commands.setFtbQuestsCompatible(true);assertEquals(IntegrationRewardResponse.Status.DUPLICATE,commands.ftbQuestReward(PLAYER,PLAYER,"570F25BE1914AE96:0F1E2D3C4B5A6978",25).status());}
    }
}
