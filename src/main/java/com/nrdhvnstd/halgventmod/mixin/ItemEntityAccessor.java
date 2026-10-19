package com.nrdhvnstd.halgventmod.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.item.ItemEntity;

/** Ручной подбор (Pickup): задержка подбора и «только для этого игрока». */
@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
    @Accessor("pickupDelay")
    int halgventmod$pickupDelay();

    @Accessor("target")
    UUID halgventmod$target();
}
