package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.Thirst;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: «хочу попить из воды, на которую смотрю». */
public record DrinkWaterPayload() implements CustomPacketPayload {
    public static final DrinkWaterPayload INSTANCE = new DrinkWaterPayload();

    public static final Type<DrinkWaterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "drink_water"));

    public static final StreamCodec<ByteBuf, DrinkWaterPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DrinkWaterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Thirst.drinkFromWater(player);
            }
        });
    }
}