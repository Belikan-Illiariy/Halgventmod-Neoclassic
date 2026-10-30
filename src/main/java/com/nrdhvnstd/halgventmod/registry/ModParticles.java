package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Свои частицы. Рисует их клиент (client/particle), спавнит — тоже клиент, по исходу удара (ClientCombat). */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, HalgventMod.MODID);

    /** Искра от удара по металлу: раскалённая, летит, падает, гаснет (client/particle/SparkParticle). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> COMBAT_SPARK =
            PARTICLES.register("combat_spark", () -> new SimpleParticleType(true));

    private ModParticles() {}

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
