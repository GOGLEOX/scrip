package com.gogleox.scrip.neoforge;

import com.gogleox.scrip.Scrip;
import com.gogleox.scrip.command.CommandService;
import com.gogleox.scrip.network.*;
import java.io.IOException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Optional NeoForge transport; the connection supplies the actor and all decisions stay server-side. */
@EventBusSubscriber(modid=Scrip.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
public final class ScripNeoForgeNetwork {
    private static final ProtocolSessionRegistry SESSIONS=new ProtocolSessionRegistry();
    private static volatile CommandService commands;
    /**
     * Registers the complete optional-client payload set on both physical sides.
     *
     * <p>NeoForge exchanges this declaration while establishing a play connection.
     * The class, callback, and payload types are deliberately public: otherwise a
     * dedicated server can start while the remote endpoint never advertises these
     * optional payloads, causing a client-side {@code may not be sent} disconnect.
     */
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar(Integer.toString(WalletProtocol.VERSION)).optional();
        registrar.playToServer(Hello.TYPE,Hello.STREAM_CODEC,ScripNeoForgeNetwork::receiveHello);
        registrar.playToServer(RequestPacket.TYPE,RequestPacket.STREAM_CODEC,ScripNeoForgeNetwork::receiveRequest);
        registrar.playToClient(ResponsePacket.TYPE,ResponsePacket.STREAM_CODEC,ScripNeoForgeNetwork::receiveResponse);
        ScripNeoForge.LOGGER.info("Registered optional Scrip wallet payloads for NeoForge protocol {}",WalletProtocol.VERSION);
    }
    static void setCommandService(CommandService value){commands=value;}
    static void requestWalletOpen(ServerPlayer player){if(SESSIONS.negotiated(player.getUUID()))net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ResponsePacket(WalletWireCodec.encodeResponse(new WalletResponse(0,WalletProtocol.Response.NOTIFICATION,java.util.Optional.empty(),java.util.Optional.empty(),java.util.List.of(),java.util.List.of(),java.util.Optional.empty(),java.util.Optional.of("OPEN_WALLET")))));}
    /** Sends a post-commit, server-derived balance refresh only to a negotiated client. */
    static void refreshWallet(ServerPlayer player){CommandService current=commands;if(current==null||!SESSIONS.negotiated(player.getUUID()))return;current.balance(player.getUUID()).ifPresent(account->net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new ResponsePacket(WalletWireCodec.encodeResponse(new WalletResponse(0,WalletProtocol.Response.WALLET_SNAPSHOT,java.util.Optional.of(account),java.util.Optional.empty(),java.util.List.of(),current.accessibleAccounts(player.getUUID()),java.util.Optional.empty(),java.util.Optional.of("BALANCE_UPDATED"))))));}
    private static void receiveHello(Hello hello,IPayloadContext context){if(context.player() instanceof ServerPlayer player&&SESSIONS.negotiate(player.getUUID(),hello.version,hello.capabilities,System.currentTimeMillis()).isEmpty())ScripNeoForge.LOGGER.info("Scrip optional wallet protocol negotiated (NeoForge)");}
    private static void receiveRequest(RequestPacket packet,IPayloadContext context){if(!(context.player() instanceof ServerPlayer player)||commands==null)return;try{WalletRequest request=WalletWireCodec.decodeRequest(packet.bytes);new WalletProtocolService(SESSIONS,commands).handle(player.getUUID(),request,packet.bytes.length,System.currentTimeMillis()).thenAccept(response->context.reply(new ResponsePacket(WalletWireCodec.encodeResponse(withBranding(response)))));}catch(IOException|IllegalArgumentException e){context.reply(new ResponsePacket(WalletWireCodec.encodeResponse(WalletResponse.error(-1,WalletProtocol.Error.MALFORMED))));}}
    // NeoForge dispatches play-to-client handlers only in the physical client environment.
    private static void receiveResponse(ResponsePacket packet,IPayloadContext context){try{WalletResponse response=WalletWireCodec.decodeResponse(packet.bytes);ScripNeoForgeClientResponseSink.accept(response);ScripNeoForge.LOGGER.info("Scrip wallet response received (NeoForge): {}",response.type());}catch(IOException ignored){}}
    private static WalletResponse withBranding(WalletResponse response){return response.type()==WalletProtocol.Response.WALLET_SNAPSHOT&&response.detail().isEmpty()?new WalletResponse(response.requestId(),response.type(),response.account(),response.preview(),response.history(),response.accessibleAccounts(),response.error(),java.util.Optional.of(WalletBranding.serverConfigured().wireValue())):response;}
    public record Hello(int version,int capabilities) implements CustomPacketPayload {public static final Type<Hello> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Scrip.MOD_ID,"wallet_hello"));public static final StreamCodec<RegistryFriendlyByteBuf,Hello> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Hello::version,ByteBufCodecs.VAR_INT,Hello::capabilities,Hello::new);@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}}
    public record RequestPacket(byte[] bytes) implements CustomPacketPayload {public static final Type<RequestPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Scrip.MOD_ID,"wallet_request"));public static final StreamCodec<RegistryFriendlyByteBuf,RequestPacket> STREAM_CODEC=StreamCodec.of((buf,value)->buf.writeByteArray(value.bytes),(buf)->new RequestPacket(buf.readByteArray(WalletProtocol.MAX_PACKET_BYTES)));@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}}
    public record ResponsePacket(byte[] bytes) implements CustomPacketPayload {public static final Type<ResponsePacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Scrip.MOD_ID,"wallet_response"));public static final StreamCodec<RegistryFriendlyByteBuf,ResponsePacket> STREAM_CODEC=StreamCodec.of((buf,value)->buf.writeByteArray(value.bytes),(buf)->new ResponsePacket(buf.readByteArray(WalletProtocol.MAX_PACKET_BYTES)));@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}}
}
