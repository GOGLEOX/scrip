package com.gogleox.scrip.reward;

import com.gogleox.scrip.account.CurrencyDefinition;
import com.gogleox.scrip.ledger.AccountLedger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RewardServiceTest {
    private static final UUID PLAYER=UUID.fromString("00000000-0000-0000-0000-000000000061");
    @Test void rewardsAreServerValidatedAndOneTimeClaimsSurviveRestart() throws Exception {
        Path directory=Files.createTempDirectory("scrip-reward");
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            ledger.registerPlayer(PLAYER,"Player").toCompletableFuture().join();
            ledger.updateRewardConfiguration(UUID.randomUUID(),new RewardConfiguration(Map.of(RewardSource.ADVANCEMENT,25L,RewardSource.MOB_KILL,3L,RewardSource.BLOCK_MINING,2L,RewardSource.ACTIVE_PLAYTIME,4L),10,StarterGrantMode.FIRST_JOIN,1,0)).toCompletableFuture().join();
            RewardService rewards=new RewardService(ledger);
            assertEquals(RewardResult.ISSUED,rewards.advancement(PLAYER,"minecraft:story/root"));
            assertEquals(RewardResult.DUPLICATE,rewards.advancement(PLAYER,"minecraft:story/root"));
            assertEquals(RewardResult.INELIGIBLE,rewards.mobKill(PLAYER,"minecraft:zombie",false,true));
            assertEquals(RewardResult.ISSUED,rewards.mobKill(PLAYER,"minecraft:zombie",true,true));
            assertEquals(RewardResult.INELIGIBLE,rewards.blockMined(PLAYER,"minecraft:stone",true,true,true));
            assertEquals(RewardResult.ISSUED,rewards.blockMined(PLAYER,"minecraft:stone",true,true,false));
            assertEquals(1,rewards.recordActivePlaytime(PLAYER,60));
            assertEquals(RewardResult.ISSUED,rewards.starterGrant(PLAYER,StarterGrantMode.FIRST_JOIN));
            assertEquals(RewardResult.DUPLICATE,rewards.starterGrant(PLAYER,StarterGrantMode.FIRST_JOIN));
            assertEquals(44,ledger.findPersonal(PLAYER).orElseThrow().balance().units());
        }
        try(AccountLedger replayed=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            assertTrue(replayed.hasPermanentClaim("advancement:"+PLAYER+":advancement:minecraft:story/root"));
            assertEquals(44,replayed.findPersonal(PLAYER).orElseThrow().balance().units());
            assertEquals(RewardResult.DUPLICATE,new RewardService(replayed).starterGrant(PLAYER,StarterGrantMode.FIRST_JOIN));
        }
    }
    @Test void kitModeWithholdsFirstJoinAndDisabledSourcesCannotIssue() throws Exception {
        Path directory=Files.createTempDirectory("scrip-reward-kit");
        try(AccountLedger ledger=AccountLedger.open(directory,CurrencyDefinition.defaultCurrency())) {
            ledger.registerPlayer(PLAYER,"Player").toCompletableFuture().join();
            ledger.updateRewardConfiguration(UUID.randomUUID(),new RewardConfiguration(Map.of(),50,StarterGrantMode.STARTER_KIT,30,0)).toCompletableFuture().join();
            RewardService rewards=new RewardService(ledger);
            assertEquals(RewardResult.DISABLED,rewards.starterGrant(PLAYER,StarterGrantMode.FIRST_JOIN));
            assertEquals(RewardResult.DISABLED,rewards.advancement(PLAYER,"minecraft:story/root"));
            assertEquals(RewardResult.ISSUED,rewards.starterGrant(PLAYER,StarterGrantMode.STARTER_KIT));
            assertEquals(50,ledger.findPersonal(PLAYER).orElseThrow().balance().units());
        }
    }
}
