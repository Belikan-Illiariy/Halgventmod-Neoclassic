package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.needs.Health;
import com.nrdhvnstd.halgventmod.weight.Encumbrance;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Атрибуты. Грузоподъёмность — атрибут, чтобы потом её могли менять рюкзаки, сумки, зелья. */
public final class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, HalgventMod.MODID);

    // Грузоподъёмность в килограммах
    public static final DeferredHolder<Attribute, Attribute> CARRY_CAPACITY = ATTRIBUTES.register("carry_capacity",
            () -> new RangedAttribute("attribute.name.halgventmod.carry_capacity",
                    Encumbrance.BASE_CAPACITY_KG, 0.0, 10000.0).setSyncable(true));

    private ModAttributes() {}

    public static void register(IEventBus modEventBus) {
        ATTRIBUTES.register(modEventBus);
        modEventBus.addListener(ModAttributes::addToPlayer);
    }

    private static void addToPlayer(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, CARRY_CAPACITY);
        event.add(EntityType.PLAYER, Attributes.MAX_HEALTH, Health.MAX); // здоровье 100 вместо 20
    }
}