package com.nrdhvnstd.halgventmod.needs;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.sound.PlayerVoice;
import com.nrdhvnstd.halgventmod.sound.Voices;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Выносливость 0–100. Тратится на бег, прыжки, плавание, удары, блок щитом и выстрелы.
 * На нуле — изнеможение: всё это запрещено, замедление и слабость, экран темнеет по краям.
 * Считается на сервере; клиент получает её через NeedsSync и сам гасит бег, прыжок и удар (StaminaInput).
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Stamina {
    // ===== Правила — меняйте здесь =====
    public static final int MAX = StaminaData.MAX;

    // Постоянная трата, за тик (20 тиков = 1 секунда)
    public static final float SPRINT_PER_TICK = 0.35F;   // бег: ~14 секунд с полной шкалы
    public static final float SWIM_PER_TICK = 0.25F;     // плавание (бег вплавь считается как бег)

    // Разовая трата
    public static final float JUMP = 4.0F;
    public static final float ATTACK = 6.0F;             // удар по существу
    public static final float SHIELD_BLOCK = 3.0F;       // блок щитом...
    public static final float SHIELD_PER_DAMAGE = 1.0F;  // ...плюс за каждую единицу отражённого урона
    public static final float BOW_SHOT = 8.0F;           // выстрел из лука при полном натяжении
    public static final float CROSSBOW_SHOT = 8.0F;

    // Восстановление
    public static final int REGEN_DELAY = 20;            // через секунду после последней траты
    public static final float REGEN_PER_TICK = 0.5F;     // 10 в секунду: с нуля до полной за 10 секунд
    public static final float SPRINT_MIN = 15.0F;        // начать бег можно только с 15 и выше

    // Изнеможение
    public static final int EXHAUSTED_TICKS = 40;        // 2 секунды
    public static final int SLOWNESS_LEVEL = 0;          // 0 = Замедление I
    public static final int WEAKNESS_LEVEL = 0;          // 0 = Слабость I

    private Stamina() {}

    public static float get(Player player) {
        return player.getData(ModAttachments.STAMINA).level();
    }

    public static boolean exhausted(Player player) {
        return player.getData(ModAttachments.STAMINA).exhaustedTicks() > 0;
    }

    /** Можно ли сейчас бежать. Уже бегущий бежит, пока шкала не опустеет. */
    public static boolean canSprint(Player player) {
        return !exhausted(player) && (player.isSprinting() ? get(player) > 0 : get(player) >= SPRINT_MIN);
    }

    /** Потратить выносливость. Перегруз увеличивает трату. Только на сервере. */
    public static void spend(Player player, float amount) {
        if (player.level().isClientSide || amount <= 0 || player.getAbilities().invulnerable) {
            return;
        }
        StaminaData data = player.getData(ModAttachments.STAMINA);
        if (data.exhaustedTicks > 0) {
            return;
        }
        data.level -= amount * (1.0F + player.getData(ModAttachments.OVERWEIGHT_PENALTY));
        data.regenDelay = REGEN_DELAY;
        if (data.level <= 0) {
            data.level = 0;
            exhaust(player, data);
        }
    }

    private static void exhaust(Player player, StaminaData data) {
        data.exhaustedTicks = EXHAUSTED_TICKS;
        player.setSprinting(false);
        if (player.getUseItem().getItem() instanceof ProjectileWeaponItem) {
            player.stopUsingItem();
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EXHAUSTED_TICKS, SLOWNESS_LEVEL, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EXHAUSTED_TICKS, WEAKNESS_LEVEL, false, false, true));
        PlayerVoice.cry(player, Voices.Cry.OUT_OF_BREATH); // раз на изнеможение
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        StaminaData data = player.getData(ModAttachments.STAMINA);
        double moved = Math.hypot(player.getX() - data.lastX, player.getZ() - data.lastZ);
        data.lastX = player.getX();
        data.lastZ = player.getZ();

        if (data.exhaustedTicks > 0) {
            data.exhaustedTicks--;
            return; // во время изнеможения не тратится и не восстанавливается
        }

        if (!player.isPassenger()) {
            if (player.isSprinting()) {
                spend(player, SPRINT_PER_TICK);
            } else if (player.isInWater() && !player.onGround() && moved > 0.02) {
                spend(player, SWIM_PER_TICK); // стоять в воде на месте можно — это не трата
            }
        }

        if (data.regenDelay > 0) {
            data.regenDelay--;
        } else if (data.level < MAX) {
            data.level = Math.min(data.level + REGEN_PER_TICK, MAX);
        }
    }

    // Сервер сам замечает прыжок игрока по пакету движения и вызывает jumpFromGround
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            spend(player, JUMP);
        }
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (exhausted(player)) {
            event.setCanceled(true);
            return;
        }
        spend(player, ATTACK);
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (event.getBlocked() && event.getEntity() instanceof Player player) {
            spend(player, SHIELD_BLOCK + event.getBlockedDamage() * SHIELD_PER_DAMAGE);
        }
    }

    @SubscribeEvent
    public static void onBowShot(ArrowLooseEvent event) {
        Player player = event.getEntity();
        if (exhausted(player)) {
            event.setCanceled(true);
            return;
        }
        spend(player, BOW_SHOT * BowItem.getPowerForTime(event.getCharge()));
    }

    // Заряженный арбалет стреляет сразу по ПКМ, без натяжения
    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof CrossbowItem) || !CrossbowItem.isCharged(stack)) {
            return;
        }
        Player player = event.getEntity();
        if (exhausted(player)) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            return;
        }
        spend(player, CROSSBOW_SHOT);
    }

    // В изнеможении нельзя натягивать лук и заряжать арбалет
    @SubscribeEvent
    public static void onStartUsing(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && blocksDrawing(player, event.getItem())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUsingTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity() instanceof Player player && blocksDrawing(player, event.getItem())) {
            event.setCanceled(true);
        }
    }

    private static boolean blocksDrawing(Player player, ItemStack stack) {
        return stack.getItem() instanceof ProjectileWeaponItem && exhausted(player);
    }
}
