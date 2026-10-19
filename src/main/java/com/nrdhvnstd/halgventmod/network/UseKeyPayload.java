package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.pickup.Pickup;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: «следующий ПКМ по блоку — от клавиши E». Идёт прямо перед ванильным пакетом
 * использования блока; сервер в этом использовании не применяет предмет из руки (Pickup.onRightClickBlock).
 */
public record UseKeyPayload() implements CustomPacketPayload {
    public static final UseKeyPayload INSTANCE = new UseKeyPayload();

    public static final Type<UseKeyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "use_key"));

    public static final StreamCodec<ByteBuf, UseKeyPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UseKeyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Pickup.markUseKey(player);
            }
        });
    }
}
