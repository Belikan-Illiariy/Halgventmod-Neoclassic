package com.nrdhvnstd.halgventmod.herbs;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.leather.LeatherTableBlockEntity;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Кустик травы (модели Conquest Reforged): ромашка, мята, зверобой… — свой блок у каждого вида ({@link Herb}).
 * Ставят строители. E ножом, серпом или косой по выросшему — срезать: трава в инвентарь, кустик срезан (низкая
 * травка) и отрастает сам — {@link #STAGES} ступени по случайным тикам при свете, кроме зимы (в среднем около
 * полутора суток ⚠️). Срезанный, пустой рукой или не тем — только подсказка. Работает и в режиме приключения:
 * это использование блока, а не поломка.
 */
public class HerbPlantBlock extends Block {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    // Меняйте здесь ⚠️
    public static final int STAGES = 3;                 // 0 — только что срезан, 3 — вырос
    public static final float GROW_CHANCE = 0.025F;     // за случайный тик: ступень — около 12 ч, весь кустик — 1,5 суток
    public static final int KNIFE_MIN = 1, KNIFE_MAX = 2; // ножом срезают столько
    public static final int SICKLE_MIN = 2, SICKLE_MAX = 3; // серпом и косой
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);

    private final Herb herb;
    private final MapCodec<HerbPlantBlock> codec;

    public HerbPlantBlock(Properties properties, Herb herb) {
        super(properties);
        this.herb = herb;
        this.codec = simpleCodec(p -> new HerbPlantBlock(p, herb));
        registerDefaultState(stateDefinition.any().setValue(AGE, STAGES));
    }

    public Herb herb() {
        return herb;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    public static boolean grown(BlockState state) {
        return state.getValue(AGE) >= STAGES;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return !grown(state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Calendar.date(Calendar.today(level)).season() == Calendar.Season.WINTER) {
            return; // зимой не отрастает
        }
        if (level.getRawBrightness(pos, 0) >= 9 && random.nextFloat() < GROW_CHANCE) {
            level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!HerbGathering.isCutter(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!grown(state)) {
            say(player, "regrowing", Component.translatable(herb.item().getDescriptionId()));
            return ItemInteractionResult.CONSUME;
        }
        boolean knife = stack.is(LeatherTableBlockEntity.KNIVES);
        int count = knife ? KNIFE_MIN + level.random.nextInt(KNIFE_MAX - KNIFE_MIN + 1)
                : SICKLE_MIN + level.random.nextInt(SICKLE_MAX - SICKLE_MIN + 1);
        ItemStack found = new ItemStack(herb.item(), count);
        Component name = found.getHoverName();
        player.getInventory().add(found);
        if (!found.isEmpty()) {
            player.drop(found, false);
        }
        if (!player.getAbilities().instabuild && stack.isDamageableItem()) {
            stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        player.swing(hand, true);
        level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.6F, 1.3F + level.random.nextFloat() * 0.2F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.4,
                    pos.getZ() + 0.5, 10, 0.25, 0.2, 0.25, 0.05);
        }
        say(player, "cut", count, name);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (grown(state)) {
                say(player, "need_cutter", Component.translatable(herb.item().getDescriptionId()));
            } else {
                say(player, "regrowing", Component.translatable(herb.item().getDescriptionId()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos); // кустики чуть сдвинуты в клетке, как цветы
        return SHAPE.move(offset.x, offset.y, offset.z);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.is(BlockTags.DIRT) || below.is(BlockTags.SAND);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    private static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.herbs." + key, args), true);
    }
}
