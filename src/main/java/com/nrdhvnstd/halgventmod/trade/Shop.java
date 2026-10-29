package com.nrdhvnstd.halgventmod.trade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.network.ShopActionPayload;
import com.nrdhvnstd.halgventmod.network.ShopOpenPayload;
import com.nrdhvnstd.halgventmod.npc.HalgNpc;
import com.nrdhvnstd.halgventmod.npc.NpcRole;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.storage.Storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Лавка НПС-торговца — сервер. Торговец торгует товаром прилавков, привязанных к нему жезлом НПС (режим «Прилавок»):
 * продаёт (деньги — в кошелёк торговца, Purse), принимает запросы на кончившийся товар (хозяину торговца — игроку из
 * HalgNpc.getMerchantOwner — сообщение, если он в сети), скупает то, на что хозяин поставил цену скупки (платит
 * кошелёк, вещь ложится в товар прилавка или в хранилище) и меняет монеты. Цена — набор монет (Price). Сделка проходит,
 * только если цена на прилавке та же, что видел покупатель.
 */
public final class Shop {
    // Меняйте здесь
    private static final double NPC_REACH = 8;     // блоков от покупателя до НПС

    private Shop() {}

    /** Открыть лавку покупателю (E по НПС-торговцу). Прилавков у торговца нет — сказать, как привязать. */
    public static void open(ServerPlayer player, HalgNpc npc) {
        if (npc.getStalls().isEmpty()) {
            player.displayClientMessage(Component.translatable("trade.halgventmod.shop.unbound"), true);
            return;
        }
        ServerLevel level = (ServerLevel) npc.level();
        List<MarketStallBlockEntity> stalls = stalls(level, npc);
        restock(stalls); // прилавки — полные, сколько есть в хранилищах
        boolean owner = isOwner(player, npc);
        PacketDistributor.sendToPlayer(player, new ShopOpenPayload(npc.getId(), npc.getDisplayName().getString(),
                sellEntries(stalls), buyEntries(stalls, player), owner, npc.getPurse().all())); // кошелёк виден всем — для обмена
    }

    private static boolean isOwner(ServerPlayer player, HalgNpc npc) {
        return player.getUUID().equals(npc.getMerchantOwner()) || Permissions.isAdmin(player); // кассу видит хозяин и администрация
    }

    /**
     * Обмен монет у торговца по весу металла (Coin.exchange): монеты покупателя уходят, нужные выдаются — иметь их
     * торговцу не нужно.
     * Один раз (например, 2 талера на 5 марок) или, с Shift, сколько хватит монет у покупателя.
     */
    private static void exchange(ServerPlayer player, HalgNpc npc, ShopActionPayload payload) {
        int from = payload.grade() / Price.COINS;
        int to = payload.grade() % Price.COINS;
        if (from == to || from < 0 || from >= Price.COINS) {
            return;
        }
        Coin give = Coin.byOrdinal(from);
        Coin get = Coin.byOrdinal(to);
        int[] rate = Coin.exchange(give, get);
        int times = Money.count(player, give) / rate[0];
        if (payload.price()[0] != 1) {
            times = Math.min(times, 1);
        }
        if (times <= 0) {
            say(player, Component.translatable("trade.halgventmod.shop.no_money", give.title()));
            return;
        }
        // Меняла не держит монет: отданные уходят, нужные выдаются — кошелёк торговца не трогается
        if (!Money.take(player, give, rate[0] * times)) {
            return;
        }
        Money.give(player, get, rate[1] * times);
        say(player, Component.translatable("trade.halgventmod.shop.exchanged", rate[0] * times, give.title(), rate[1] * times,
                get.title()));
    }

    // Хозяин вносит в кошелёк торговца все монеты вида, что при себе (на скупку)
    private static void deposit(ServerPlayer player, HalgNpc npc, Coin coin) {
        if (!isOwner(player, npc)) {
            return;
        }
        int amount = Money.count(player, coin);
        if (amount > 0 && Money.take(player, coin, amount)) {
            npc.getPurse().add(coin, amount);
            say(player, Component.translatable("trade.halgventmod.shop.deposited", amount, coin.title()));
        }
    }

    // Хозяин забирает весь кошелёк торговца
    private static void withdraw(ServerPlayer player, HalgNpc npc) {
        if (!isOwner(player, npc)) {
            return;
        }
        int[] all = npc.getPurse().takeAll();
        Money.give(player, all);
        if (Price.isSet(all)) {
            say(player, Component.translatable("trade.halgventmod.shop.withdrawn", Price.text(all)));
        }
    }

