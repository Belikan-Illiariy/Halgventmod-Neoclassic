package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.nrdhvnstd.halgventmod.farming.FarmingRules;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(FarmBlock.class)
public abstract class FarmBlockMixin {

    // Сухая пашня без воды и без растения остаётся пашней (правило farmlandDriesToDirt).
    // Вытаптывание и блок, поставленный сверху, по-прежнему превращают её в землю.
    @WrapWithCondition(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/FarmBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private boolean halgventmod$keepDryFarmland(Entity entity, BlockState state, Level level, BlockPos pos) {
        return FarmingRules.farmlandDriesToDirt(level);
    }
}
