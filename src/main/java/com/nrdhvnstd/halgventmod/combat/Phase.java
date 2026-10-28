package com.nrdhvnstd.halgventmod.combat;

import java.util.function.IntFunction;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/** Фаза бойца (ТЗ боя, 3): покой, замах, удар, возврат, сбитый замах, оглушение, пинок. */
public enum Phase {
    IDLE, WINDUP, RELEASE, RECOVERY, FLINCH, STUN, KICK;

    private static final IntFunction<Phase> BY_ID =
            ByIdMap.continuous(Phase::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, Phase> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Phase::ordinal);
}
