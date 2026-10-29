package com.nrdhvnstd.halgventmod.trade;

import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.network.ShopActionPayload;
import com.nrdhvnstd.halgventmod.network.ShopOpenPayload;
import com.nrdhvnstd.halgventmod.npc.HalgNpc;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.quality.QualityDisplay;
import com.nrdhvnstd.halgventmod.storage.Storage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Розлив у НПС-торговца (бочонки с краном хозяина в 30 блоках, StallKind.TAP). Покупатель берёт питьё мерой:
 * «В свою посуду» — торговец наливает в кружку, кувшин или корчагу в руке покупателя (за каждую меру — цена меры);
 * «С кружкой» — наливает в кружку торговца, сверх цены — залог за кружку. Пустую кружку торговца (любую глиняную
 * или пьютеровую такого вида) покупатель сдаёт во вкладке «Продать» и получает залог назад; кружка ложится в бочонок.
 * Деньги — в кошелёк торговца и из него (Purse). Сделка проходит, только если цена не менялась, пока окно было открыто.
 */
final class Taps {
    private Taps() {}

    // ===== Строки лавки =====

    /** Питьё в разлив: бочонок с питьём и ценой меры — строка «Купить». */
    static void sellEntries(List<MarketStallBlockEntity> stalls, List<ShopOpenPayload.Entry> entries) {
        for (MarketStallBlockEntity stall : stalls) {
            ItemStack sample = stall.tapSample();
            int[] price = stall.cupPrice();
            if (stall.kind() != StallKind.TAP || sample.isEmpty() || !Price.isSet(price)) {
                continue;
            }
            GearQuality.Grade grade = QualityDisplay.of(sample);
            int slot = stall.mugSlot();
            Mug mug = slot < 0 ? null : Mug.of(stall.stock().getItem(slot));
            int[] deposit = mug == null ? Price.zero() : stall.deposit(mug);
            entries.add(new ShopOpenPayload.Entry(sample, id(sample), grade != null, grade != null ? grade.ordinal() : 0,
                    price, stall.tapMeasures(), ShopOpenPayload.Entry.TAP, Price.isSet(deposit) ? stall.mugCount() : 0, deposit));
        }
    }

    /** Сдать пустую кружку за залог — строка «Продать»: по виду кружки и залогу, count — сколько таких у покупателя. */
    static void buyEntries(List<MarketStallBlockEntity> stalls, ServerPlayer player, List<ShopOpenPayload.Entry> entries) {
        for (Mug mug : Mug.values()) {
            for (MarketStallBlockEntity stall : stalls) {
                int[] deposit = stall.deposit(mug);
                if (stall.kind() != StallKind.TAP || !Price.isSet(deposit)) {
                    continue;
                }
                ItemStack display = new ItemStack(mug.item());
                int held = Money.count(player, stack -> stack.is(mug.item()) && Mug.empty(stack));
                entries.add(new ShopOpenPayload.Entry(display, id(display), false, 0, deposit, held,
                        ShopOpenPayload.Entry.MUG, 0, Price.zero()));
                break; // один залог на вид кружки: у первого бочонка
            }
        }
    }

    // ===== Сделки =====

    /** В свою посуду в руке: сколько влезет, сколько есть в бочонке и на сколько хватит денег. */
    static void pourOwn(ServerPlayer player, HalgNpc npc, List<MarketStallBlockEntity> stalls, ShopActionPayload payload) {
        MarketStallBlockEntity stall = tap(stalls, payload);
        if (stall == null) {
            Shop.say(player, "sold_out");
            return;
        }
        ItemStack vessel = player.getMainHandItem();
        if (Pottery.drinkCapacity(vessel) <= 0) {
            Shop.say(player, "tap_need_vessel");
            return;
        }
        // Сколько мер войдёт: пробуем на копии посуды
        ItemStack trial = vessel.copy();
        ItemStack sample = stall.tapSample();
        int fits = 0;
        while (fits < stall.tapMeasures() && Pottery.canPour(trial, sample, player.level())) {
            Pottery.pour(trial, sample, player.level());
            fits++;
        }
        if (fits == 0) {
            Shop.say(player, "tap_vessel_full");
            return;
        }
        int measures = fits;
        while (measures > 0 && !Money.canPay(player, Price.times(payload.price(), measures))) {
            measures--;
        }
        if (measures == 0) {
            Shop.say(player, Component.translatable("trade.halgventmod.shop.no_money", Price.text(payload.price())));
            return;
        }
        int[] total = Price.times(payload.price(), measures);
        Money.pay(player, total);
        for (int i = 0; i < measures; i++) {
            Pottery.pour(vessel, stall.tapTake(), player.level());
        }
        npc.getPurse().add(total);
        poured(player);
        Shop.say(player, Component.translatable("trade.halgventmod.shop.tap_poured", measures, sample.getHoverName(), Price.text(total)));
    }

