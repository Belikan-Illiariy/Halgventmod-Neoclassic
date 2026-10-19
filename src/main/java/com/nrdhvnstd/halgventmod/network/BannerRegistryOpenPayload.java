package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.banner.BannerClient;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: открыть окно реестра знамён (/banner list). Сами знамёна у клиента уже есть (BannersSyncPayload).
 * Обработчик зовётся только на клиенте; окно открывает BannerClient, чтобы сервер не трогал классы интерфейса.
 */
public record BannerRegistryOpenPayload() implements CustomPacketPayload {
    public static final BannerRegistryOpenPayload INSTANCE = new BannerRegistryOpenPayload();

    public static final Type<BannerRegistryOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "banner_registry_open"));

    public static final StreamCodec<FriendlyByteBuf, BannerRegistryOpenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BannerRegistryOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BannerClient.openRegistry());
    }
}
