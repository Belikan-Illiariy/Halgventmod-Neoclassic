package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.farming.GooseEntity;
import com.nrdhvnstd.halgventmod.npc.HalgNpc;
import com.nrdhvnstd.halgventmod.npc.SeatEntity;

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

    // НПС — стражник, торговец, горожанин (роль внутри сущности); размер как у игрока
    public static final DeferredHolder<EntityType<?>, EntityType<HalgNpc>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.of(HalgNpc::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F).ridingOffset(-0.6F)
                    .clientTrackingRange(10).build("npc"));

    // Невидимое сиденье НПС на стуле (npc/SeatEntity)
    public static final DeferredHolder<EntityType<?>, EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC).sized(0.1F, 0.1F)
                    .noSummon().clientTrackingRange(8).updateInterval(20).build("seat"));

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        modEventBus.addListener(ModEntities::attributes);
        modEventBus.addListener(ModEntities::spawnPlacements);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(GOOSE.get(), Chicken.createAttributes().build());
        event.put(NPC.get(), HalgNpc.createAttributes().build());
    }

    private static void spawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(GOOSE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Animal::checkAnimalSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
