package com.nrdhvnstd.halgventmod.client.armor;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Плавный подъём и опускание забрала — только на клиенте, у каждой сущности своё (игрок, стойка для брони).
 * Сервер хранит лишь «поднято / опущено»; здесь от прежнего положения к новому за DURATION_TICKS.
 * Время — ageInTicks отрисовки (tickCount + partialTick), поэтому поворот плавный и между тиками.
 */
final class VisorAnimation {
    private static final float DURATION_TICKS = 4.0F;

    // Сущность ушла из мира — её состояние уберёт сборщик мусора
    private static final Map<LivingEntity, VisorAnimation> STATES = new WeakHashMap<>();

    private boolean raised;
    private float from;      // положение в начале движения: 0 — опущено, 1 — поднято
    private float startTime; // ageInTicks начала движения

    private VisorAnimation(boolean raised) {
        this.raised = raised;
        this.from = raised ? 1.0F : 0.0F;
        this.startTime = Float.NEGATIVE_INFINITY; // уже на месте
    }

    /**
     * Насколько поднято забрало сейчас: 0 — опущено, 1 — поднято.
     * Сущность впервые на глазах — забрало сразу на своём месте, без движения.
     */
    static float progress(LivingEntity entity, boolean raised, float ageInTicks) {
        VisorAnimation state = STATES.get(entity);
        if (state == null) {
            STATES.put(entity, new VisorAnimation(raised));
            return raised ? 1.0F : 0.0F;
        }
        if (raised != state.raised) {
            state.from = state.at(ageInTicks); // развернулось на полпути — от текущего положения
            state.raised = raised;
            state.startTime = ageInTicks;
        }
        return state.at(ageInTicks);
    }

    private float at(float ageInTicks) {
        float to = raised ? 1.0F : 0.0F;
        float t = Mth.clamp((ageInTicks - startTime) / DURATION_TICKS, 0.0F, 1.0F);
        float eased = t * t * (3.0F - 2.0F * t); // плавно трогается и плавно останавливается
        return Mth.lerp(eased, from, to);
    }
}
