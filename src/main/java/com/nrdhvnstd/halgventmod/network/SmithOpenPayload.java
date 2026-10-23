package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.smithing.SmithScreens;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: открыть мини-игру кузнечества (smithing.Minigame): вид, изделие, зерно для заготовки и два
 * числа сведений — у каждого вида свои. Обработчик зовётся только на клиенте.
 */
public record SmithOpenPayload(BlockPos pos, int kind, int product, int seed, int info, int extra)
        implements CustomPacketPayload {
    public static final Type<SmithOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "smith_open"));
    public static final StreamCodec<ByteBuf, SmithOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SmithOpenPayload::pos,
            ByteBufCodecs.VAR_INT, SmithOpenPayload::kind,
            ByteBufCodecs.VAR_INT, SmithOpenPayload::product,
            ByteBufCodecs.INT, SmithOpenPayload::seed,
            ByteBufCodecs.INT, SmithOpenPayload::info,
            ByteBufCodecs.INT, SmithOpenPayload::extra,
            SmithOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SmithOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SmithScreens.open(payload));
    }
}
