package com.nrdhvnstd.halgventmod.farming;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.registry.ModEntities;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.Level;

/**
 * Гусь: ведёт себя как курица (бродит, пугается, несёт яйца, разводится зерном), но крупнее и живёт у воды —
 * у рек, болот и на берегах. Добыча — гусятина и перья (loot_table/entities/goose).
 */
public class GooseEntity extends Chicken {
    public GooseEntity(EntityType<? extends Chicken> type, Level level) {
        super(type, level);
    }

    @Nullable
    @Override
    public Chicken getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return ModEntities.GOOSE.get().create(level);
    }
}
