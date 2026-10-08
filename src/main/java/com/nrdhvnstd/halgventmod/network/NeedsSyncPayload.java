package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Сервер → клиент: голод, насыщение, жажда и выносливость для шкал на экране. */
public record NeedsSyncPayload(int hunger, float saturation, int thirst, float stamina, int exhaustedTicks)
        implements CustomPacketPayload {
    public static final Type<NeedsSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "needs_sync"));

    public static final StreamCodec<ByteBuf, NeedsSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NeedsSyncPayload::hunger,
            ByteBufCodecs.FLOAT, NeedsSyncPayload::saturation,
            ByteBufCodecs.VAR_INT, NeedsSyncPayload::thirst,
            ByteBufCodecs.FLOAT, NeedsSyncPayload::stamina,
            ByteBufCodecs.VAR_INT, NeedsSyncPayload::exhaustedTicks,
            NeedsSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NeedsSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            context.player().getData(ModAttachments.HUNGER).setClient(payload.hunger(), payload.saturation());
            context.player().getData(ModAttachments.THIRST).setClientLevel(payload.thirst());
            context.player().getData(ModAttachments.STAMINA).setClient(payload.stamina(), payload.exhaustedTicks());
        });
    }
}
