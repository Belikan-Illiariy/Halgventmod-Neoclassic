package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armorial.ArmorialBook;
import com.nrdhvnstd.halgventmod.armorial.ArmorialBooks;
import com.nrdhvnstd.halgventmod.banner.BannerEntry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: «Готово» в правке гербовника — все страницы книги разом. Права и содержимое проверяет сервер. */
public record ArmorialEditPayload(String id, ArmorialBook book) implements CustomPacketPayload {
    public static final Type<ArmorialEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "armorial_edit"));

    public static final StreamCodec<FriendlyByteBuf, ArmorialEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(BannerEntry.ID_MAX), ArmorialEditPayload::id,
            ArmorialBook.STREAM_CODEC, ArmorialEditPayload::book,
            ArmorialEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArmorialEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ArmorialBooks.edit(player, payload.id(), payload.book());
            }
        });
    }
}
