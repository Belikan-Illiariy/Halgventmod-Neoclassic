package com.nrdhvnstd.halgventmod.network;

import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorStats;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: боевые параметры брони из файлов (id вещи → JSON) — для подсказки, при входе и по /reload. */
public record ArmorStatsSyncPayload(Map<String, String> items) implements CustomPacketPayload {
    private static final int MAX_JSON = 1 << 12;

    public static final Type<ArmorStatsSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "armor_stats"));

    public static final StreamCodec<ByteBuf, ArmorStatsSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(java.util.HashMap::new, ByteBufCodecs.stringUtf8(256), ByteBufCodecs.stringUtf8(MAX_JSON)),
            ArmorStatsSyncPayload::items, ArmorStatsSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArmorStatsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ArmorStats.applyClient(payload.items()));
    }
}
