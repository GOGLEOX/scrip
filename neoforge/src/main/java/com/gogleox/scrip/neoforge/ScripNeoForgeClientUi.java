package com.gogleox.scrip.neoforge;

import com.gogleox.scrip.Scrip;
import com.gogleox.scrip.client.WalletClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.lwjgl.glfw.GLFW;

/** NeoForge-only entry point for the optional wallet controls. */
@EventBusSubscriber(modid=Scrip.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
final class ScripNeoForgeClientUi {
    private static final KeyMapping WALLET=new KeyMapping("key.scrip.wallet",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_B,"key.categories.scrip");
    @SubscribeEvent static void register(RegisterKeyMappingsEvent event){event.register(WALLET);}
    @EventBusSubscriber(modid=Scrip.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.GAME)
    static final class Input {
        @SubscribeEvent static void tick(ClientTickEvent.Post event){if(WALLET.consumeClick()&&!WalletClient.isOpen())WalletClient.open();}
        @SubscribeEvent static void renderHud(RenderGuiEvent.Post event){WalletClient.renderHud(event.getGuiGraphics());}
    }
}
