package com.nrdhvnstd.halgventmod.combat;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Кровь на снаряжении (по образцу Kingdom Come Combat, BloodiedEquipment): компонент halgventmod:blood 0…1.
 * <ul>
 *   <li>удар, пустивший кровь, пачкает оружие бьющего (сильнее — рубящий и режущий, дробящий — меньше), броню цели в
 *       той части тела, куда пришёлся, и чуть-чуть — броню бьющего (брызги);</li>
 *   <li>отмывается: E с вещью в руке по котлу с водой (котёл мелеет на ступень), Shift+E пустой рукой по котлу — вся
 *       надетая броня; в воде — быстро, под дождём — понемногу. Сама кровь не сходит — засыхает.</li>
 * </ul>
 * Видно: на броне — пятна в склейке брони, на оружии — капли, в инвентаре — капля в углу ячейки (client/BloodClient).
 * Кровь не идёт у скелетов, големов, сполохов и слизней.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Blood {
    // Меняйте здесь ⚠️
    public static final float WEAPON_GAIN = 0.12F;      // за 10 урона по телу
    public static final float ARMOR_GAIN = 0.1F;
    public static final float SPLATTER_GAIN = 0.03F;    // бьющему на броню
    public static final float WATER_WASH = 0.08F;       // в воде, за секунду
    public static final float RAIN_WASH = 0.01F;        // под дождём, за секунду
    public static final float VISIBLE = 0.15F;          // от этого видно
    private static final double CLOSE = 5.0;            // дальше — удар издалека (стрела): оружие не пачкается

    private Blood() {}

    public static float level(ItemStack stack) {
        return stack.getOrDefault(ModComponents.BLOOD.get(), 0.0F);
    }

    private static void add(ItemStack stack, float amount) {
        if (stack.isEmpty() || amount <= 0) {
            return;
        }
        set(stack, level(stack) + amount);
    }

    private static boolean set(ItemStack stack, float value) {
        float clamped = Math.max(0, Math.min(1, value));
        float before = level(stack);
        if (clamped <= 0.001F) {
            if (before > 0) {
                stack.remove(ModComponents.BLOOD.get());
                return true;
            }
            return false;
        }
        if (Math.abs(clamped - before) < 1.0E-4F) {
            return false;
        }
        stack.set(ModComponents.BLOOD.get(), clamped);
        return true;
    }

    /** Отмыть вещь целиком (чан, котёл); true — была в крови. */
    public static boolean wash(ItemStack stack) {
        return set(stack, 0);
    }

    /** Есть ли у этого кровь. */
    public static boolean bleeds(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        return !(type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.WITHER_SKELETON
                || type == EntityType.BOGGED || type == EntityType.IRON_GOLEM || type == EntityType.SNOW_GOLEM
                || type == EntityType.BLAZE || type == EntityType.SLIME || type == EntityType.MAGMA_CUBE
                || type == EntityType.WITHER || type == EntityType.ARMOR_STAND);
    }

    /** Удар дошёл до тела (Combat.landed): кровь на оружие, на броню цели и брызги на бьющего. */
    static void onWound(LivingEntity attacker, LivingEntity target, DamageKind kind, HitZone zone, float body) {
        if (body <= 0 || !bleeds(target)) {
            return;
        }
        float share = Math.min(1.5F, body / 10.0F) * (kind == DamageKind.BLUNT ? 0.4F : 1.0F);
        stain(target, zone.bodyPart(), ARMOR_GAIN * share);
        // Оружие и брызги — только вблизи и не лук: стрела пачкает того, в кого попала, а не стрелка
        ItemStack weapon = attacker.getMainHandItem();
        if (attacker.distanceToSqr(target) <= CLOSE * CLOSE && !(weapon.getItem() instanceof ProjectileWeaponItem)) {
            add(weapon, WEAPON_GAIN * share);
            stain(attacker, ArmorSlot.BodyPart.TORSO, SPLATTER_GAIN * share);
        }
    }

    // Броня в части тела
    private static void stain(LivingEntity entity, ArmorSlot.BodyPart part, float amount) {
        ArmorInventory armor = entity.getExistingDataOrNull(ModAttachments.ARMOR);
        if (armor == null) {
            return;
        }
        boolean changed = false;
        for (ArmorSlot slot : ArmorSlot.values()) {
            if (slot.bodyPart() == part && !armor.get(slot).isEmpty()) {
                ItemStack stack = armor.get(slot).copy();
                if (set(stack, level(stack) + amount)) {
                    armor.set(slot, stack);
                    changed = true;
                }
            }
        }
        if (changed) {
            entity.syncData(ModAttachments.ARMOR);
        }
    }

    /** Отмыть всю надетую броню на долю amount; true — что-то отмылось. */
    public static boolean washArmor(LivingEntity entity, float amount) {
        ArmorInventory armor = entity.getExistingDataOrNull(ModAttachments.ARMOR);
        if (armor == null) {
            return false;
        }
        boolean changed = false;
        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack stack = armor.get(slot);
            if (level(stack) > 0) {
                ItemStack copy = stack.copy();
                set(copy, level(copy) - amount);
                armor.set(slot, copy);
                changed = true;
            }
        }
        if (changed) {
            entity.syncData(ModAttachments.ARMOR);
        }
        return changed;
    }

    // В воде и под дождём отмывается само
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        float wash = player.isInWater() ? WATER_WASH : player.isInWaterOrRain() ? RAIN_WASH : 0;
        if (wash <= 0) {
            return;
        }
        set(player.getMainHandItem(), level(player.getMainHandItem()) - wash);
        set(player.getOffhandItem(), level(player.getOffhandItem()) - wash);
        washArmor(player, wash);
    }

    // Котёл с водой: вещь в руке — отмыть её; Shift и пустая рука — отмыть надетую броню
    @SubscribeEvent
    public static void onUseCauldron(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockState block = event.getLevel().getBlockState(event.getPos());
        if (!(event.getLevel() instanceof ServerLevel level) || !block.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) {
            return;
        }
        ItemStack held = player.getItemInHand(event.getHand());
        boolean washed;
        if (level(held) > 0) {
            washed = set(held, 0);
        } else if (held.isEmpty() && player.isSecondaryUseActive()) {
            washed = washArmor(player, 1);
        } else {
            return;
        }
        if (!washed) {
            return;
        }
        BlockPos pos = event.getPos();
        LayeredCauldronBlock.lowerFillLevel(block, level, pos);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8F, 1.2F);
        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.1);
        player.displayClientMessage(Component.translatable("message.halgventmod.blood.washed"), true);
        event.setCanceled(true);
    }
}
