package com.nrdhvnstd.halgventmod.sound;

import com.mojang.serialization.MapCodec;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.client.sound.SoundClient;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Невидимый источник звуков места (З9), как ванильный блок света: не рисуется, сквозь него ходят.
 * Навести, сломать и настроить (E с предметом в руке, администрация в творческом) можно, только держа его предмет.
 * Серверного тикера нет: звуки целиком на клиенте (client/sound/SoundEmitters).
 */
public class SoundEmitterBlock extends BaseEntityBlock {
    public static final MapCodec<SoundEmitterBlock> CODEC = simpleCodec(SoundEmitterBlock::new);

    public SoundEmitterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /** Кто настраивает источник: администрация в творческом режиме. */
    public static boolean mayEdit(Player player) {
        return Permissions.isAdmin(player) && player.isCreative();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context.isHoldingItem(ModItems.SOUND_EMITTER.get()) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SoundEmitterBlockEntity(pos, state);
    }

    // E по источнику с его предметом в руке — окно настройки (сохраняет сервер, SoundEmitterEditPayload)
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.SOUND_EMITTER.get()) || !mayEdit(player)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide && level.getBlockEntity(pos) instanceof SoundEmitterBlockEntity emitter) {
            SoundClient.openScreen(emitter);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
