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
final class ScripNeoForgeNetwork {
    private static final ProtocolSessionRegistry SESSIONS=new ProtocolSessionRegistry();
    private static volatile CommandService commands;
    @SubscribeEvent static void register(RegisterPayloadHandlersEvent event){var registrar=event.registrar("1").optional();registrar.playToServer(Hello.TYPE,Hello.STREAM_CODEC,ScripNeoForgeNetwork::receiveHello);registrar.playToServer(RequestPacket.TYPE,RequestPacket.STREAM_CODEC,ScripNeoForgeNetwork::receiveRequest);registrar.playToClient(ResponsePacket.TYPE,ResponsePacket.STREAM_CODEC,ScripNeoForgeNetwork::receiveResponse);}
    static void setCommandService(CommandService value){commands=value;}
    private static void receiveHello(Hello hello,IPayloadContext context){if(context.player() instanceof ServerPlayer player&&SESSIONS.negotiate(player.getUUID(),hello.version,hello.capabilities,System.currentTimeMillis()).isEmpty())ScripNeoForge.LOGGER.info("Scrip optional wallet protocol negotiated (NeoForge)");}
    private static void receiveRequest(RequestPacket packet,IPayloadContext context){if(!(context.player() instanceof ServerPlayer player)||commands==null)return;try{WalletRequest request=WalletWireCodec.decodeRequest(packet.bytes);new WalletProtocolService(SESSIONS,commands).handle(player.getUUID(),request,packet.bytes.length,System.currentTimeMillis()).thenAccept(response->context.reply(new ResponsePacket(WalletWireCodec.encodeResponse(response))));}catch(IOException|IllegalArgumentException e){context.reply(new ResponsePacket(WalletWireCodec.encodeResponse(WalletResponse.error(-1,WalletProtocol.Error.MALFORMED))));}}
    private static void receiveResponse(ResponsePacket packet,IPayloadContext context){try{WalletResponse response=WalletWireCodec.decodeResponse(packet.bytes);ScripNeoForge.LOGGER.info("Scrip wallet response received (NeoForge): {}",response.type());}catch(IOException ignored){}}
    record Hello(int version,int capabilities) implements CustomPacketPayload {static final Type<Hello> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Scrip.MOD_ID,"wallet_hello"));static final StreamCodec<RegistryFriendlyByteBuf,Hello> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Hello::version,ByteBufCodecs.VAR_INT,Hello::capabilities,Hello::new);@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}}
    record RequestPacket(byte[] bytes) implements CustomPacketPayload {static final Type<RequestPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Scrip.MOD_ID,"wallet_request"));static final StreamCodec<RegistryFriendlyByteBuf,RequestPacket> STREAM_CODEC=StreamCodec.of((buf,value)->buf.writeByteArray(value.bytes),(buf)->new RequestPacket(buf.readByteArray(WalletProtocol.MAX_PACKET_BYTES)));@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}}
    record ResponsePacket(byte[] bytes) implements CustomPacketPayload {static final Type<ResponsePacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Scrip.MOD_ID,"wallet_response"));static final StreamCodec<RegistryFriendlyByteBuf,ResponsePacket> STREAM_CODEC=StreamCodec.of((buf,value)->buf.writeByteArray(value.bytes),(buf)->new ResponsePacket(buf.readByteArray(WalletProtocol.MAX_PACKET_BYTES)));@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}}
}
