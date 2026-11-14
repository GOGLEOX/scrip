package com.gogleox.scrip.command;

import com.gogleox.scrip.account.CurrencyDefinition;
import com.gogleox.scrip.api.IntegrationRewardResponse;
import com.gogleox.scrip.ledger.AccountLedger;
import com.gogleox.scrip.reward.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarterKitCommandBridgeTest {
    private static final UUID PLAYER=UUID.fromString("00000000-0000-0000-0000-000000000081");
    @Test void verifiedStarterKitContextUsesCanonicalClaimAndSelectedMode() throws Exception {
        Path dir=Files.createTempDirectory("scrip-starter-kit");try(AccountLedger ledger=AccountLedger.open(dir,CurrencyDefinition.defaultCurrency())){
            ledger.registerPlayer(PLAYER,"Player").toCompletableFuture().join();ledger.updateRewardConfiguration(UUID.randomUUID(),new RewardConfiguration(Map.of(),20,StarterGrantMode.STARTER_KIT,30,0)).toCompletableFuture().join();CommandService commands=new CommandService(ledger);
            assertEquals(IntegrationRewardResponse.Status.UNAUTHORIZED_SOURCE,commands.starterKitGrant(PLAYER,PLAYER,"starter",20).status());commands.setStarterKitCompatible(true);assertEquals(IntegrationRewardResponse.Status.UNAUTHORIZED_SOURCE,commands.starterKitGrant(UUID.randomUUID(),PLAYER,"starter",20).status());assertEquals(IntegrationRewardResponse.Status.INVALID,commands.starterKitGrant(PLAYER,PLAYER,"starter",19).status());assertEquals(IntegrationRewardResponse.Status.ISSUED,commands.starterKitGrant(PLAYER,PLAYER,"starter",20).status());assertEquals(IntegrationRewardResponse.Status.DUPLICATE,commands.starterKitGrant(PLAYER,PLAYER,"starter",20).status());assertTrue(ledger.hasPermanentClaim("starter_grant:"+PLAYER));assertEquals(20,ledger.findPersonal(PLAYER).orElseThrow().balance().units());
        }try(AccountLedger replayed=AccountLedger.open(dir,CurrencyDefinition.defaultCurrency())){CommandService commands=new CommandService(replayed);commands.setStarterKitCompatible(true);assertEquals(IntegrationRewardResponse.Status.DUPLICATE,commands.starterKitGrant(PLAYER,PLAYER,"starter",20).status());}
    }
}
