package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.weight.OverweightEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Эффекты. Значок: assets/halgventmod/textures/mob_effect/<id>.png, 18×18. */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, HalgventMod.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> BURDEN =
            EFFECTS.register("burden", () -> new OverweightEffect(0x8B7355));      // Обуза
    public static final DeferredHolder<MobEffect, MobEffect> ENCUMBERED =
            EFFECTS.register("encumbered", () -> new OverweightEffect(0x6B4F2F));  // Скованность
    public static final DeferredHolder<MobEffect, MobEffect> OVERLOADED =
            EFFECTS.register("overloaded", () -> new OverweightEffect(0x4A2A1A));  // Тяжесть

    private ModEffects() {}

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}