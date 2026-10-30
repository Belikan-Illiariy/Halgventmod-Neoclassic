package com.nrdhvnstd.halgventmod.network;

import java.util.function.IntFunction;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.AttackKind;
import com.nrdhvnstd.halgventmod.combat.Combat;
import com.nrdhvnstd.halgventmod.combat.StrikeSide;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ByIdMap;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: намерение в бою (ТЗ боя, 12.1). Всё решает Combat — пакет не в ту фазу молча пропускается. */
public record CombatInputPayload(Action action, AttackKind attack, StrikeSide side) implements CustomPacketPayload {
    /** BLOCK_SIDE — блок зажат, сторону сменили движением мыши. side — сторона удара или блока (по мыши). */
    public enum Action {
        ATTACK_START, ATTACK_RELEASE, FEINT, BLOCK_DOWN, BLOCK_UP, BLOCK_SIDE, KICK, GRIP,
        STANCE_ON, STANCE_OFF; // захват цели: оружие в стойке выбранной стороны (видно другим) и выход из неё

        private static final IntFunction<Action> BY_ID =
                ByIdMap.continuous(Action::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Action> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Action::ordinal);
    }

    public static final Type<CombatInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "combat_input"));

    public static final StreamCodec<ByteBuf, CombatInputPayload> STREAM_CODEC = StreamCodec.composite(
            Action.STREAM_CODEC, CombatInputPayload::action,
            AttackKind.STREAM_CODEC, CombatInputPayload::attack,
            StrikeSide.STREAM_CODEC, CombatInputPayload::side,
            CombatInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CombatInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Combat.input(player, payload.action(), payload.attack(), payload.side());
            }
        });
    }
}
