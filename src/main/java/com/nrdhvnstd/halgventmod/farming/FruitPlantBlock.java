package com.nrdhvnstd.halgventmod.farming;

import java.util.function.Supplier;

import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Плодоносящее растение: ягодный куст или плодовая листва. Само зреет (случайные тики, при свете, кроме зимы);
 * спелое — E пустой рукой: плоды падают, растение начинает зреть заново. Работает и в режиме приключения —
 * это использование блока. Ставят его строители (и находится в мире — кусты).
 */
public abstract class FruitPlantBlock extends Block {
    private final Supplier<? extends Item> fruit;
    private final int minFruit;
    private final int maxFruit;

    protected FruitPlantBlock(Properties properties, Supplier<? extends Item> fruit, int minFruit, int maxFruit) {
        super(properties);
        this.fruit = fruit;
        this.minFruit = minFruit;
        this.maxFruit = maxFruit;
    }

    protected abstract IntegerProperty age();

    protected abstract int maxAge();

    /** До какой стадии откатывается после сбора. */
    protected abstract int pickedAge();

    /** Шанс подрасти за случайный тик. */
    protected abstract float growChance();

    public boolean ripe(BlockState state) {
        return state.getValue(age()) >= maxAge();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !ripe(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Calendar.date(Calendar.today(level)).season() == Calendar.Season.WINTER) {
            return; // зимой не зреет
        }
        if (level.getRawBrightness(pos, 0) >= 9 && random.nextFloat() < growChance()) {
            level.setBlock(pos, state.setValue(age(), state.getValue(age()) + 1), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!ripe(state)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.halgventmod.farming.not_ripe"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            int count = minFruit + level.random.nextInt(maxFruit - minFruit + 1);
            popResource(level, pos, new ItemStack(fruit.get(), count));
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F,
                    0.8F + level.random.nextFloat() * 0.4F);
            level.setBlock(pos, state.setValue(age(), pickedAge()), Block.UPDATE_CLIENTS);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
