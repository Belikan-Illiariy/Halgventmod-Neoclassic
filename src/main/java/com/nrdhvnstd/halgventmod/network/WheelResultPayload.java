package com.nrdhvnstd.halgventmod.network;

import java.util.Arrays;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.pottery.PotteryWheelBlockEntity;
import com.nrdhvnstd.halgventmod.pottery.VesselGeometry;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: на круге вылеплена форма. profile — разрез тела, details — воксели деталей, uneven — неровности
 * (смятые и налепленные кольца, обвалы). Сервер всё перепроверяет (PotteryWheelBlockEntity#finish).
 */
public record WheelResultPayload(BlockPos pos, int form, int[] profile, int[] details, int uneven)
        implements CustomPacketPayload {
    public static final Type<WheelResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "wheel_result"));

    private static final StreamCodec<ByteBuf, int[]> INTS = ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(VesselGeometry.VOXELS))
            .map(list -> list.stream().mapToInt(Integer::intValue).toArray(),
                    array -> Arrays.stream(array).boxed().toList());

    public static final StreamCodec<ByteBuf, WheelResultPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WheelResultPayload::pos,
            ByteBufCodecs.VAR_INT, WheelResultPayload::form,
            INTS, WheelResultPayload::profile,
            INTS, WheelResultPayload::details,
            ByteBufCodecs.VAR_INT, WheelResultPayload::uneven,
            WheelResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WheelResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().getBlockEntity(payload.pos()) instanceof PotteryWheelBlockEntity wheel) {
                wheel.finish(player, payload.form(), payload.profile(), payload.details(), payload.uneven());
            }
        });
    }
}
