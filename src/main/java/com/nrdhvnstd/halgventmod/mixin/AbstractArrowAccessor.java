package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;

/** Ручной подбор стрел (Pickup). */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Accessor("inGround")
    boolean halgventmod$inGround();

    /** Ванильная проверка «можно ли подобрать» и сам подбор через Inventory.add. */
    @Invoker("tryPickup")
    boolean halgventmod$tryPickup(Player player);
}
