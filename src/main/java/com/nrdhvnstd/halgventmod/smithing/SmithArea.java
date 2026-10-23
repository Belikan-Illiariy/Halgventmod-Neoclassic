package com.nrdhvnstd.halgventmod.smithing;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Что стоит рядом с наковальней или литейным столом: горн, чан (для закалки), точило — в {@link #RADIUS} блоках.
 * Сведения для окна ковки упаковываются в одно число ({@link #info}).
 */
public final class SmithArea {
    public static final int RADIUS = 4;

    // Биты сведений для окна (SmithOpenPayload.info)
    public static final int HEAT_MASK = 0xFF;      // жар горна 0–100
    public static final int COAL_BIT = 1 << 8;     // горн на каменном угле
    public static final int LIQUID_SHIFT = 9;      // жидкость в бочке: 0 нет, 1 вода, 2 рассол, 3 масло
    public static final int WHEEL_BIT = 1 << 11;   // точило рядом

    private SmithArea() {}

    /** Ближайший горящий горн или null. */
    @Nullable
    public static ForgeBlockEntity forge(Level level, BlockPos center) {
        ForgeBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -1, -RADIUS), center.offset(RADIUS, 1, RADIUS))) {
            if (level.getBlockEntity(pos) instanceof ForgeBlockEntity forge && forge.lit()) {
                double distance = pos.distSqr(center);
                if (best == null || forge.heat() > best.heat() || forge.heat() == best.heat() && distance < bestDistance) {
                    best = forge;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /** Чем закаливать: жидкость в ближайшем чане (вода, рассол, жир); 0 — нет подходящего. */
    public static int liquid(Level level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -1, -RADIUS), center.offset(RADIUS, 1, RADIUS))) {
            if (level.getBlockEntity(pos) instanceof com.nrdhvnstd.halgventmod.leather.TanningVatBlockEntity vat
                    && vat.quenchLiquid() > 0) {
                return vat.quenchLiquid();
            }
        }
        return 0;
    }

    public static boolean wheel(Level level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -1, -RADIUS), center.offset(RADIUS, 1, RADIUS))) {
            if (level.getBlockState(pos).getBlock() instanceof SharpeningWheelBlock) {
                return true;
            }
        }
        return false;
    }

    /** Сведения для окна ковки одним числом. */
    public static int info(Level level, BlockPos center) {
        ForgeBlockEntity forge = forge(level, center);
        int info = forge == null ? 0 : Math.clamp(Math.round(forge.heat()), 0, 100);
        if (forge != null && forge.coal()) {
            info |= COAL_BIT;
        }
        info |= liquid(level, center) << LIQUID_SHIFT;
        if (wheel(level, center)) {
            info |= WHEEL_BIT;
        }
        return info;
    }
}
