package com.nrdhvnstd.halgventmod.smithing;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Золотоносный песок (речная россыпь), ставят строители. E лотком — промывать (мини-игра, PanStep): сколько
 * золотого песка выйдет — от богатства россыпи и чистоты промывки. После промывки россыпь может обеднеть
 * ({@link #DEPLETE}); намывается снова по календарю мира, как жила ({@link #REGROW} суток на ступень, {@link VeinClock}).
 */
public class GoldPlacerBlock extends Block implements Minigame.Host {
    public static final MapCodec<GoldPlacerBlock> CODEC = simpleCodec(GoldPlacerBlock::new);
    public static final float DEPLETE = 0.35F;   // ⚠️
    public static final float REGROW = 3.0F;     // ⚠️ суток по календарю на ступень в среднем (VeinClock, ± половина)
    public static final int MIN_TICKS = 160;
    public static final float NUGGET = 0.06F;    // ⚠️ шанс самородка за ступень богатства
    public static final int NUGGET_DUST = 2;     // золотого песка за самородок

    public GoldPlacerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OreVeinBlock.RICHNESS, 3));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OreVeinBlock.RICHNESS);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(OreVeinBlock.RICHNESS) < 3;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        VeinClock.catchUp(level, pos, state, REGROW);
    }

    /** Обеднела или вернула ступень — проверить к сроку следующей (VeinClock.schedule). */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (level instanceof ServerLevel server && state.getValue(OreVeinBlock.RICHNESS) < 3) {
            VeinClock.schedule(server, pos, this);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        VeinClock.tick(level, pos, state, REGROW);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            VeinClock.forget(server, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(SmithingItems.GOLD_PAN.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        state = VeinClock.catchUp((ServerLevel) level, pos, state, REGROW);
        int richness = state.getValue(OreVeinBlock.RICHNESS);
        if (richness <= 0) {
            Smithing.say(player, "placer_empty", VeinClock.until((ServerLevel) level, pos));
        } else if (player instanceof ServerPlayer serverPlayer) {
            // Самородок решает сервер: окно лишь показывает его
            boolean nugget = level.random.nextFloat() < richness * NUGGET;
            Minigame.open(serverPlayer, pos, Minigame.PAN_PLACER, 0, MIN_TICKS, richness, nugget ? 2 : 0);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            state = VeinClock.catchUp((ServerLevel) level, pos, state, REGROW);
            Smithing.say(player, "placer_report_" + state.getValue(OreVeinBlock.RICHNESS));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void minigameResult(ServerPlayer player, Minigame.Session session, boolean done, List<Integer> data) {
        ItemStack pan = player.getMainHandItem();
        BlockState state = player.level().getBlockState(session.pos());
        if (!done || !pan.is(SmithingItems.GOLD_PAN.get()) || !state.is(this)) {
            return;
        }
        int richness = state.getValue(OreVeinBlock.RICHNESS);
        int kept = Minigame.score(data, 0);   // сколько золота осталось в лотке, %
        int washed = Minigame.score(data, 1); // насколько отмыт песок, %
        // Богатая россыпь — до трёх-четырёх крупинок за промывку ⚠️
        float grains = richness * 1.2F * kept / 100F * (0.4F + 0.6F * washed / 100F);
        int dust = (int) grains + (player.getRandom().nextFloat() < grains - (int) grains ? 1 : 0);
        boolean nugget = (session.extra() & 2) != 0 && Minigame.value(data, 2, 0) == 1;
        if (nugget) {
            dust += NUGGET_DUST;
            Smithing.tell(player, "panned_nugget");
        }
        if (!player.getAbilities().instabuild) {
            pan.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        if (dust > 0) {
            ItemStack gold = new ItemStack(SmithingItems.GOLD_DUST.get(), dust);
            player.getInventory().add(gold);
            if (!gold.isEmpty()) {
                player.drop(gold, false);
            }
            Smithing.say(player, "panned", dust);
        } else {
            Smithing.say(player, "panned_nothing");
        }
        player.level().playSound(null, session.pos(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6F, 1.2F);
        if (player.getRandom().nextFloat() < DEPLETE) {
            player.level().setBlock(session.pos(), state.setValue(OreVeinBlock.RICHNESS, richness - 1), 3);
            VeinClock.depleted(player.serverLevel(), session.pos(), REGROW);
        }
    }
}