    /** В кружку торговца: мера и залог за кружку. */
    static void pourMug(ServerPlayer player, HalgNpc npc, List<MarketStallBlockEntity> stalls, ShopActionPayload payload) {
        MarketStallBlockEntity stall = tap(stalls, payload);
        if (stall == null) {
            Shop.say(player, "sold_out");
            return;
        }
        int slot = stall.mugSlot();
        Mug mug = slot < 0 ? null : Mug.of(stall.stock().getItem(slot));
        if (mug == null || !Price.isSet(stall.deposit(mug))) {
            Shop.say(player, "tap_no_mugs");
            return;
        }
        int[] total = Price.plus(payload.price(), stall.deposit(mug));
        if (!Money.pay(player, total)) {
            Shop.say(player, Component.translatable("trade.halgventmod.shop.no_money", Price.text(total)));
            return;
        }
        ItemStack cup = stall.stock().removeItem(slot, 1);
        ItemStack drink = stall.tapTake();
        Pottery.pour(cup, drink, player.level());
        npc.getPurse().add(total);
        Storage.addOrDrop(player.getInventory(), cup);
        poured(player);
        Shop.say(player, Component.translatable("trade.halgventmod.shop.tap_mug", drink.getHoverName(), Price.text(total)));
    }

    /** Сдать пустую кружку: залог из кошелька торговца, кружка — в бочонок. */
    static void returnMug(ServerPlayer player, HalgNpc npc, List<MarketStallBlockEntity> stalls, ShopActionPayload payload) {
        Mug mug = Mug.of(new ItemStack(BuiltInRegistries.ITEM.get(payload.item())));
        if (mug == null) {
            return;
        }
        ItemStack empty = new ItemStack(mug.item());
        MarketStallBlockEntity stall = null;
        for (MarketStallBlockEntity each : stalls) {
            if (each.kind() == StallKind.TAP && Price.isSet(each.deposit(mug)) && Price.same(each.deposit(mug), payload.price())
                    && each.hasRoom(empty)) {
                stall = each;
                break;
            }
        }
        if (stall == null || !npc.getPurse().canPay(payload.price())) {
            Shop.say(player, "not_buying");
            return;
        }
        ItemStack returned = Money.takeOne(player, stack -> stack.is(mug.item()) && Mug.empty(stack));
        if (returned.isEmpty()) {
            Shop.say(player, "nothing_to_sell");
            return;
        }
        stall.put(returned);
        npc.getPurse().pay(payload.price());
        Money.give(player, payload.price());
        Shop.say(player, Component.translatable("trade.halgventmod.shop.mug_returned", Price.text(payload.price())));
    }

    // Бочонок со строкой лавки: то же питьё, то же качество, та же цена меры, есть что наливать
    @Nullable
    private static MarketStallBlockEntity tap(List<MarketStallBlockEntity> stalls, ShopActionPayload payload) {
        for (MarketStallBlockEntity stall : stalls) {
            ItemStack sample = stall.tapSample();
            if (stall.kind() != StallKind.TAP || sample.isEmpty() || !id(sample).equals(payload.item())
                    || !Price.same(stall.cupPrice(), payload.price())) {
                continue;
            }
            GearQuality.Grade grade = QualityDisplay.of(sample);
            if ((grade != null ? grade.ordinal() : 0) == payload.grade()) {
                return stall;
            }
        }
        return null;
    }

    private static ResourceLocation id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    private static void poured(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8F, 1.0F);
    }
}
