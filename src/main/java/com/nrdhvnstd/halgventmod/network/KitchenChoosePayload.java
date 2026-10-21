package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.cooking.KitchenTableBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: на кухонном столе выбран рецепт index. Сервер проверяет дальность и что стол свободен. */
public record KitchenChoosePayload(BlockPos pos, int index) implements CustomPacketPayload {
    public static final Type<KitchenChoosePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "kitchen_choose"));
    public static final StreamCodec<ByteBuf, KitchenChoosePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, KitchenChoosePayload::pos,
            ByteBufCodecs.VAR_INT, KitchenChoosePayload::index,
            KitchenChoosePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(KitchenChoosePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.pos()) instanceof KitchenTableBlockEntity table) {
                table.choose(player, payload.index());
            }
        });
    }
}
