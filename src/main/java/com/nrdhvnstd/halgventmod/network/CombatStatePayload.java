package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.ClientCombat;
import com.nrdhvnstd.halgventmod.combat.AttackKind;
import com.nrdhvnstd.halgventmod.combat.CombatState;
import com.nrdhvnstd.halgventmod.combat.Phase;
import com.nrdhvnstd.halgventmod.combat.StrikeSide;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → бойцу и всем, кто его видит: новая фаза (ТЗ боя, 12.1) — для анимаций, звука и интерфейса.
 * start — серверный тик начала фазы, length — её длина в тиках, move — приём (комбо: анимация, окно удара, анимация
 * жертвы — ComboMoves.Combo.move) или пусто.
 */
public record CombatStatePayload(int entityId, Phase phase, AttackKind attack, StrikeSide side, StrikeSide blockSide,
                                 int flags, long start, int length, String move) implements CustomPacketPayload {
    public static final int HEAVY = 2, RIPOSTE = 4, BLOCKING = 8, ENGAGED = 16;

    public static final Type<CombatStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "combat_state"));

    // Восемь полей — больше, чем принимает StreamCodec.composite
    public static final StreamCodec<ByteBuf, CombatStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.entityId());
                Phase.STREAM_CODEC.encode(buf, payload.phase());
                AttackKind.STREAM_CODEC.encode(buf, payload.attack());
                StrikeSide.STREAM_CODEC.encode(buf, payload.side());
                StrikeSide.STREAM_CODEC.encode(buf, payload.blockSide());
                ByteBufCodecs.VAR_INT.encode(buf, payload.flags());
                ByteBufCodecs.VAR_LONG.encode(buf, payload.start());
                ByteBufCodecs.VAR_INT.encode(buf, payload.length());
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.move());
            },
            buf -> new CombatStatePayload(ByteBufCodecs.VAR_INT.decode(buf), Phase.STREAM_CODEC.decode(buf),
                    AttackKind.STREAM_CODEC.decode(buf), StrikeSide.STREAM_CODEC.decode(buf), StrikeSide.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)));

    public static CombatStatePayload of(LivingEntity entity, CombatState state) {
        int flags = (state.heavy ? HEAVY : 0) | (state.riposte ? RIPOSTE : 0) | (state.blocking ? BLOCKING : 0)
                | (state.engaged ? ENGAGED : 0);
        return new CombatStatePayload(entity.getId(), state.phase, state.attack, state.side, state.blockSide, flags, state.phaseStart,
                (int) (state.phaseEnd - state.phaseStart), state.move);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CombatStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCombat.onState(payload));
    }
}
