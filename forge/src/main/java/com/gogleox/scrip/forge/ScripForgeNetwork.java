package com.gogleox.scrip.forge;

import com.gogleox.scrip.Scrip;
import com.gogleox.scrip.command.CommandService;
import com.gogleox.scrip.network.*;
import java.io.IOException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.SimpleChannel;

/** Forge transport; all account authority is delegated to the shared server dispatcher. */
final class ScripForgeNetwork {
    private static final ProtocolSessionRegistry SESSIONS=new ProtocolSessionRegistry();
    private static volatile CommandService commands;
    private static final SimpleChannel CHANNEL=ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath(Scrip.MOD_ID,"wallet"))
            .networkProtocolVersion(WalletProtocol.VERSION).optionalServer().simpleChannel();
    static {
        CHANNEL.messageBuilder(Hello.class).direction(PacketFlow.SERVERBOUND).encoder(Hello::encode).decoder(Hello::decode).consumerMainThread(ScripForgeNetwork::receiveHello).add();
        CHANNEL.messageBuilder(RequestPacket.class).direction(PacketFlow.SERVERBOUND).encoder(RequestPacket::encode).decoder(RequestPacket::decode).consumerMainThread(ScripForgeNetwork::receiveRequest).add();
        CHANNEL.messageBuilder(ResponsePacket.class).direction(PacketFlow.CLIENTBOUND).encoder(ResponsePacket::encode).decoder(ResponsePacket::decode).consumerMainThread(ScripForgeNetwork::receiveResponse).add();
    }
    static void initialize() { }
    static void setCommandService(CommandService value) { commands=value; }
    static void sendHello() { CHANNEL.send(new Hello(WalletProtocol.VERSION,0),PacketDistributor.SERVER.noArg()); }
    static void sendRequest(WalletRequest request) { CHANNEL.send(new RequestPacket(WalletWireCodec.encodeRequest(request)),PacketDistributor.SERVER.noArg()); }
    private static void receiveHello(Hello hello,CustomPayloadEvent.Context context) { if(context.getSender()!=null&&SESSIONS.negotiate(context.getSender().getUUID(),hello.version,hello.capabilities,System.currentTimeMillis()).isEmpty())ScripForge.LOGGER.info("Scrip optional wallet protocol negotiated (Forge)");context.setPacketHandled(true); }
    private static void receiveRequest(RequestPacket packet,CustomPayloadEvent.Context context) {
        ServerPlayer sender=context.getSender(); CommandService current=commands;
        if(sender!=null&&current!=null) try { WalletRequest request=WalletWireCodec.decodeRequest(packet.bytes); new WalletProtocolService(SESSIONS,current).handle(sender.getUUID(),request,packet.bytes.length,System.currentTimeMillis()).thenAccept(response->CHANNEL.send(new ResponsePacket(WalletWireCodec.encodeResponse(response)),PacketDistributor.PLAYER.with(sender))); }
        catch(IOException|IllegalArgumentException e){CHANNEL.send(new ResponsePacket(WalletWireCodec.encodeResponse(WalletResponse.error(-1,WalletProtocol.Error.MALFORMED))),PacketDistributor.PLAYER.with(sender));}
        context.setPacketHandled(true);
    }
    private static void receiveResponse(ResponsePacket packet,CustomPayloadEvent.Context context) { try { WalletResponse response=WalletWireCodec.decodeResponse(packet.bytes); ScripForge.LOGGER.info("Scrip wallet response received (Forge): {}",response.type()); } catch(IOException ignored) { } context.setPacketHandled(true); }
    private record Hello(int version,int capabilities){static void encode(Hello h,FriendlyByteBuf b){b.writeVarInt(h.version);b.writeVarInt(h.capabilities);}static Hello decode(FriendlyByteBuf b){if(b.readableBytes()>8)throw new IllegalArgumentException("oversized hello");return new Hello(b.readVarInt(),b.readVarInt());}}
    private record RequestPacket(byte[] bytes){static void encode(RequestPacket p,FriendlyByteBuf b){b.writeByteArray(p.bytes);}static RequestPacket decode(FriendlyByteBuf b){return new RequestPacket(b.readByteArray(WalletProtocol.MAX_PACKET_BYTES));}}
    private record ResponsePacket(byte[] bytes){static void encode(ResponsePacket p,FriendlyByteBuf b){b.writeByteArray(p.bytes);}static ResponsePacket decode(FriendlyByteBuf b){return new ResponsePacket(b.readByteArray(WalletProtocol.MAX_PACKET_BYTES));}}
}
