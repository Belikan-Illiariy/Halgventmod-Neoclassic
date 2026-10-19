package com.nrdhvnstd.halgventmod.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.banner.BannerEntry;
import com.nrdhvnstd.halgventmod.banner.BannerRegistry;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: весь реестр знамён по порядку внесения (при входе и после каждой правки).
 * Кодек на RegistryFriendlyByteBuf: слои узоров ссылаются на реестр узоров баннеров.
 */
public record BannersSyncPayload(List<BannerEntry> entries) implements CustomPacketPayload {
    public static final Type<BannersSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "banners_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BannersSyncPayload> STREAM_CODEC =
            BannerEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(BannersSyncPayload::new, BannersSyncPayload::entries);

    public BannersSyncPayload {
        entries = List.copyOf(entries); // записи неизменяемые — в одиночной игре хватает копии списка
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BannersSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Map<String, BannerEntry> map = new LinkedHashMap<>();
            for (BannerEntry entry : payload.entries()) {
                map.put(entry.id(), entry);
            }
            BannerRegistry.clientEntries = Collections.unmodifiableMap(map);
        });
    }
}
