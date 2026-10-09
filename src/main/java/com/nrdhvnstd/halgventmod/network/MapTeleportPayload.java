package com.nrdhvnstd.halgventmod.network;

import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;

/**
 * Клиент → сервер: телепортация администратора в точку карты.
 * Права проверяет сервер, клиент только прячет пункт меню. Высоту тоже находит сервер — по поверхности;
 * yHint — высота с карты клиента (NO_HINT — неизвестна), нужна там, где у мира потолок (Незер).
 */
public record MapTeleportPayload(int x, int z, int yHint) implements CustomPacketPayload {
    public static final int NO_HINT = Integer.MIN_VALUE;
    public static final int PERMISSION_LEVEL = 2; // как у /tp

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Type<MapTeleportPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "map_teleport"));

    public static final StreamCodec<ByteBuf, MapTeleportPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MapTeleportPayload::x,
            ByteBufCodecs.VAR_INT, MapTeleportPayload::z,
            ByteBufCodecs.INT, MapTeleportPayload::yHint,
            MapTeleportPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MapTeleportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(PERMISSION_LEVEL)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            if (!level.getWorldBorder().isWithinBounds(payload.x(), payload.z())) {
                return;
            }
            int y;
            if (level.dimensionType().hasCeiling() && payload.yHint() != NO_HINT) {
                y = payload.yHint(); // по карте — пол под потолком
            } else {
                y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, payload.x(), payload.z()); // загрузит чанк
                if (y <= level.getMinBuildHeight()) {
                    y = payload.yHint() != NO_HINT ? payload.yHint() : level.getSeaLevel();
                }
            }
            double x = payload.x() + 0.5;
            double z = payload.z() + 0.5;
            player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
            LOGGER.info("{} телепортировался по карте в {} {} {}", player.getGameProfile().getName(), payload.x(), y, payload.z());
            player.sendSystemMessage(Component.translatable("commands.teleport.success.location.single",
                    player.getDisplayName(), String.format("%.1f", x), y, String.format("%.1f", z)));
        });
    }
}
