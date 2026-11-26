package com.gogleox.scrip.forge;

import com.gogleox.scrip.Scrip;
import com.gogleox.scrip.account.CurrencyDefinition;
import com.gogleox.scrip.command.CommandService;
import com.gogleox.scrip.command.ScripCommandRegistrar;
import com.gogleox.scrip.ledger.AccountLedger;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.util.Optional;

@Mod(Scrip.MOD_ID)
public final class ScripForge {
    static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private AccountLedger ledger;
    private CommandService commands;

    public ScripForge() {
        ScripForgeNetwork.initialize();
        MinecraftForge.EVENT_BUS.addListener(this::serverStarting);
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
        MinecraftForge.EVENT_BUS.addListener(this::serverStopping);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(this::playerLoggedIn);
    }

    private void serverStarting(ServerStartingEvent event) {
        ledger = AccountLedger.open(event.getServer().getWorldPath(LevelResource.ROOT).resolve("scrip"), CurrencyDefinition.defaultCurrency());
        commands = new CommandService(ledger);
        ScripForgeNetwork.setCommandService(commands);
        commands.setStarterKitCompatible(ModList.get().isLoaded("starterkit") && ModList.get().isLoaded("collective"));
    }

    private void serverStarted(ServerStartedEvent event) {
        LOGGER.info("Scrip P07-C server ready (Forge); currency default: {}; Starter Kit compatible: {}; FTB Quests integration disabled on Forge", Scrip.DEFAULT_CURRENCY, ModList.get().isLoaded("starterkit") && ModList.get().isLoaded("collective"));
    }

    private void serverStopping(ServerStoppingEvent event) {
        if (ledger != null) { ledger.close(); ledger = null; commands = null; ScripForgeNetwork.setCommandService(null); }
    }

    private void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (commands == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        ledger.registerPlayer(player.getUUID(), player.getGameProfile().getName()).toCompletableFuture().join();
        commands.playerJoined(player.getUUID());
    }

    private void registerCommands(RegisterCommandsEvent event) {
        new ScripCommandRegistrar(() -> Optional.ofNullable(commands)).register(event.getDispatcher(), System.getProperty("scrip.command.aliases", ""));
    }
}
