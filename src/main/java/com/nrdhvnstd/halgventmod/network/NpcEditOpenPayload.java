package com.nrdhvnstd.halgventmod.network;

import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.npc.NpcEditScreen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: данные НПС для окна настройки (NpcEditing.open). reset — заменить и личность в открытом окне
 * (новое окно или «Новая личность»); иначе обновляются только привязки, а несохранённые правки остаются.
 * Место работы и дом — BlockPos.ZERO, если не заданы; merchant — ник игрока-торговца (пусто — не привязан).
 * Снаряжение здесь не передаётся: оно в своём окне (NpcGearMenu).
 */
public record NpcEditOpenPayload(int entityId, boolean reset, String name, int role, String skin, boolean slim,
                                 Map<String, String> appearance, BlockPos post, BlockPos home, int routePoints,
                                 String template, String merchant) implements CustomPacketPayload {
    public static final Type<NpcEditOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "npc_edit_open"));

    public static final StreamCodec<FriendlyByteBuf, NpcEditOpenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeBoolean(payload.reset());
                buf.writeUtf(payload.name(), NpcEditPayload.TEXT_MAX);
                buf.writeVarInt(payload.role());
                buf.writeUtf(payload.skin(), NpcEditPayload.TEXT_MAX);
                buf.writeBoolean(payload.slim());
                NpcEditPayload.writeMap(buf, payload.appearance());
                buf.writeBlockPos(payload.post());
                buf.writeBlockPos(payload.home());
                buf.writeVarInt(payload.routePoints());
                buf.writeUtf(payload.template(), NpcEditPayload.TEXT_MAX);
                buf.writeUtf(payload.merchant(), NpcEditPayload.TEXT_MAX);
            },
            buf -> new NpcEditOpenPayload(buf.readVarInt(), buf.readBoolean(), buf.readUtf(NpcEditPayload.TEXT_MAX),
                    buf.readVarInt(), buf.readUtf(NpcEditPayload.TEXT_MAX), buf.readBoolean(),
                    NpcEditPayload.readMap(buf), buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(),
                    buf.readUtf(NpcEditPayload.TEXT_MAX), buf.readUtf(NpcEditPayload.TEXT_MAX)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NpcEditOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> NpcEditScreen.open(payload));
    }
}
