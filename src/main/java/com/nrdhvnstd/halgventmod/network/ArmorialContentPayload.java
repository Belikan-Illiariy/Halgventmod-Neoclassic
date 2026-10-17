package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armorial.ArmorialBook;
import com.nrdhvnstd.halgventmod.banner.BannerEntry;
import com.nrdhvnstd.halgventmod.client.armorial.ArmorialClient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: игрок открыл гербовник — содержимое книги и можно ли её править (администрация в творческом).
 * Знамёна клиент берёт из своего реестра (BannersSyncPayload). Окно открывает ArmorialClient — только на клиенте.
 */
public record ArmorialContentPayload(String id, ArmorialBook book, boolean editable) implements CustomPacketPayload {
    public static final Type<ArmorialContentPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "armorial_content"));

    public static final StreamCodec<FriendlyByteBuf, ArmorialContentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(BannerEntry.ID_MAX), ArmorialContentPayload::id,
            ArmorialBook.STREAM_CODEC, ArmorialContentPayload::book,
            ByteBufCodecs.BOOL, ArmorialContentPayload::editable,
            ArmorialContentPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArmorialContentPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ArmorialClient.open(payload.id(), payload.book(), payload.editable()));
    }
}
