package com.nrdhvnstd.halgventmod.food;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Жажда 0–100: трата и питьё из воды. Здоровье не отнимает (docs/TZ_downed_and_hp.md, 2): при нуле — эффект «Жажда»
 * (Starvation). На клиент её отправляет NeedsSync.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Thirst {
    // ===== Правила — меняйте здесь =====
    public static final int MAX = ThirstData.MAX;

    // Пассивная трата, даже когда игрок стоит: единиц за игровые сутки. Длина суток — в DayLength.
    public static final float PASSIVE_PER_DAY = 40;

    public static final float EXHAUSTION_PER_POINT = Hunger.EXHAUSTION_PER_POINT; // как у голода
    public static final float THIRST_MULTIPLIER = 1.0F;     // жажда от действий: 1.5 — в полтора раза быстрее голода
    public static final int NO_SPRINT_AT = 30;              // при 30 и ниже бегать нельзя, как с голодом

    public static final int WATER_SIP = 10;                 // глоток из воды
    public static final int WATER_SIP_COOLDOWN = 10;        // тиков между глотками

    private Thirst() {}

    public static int get(Player player) {
        return player.getData(ModAttachments.THIRST).level();
    }

    public static boolean needs(Player player) {
        return get(player) < MAX;
    }

    /** Изменить жажду: + утолить, − усилить. Только на сервере. */
    public static void add(Player player, int amount) {
        if (player.level().isClientSide) {
            return;
        }
        ThirstData data = player.getData(ModAttachments.THIRST);
        data.level = Mth.clamp(data.level + amount, 0, MAX);
    }

    /** Вызывается из PlayerMixin на каждую трату голода. */
    public static void addExhaustion(Player player, float amount) {
        ThirstData data = player.getData(ModAttachments.THIRST);
        data.exhaustion = Math.min(data.exhaustion + amount * THIRST_MULTIPLIER, 40.0F);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ThirstData data = player.getData(ModAttachments.THIRST);

        // Пассивная трата раз в секунду, перегруз её увеличивает
        if (player.tickCount % 20 == 0 && !player.getAbilities().invulnerable) {
            float overweight = 1.0F + player.getData(ModAttachments.OVERWEIGHT_PENALTY);
            data.exhaustion = Math.min(data.exhaustion + Hunger.perSecond(PASSIVE_PER_DAY) * overweight, 40.0F);
        }

        // Накопленная усталость превращается в потерю жажды
        if (data.exhaustion >= EXHAUSTION_PER_POINT) {
            data.exhaustion -= EXHAUSTION_PER_POINT;
            data.level = Math.max(data.level - 1, 0);
        }

        // На мирной сложности жажда восстанавливается сама, как голод
        if (player.level().getDifficulty() == Difficulty.PEACEFUL
                && player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)
                && player.tickCount % 10 == 0 && data.level < MAX) {
            data.level = Math.min(data.level + Hunger.VANILLA_SCALE, MAX);
        }
    }

    /** Глоток из блока воды. Вызывается по пакету от клиента, всё перепроверяем на сервере. */
    public static void drinkFromWater(ServerPlayer player) {
        ThirstData data = player.getData(ModAttachments.THIRST);
        if (!player.getMainHandItem().isEmpty() || !needs(player)
                || player.tickCount - data.lastDrinkTick < WATER_SIP_COOLDOWN) {
            return;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, true);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        var pos = ((BlockHitResult) hit).getBlockPos();
        if (!player.level().getBlockState(pos).is(Blocks.WATER)
                || !player.level().getFluidState(pos).is(FluidTags.WATER)) {
            return;
        }
        data.lastDrinkTick = player.tickCount;
        add(player, WATER_SIP);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.5F, 1.0F);
    }
}
