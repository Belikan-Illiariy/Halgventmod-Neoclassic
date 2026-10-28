package com.nrdhvnstd.halgventmod.combat;

import java.util.function.IntFunction;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/**
 * Сторона удара и блока — по движению мыши перед кликом (как в Mount &amp; Blade). У удара — откуда идёт клинок со
 * стороны бьющего: RIGHT — справа налево, LEFT — слева направо, UP — сверху, DOWN — снизу вверх. У блока — откуда
 * удар приходит на экране блокирующего: удар справа (у бьющего) приходит слева, поэтому его держит блок LEFT;
 * сверху и снизу — как есть; укол держит блок DOWN.
 */
public enum StrikeSide {
    LEFT, RIGHT, UP, DOWN;

    private static final IntFunction<StrikeSide> BY_ID =
            ByIdMap.continuous(StrikeSide::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, StrikeSide> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, StrikeSide::ordinal);

    /** Каким блоком держится удар с этой стороны: лево и право меняются местами. */
    public StrikeSide blockedBy() {
        return switch (this) {
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
            default -> this;
        };
    }

    /** Ключ перевода: strike_side.halgventmod.left */
    public String langKey() {
        return "strike_side.halgventmod." + name().toLowerCase();
    }
}
