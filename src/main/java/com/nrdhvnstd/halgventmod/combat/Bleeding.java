package com.nrdhvnstd.halgventmod.combat;

import java.util.Set;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.downed.Downed;
import com.nrdhvnstd.halgventmod.registry.ModEffects;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.EffectCure;

/**
 * Кровотечение (эффект halgventmod:bleeding): рубящая и режущая рана открывает его с шансом оружия (BleedEvent,
 * Combat.landed), сильная — сразу на две ступени; новые раны добавляют ступени (до {@link #MAX_LEVEL}). Каждая ступень —
 * {@link #DAMAGE_PER_LEVEL} здоровья в секунду мимо брони; само не останавливается — нужна повязка (Injuries.treat,
 * себе или другому), иначе смерть (игрок сначала «Повержен», и лёжа уже не истекает). Молоко не помогает. С тела капает кровь.
 * НПС перевязываются сами, когда бой кончился (HalgNpc).
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Bleeding {
    // Меняйте здесь ⚠️
    public static final float DAMAGE_PER_LEVEL = 0.5F;   // здоровья в секунду за ступень (здоровье — 100)
    public static final int MAX_LEVEL = 5;               // до 2,5 в секунду
    public static final int SEVERE_LEVELS = 2;

    private Bleeding() {}

    /** Эффект: тикает раз в секунду, уровень — ступени − 1. */
    public static class Effect extends MobEffect {
        public Effect() {
            super(MobEffectCategory.HARMFUL, 0x8A0A0A,
                    new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.NETHER_WART_BLOCK.defaultBlockState()));
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 20 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            // «Повержен» — у него своё время до смерти, кровь не добивает
            if (!entity.level().isClientSide && !(entity instanceof Player player && Downed.isDowned(player))) {
                entity.hurt(CombatDamageTypes.bleeding(entity), DAMAGE_PER_LEVEL * (amplifier + 1));
            }
            return true;
        }

        @Override
        public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance effectInstance) {
            // молоко и мёд не останавливают — только повязка
        }
    }

    public static int level(LivingEntity entity) {
        MobEffectInstance instance = entity.getEffect(ModEffects.BLEEDING);
        return instance == null ? 0 : instance.getAmplifier() + 1;
    }

    /** Открыть или усилить кровотечение на levels ступеней. */
    public static void open(LivingEntity entity, int levels) {
        if (!Blood.bleeds(entity) || levels <= 0) {
            return;
        }
        int before = level(entity);
        int after = Math.min(MAX_LEVEL, before + levels);
        if (after == before) {
            return;
        }
        entity.addEffect(new MobEffectInstance(ModEffects.BLEEDING, MobEffectInstance.INFINITE_DURATION, after - 1,
                false, true, true));
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(before == 0 ? "message.halgventmod.bleeding.start"
                    : "message.halgventmod.bleeding.worse"), true);
        }
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.NETHER_WART_BLOCK.defaultBlockState()),
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 6, 0.2, 0.2, 0.2, 0);
        }
    }

    /** Перевязать: кровотечение остановлено. true — было. */
    public static boolean stop(LivingEntity entity) {
        if (level(entity) == 0) {
            return false;
        }
        entity.removeEffect(ModEffects.BLEEDING);
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WOOL_PLACE,
                SoundSource.PLAYERS, 0.8F, 1.2F);
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable("message.halgventmod.bleeding.stopped"), true);
        }
        return true;
    }

    // Рана с кровотечением (Combat.landed)
    @SubscribeEvent
    public static void onBleed(BleedEvent event) {
        open(event.getEntity(), event.severe() ? SEVERE_LEVELS : 1);
    }
}
