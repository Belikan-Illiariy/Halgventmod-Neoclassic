package com.nrdhvnstd.halgventmod.npc;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * Стулья для НПС в трактире. Что считается сиденьем — тег блоков halgventmod:npc_seats (по умолчанию стулья, табуреты
 * и скамьи Conquest Reforged; администрация дописывает в датапаке). Занятое место — невидимая SeatEntity на стуле.
 */
public final class NpcSeats {
    public static final TagKey<Block> SEATS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "npc_seats"));

    // Меняйте здесь ⚠️ (подобрано на глаз, проверить по моделям стульев)
    public static final int SEARCH_RADIUS = 6;          // блоков вокруг точки «трактир» ищется стул
    private static final double SEAT_HEIGHT = 0.5;       // высота сиденья над низом блока стула
    private static final double SEAT_ENTITY_DROP = 0.25; // сиденье-сущность ниже поверхности: компенсирует посадку модели

    private NpcSeats() {}

    /** Ближайший свободный стул вокруг точки (по вертикали ±1) или null. */
    @Nullable
    public static BlockPos find(ServerLevel level, BlockPos center, RandomSource random) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SEARCH_RADIUS, -1, -SEARCH_RADIUS),
                center.offset(SEARCH_RADIUS, 1, SEARCH_RADIUS))) {
            if (!level.getBlockState(pos).is(SEATS) || taken(level, pos)) {
                continue;
            }
            double score = pos.distSqr(center) + random.nextDouble() * 4; // чуть случайности, чтобы не все на один
            if (score < bestScore) {
                bestScore = score;
                best = pos.immutable();
            }
        }
        return best;
    }

    /** На этом стуле уже сидят или идут к нему. */
    public static boolean taken(ServerLevel level, BlockPos chair) {
        return !level.getEntitiesOfClass(SeatEntity.class, new AABB(chair).inflate(0.3)).isEmpty();
    }

    /** Занять стул: ставит пустое сиденье, пока НПС идёт; потом НПС садится на него. */
    @Nullable
    public static SeatEntity reserve(ServerLevel level, BlockPos chair) {
        SeatEntity seat = ModEntities.SEAT.get().create(level);
        if (seat == null) {
            return null;
        }
        seat.setPos(chair.getX() + 0.5, chair.getY() + SEAT_HEIGHT - SEAT_ENTITY_DROP, chair.getZ() + 0.5);
        level.addFreshEntity(seat);
        return seat;
    }

    /** Куда смотрит сидящий: туда же, куда стул (свойство facing); нет — на точку интереса. */
    public static float yaw(BlockState chair, BlockPos chairPos, BlockPos look) {
        Direction facing = chair.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(null);
        if (facing != null) {
            return facing.toYRot();
        }
        return (float) (Math.toDegrees(Math.atan2(look.getZ() - chairPos.getZ(), look.getX() - chairPos.getX())) - 90.0);
    }

    /** Свободное место рядом со стулом, куда встать: по ногам и голове воздух. */
    public static BlockPos standPos(ServerLevel level, BlockPos chair) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = chair.relative(direction);
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                return pos;
            }
        }
        return chair;
    }
}
