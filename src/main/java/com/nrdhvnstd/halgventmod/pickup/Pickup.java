package com.nrdhvnstd.halgventmod.pickup;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorRules;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.gear.GearInventory;
import com.nrdhvnstd.halgventmod.gear.GearRules;
import com.nrdhvnstd.halgventmod.gear.GearSlot;
import com.nrdhvnstd.halgventmod.gear.ModGear;
import com.nrdhvnstd.halgventmod.mixin.AbstractArrowAccessor;
import com.nrdhvnstd.halgventmod.mixin.ItemEntityAccessor;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.storage.Storage;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

/**
 * Ручной подбор (ТЗ 6): касанием вещи не подбираются (кроме творческого режима), только клавишей
 * «Подобрать» (E) по вещи под прицелом. Клиент выбирает цель (PickupInput) и шлёт PickupPayload;
 * сервер проверяет дальность, прямую видимость, задержку между подборами и кладёт вещь через Storage.
 * Shift+E по надеваемой вещи — подобрать и сразу надеть, если ячейка свободна и правила пускают.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Pickup {
    public static final double REACH = 3.0;             // блоков от глаз ⚠️
    private static final double REACH_MARGIN = 1.0;     // сервер прощает сдвиг за время пакета
    public static final int PICKUP_COOLDOWN = 8;        // 0,4 с между подборами, и удержание E — с тем же шагом ⚠️
    private static final int COOLDOWN_SLACK = 2;        // пакеты приходят неровно
    public static final int OWN_DROP_DELAY = 20;        // только что выброшенное своё — 1 с не подобрать ⚠️
    private static final int NEVER_PICKUP = 32767;      // ванильная «вечная» задержка (предметы-декорации)
    private static final double TARGET_SLACK = 0.5;     // луч видимости может упереться в блок рядом с вещью

    private Pickup() {}

    /** Последний подбор (вложение без сохранения). */
    public static final class State {
        private int lastTick = -PICKUP_COOLDOWN;
    }

    /** Подбирать только вручную — всем, кроме творческого режима. */
    public static boolean manualOnly(Player player) {
        return !player.getAbilities().instabuild;
    }

    // Касанием — нет
    @SubscribeEvent
    public static void onTouchPickup(ItemEntityPickupEvent.Pre event) {
        if (manualOnly(event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    /** Можно ли вообще подобрать эту сущность (без дальности). Клиент зовёт для подсветки. */
    public static boolean canTarget(Player player, Entity entity) {
        if (!entity.isAlive()) {
            return false;
        }
        if (entity instanceof ItemEntity item) {
            ItemEntityAccessor access = (ItemEntityAccessor) item;
            if (item.getItem().isEmpty() || access.halgventmod$pickupDelay() == NEVER_PICKUP) {
                return false;
            }
            if (access.halgventmod$target() != null && !access.halgventmod$target().equals(player.getUUID())) {
                return false;
            }
            return !(item.getOwner() == player && item.getAge() < OWN_DROP_DELAY);
        }
        if (entity instanceof AbstractArrow arrow) {
            // Разрешение подбора клиенту не приходит — там решает сервер
            boolean allowed = player.level().isClientSide
                    || arrow.pickup == AbstractArrow.Pickup.ALLOWED
                    || arrow.pickup == AbstractArrow.Pickup.CREATIVE_ONLY && player.hasInfiniteMaterials();
            return allowed && ((AbstractArrowAccessor) arrow).halgventmod$inGround() && arrow.shakeTime <= 0;
        }
        return false;
    }

    /** Достаёт ли игрок до сущности и видит ли её (между глазами и ней нет блока). */
    public static boolean inReach(Player player, Entity entity, double reach) {
        Vec3 eye = player.getEyePosition();
        AABB box = entity.getBoundingBox();
        Vec3 closest = new Vec3(
                Math.clamp(eye.x, box.minX, box.maxX),
                Math.clamp(eye.y, box.minY, box.maxY),
                Math.clamp(eye.z, box.minZ, box.maxZ));
        if (eye.distanceToSqr(closest) > reach * reach) {
            return false;
        }
        HitResult hit = player.level().clip(new ClipContext(eye, box.getCenter(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || box.inflate(TARGET_SLACK).contains(hit.getLocation());
    }

    /** Пакет от клиента: подобрать сущность entityId; equip — Shift+E. */
    public static void handle(ServerPlayer player, int entityId, boolean equip) {
        if (player.isSpectator() || !player.isAlive()) {
            return;
        }
        State state = player.getData(ModAttachments.PICKUP);
        if (player.tickCount - state.lastTick < PICKUP_COOLDOWN - COOLDOWN_SLACK) {
            return;
        }
        Entity entity = player.level().getEntity(entityId);
        if (entity == null || !canTarget(player, entity) || !inReach(player, entity, REACH + REACH_MARGIN)) {
            return;
        }
        state.lastTick = player.tickCount;
        if (entity instanceof ItemEntity item) {
            pickItem(player, item, equip);
        } else if (entity instanceof AbstractArrow arrow) {
            pickArrow(player, arrow);
        }
    }

    private static void pickItem(ServerPlayer player, ItemEntity item, boolean equip) {
        ItemStack stack = item.getItem().copy();
        int before = stack.getCount();
        if (!(equip && equip(player, stack))) {
            Storage.add(player.getInventory(), stack);
        }
        int taken = before - stack.getCount();
        if (taken == 0) {
            ArmorRules.tell(player, Component.translatable("message.halgventmod.no_room"));
            return;
        }
        player.take(item, taken); // ванильная анимация «вещь летит в игрока» и звук
        player.awardStat(Stats.ITEM_PICKED_UP.get(item.getItem().getItem()), taken);
        player.onItemPickup(item);
        if (stack.isEmpty()) {
            item.discard();
        } else {
            item.setItem(stack);
            ArmorRules.tell(player, Component.translatable("message.halgventmod.no_room"));
        }
    }

    // Стрела: ванильная проверка и подбор через Inventory.add — значит, через Storage, сначала в колчан
    private static void pickArrow(ServerPlayer player, AbstractArrow arrow) {
        if (((AbstractArrowAccessor) arrow).halgventmod$tryPickup(player)) {
            player.take(arrow, 1);
            arrow.discard();
        } else {
            ArmorRules.tell(player, Component.translatable("message.halgventmod.no_room"));
        }
    }

    // Надеть сразу (Shift+E): броня или снаряжение, свободная ячейка, правила надевания. true — надето
    private static boolean equip(ServerPlayer player, ItemStack stack) {
        ArmorSlot armorSlot = ModArmor.slotOf(stack);
        if (armorSlot != null) {
            ArmorInventory armor = player.getData(ModAttachments.ARMOR);
            if (!armor.get(armorSlot).isEmpty() || !armor.isItemValid(armorSlot.ordinal(), stack)) {
                return false;
            }
            Component refusal = ArmorRules.refusal(armor, armorSlot, stack);
            if (refusal != null) {
                ArmorRules.tell(player, refusal);
                return false;
            }
            armor.set(armorSlot, stack.split(1));
            equipSound(player);
            return true;
        }
        GearInventory gear = player.getData(ModAttachments.GEAR);
        GearSlot gearSlot = GearRules.slotFor(gear, stack);
        if (gearSlot == null || !gear.get(gearSlot).isEmpty()) {
            return false;
        }
        Component refusal = GearRules.refusal(gear, gearSlot, stack);
        if (refusal != null) {
            ArmorRules.tell(player, refusal);
            return false;
        }
        gear.set(gearSlot, stack.split(1));
        equipSound(player);
        return true;
    }

    private static void equipSound(Player player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Надевается ли вещь вообще (для подсказки «Shift+E — надеть»). */
    public static boolean equippable(ItemStack stack) {
        return ModArmor.slotOf(stack) != null || ModGear.typeOf(stack) != null;
    }
}
