package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.world.Announcements;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: рассвет — «Монтаг, 3 трент» и «Весна, 1384 г.» вверху экрана (Announcements, TopBanner). */
public record DawnPayload(Component title, Component subtitle) implements CustomPacketPayload {
    public static final Type<DawnPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "dawn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DawnPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, DawnPayload::title,
            ComponentSerialization.TRUSTED_STREAM_CODEC, DawnPayload::subtitle,
            DawnPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DawnPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Announcements.dawn(payload.title(), payload.subtitle()));
    }
}
