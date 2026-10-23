package com.nrdhvnstd.halgventmod.smithing;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Угольная куча: {@link #PILE} — что видно: 0 — пустое место, 1 — сложены дрова, 2 — обсыпана землёй (тлеет —
 * дымит), 3 — вскрыта, лежит уголь.
 */
public class CharcoalPileBlock extends SmithStationBlock.Lit {
    public static final IntegerProperty PILE = IntegerProperty.create("pile", 0, 3);

    public CharcoalPileBlock(Properties properties) {
        super(properties, Block.box(0, 0, 0, 16, 12, 16), CharcoalPileBlockEntity::new,
                () -> SmithingBlocks.CHARCOAL_PILE_ENTITY.get());
        registerDefaultState(defaultBlockState().setValue(PILE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PILE);
    }
}