    /**
     * Прилавки торговца: привязанные к нему жезлом НПС (режим «Прилавок») и сейчас загруженные. Прилавок, который
     * сломали или перепривязали к другому торговцу, в счёт не идёт.
     */
    public static List<MarketStallBlockEntity> stalls(ServerLevel level, HalgNpc npc) {
        List<MarketStallBlockEntity> found = new ArrayList<>();
        for (BlockPos pos : npc.getStalls()) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall
                    && npc.getUUID().equals(stall.merchant())) {
                found.add(stall);
            }
        }
        return found;
    }

    private record Key(ResourceLocation item, int grade, List<Integer> price) {
        static Key of(ResourceLocation item, int grade, int[] price) {
            return new Key(item, grade, Arrays.stream(Price.clean(price)).boxed().toList());
        }
    }

    // Что продаётся: по виду, ступени и цене; запасы прилавков складываются
    private static List<ShopOpenPayload.Entry> sellEntries(List<MarketStallBlockEntity> stalls) {
        Map<Key, ShopOpenPayload.Entry> entries = new LinkedHashMap<>();
        for (MarketStallBlockEntity stall : stalls) {
            for (MarketStallBlockEntity.Offer offer : stall.offers()) {
                for (int grade = 0; grade < offer.rows(); grade++) {
                    int[] price = offer.sell(grade);
                    if (!Price.isSet(price)) {
                        continue;
                    }
                    Key key = Key.of(offer.item, grade, price);
                    int stock = stall.stockOf(offer.item, grade);
                    ShopOpenPayload.Entry old = entries.get(key);
                    ItemStack display = old != null && !old.display().isEmpty() ? old.display() : stall.sample(offer.item, grade);
                    if (display.isEmpty()) {
                        display = display(offer.item, offer.graded, grade);
                    }
                    entries.put(key, new ShopOpenPayload.Entry(display, offer.item, offer.graded, grade, Price.clean(price),
                            (old != null ? old.count() : 0) + stock));
                }
            }
        }
        List<ShopOpenPayload.Entry> list = compact(entries.values());
        Taps.sellEntries(stalls, list); // питьё из бочонков
        return list;
    }

    // Что скупается: count — сколько такого у покупателя при себе
    private static List<ShopOpenPayload.Entry> buyEntries(List<MarketStallBlockEntity> stalls, ServerPlayer player) {
        Map<Key, ShopOpenPayload.Entry> entries = new LinkedHashMap<>();
        for (MarketStallBlockEntity stall : stalls) {
            for (MarketStallBlockEntity.Offer offer : stall.offers()) {
                for (int grade = 0; grade < offer.rows(); grade++) {
                    int[] price = offer.buy(grade);
                    if (!Price.isSet(price)) {
                        continue;
                    }
                    Key key = Key.of(offer.item, grade, price);
                    if (!entries.containsKey(key)) {
                        int held = Money.count(player, matches(offer.item, offer.graded, grade));
                        entries.put(key, new ShopOpenPayload.Entry(display(offer.item, offer.graded, grade), offer.item,
                                offer.graded, grade, Price.clean(price), held));
                    }
                }
            }
        }
        List<ShopOpenPayload.Entry> list = compact(entries.values());
        Taps.buyEntries(stalls, player, list); // пустые кружки за залог
        return list;
    }

    /**
     * Строки лавки без лишних качеств: у вещи с качествами остаются только ступени, которые есть (в товаре — для
     * покупки, при себе у покупателя — для скупки). Нет ни одной — одна строка, по нижней ступени: заказать или
     * узнать цену. Вещи без качеств — как есть.
     */
    private static List<ShopOpenPayload.Entry> compact(Collection<ShopOpenPayload.Entry> entries) {
        Map<ResourceLocation, Boolean> present = new LinkedHashMap<>();
        for (ShopOpenPayload.Entry entry : entries) {
            if (entry.graded()) {
                present.merge(entry.item(), entry.count() > 0, Boolean::logicalOr);
            }
        }
        List<ShopOpenPayload.Entry> list = new ArrayList<>();
        Set<ResourceLocation> shown = new HashSet<>();
        for (ShopOpenPayload.Entry entry : entries) {
            if (!entry.graded()) {
                list.add(entry);
            } else if (present.get(entry.item())) {
                if (entry.count() > 0) {
                    list.add(entry);
                }
            } else if (shown.add(entry.item())) {
                list.add(lowest(entries, entry.item()));
            }
        }
        return list;
    }

    private static ShopOpenPayload.Entry lowest(Collection<ShopOpenPayload.Entry> entries, ResourceLocation item) {
        ShopOpenPayload.Entry best = null;
        for (ShopOpenPayload.Entry entry : entries) {
            if (entry.graded() && entry.item().equals(item) && (best == null || entry.grade() < best.grade())) {
                best = entry;
            }
        }
        return best;
    }

    // Вещь для показа: вид, у снаряжения — с качеством ступени
    private static ItemStack display(ResourceLocation item, boolean graded, int grade) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item));
        if (graded && GearQuality.applies(stack)) {
            GearQuality.set(stack, GearQuality.Grade.values()[grade]);
        }
        return stack;
    }

    private static Predicate<ItemStack> matches(ResourceLocation item, boolean graded, int grade) {
        return stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)
                && MarketStallBlockEntity.grade(stack, graded) == grade;
    }

    /** Действие покупателя в лавке. */
    public static void act(ServerPlayer player, ShopActionPayload payload) {
        Entity entity = player.level().getEntity(payload.npcId());
        if (!(entity instanceof HalgNpc npc) || npc.getRole() != NpcRole.MERCHANT
                || !npc.isAlive() || player.distanceToSqr(npc) > NPC_REACH * NPC_REACH) {
            return;
        }
        List<MarketStallBlockEntity> stalls = stalls((ServerLevel) npc.level(), npc);
        switch (payload.action()) {
            case BUY -> buy(player, npc, stalls, payload);
            case REQUEST -> request(player, npc, stalls, payload);
            case SELL -> sell(player, npc, stalls, payload);
            case DEPOSIT -> deposit(player, npc, Coin.byOrdinal(payload.grade()));
            case WITHDRAW -> withdraw(player, npc);
            case TAP_OWN -> Taps.pourOwn(player, npc, stalls, payload);
            case TAP_MUG -> Taps.pourMug(player, npc, stalls, payload);
            case MUG_RETURN -> Taps.returnMug(player, npc, stalls, payload);
            case EXCHANGE -> exchange(player, npc, payload);
        }
        open(player, npc); // окно — с новыми запасами и деньгами
    }

    private static void buy(ServerPlayer player, HalgNpc npc, List<MarketStallBlockEntity> stalls, ShopActionPayload payload) {
        if (!Price.isSet(payload.price())) {
            return;
        }
        MarketStallBlockEntity stall = find(stalls, payload, true);
        if (stall == null) {
            say(player, "sold_out");
            return;
        }
        if (!Money.canPay(player, payload.price())) {
            say(player, Component.translatable("trade.halgventmod.shop.no_money", Price.text(payload.price())));
            return;
        }
        ItemStack bought = stall.takeOne(payload.item(), payload.grade());
        if (bought.isEmpty() || !Money.pay(player, payload.price())) {
            say(player, "sold_out");
            return;
        }
        npc.getPurse().add(payload.price()); // выручка — в кошелёк торговца
        Component name = bought.getHoverName();
        Storage.addOrDrop(player.getInventory(), bought);
        say(player, Component.translatable("trade.halgventmod.shop.bought", name, Price.text(payload.price())));
    }

    private static void sell(ServerPlayer player, HalgNpc npc, List<MarketStallBlockEntity> stalls, ShopActionPayload payload) {
        MarketStallBlockEntity stall = Price.isSet(payload.price()) && npc.getPurse().canPay(payload.price())
                ? find(stalls, payload, false) : null;
        if (stall == null) {
            say(player, "not_buying");
            return;
        }
        MarketStallBlockEntity.Offer offer = stall.offer(payload.item());
        Predicate<ItemStack> test = matches(payload.item(), offer.graded, payload.grade());
        ItemStack sold = Money.count(player, test) > 0 ? Money.takeOne(player, test) : ItemStack.EMPTY;
        if (sold.isEmpty()) {
            say(player, "nothing_to_sell");
            return;
        }
        Component name = sold.getHoverName();
        if (!(stall.put(sold) || putInStorage(stalls, sold)) || !npc.getPurse().pay(payload.price())) {
            Storage.addOrDrop(player.getInventory(), sold); // не вышло — вещь обратно
            say(player, "not_buying");
            return;
        }
        Money.give(player, payload.price());
        say(player, Component.translatable("trade.halgventmod.shop.sold", name, Price.text(payload.price())));
    }

    private static void request(ServerPlayer player, HalgNpc npc, List<MarketStallBlockEntity> stalls, ShopActionPayload payload) {
        for (MarketStallBlockEntity stall : stalls) {
            MarketStallBlockEntity.Offer offer = stall.offer(payload.item());
            if (offer != null && Price.isSet(offer.sell(payload.grade()))) {
                stall.addRequest(payload.item(), payload.grade(), player.getGameProfile().getName());
                say(player, "requested");
                ServerPlayer owner = player.server.getPlayerList().getPlayer(npc.getMerchantOwner());
                if (owner != null) {
                    Component item = display(payload.item(), offer.graded, payload.grade()).getHoverName();
                    owner.sendSystemMessage(Component.translatable("trade.halgventmod.shop.request_notice",
                            player.getDisplayName(), item));
                }
                return;
            }
        }
    }

    /**
     * Прилавок со строкой лавки: та же цена. selling — продаёт и есть товар; иначе — скупает и в товаре есть место
     * (хватит ли денег, проверяет кошелёк торговца).
     */
    @Nullable
    private static MarketStallBlockEntity find(List<MarketStallBlockEntity> stalls, ShopActionPayload payload, boolean selling) {
        for (MarketStallBlockEntity stall : stalls) {
            MarketStallBlockEntity.Offer offer = stall.offer(payload.item());
            if (offer == null) {
                continue;
            }
            if (selling) {
                if (Price.same(offer.sell(payload.grade()), payload.price()) && stall.stockOf(payload.item(), payload.grade()) > 0) {
                    return stall;
                }
            } else if (Price.same(offer.buy(payload.grade()), payload.price())
                    && (stall.hasRoom(display(payload.item(), offer.graded, payload.grade()))
                            || storageRoom(stalls, display(payload.item(), offer.graded, payload.grade())))) {
                return stall;
            }
        }
        return null;
    }

    // ===== Хранилища торговца (StallKind.STORAGE): запас для прилавков и место для скупленного =====

    private static List<MarketStallBlockEntity> storages(List<MarketStallBlockEntity> stalls) {
        return stalls.stream().filter(stall -> stall.kind() == StallKind.STORAGE).toList();
    }

    /** Пополнить прилавки из хранилищ: товар с ценой продажи, по виду и качеству, сколько войдёт. */
    private static void restock(List<MarketStallBlockEntity> stalls) {
        List<MarketStallBlockEntity> storages = storages(stalls);
        if (storages.isEmpty()) {
            return;
        }
        for (MarketStallBlockEntity stall : stalls) {
            if (stall.kind() == StallKind.STORAGE) {
                continue;
            }
            for (MarketStallBlockEntity.Offer offer : stall.offers()) {
                for (int grade = 0; grade < offer.rows(); grade++) {
                    if (Price.isSet(offer.sell(grade))) {
                        fill(stall, storages, matches(offer.item, offer.graded, grade));
                    }
                }
            }
        }
    }

    // Перекладывать по одной, пока на прилавке есть место и в хранилищах есть такое
    private static void fill(MarketStallBlockEntity stall, List<MarketStallBlockEntity> storages, Predicate<ItemStack> test) {
        for (MarketStallBlockEntity storage : storages) {
            int slot;
            while ((slot = storage.find(test)) >= 0 && stall.hasRoom(storage.stock().getItem(slot).copyWithCount(1))) {
                ItemStack one = storage.takeAt(slot);
                if (!stall.put(one)) {
                    storage.put(one); // не вошло — обратно
                    return;
                }
            }
        }
    }

    // Скупленное, если на прилавке нет места, — в хранилище
    private static boolean putInStorage(List<MarketStallBlockEntity> stalls, ItemStack stack) {
        for (MarketStallBlockEntity storage : storages(stalls)) {
            if (storage.put(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean storageRoom(List<MarketStallBlockEntity> stalls, ItemStack stack) {
        for (MarketStallBlockEntity storage : storages(stalls)) {
            if (storage.hasRoom(stack)) {
                return true;
            }
        }
        return false;
    }

    static void say(ServerPlayer player, String key) {
        say(player, Component.translatable("trade.halgventmod.shop." + key));
    }

    static void say(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }
}
