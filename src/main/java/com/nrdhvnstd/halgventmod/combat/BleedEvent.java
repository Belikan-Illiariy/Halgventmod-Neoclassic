package com.nrdhvnstd.halgventmod.combat;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * Удар открыл кровотечение (ТЗ боя, 7.4): лёгкое (−0.05 HP/с) или сильное (−0.2 HP/с). Пока только событие на
 * NeoForge.EVENT_BUS — само кровотечение, лечение и брызги — отдельная задача.
 */
public class BleedEvent extends LivingEvent {
    private final boolean severe;

    public BleedEvent(LivingEntity entity, boolean severe) {
        super(entity);
        this.severe = severe;
    }

    public boolean severe() {
        return severe;
    }
}
