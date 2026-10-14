package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.world.NameTags;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: кому видны ники над головами игроков. */
public record NameTagsSyncPayload(NameTags.Mode mode) implements CustomPacketPayload {
    public static final Type<NameTagsSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "name_tags_sync"));

    public static final StreamCodec<ByteBuf, NameTagsSyncPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            ordinal -> new NameTagsSyncPayload(NameTags.Mode.values()[ordinal]),
            payload -> payload.mode().ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NameTagsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> NameTags.clientMode = payload.mode());
    }
}
