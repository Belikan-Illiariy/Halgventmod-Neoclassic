package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.sound.SoundEmitterBlock;
import com.nrdhvnstd.halgventmod.sound.SoundEmitterBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: настройка источника звука (сцена, радиус; 0 — из сцены). Права и расстояние проверяет сервер. */
public record SoundEmitterEditPayload(BlockPos pos, String scene, int radius) implements CustomPacketPayload {
    private static final int SCENE_MAX = 256;

    public static final Type<SoundEmitterEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "sound_emitter_edit"));

    public static final StreamCodec<ByteBuf, SoundEmitterEditPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SoundEmitterEditPayload::pos,
            ByteBufCodecs.stringUtf8(SCENE_MAX), SoundEmitterEditPayload::scene,
            ByteBufCodecs.VAR_INT, SoundEmitterEditPayload::radius,
            SoundEmitterEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SoundEmitterEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !SoundEmitterBlock.mayEdit(player)
                    || !player.canInteractWithBlock(payload.pos(), 1.0)) {
                return;
            }
            String scene = payload.scene().strip();
            boolean sceneValid = scene.isEmpty() || ResourceLocation.tryParse(scene) != null;
            boolean radiusValid = payload.radius() >= 0 && payload.radius() <= SoundEmitterBlockEntity.MAX_RADIUS;
            if (sceneValid && radiusValid
                    && player.level().getBlockEntity(payload.pos()) instanceof SoundEmitterBlockEntity emitter) {
                emitter.set(scene, payload.radius());
            }
        });
    }
}
