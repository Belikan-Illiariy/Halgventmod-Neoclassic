package com.nrdhvnstd.halgventmod.trade;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.menu.MarketStallMenu;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.storage.Storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Прилавок (оружейный, бронный, съестной, прочих товаров — StallKind). Ставит игрок-торговец и становится хозяином.
 * E — хозяину (и администрации) окно прилавка: товар, цены продажи и скупки по качествам, запросы.
 * Другим — подсказка, у кого покупать: напрямую с прилавка не покупают, только у НПС-торговца хозяина.
 * Сломать может только хозяин и администрация; товар тогда высыпается. Денег на прилавке нет — они у НПС-торговца.
 */
public class MarketStallBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private final StallKind kind;

    public MarketStallBlock(StallKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public StallKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new MarketStallBlock(kind, properties));
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

    // Стойка — доска у стены за блоком (спереди крюки), манекен — тонкая подставка (тело выше — отрисовка),
    // витрина и прилавок товаров — целый блок
    private static final VoxelShape RACK_NORTH = Block.box(2, 0, 10, 14, 16, 16);
    private static final VoxelShape RACK_SOUTH = Block.box(2, 0, 0, 14, 16, 6);
    private static final VoxelShape RACK_WEST = Block.box(10, 0, 2, 16, 16, 14);
    private static final VoxelShape RACK_EAST = Block.box(0, 0, 2, 6, 16, 14);
    private static final VoxelShape MANNEQUIN = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape SHOWCASE = Block.box(0, 0, 0, 16, 20, 16); // горка выше блока (модель шире — на полблока)
    private static final VoxelShape TABLE = Block.box(0, 0, 0, 16, 18, 16);    // стол со ступенями (модель — в два блока шириной)

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (kind) {
            case WEAPONS -> switch (state.getValue(FACING)) {
                case SOUTH -> RACK_SOUTH;
                case WEST -> RACK_WEST;
                case EAST -> RACK_EAST;
                default -> RACK_NORTH;
            };
            case ARMOR -> MANNEQUIN;
            case FOOD -> SHOWCASE;
            case GOODS -> TABLE;
            default -> Shapes.block();
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarketStallBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall) {
            stall.setOwner(player);
        }
    }

    /**
     * Бочонок с краном (хозяин и администрация), как бочка для выдержки: E склянкой питья — влить (склянка
     * остаётся), кувшином или корчагой с питьём — влить сколько влезет; Shift+E пустой (или с тем же питьём) кружкой,
     * кувшином, корчагой — отлить себе. Прочее — как без вещи: окно.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (kind != StallKind.TAP || !(level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall)
                || !(stall.isOwner(player) || Permissions.isAdmin(player))) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean drink = StallKind.isDrink(stack);
        boolean vessel = Pottery.drinkCapacity(stack) > 0;
        if (!drink && !vessel) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (drink) {
            if (!stall.tapAccepts(stack)) {
                player.displayClientMessage(Component.translatable("trade.halgventmod.tap.refuse"), true);
                return ItemInteractionResult.CONSUME;
            }
            FoodProperties food = stack.get(DataComponents.FOOD);
            ItemStack container = food != null ? food.usingConvertsTo().map(ItemStack::copy).orElse(ItemStack.EMPTY)
                    : stack.is(Items.POTION) ? new ItemStack(Items.GLASS_BOTTLE) : ItemStack.EMPTY;
            stall.tapAdd(stack);
            stack.consume(1, player);
            if (!container.isEmpty()) {
                Storage.addOrDrop(player.getInventory(), container);
            }
        } else if (player.isShiftKeyDown()) {
            int poured = 0;
            while (stall.tapMeasures() > 0 && Pottery.canPour(stack, stall.tapSample(), level)) {
                Pottery.pour(stack, stall.tapTake(), level);
                poured++;
            }
            if (poured == 0) {
                return ItemInteractionResult.CONSUME;
            }
        } else {
            ItemStack inside = Pottery.storedDrink(stack, level);
            int poured = 0;
            while (!inside.isEmpty() && stall.tapAccepts(inside)) {
                stall.tapAdd(inside.copyWithCount(1));
                Pottery.takeDrink(stack, level);
                inside = Pottery.storedDrink(stack, level);
                poured++;
            }
            if (poured == 0) {
                player.displayClientMessage(Component.translatable("trade.halgventmod.tap.refuse"), true);
                return ItemInteractionResult.CONSUME;
            }
        }
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 0.9F);
        player.displayClientMessage(Component.translatable("trade.halgventmod.tap.level", stall.tapMeasures(),
                MarketStallBlockEntity.TAP_CAPACITY), true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stall.isOwner(player) || Permissions.isAdmin(player)) {
            stall.giveOverflow(player);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new MarketStallMenu(id, inventory,
                        stall.stock(), pos, kind, stall), kind.title()), buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeVarInt(kind.ordinal());
                });
            }
        } else {
            player.displayClientMessage(Component.translatable("trade.halgventmod.stall.not_yours", stall.ownerName()), true);
        }
        return InteractionResult.CONSUME;
    }

    // Чужой прилавок не ломается: только хозяин и администрация
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall && stall.owner() != null
                && !stall.isOwner(player) && !Permissions.isAdmin(player)) {
            return 0.0F;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    // Сломали — товар высыпается
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MarketStallBlockEntity stall) {
            Containers.dropContents(level, pos, stall.stock());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
