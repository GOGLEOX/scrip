package com.gogleox.scrip.neoforge;

import com.gogleox.scrip.Scrip;
import com.gogleox.scrip.network.WalletProtocol;
import com.gogleox.scrip.network.WalletRequest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Physical-client-only handshake sender. This class is never loaded by a dedicated server. */
@EventBusSubscriber(modid=Scrip.MOD_ID, value=Dist.CLIENT, bus=EventBusSubscriber.Bus.GAME)
final class ScripNeoForgeClientNetwork {
    @SubscribeEvent static void loggedIn(ClientPlayerNetworkEvent.LoggingIn event) { PacketDistributor.sendToServer(new ScripNeoForgeNetwork.Hello(WalletProtocol.VERSION, 0)); PacketDistributor.sendToServer(new ScripNeoForgeNetwork.RequestPacket(com.gogleox.scrip.network.WalletWireCodec.encodeRequest(WalletRequest.open(System.nanoTime())))); }
}
