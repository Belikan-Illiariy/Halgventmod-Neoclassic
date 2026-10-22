package com.nrdhvnstd.halgventmod.cooking;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Крюк для копчения и сушки: висит под потолком или балкой. Пустой — цепь; с мясом или рыбой — связка колбас,
 * с травами и грибами — пучок трав (модели Conquest Reforged). Мясо коптится, если ниже горит очаг или костёр.
 */
public class HookBlock extends StationBlock {
    public static final MapCodec<HookBlock> CODEC = simpleCodec(HookBlock::new);
    public static final EnumProperty<Load> LOAD = EnumProperty.create("load", Load.class);
    private static final VoxelShape SHAPE = Block.box(5, 2, 5, 11, 16, 11);

    public enum Load implements StringRepresentable {
        EMPTY, SMOKING, DRYING;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public HookBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LOAD, Load.EMPTY));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOAD);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HookBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.SMOKING_HOOK.get(), HookBlockEntity::serverTick);
    }

    /** Дым вокруг коптящегося. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LOAD) == Load.SMOKING && HookBlockEntity.smoky(level, pos) && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.2,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
        }
    }
}
