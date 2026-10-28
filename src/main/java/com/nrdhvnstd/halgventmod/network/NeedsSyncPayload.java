package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: голод, насыщение, жажда и выносливость для шкал на экране; боль — для её эффектов. */
public record NeedsSyncPayload(int hunger, float saturation, int thirst, float stamina, int exhaustedTicks,
                               float pain, int shockTicks)
        implements CustomPacketPayload {
    public static final Type<NeedsSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "needs_sync"));

    // Семь полей — больше, чем принимает StreamCodec.composite
    public static final StreamCodec<ByteBuf, NeedsSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.hunger());
                ByteBufCodecs.FLOAT.encode(buf, payload.saturation());
                ByteBufCodecs.VAR_INT.encode(buf, payload.thirst());
                ByteBufCodecs.FLOAT.encode(buf, payload.stamina());
                ByteBufCodecs.VAR_INT.encode(buf, payload.exhaustedTicks());
                ByteBufCodecs.FLOAT.encode(buf, payload.pain());
                ByteBufCodecs.VAR_INT.encode(buf, payload.shockTicks());
            },
            buf -> new NeedsSyncPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NeedsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            context.player().getData(ModAttachments.HUNGER).setClient(payload.hunger(), payload.saturation());
            context.player().getData(ModAttachments.THIRST).setClientLevel(payload.thirst());
            context.player().getData(ModAttachments.STAMINA).setClient(payload.stamina(), payload.exhaustedTicks());
            context.player().getData(ModAttachments.PAIN).setClient(payload.pain(), payload.shockTicks());
        });
    }
}
