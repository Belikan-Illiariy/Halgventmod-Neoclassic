package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.farming.GooseEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Свои звери. Где водятся — data/halgventmod/neoforge/biome_modifier, добыча — loot_table/entities. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, HalgventMod.MODID);

    // Гусь — крупнее курицы
    public static final DeferredHolder<EntityType<?>, EntityType<GooseEntity>> GOOSE = ENTITIES.register("goose",
            () -> EntityType.Builder.of(GooseEntity::new, MobCategory.CREATURE).sized(0.55F, 0.9F).eyeHeight(0.8F)
                    .clientTrackingRange(10).build("goose"));

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        modEventBus.addListener(ModEntities::attributes);
        modEventBus.addListener(ModEntities::spawnPlacements);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(GOOSE.get(), Chicken.createAttributes().build());
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(GOOSE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Animal::checkAnimalSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
