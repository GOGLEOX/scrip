package com.gogleox.scrip.forge;

import com.gogleox.scrip.Scrip;
import com.gogleox.scrip.client.WalletClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Forge-only entry point for the optional wallet controls. */
@Mod.EventBusSubscriber(modid=Scrip.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
final class ScripForgeClientUi {
    private static final KeyMapping WALLET=new KeyMapping("key.scrip.wallet",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_B,"key.categories.scrip");
    @SubscribeEvent static void register(RegisterKeyMappingsEvent event){event.register(WALLET);}
    @Mod.EventBusSubscriber(modid=Scrip.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
    static final class Input {
        @SubscribeEvent static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END&&WALLET.consumeClick()&&!WalletClient.isOpen())WalletClient.open();}
        @SubscribeEvent static void renderHud(CustomizeGuiOverlayEvent.Chat event){WalletClient.renderHud(event.getGuiGraphics());}
        /** The chat overlay is suppressed while its text screen is open, so draw the same HUD after that screen. */
        @SubscribeEvent static void renderHudOverChat(ScreenEvent.Render.Post event){if(event.getScreen() instanceof ChatScreen)WalletClient.renderHud(event.getGuiGraphics());}
    }
}
