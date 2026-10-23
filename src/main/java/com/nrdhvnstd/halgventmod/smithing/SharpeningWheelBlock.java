package com.nrdhvnstd.halgventmod.smithing;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Точило. Нужно наковальне для заточки (этап GRIND). E затупившимся клинком или инструментом — подточить
 * (мини-игра, как заточка на наковальне): вернёт часть прочности — тем больше, чем чище работа, но не дальше
 * {@link #FLOOR} износа: последнее снимает только перековка на наковальне.
 */
public class SharpeningWheelBlock extends HorizontalDirectionalBlock implements Minigame.Host {
    public static final MapCodec<SharpeningWheelBlock> CODEC = simpleCodec(SharpeningWheelBlock::new);
    public static final float FLOOR = 0.15F;       // ⚠️ точило не снимает износ ниже этой доли прочности
    public static final float BASE = 0.08F;        // ⚠️ возвращает доли прочности: столько …
    public static final float PER_SCORE = 0.25F;   // … и ещё столько за счёт 100
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);

    public SharpeningWheelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Можно ли это подточить: кованое с режущей кромкой, затупившееся. */
    public static boolean sharpenable(ItemStack stack) {
        return SmithProducts.reforgeable(stack);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!sharpenable(stack)) {
            Smithing.say(player, stack.isDamaged() ? "wheel_not_blade" : "wheel_sharp");
            return ItemInteractionResult.CONSUME;
        }
        if (stack.getDamageValue() <= Math.round(stack.getMaxDamage() * FLOOR)) {
            Smithing.say(player, "wheel_reforge");
            return ItemInteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            int wear = Math.round(100F * stack.getDamageValue() / stack.getMaxDamage());
            Minigame.open(serverPlayer, pos, Minigame.SHARPEN, player.getInventory().selected, 60, wear, 0);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            Smithing.say(player, "wheel_report");
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void minigameResult(ServerPlayer player, Minigame.Session session, boolean done, List<Integer> data) {
        if (!done || player.getInventory().selected != session.product()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!sharpenable(stack)) {
            return;
        }
        int score = Minigame.score(data, 0);
        int max = stack.getMaxDamage();
        int floor = Math.round(max * FLOOR);
        int restore = Math.round(max * (BASE + PER_SCORE * score / 100F));
        stack.setDamageValue(Math.max(floor, stack.getDamageValue() - restore));
        if (score < Smithing.SCORE_POOR && stack.has(DataComponents.MAX_DAMAGE)) {
            // Пережгли кромку — металл сняли зря
            stack.set(DataComponents.MAX_DAMAGE, Math.max(1, Math.round(max * 0.98F)));
        }
        player.level().playSound(null, session.pos(), SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        Smithing.say(player, score < Smithing.SCORE_POOR ? "wheel_burnt" : "wheel_done",
                Math.round(100F * (max - stack.getDamageValue()) / max));
    }
}
