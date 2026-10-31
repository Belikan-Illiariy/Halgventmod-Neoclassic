package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.leather.ScrapeScreen;
import com.nrdhvnstd.halgventmod.leather.ScrapeJob;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → клиент: открыть мини-игру скобления на колоде. Работа, инструмент, размер шкуры (кусков)
 * и зерно, из которого клиент строит шкуру. Обработчик зовётся только на клиенте.
 */
public record ScrapeOpenPayload(BlockPos pos, int job, ItemStack tool, int size, int seed) implements CustomPacketPayload {
    public static final Type<ScrapeOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "scrape_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScrapeOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ScrapeOpenPayload::pos,
            ByteBufCodecs.VAR_INT, ScrapeOpenPayload::job,
            ItemStack.STREAM_CODEC, ScrapeOpenPayload::tool,
            ByteBufCodecs.VAR_INT, ScrapeOpenPayload::size,
            ByteBufCodecs.INT, ScrapeOpenPayload::seed,
            ScrapeOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScrapeOpenPayload payload, IPayloadContext context) {
        // Окно открывает сам ScrapeScreen (статический вызов), чтобы сервер не трогал классы интерфейса
        context.enqueueWork(() -> ScrapeScreen.open(payload.pos(), ScrapeJob.byIndex(payload.job()), payload.tool(),
                payload.size(), payload.seed()));
    }
}
