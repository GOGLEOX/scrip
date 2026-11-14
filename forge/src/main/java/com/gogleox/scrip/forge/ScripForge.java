package com.gogleox.scrip.forge;

import com.gogleox.scrip.Scrip;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(Scrip.MOD_ID)
public final class ScripForge {
    public ScripForge() {
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
    }

    private void serverStarted(ServerStartedEvent event) {
        LogUtils.getLogger().info("Scrip P00 server ready (Forge); currency default: {}", Scrip.DEFAULT_CURRENCY);
    }
}
