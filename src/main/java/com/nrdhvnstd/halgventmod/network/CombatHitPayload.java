package com.nrdhvnstd.halgventmod.network;

import java.util.function.IntFunction;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.ClientCombat;
import com.nrdhvnstd.halgventmod.combat.HitZone;
import com.nrdhvnstd.halgventmod.quality.DamageKind;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ByIdMap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → всем, кто видит атакующего: чем кончился удар (ТЗ боя, 12.1) — исход, зона, тип урона, точка.
 * По нему клиент рисует искры и (потом) кровь. target = -1 — удар в стену.
 */
public record CombatHitPayload(int attackerId, int targetId, Outcome outcome, HitZone zone, DamageKind kind, Vec3 point)
        implements CustomPacketPayload {
    public enum Outcome {
        HIT, BLOCK, PARRY, COUNTER, DEFLECT, WALL, GUARD_BREAK, KICK;

        private static final IntFunction<Outcome> BY_ID =
                ByIdMap.continuous(Outcome::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final StreamCodec<ByteBuf, Outcome> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Outcome::ordinal);
    }

    private static final IntFunction<DamageKind> KIND_BY_ID =
            ByIdMap.continuous(DamageKind::ordinal, DamageKind.values(), ByIdMap.OutOfBoundsStrategy.ZERO);
    private static final StreamCodec<ByteBuf, DamageKind> KIND_CODEC = ByteBufCodecs.idMapper(KIND_BY_ID, DamageKind::ordinal);
    private static final StreamCodec<ByteBuf, Vec3> POINT_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, Vec3::x, ByteBufCodecs.DOUBLE, Vec3::y, ByteBufCodecs.DOUBLE, Vec3::z, Vec3::new);

    public static final Type<CombatHitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "combat_hit"));

    public static final StreamCodec<ByteBuf, CombatHitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatHitPayload::attackerId,
            ByteBufCodecs.VAR_INT, CombatHitPayload::targetId,
            Outcome.STREAM_CODEC, CombatHitPayload::outcome,
            HitZone.STREAM_CODEC, CombatHitPayload::zone,
            KIND_CODEC, CombatHitPayload::kind,
            POINT_CODEC, CombatHitPayload::point,
            CombatHitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CombatHitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCombat.onHit(payload));
    }
}
