package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.menu.MarketStallMenu;
import com.nrdhvnstd.halgventmod.trade.MarketStallBlockEntity;
import com.nrdhvnstd.halgventmod.trade.Price;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: хозяин правит прилавок из своего окна. SET_PRICES — цены вида: продажа и скупка по качествам,
 * набором монет подряд (MarketStallBlockEntity.flat); REMOVE_OFFER — снять вид с продажи и скупки; CLEAR_REQUESTS —
 * забыть запросы; SET_TAP_PRICES — цены бочонка с краном в sell (мера, залоги за кружки; MarketStallBlockEntity.TAP_ROWS
 * строк), EMPTY_TAP — вылить бочонок. Работает только при открытом окне этого прилавка. Деньги — у НПС-торговца (Shop).
 */
public record StallEditPayload(Action action, ResourceLocation item, int[] sell, int[] buy) implements CustomPacketPayload {
    public enum Action { SET_PRICES, REMOVE_OFFER, CLEAR_REQUESTS, SET_TAP_PRICES, EMPTY_TAP }

    private static final ResourceLocation NONE = ResourceLocation.withDefaultNamespace("air");
    private static final int PRICES_MAX = MarketStallBlockEntity.GRADES * Price.COINS;

    public static final Type<StallEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "stall_edit"));

    public static final StreamCodec<FriendlyByteBuf, StallEditPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.action());
                buf.writeResourceLocation(payload.item());
                buf.writeVarIntArray(payload.sell());
                buf.writeVarIntArray(payload.buy());
            },
            buf -> new StallEditPayload(buf.readEnum(Action.class), buf.readResourceLocation(),
                    buf.readVarIntArray(PRICES_MAX), buf.readVarIntArray(PRICES_MAX)));

    /** Действие без вида и цен. */
    public static StallEditPayload of(Action action) {
        return new StallEditPayload(action, NONE, new int[0], new int[0]);
    }

    /** Действие над видом без цен (снять с продажи). */
    public static StallEditPayload of(Action action, ResourceLocation item) {
        return new StallEditPayload(action, item, new int[0], new int[0]);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StallEditPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof MarketStallMenu menu)
                    || menu.stall() == null || !menu.stillValid(player)) {
                return;
            }
            MarketStallBlockEntity stall = menu.stall();
            switch (payload.action()) {
                case SET_PRICES -> stall.setPrices(payload.item(), payload.sell(), payload.buy());
                case REMOVE_OFFER -> stall.removeOffer(payload.item());
                case CLEAR_REQUESTS -> stall.clearRequests();
                case SET_TAP_PRICES -> stall.setTapPrices(payload.sell());
                case EMPTY_TAP -> stall.tapEmpty();
            }
        });
    }
}
