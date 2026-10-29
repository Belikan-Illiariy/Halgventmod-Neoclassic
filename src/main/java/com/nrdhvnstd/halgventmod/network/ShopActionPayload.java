package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.trade.Price;
import com.nrdhvnstd.halgventmod.trade.Shop;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: в лавке НПС-торговца купить (BUY), оставить запрос на кончившееся (REQUEST) или продать торговцу
 * (SELL). Строку называет вид, ступень и цена набором монет — сервер заново ищет её на прилавках (trade/Shop): если
 * цена за это время поменялась, сделки не будет. Хозяину: DEPOSIT — внести в кошелёк торговца все монеты вида grade
 * (номер Coin), WITHDRAW — забрать весь кошелёк. Розлив: TAP_OWN — налить в посуду в руке (item — питьё, grade — его
 * качество, price — цена меры), TAP_MUG — налить в кружку торговца (сверх цены залог), MUG_RETURN — сдать пустую
 * кружку (item — вид кружки, price — залог). EXCHANGE — обмен монет: grade = from × Price.COINS + to, price[0] = 1 —
 * сколько можно разом (Shift), иначе один раз.
 */
public record ShopActionPayload(int npcId, Action action, ResourceLocation item, int grade, int[] price)
        implements CustomPacketPayload {
    public enum Action { BUY, REQUEST, SELL, DEPOSIT, WITHDRAW, TAP_OWN, TAP_MUG, MUG_RETURN, EXCHANGE }

    public static final Type<ShopActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "shop_action"));

    public static final StreamCodec<FriendlyByteBuf, ShopActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.npcId());
                buf.writeEnum(payload.action());
                buf.writeResourceLocation(payload.item());
                buf.writeVarInt(payload.grade());
                buf.writeVarIntArray(payload.price());
            },
            buf -> new ShopActionPayload(buf.readVarInt(), buf.readEnum(Action.class), buf.readResourceLocation(),
                    buf.readVarInt(), Price.clean(buf.readVarIntArray(Price.COINS))));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShopActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Shop.act(player, payload);
            }
        });
    }
}
