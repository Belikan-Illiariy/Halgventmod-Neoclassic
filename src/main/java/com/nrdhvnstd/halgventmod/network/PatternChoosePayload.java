package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: на столе кожевника выбрана выкройка index. Сервер проверяет дальность и что стол свободен. */
public record PatternChoosePayload(BlockPos pos, int index) implements CustomPacketPayload {
    public static final Type<PatternChoosePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "pattern_choose"));
    public static final StreamCodec<ByteBuf, PatternChoosePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PatternChoosePayload::pos,
            ByteBufCodecs.VAR_INT, PatternChoosePayload::index,
            PatternChoosePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PatternChoosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.pos()) instanceof LeatherTableBlockEntity table) {
                table.choosePattern(player, payload.index());
            }
        });
    }
}
