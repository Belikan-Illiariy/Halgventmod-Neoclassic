package com.nrdhvnstd.halgventmod.network;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.sound.MusicSet;
import com.nrdhvnstd.halgventmod.sound.SoundData;
import com.nrdhvnstd.halgventmod.sound.SoundScene;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: все наборы музыки биомов и сцены звуков мест (при входе и после /reload, единицы КБ). */
public record SoundDataPayload(Map<ResourceLocation, MusicSet> sets, Map<ResourceLocation, SoundScene> scenes)
        implements CustomPacketPayload {
    public static final Type<SoundDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "sound_data"));

    public static final StreamCodec<ByteBuf, SoundDataPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodec(Codec.unboundedMap(ResourceLocation.CODEC, MusicSet.CODEC)), SoundDataPayload::sets,
            ByteBufCodecs.fromCodec(Codec.unboundedMap(ResourceLocation.CODEC, SoundScene.CODEC)), SoundDataPayload::scenes,
            SoundDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SoundDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SoundData.receive(payload.sets(), payload.scenes()));
    }
}
