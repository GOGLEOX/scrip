package com.gogleox.scrip.neoforge;

import com.gogleox.scrip.Scrip;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.fml.common.Mod;

@Mod(Scrip.MOD_ID)
public final class ScripNeoForge {
    public ScripNeoForge() {
        NeoForge.EVENT_BUS.addListener(this::serverStarted);
    }

    private void serverStarted(ServerStartedEvent event) {
        LogUtils.getLogger().info("Scrip P00 server ready (NeoForge); currency default: {}", Scrip.DEFAULT_CURRENCY);
    }
}
