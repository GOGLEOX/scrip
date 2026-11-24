package com.gogleox.scrip.neoforge;

import com.gogleox.scrip.Scrip;
import com.gogleox.scrip.network.*;
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

/** NeoForge-only optional payload handshake; no client types are referenced by the server handler. */
@EventBusSubscriber(modid=Scrip.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
final class ScripNeoForgeNetwork {
    private static final ProtocolSessionRegistry SESSIONS=new ProtocolSessionRegistry();
    @SubscribeEvent static void register(RegisterPayloadHandlersEvent event){event.registrar("1").optional().playToServer(Hello.TYPE,Hello.STREAM_CODEC,ScripNeoForgeNetwork::receive);}
    private static void receive(Hello hello,IPayloadContext context){if(context.player() instanceof ServerPlayer player)SESSIONS.negotiate(player.getUUID(),hello.version,hello.capabilities,System.currentTimeMillis());}
    record Hello(int version,int capabilities) implements CustomPacketPayload {
        static final Type<Hello> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Scrip.MOD_ID,"wallet_hello"));
        static final StreamCodec<RegistryFriendlyByteBuf,Hello> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Hello::version,ByteBufCodecs.VAR_INT,Hello::capabilities,Hello::new);
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
}
