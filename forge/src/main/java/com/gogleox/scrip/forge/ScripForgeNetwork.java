package com.gogleox.scrip.forge;

import com.gogleox.scrip.Scrip;
import com.gogleox.scrip.network.*;
import java.util.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** Forge-only optional handshake transport. It never loads client classes on a dedicated server. */
final class ScripForgeNetwork {
    private static final ProtocolSessionRegistry SESSIONS=new ProtocolSessionRegistry();
    private static final SimpleChannel CHANNEL=ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(Scrip.MOD_ID,"wallet"))
            // Server-side channel: accept a Forge client that does not advertise this optional feature.
            .networkProtocolVersion(WalletProtocol.VERSION).optionalServer().simpleChannel();
    static { CHANNEL.messageBuilder(Hello.class).direction(PacketFlow.SERVERBOUND).encoder(Hello::encode).decoder(Hello::decode).consumerMainThread(ScripForgeNetwork::receive).add(); }
    static void initialize() { }
    static void sendHello() { CHANNEL.send(new Hello(WalletProtocol.VERSION, 0), PacketDistributor.SERVER.noArg()); }
    private static void receive(Hello hello, CustomPayloadEvent.Context context) { if(context.getSender()!=null && SESSIONS.negotiate(context.getSender().getUUID(),hello.version,hello.capabilities,System.currentTimeMillis()).isEmpty()) ScripForge.LOGGER.info("Scrip optional wallet protocol negotiated (Forge)"); context.setPacketHandled(true); }
    private record Hello(int version,int capabilities){static void encode(Hello h,FriendlyByteBuf b){b.writeVarInt(h.version);b.writeVarInt(h.capabilities);}static Hello decode(FriendlyByteBuf b){if(b.readableBytes()>8)throw new IllegalArgumentException("oversized hello");return new Hello(b.readVarInt(),b.readVarInt());}}
}
