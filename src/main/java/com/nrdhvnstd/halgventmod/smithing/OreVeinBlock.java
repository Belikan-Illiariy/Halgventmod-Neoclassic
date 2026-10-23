package com.nrdhvnstd.halgventmod.smithing;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Рудная жила или болотный ил: блок не ломается — по нему бьют киркой (лопатой) ЛКМ, и он отдаёт куски руды
 * (OreMining). {@link #RICHNESS}: 3 — богатая, 2 — средняя, 1 — бедная, 0 — пустая; со временем ступени
 * возвращаются по календарю мира — за {@link OreKind#regrow} суток на ступень ({@link VeinClock}). Ставят строители.
 */
public class OreVeinBlock extends Block {
    public static final IntegerProperty RICHNESS = IntegerProperty.create("richness", 0, 3);
    public static final MapCodec<OreVeinBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            StringRepresentable.fromEnum(OreKind::values).fieldOf("kind").forGetter(OreVeinBlock::kind),
            propertiesCodec()
    ).apply(instance, OreVeinBlock::new));

    private final OreKind kind;

    public OreVeinBlock(OreKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(RICHNESS, 3));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public OreKind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RICHNESS);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(RICHNESS) < 3;
    }

    /** Случайный тик — только чтобы загруженная жила догнала календарь на глазах; счёт — в VeinClock. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        VeinClock.catchUp(level, pos, state, kind.regrow());
    }

    /** Обеднела или вернула ступень — проверить к сроку следующей (VeinClock.schedule). */
    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState oldState,
                           boolean moved) {
        if (level instanceof ServerLevel server && state.getValue(RICHNESS) < 3) {
            VeinClock.schedule(server, pos, this);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        VeinClock.tick(level, pos, state, kind.regrow());
    }

    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState,
                            boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            VeinClock.forget(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
