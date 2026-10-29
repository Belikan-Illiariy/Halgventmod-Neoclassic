package com.nrdhvnstd.halgventmod.network;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.trade.MarketStallScreen;
import com.nrdhvnstd.halgventmod.trade.MarketStallBlockEntity;
import com.nrdhvnstd.halgventmod.trade.Price;
import com.nrdhvnstd.halgventmod.trade.StallKind;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → хозяину прилавка: виды вещей с ценами (продажа и скупка по качествам), запросы покупателей, у бочонка с
 * краном — налитое, цены меры и залогов, сколько кружек.
 * Шлётся из MarketStallMenu при открытии и после каждого изменения прилавка.
 */
public record StallSyncPayload(BlockPos pos, List<Kind> kinds, List<MarketStallBlockEntity.Request> requests, Tap tap)
        implements CustomPacketPayload {
    /** Вид вещи: есть ли качество, есть ли предложение, цены подряд (строка — ступень, в ней Price.COINS монет), сколько лежит. */
    public record Kind(ResourceLocation item, boolean graded, boolean offered, int[] sell, int[] buy, int stock) {}

    /** Бочонок с краном: мера налитого (пусто — бочонок пуст), сколько мер, цены подряд (мера, залоги), кружек торговца. */
    public record Tap(ItemStack drink, int measures, int[] prices, int mugs) {
        static final Tap NONE = new Tap(ItemStack.EMPTY, 0, new int[0], 0);
    }

    public static final Type<StallSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "stall_sync"));
    private static final int PRICES_MAX = MarketStallBlockEntity.GRADES * Price.COINS;

    public static final StreamCodec<RegistryFriendlyByteBuf, StallSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeVarInt(payload.kinds().size());
                for (Kind kind : payload.kinds()) {
                    buf.writeResourceLocation(kind.item());
                    buf.writeBoolean(kind.graded());
                    buf.writeBoolean(kind.offered());
                    buf.writeVarIntArray(kind.sell());
                    buf.writeVarIntArray(kind.buy());
                    buf.writeVarInt(kind.stock());
                }
                buf.writeVarInt(payload.requests().size());
                for (MarketStallBlockEntity.Request request : payload.requests()) {
                    buf.writeResourceLocation(request.item());
                    buf.writeVarInt(request.grade());
                    buf.writeUtf(request.buyer(), 64);
                }
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.tap().drink());
                buf.writeVarInt(payload.tap().measures());
                buf.writeVarIntArray(payload.tap().prices());
                buf.writeVarInt(payload.tap().mugs());
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int count = Math.min(buf.readVarInt(), 256);
                List<Kind> kinds = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    kinds.add(new Kind(buf.readResourceLocation(), buf.readBoolean(), buf.readBoolean(),
                            buf.readVarIntArray(PRICES_MAX), buf.readVarIntArray(PRICES_MAX), buf.readVarInt()));
                }
                int requestCount = Math.min(buf.readVarInt(), MarketStallBlockEntity.MAX_REQUESTS);
                List<MarketStallBlockEntity.Request> requests = new ArrayList<>();
                for (int i = 0; i < requestCount; i++) {
                    requests.add(new MarketStallBlockEntity.Request(buf.readResourceLocation(), buf.readVarInt(), buf.readUtf(64)));
                }
                Tap tap = new Tap(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(),
                        buf.readVarIntArray(MarketStallBlockEntity.TAP_ROWS * Price.COINS), buf.readVarInt());
                return new StallSyncPayload(pos, kinds, requests, tap);
            });

    /** Снимок прилавка для хозяина. */
    public static StallSyncPayload of(MarketStallBlockEntity stall) {
        List<Kind> kinds = new ArrayList<>();
        for (ResourceLocation item : stall.kinds()) {
            MarketStallBlockEntity.Offer offer = stall.offer(item);
            boolean graded = stall.gradedKind(item);
            int stock = 0;
            for (int grade = 0; grade < (graded ? MarketStallBlockEntity.GRADES : 1); grade++) {
                stock += stall.stockOf(item, grade);
            }
            kinds.add(new Kind(item, graded, offer != null,
                    offer != null ? MarketStallBlockEntity.flat(offer.sell) : new int[0],
                    offer != null ? MarketStallBlockEntity.flat(offer.buy) : new int[0], stock));
        }
        Tap tap = stall.kind() == StallKind.TAP
                ? new Tap(stall.tapSample(), stall.tapMeasures(), stall.tapPricesFlat(), stall.mugCount()) : Tap.NONE;
        return new StallSyncPayload(stall.getBlockPos(), kinds, stall.requests(), tap);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StallSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MarketStallScreen.sync(payload));
    }
}
