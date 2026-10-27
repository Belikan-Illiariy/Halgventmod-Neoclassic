package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.npc.NpcInfoScreen;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: окно «Информация» НПС — имя и профессия (Npcs.handle). */
public record NpcInfoPayload(Component name, Component profession) implements CustomPacketPayload {
    public static final Type<NpcInfoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "npc_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NpcInfoPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, NpcInfoPayload::name,
            ComponentSerialization.TRUSTED_STREAM_CODEC, NpcInfoPayload::profession,
            NpcInfoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NpcInfoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> NpcInfoScreen.open(payload.name(), payload.profession()));
    }
}
