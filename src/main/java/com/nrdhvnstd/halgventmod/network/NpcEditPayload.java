package com.nrdhvnstd.halgventmod.network;

import java.util.LinkedHashMap;
import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.npc.NpcEditing;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: администратор настраивает НПС из окна. APPLY — сохранить личность (имя, роль, скин, руки,
 * облик); остальные действия поля не используют: привязки места работы и дома — порознь, очистка маршрута,
 * новая личность, убрать. Права, дальность и значения проверяет сервер (NpcEditing).
 */
public record NpcEditPayload(int entityId, Action action, String name, int role, String skin, boolean slim,
                             Map<String, String> appearance) implements CustomPacketPayload {
    public enum Action { APPLY, REROLL, POST_HERE, CLEAR_POST, HOME_HERE, CLEAR_HOME, CLEAR_ROUTE, OPEN_GEAR, OPEN_SETTINGS, SET_MERCHANT, CLEAR_MERCHANT, REMOVE }

    public static final int TEXT_MAX = 64;
    private static final int MAP_MAX = 32;

    public static final Type<NpcEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "npc_edit"));

    public static final StreamCodec<FriendlyByteBuf, NpcEditPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeEnum(payload.action());
                buf.writeUtf(payload.name(), TEXT_MAX);
                buf.writeVarInt(payload.role());
                buf.writeUtf(payload.skin(), TEXT_MAX);
                buf.writeBoolean(payload.slim());
                writeMap(buf, payload.appearance());
            },
            buf -> new NpcEditPayload(buf.readVarInt(), buf.readEnum(Action.class), buf.readUtf(TEXT_MAX),
                    buf.readVarInt(), buf.readUtf(TEXT_MAX), buf.readBoolean(), readMap(buf)));

    /** Только действие без полей. */
    public static NpcEditPayload of(int entityId, Action action) {
        return new NpcEditPayload(entityId, action, "", 0, "", false, Map.of());
    }

    static void writeMap(FriendlyByteBuf buf, Map<String, String> map) {
        buf.writeVarInt(map.size());
        map.forEach((key, value) -> {
            buf.writeUtf(key, TEXT_MAX);
            buf.writeUtf(value, TEXT_MAX);
        });
    }

    static Map<String, String> readMap(FriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), MAP_MAX);
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            map.put(buf.readUtf(TEXT_MAX), buf.readUtf(TEXT_MAX));
        }
        return map;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NpcEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NpcEditing.handle(player, payload);
            }
        });
    }
}
