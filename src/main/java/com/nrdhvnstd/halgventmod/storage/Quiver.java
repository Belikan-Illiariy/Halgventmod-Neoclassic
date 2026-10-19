package com.nrdhvnstd.halgventmod.storage;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.gear.GearInventory;
import com.nrdhvnstd.halgventmod.gear.GearSlot;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Колчан на поясе (ТЗ 6.2): одна стопка стрел, до MAX_ARROWS. Подобранные и выданные стрелы
 * идут сюда раньше карманов (Storage.add); лук и арбалет берут стрелы сначала отсюда.
 *
 * Оружие тратит стрелу прямо в стопке, которую ему дали (split/shrink), поэтому даём ему живую
 * стопку колчана и в конце тика, если она уменьшилась, записываем колчан.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Quiver {
    public static final int MAX_ARROWS = 20; // ⚠️

    private Quiver() {}

    /** Стрела ли это (всё, что ванильный тег считает стрелами: обычные, с зельем, спектральные). */
    public static boolean accepts(ItemStack stack) {
        return stack.is(ItemTags.ARROWS);
    }

    /** Ячейка надетого колчана (живой список; пустой, если колчана нет). */
    private static List<ItemStack> items(Player player) {
        return player.getData(ModAttachments.GEAR).items(GearSlot.QUIVER);
    }

    /** Стрелы в надетом колчане (живая стопка) или пустая стопка. */
    public static ItemStack arrows(Player player) {
        List<ItemStack> items = items(player);
        return items.isEmpty() ? ItemStack.EMPTY : items.get(0);
    }

    /** Положить стрелы в надетый колчан; что не влезло, остаётся в stack. true — положили хоть что-то. */
    public static boolean insert(Player player, ItemStack stack) {
        List<ItemStack> items = items(player);
        if (items.isEmpty() || stack.isEmpty() || !accepts(stack)) {
            return false;
        }
        ItemStack inside = items.get(0);
        int moved;
        if (inside.isEmpty()) {
            moved = Math.min(stack.getCount(), MAX_ARROWS);
            items.set(0, stack.split(moved));
        } else if (ItemStack.isSameItemSameComponents(inside, stack)) {
            moved = Math.min(stack.getCount(), MAX_ARROWS - inside.getCount());
            if (moved <= 0) {
                return false;
            }
            inside.grow(moved);
            stack.shrink(moved);
        } else {
            return false; // другие стрелы — колчан держит одну стопку
        }
        player.getData(ModAttachments.GEAR).contentsChanged(GearSlot.QUIVER);
        return true;
    }

    /** Вынуть все стрелы из надетого колчана. */
    public static ItemStack takeAll(Player player) {
        List<ItemStack> items = items(player);
        if (items.isEmpty() || items.get(0).isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = items.get(0);
        items.set(0, ItemStack.EMPTY);
        player.getData(ModAttachments.GEAR).contentsChanged(GearSlot.QUIVER);
        return taken;
    }

    // ----- Стрельба из колчана -----

    /** Стопка, отданная оружию в этом тике, и сколько в ней было (вложение без сохранения). */
    public static final class Lent {
        private ItemStack stack = ItemStack.EMPTY;
        private int count;
    }

    @SubscribeEvent
    public static void onGetProjectile(LivingGetProjectileEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getProjectileWeaponItemStack().getItem() instanceof ProjectileWeaponItem weapon)) {
            return;
        }
        ItemStack arrows = arrows(player);
        if (arrows.isEmpty() || !weapon.getAllSupportedProjectiles(event.getProjectileWeaponItemStack()).test(arrows)) {
            return;
        }
        // Стрелы в руке (вторая рука) ваниль уже предпочла — их и оставляем
        ItemStack chosen = event.getProjectileItemStack();
        if (!chosen.isEmpty() && (player.getOffhandItem() == chosen || player.getMainHandItem() == chosen)) {
            return;
        }
        event.setProjectileItemStack(arrows);
        Lent lent = player.getData(ModAttachments.QUIVER_LENT);
        lent.stack = arrows;
        lent.count = arrows.getCount();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Lent lent = player.getData(ModAttachments.QUIVER_LENT);
        if (lent.stack.isEmpty() && lent.count == 0) {
            return;
        }
        GearInventory gear = player.getData(ModAttachments.GEAR);
        if (lent.stack == arrows(player) && lent.stack.getCount() != lent.count) {
            if (lent.stack.isEmpty()) {
                items(player).set(0, ItemStack.EMPTY);
            }
            gear.contentsChanged(GearSlot.QUIVER);
        }
        lent.stack = ItemStack.EMPTY;
        lent.count = 0;
    }
}
