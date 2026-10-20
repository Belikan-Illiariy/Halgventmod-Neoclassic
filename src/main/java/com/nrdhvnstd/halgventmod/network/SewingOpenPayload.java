package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.leather.SewingScreen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: открыть мини-игру шитья для стола кожевника. Что шьём и сколько стежков.
 * Обработчик зовётся только на клиенте.
 */
public record SewingOpenPayload(BlockPos pos, ItemStack result, int stitches) implements CustomPacketPayload {
    public static final Type<SewingOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "sewing_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SewingOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SewingOpenPayload::pos,
            ItemStack.STREAM_CODEC, SewingOpenPayload::result,
            ByteBufCodecs.VAR_INT, SewingOpenPayload::stitches,
            SewingOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SewingOpenPayload payload, IPayloadContext context) {
        // Окно открывает сам SewingScreen (статический вызов), чтобы сервер не трогал классы интерфейса
        context.enqueueWork(() -> SewingScreen.open(payload.pos(), payload.result(), payload.stitches()));
    }
}
