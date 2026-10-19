package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.pickup.Pickup;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: «подбери эту вещь» (клавиша E; equip — Shift+E, сразу надеть). Всё проверяет Pickup. */
public record PickupPayload(int entityId, boolean equip) implements CustomPacketPayload {
    public static final Type<PickupPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "pickup"));

    public static final StreamCodec<ByteBuf, PickupPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PickupPayload::entityId,
            ByteBufCodecs.BOOL, PickupPayload::equip,
            PickupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PickupPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Pickup.handle(player, payload.entityId(), payload.equip());
            }
        });
    }
}
