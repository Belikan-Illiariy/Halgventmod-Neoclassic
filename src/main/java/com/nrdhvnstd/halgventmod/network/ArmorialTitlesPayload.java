package com.nrdhvnstd.halgventmod.network;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armorial.ArmorialBook;
import com.nrdhvnstd.halgventmod.armorial.ArmorialBooks;
import com.nrdhvnstd.halgventmod.banner.BannerEntry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: названия всех книг-гербовников (id → название) — для подсказки предмета. */
public record ArmorialTitlesPayload(Map<String, String> titles) implements CustomPacketPayload {
    public static final Type<ArmorialTitlesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "armorial_titles"));

    public static final StreamCodec<FriendlyByteBuf, ArmorialTitlesPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.titles().size());
                payload.titles().forEach((id, title) -> {
                    buf.writeUtf(id, BannerEntry.ID_MAX);
                    buf.writeUtf(title, ArmorialBook.TITLE_MAX * 4);
                });
            },
            buf -> {
                Map<String, String> titles = new LinkedHashMap<>();
                int count = buf.readVarInt();
                for (int i = 0; i < count; i++) {
                    titles.put(buf.readUtf(BannerEntry.ID_MAX), buf.readUtf(ArmorialBook.TITLE_MAX * 4));
                }
                return new ArmorialTitlesPayload(titles);
            });

    public ArmorialTitlesPayload {
        titles = Collections.unmodifiableMap(new LinkedHashMap<>(titles));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ArmorialTitlesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ArmorialBooks.clientTitles = payload.titles());
    }
}
