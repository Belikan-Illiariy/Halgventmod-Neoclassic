package com.nrdhvnstd.halgventmod.combat;

import java.util.function.IntFunction;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;

/** Вид удара (ТЗ боя, 3): рубящий взмах, удар сверху, укол, особый (R), с разбега. */
public enum AttackKind {
    SLASH("slash"), OVERHEAD("overhead"), STAB("stab"), SPECIAL("special"), SPRINT("sprint");

    private static final IntFunction<AttackKind> BY_ID =
            ByIdMap.continuous(AttackKind::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    public static final StreamCodec<ByteBuf, AttackKind> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, AttackKind::ordinal);

    private final String id;

    AttackKind(String id) {
        this.id = id;
    }

    /** Ключ в JSON профиля. */
    public String id() {
        return id;
    }

    /** Ключ перевода: attack_kind.halgventmod.slash */
    public String langKey() {
        return "attack_kind.halgventmod." + id;
    }

    @Nullable
    public static AttackKind byId(String id) {
        for (AttackKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
