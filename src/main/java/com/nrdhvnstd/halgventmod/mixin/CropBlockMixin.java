package com.nrdhvnstd.halgventmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.nrdhvnstd.halgventmod.farming.FarmingRules;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(CropBlock.class)
public abstract class CropBlockMixin {

    // Скорость роста: сухая пашня считается сырой, если выключено правило cropsNeedWater.
    // Тем же методом считается рост стеблей тыквы и арбуза.
    @WrapOperation(method = "getGrowthSpeed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;isFertile(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean halgventmod$fertileWhenDry(BlockState soil, BlockGetter level, BlockPos pos, Operation<Boolean> original) {
        if (soil.getBlock() instanceof FarmBlock && !FarmingRules.cropsNeedWater(level)) {
            return true;
        }
        return original.call(soil, level, pos);
    }
}
