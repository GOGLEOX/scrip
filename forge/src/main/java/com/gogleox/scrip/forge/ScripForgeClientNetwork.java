package com.gogleox.scrip.forge;

import com.gogleox.scrip.Scrip;
import com.gogleox.scrip.network.WalletRequest;
import com.gogleox.scrip.client.WalletClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Physical-client-only handshake sender. This class is never loaded by a dedicated server. */
@Mod.EventBusSubscriber(modid=Scrip.MOD_ID, value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
final class ScripForgeClientNetwork {
    @SubscribeEvent static void loggedIn(ClientPlayerNetworkEvent.LoggingIn event) { WalletClient.configure(ScripForgeNetwork::sendRequest); ScripForgeNetwork.sendHello(); ScripForgeNetwork.sendRequest(WalletRequest.open(System.nanoTime())); }
}
