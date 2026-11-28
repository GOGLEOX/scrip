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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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
    static void requestWalletOpen(ServerPlayer player) { if(SESSIONS.negotiated(player.getUUID())) CHANNEL.send(new ResponsePacket(WalletWireCodec.encodeResponse(new WalletResponse(0,WalletProtocol.Response.NOTIFICATION,java.util.Optional.empty(),java.util.Optional.empty(),java.util.List.of(),java.util.List.of(),java.util.Optional.empty(),java.util.Optional.of("OPEN_WALLET")))),PacketDistributor.PLAYER.with(player)); }
    /** Sends a post-commit, server-derived balance refresh only to a negotiated client. */
    static void refreshWallet(ServerPlayer player) { CommandService current=commands; if(current==null||!SESSIONS.negotiated(player.getUUID()))return; current.balance(player.getUUID()).ifPresent(account -> CHANNEL.send(new ResponsePacket(WalletWireCodec.encodeResponse(new WalletResponse(0,WalletProtocol.Response.WALLET_SNAPSHOT,java.util.Optional.of(account),java.util.Optional.empty(),java.util.List.of(),current.accessibleAccounts(player.getUUID()),java.util.Optional.empty(),java.util.Optional.of("BALANCE_UPDATED")))),PacketDistributor.PLAYER.with(player))); }
    static void sendHello() { CHANNEL.send(new Hello(WalletProtocol.VERSION,0),PacketDistributor.SERVER.noArg()); }
    static void sendRequest(WalletRequest request) { CHANNEL.send(new RequestPacket(WalletWireCodec.encodeRequest(request)),PacketDistributor.SERVER.noArg()); }
    private static void receiveHello(Hello hello,CustomPayloadEvent.Context context) { if(context.getSender()!=null&&SESSIONS.negotiate(context.getSender().getUUID(),hello.version,hello.capabilities,System.currentTimeMillis()).isEmpty())ScripForge.LOGGER.info("Scrip optional wallet protocol negotiated (Forge)");context.setPacketHandled(true); }
    private static void receiveRequest(RequestPacket packet,CustomPayloadEvent.Context context) {
        ServerPlayer sender=context.getSender(); CommandService current=commands;
        if(sender!=null&&current!=null) try { WalletRequest request=WalletWireCodec.decodeRequest(packet.bytes); new WalletProtocolService(SESSIONS,current).handle(sender.getUUID(),request,packet.bytes.length,System.currentTimeMillis()).thenAccept(response->CHANNEL.send(new ResponsePacket(WalletWireCodec.encodeResponse(withBranding(response))),PacketDistributor.PLAYER.with(sender))); }
        catch(IOException|IllegalArgumentException e){CHANNEL.send(new ResponsePacket(WalletWireCodec.encodeResponse(WalletResponse.error(-1,WalletProtocol.Error.MALFORMED))),PacketDistributor.PLAYER.with(sender));}
        context.setPacketHandled(true);
    }
    private static void receiveResponse(ResponsePacket packet,CustomPayloadEvent.Context context) { try { WalletResponse response=WalletWireCodec.decodeResponse(packet.bytes); DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ScripForgeClientResponseSink.accept(response)); ScripForge.LOGGER.info("Scrip wallet response received (Forge): {}",response.type()); } catch(IOException ignored) { } context.setPacketHandled(true); }
    private static WalletResponse withBranding(WalletResponse response) { return response.type()==WalletProtocol.Response.WALLET_SNAPSHOT && response.detail().isEmpty() ? new WalletResponse(response.requestId(),response.type(),response.account(),response.preview(),response.history(),response.accessibleAccounts(),response.error(),java.util.Optional.of(WalletBranding.serverConfigured().wireValue())) : response; }
    private record Hello(int version,int capabilities){static void encode(Hello h,FriendlyByteBuf b){b.writeVarInt(h.version);b.writeVarInt(h.capabilities);}static Hello decode(FriendlyByteBuf b){if(b.readableBytes()>8)throw new IllegalArgumentException("oversized hello");return new Hello(b.readVarInt(),b.readVarInt());}}
    private record RequestPacket(byte[] bytes){static void encode(RequestPacket p,FriendlyByteBuf b){b.writeByteArray(p.bytes);}static RequestPacket decode(FriendlyByteBuf b){return new RequestPacket(b.readByteArray(WalletProtocol.MAX_PACKET_BYTES));}}
    private record ResponsePacket(byte[] bytes){static void encode(ResponsePacket p,FriendlyByteBuf b){b.writeByteArray(p.bytes);}static ResponsePacket decode(FriendlyByteBuf b){return new ResponsePacket(b.readByteArray(WalletProtocol.MAX_PACKET_BYTES));}}
}
