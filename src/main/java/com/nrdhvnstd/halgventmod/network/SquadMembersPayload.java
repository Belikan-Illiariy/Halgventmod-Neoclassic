package com.nrdhvnstd.halgventmod.network;

import java.util.List;
import java.util.UUID;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.squad.SquadMarkers;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: где сейчас остальные участники отряда в том же измерении (для меток на карте). */
public record SquadMembersPayload(List<Member> members) implements CustomPacketPayload {
    public record Member(UUID id, String name, double x, double z, float yaw) {
        static final StreamCodec<ByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::id,
                ByteBufCodecs.STRING_UTF8, Member::name,
                ByteBufCodecs.DOUBLE, Member::x,
                ByteBufCodecs.DOUBLE, Member::z,
                ByteBufCodecs.FLOAT, Member::yaw,
                Member::new);
    }

    public static final Type<SquadMembersPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "squad_members"));

    public static final StreamCodec<ByteBuf, SquadMembersPayload> STREAM_CODEC =
            Member.STREAM_CODEC.apply(ByteBufCodecs.list()).map(SquadMembersPayload::new, SquadMembersPayload::members);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SquadMembersPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SquadMarkers.clientMembers = payload.members());
    }
}
