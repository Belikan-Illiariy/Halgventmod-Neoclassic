package com.nrdhvnstd.halgventmod.network;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.trade.ShopScreen;
import com.nrdhvnstd.halgventmod.trade.Price;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Сервер → покупателю: лавка НПС-торговца (trade/Shop). sell — что торговец продаёт (count — сколько есть, 0 — кончилось,
 * можно оставить запрос), buy — что он скупает (count — сколько такого у покупателя при себе). Цена — набор монет (Price).
 * owner — это смотрит хозяин торговца: ему видна вкладка «Касса». purse — кошелёк торговца (всем: по нему видно, на что
 * можно обменять монеты).
 * Открытое окно той же лавки обновляется на месте, новое — открывается.
 */
public record ShopOpenPayload(int npcId, String merchant, List<Entry> sell, List<Entry> buy, boolean owner, int[] purse)
        implements CustomPacketPayload {
    /**
     * Строка лавки: показ (вещь с качеством), вид, есть ли у вида качество, ступень, цена набором монет, количество.
     * type — что за строка (ITEM, TAP — питьё из бочонка, MUG — сдать пустую кружку за залог); у TAP mugs — сколько
     * кружек у торговца, deposit — залог за ту, что он даст.
     */
    public record Entry(ItemStack display, ResourceLocation item, boolean graded, int grade, int[] price, int count,
                        int type, int mugs, int[] deposit) {
        public static final int ITEM = 0, TAP = 1, MUG = 2;

        public Entry(ItemStack display, ResourceLocation item, boolean graded, int grade, int[] price, int count) {
            this(display, item, graded, grade, price, count, ITEM, 0, new int[0]);
        }
    }

    private static final int MAX_ENTRIES = 512;

    public static final Type<ShopOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "shop_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopOpenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.npcId());
                buf.writeUtf(payload.merchant(), 64);
                write(buf, payload.sell());
                write(buf, payload.buy());
                buf.writeBoolean(payload.owner());
                buf.writeVarIntArray(payload.purse());
            },
            buf -> new ShopOpenPayload(buf.readVarInt(), buf.readUtf(64), read(buf), read(buf), buf.readBoolean(),
                    buf.readVarIntArray(Price.COINS)));

    private static void write(RegistryFriendlyByteBuf buf, List<Entry> entries) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.display());
            buf.writeResourceLocation(entry.item());
            buf.writeBoolean(entry.graded());
            buf.writeVarInt(entry.grade());
            buf.writeVarIntArray(entry.price());
            buf.writeVarInt(entry.count());
            buf.writeVarInt(entry.type());
            buf.writeVarInt(entry.mugs());
            buf.writeVarIntArray(entry.deposit());
        }
    }

    private static List<Entry> read(RegistryFriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), MAX_ENTRIES);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readResourceLocation(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarIntArray(Price.COINS), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarIntArray(Price.COINS)));
        }
        return entries;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShopOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ShopScreen.open(payload));
    }
}
