package com.nrdhvnstd.halgventmod.network;

import java.util.Optional;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.squad.SquadClientState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: ответ на действие из окна «Отряд»: error = null — получилось, иначе причина отказа. */
public record SquadResultPayload(SquadActionPayload.Action action, @Nullable Component error)
        implements CustomPacketPayload {

    public static final Type<SquadResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "squad_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SquadResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> SquadActionPayload.Action.values()[i], Enum::ordinal), SquadResultPayload::action,
            ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, payload -> Optional.ofNullable(payload.error()),
            (action, error) -> new SquadResultPayload(action, error.orElse(null)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SquadResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SquadClientState.result(payload.action(), payload.error()));
    }
}
