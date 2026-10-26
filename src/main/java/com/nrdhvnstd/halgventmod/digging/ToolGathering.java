package com.nrdhvnstd.halgventmod.digging;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Добыча инструментом по ПКМ, блок при этом остаётся — песок, глина и деревья не кончаются:
 * <ul>
 *   <li>лопата (ModItems.SHOVEL) по песку — комок песка, по глине — комок глины;</li>
 *   <li>топор (ModItems.AXE) по дереву (тег logs) — бревно того же блока.</li>
 * </ul>
 * Добытое ложится в инвентарь, а если места нет — падает над блоком.
 * Действие предмета через событие, а не useOn: так оно работает и в режиме приключения.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class ToolGathering {
    // Меняйте здесь
    public static final int SHOVEL_COOLDOWN_TICKS = 10; // пауза между копками ⚠️
    public static final int AXE_COOLDOWN_TICKS = 20;    // пауза между рубками ⚠️

    private ToolGathering() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack tool = event.getItemStack();
        if (player.isSpectator()) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack yield;
        SoundEvent sound;
        int cooldown;
        if (tool.is(ModItems.SHOVEL.get())) {
            yield = lumpOf(state);
            sound = SoundEvents.SHOVEL_FLATTEN;
            cooldown = SHOVEL_COOLDOWN_TICKS;
        } else if (tool.is(ModItems.AXE.get())) {
            yield = state.is(BlockTags.LOGS) ? new ItemStack(state.getBlock().asItem()) : ItemStack.EMPTY;
            sound = SoundEvents.AXE_STRIP;
            cooldown = AXE_COOLDOWN_TICKS;
        } else {
            return;
        }
        if (yield.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (player.getCooldowns().isOnCooldown(tool.getItem())) {
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        player.swing(event.getHand());
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) {
            return;
        }
        // Частицы и звук ломки блока (песок шуршит, глина чавкает, дерево трещит) — и звук самого инструмента
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        player.getInventory().add(yield);
        if (!yield.isEmpty()) {
            Block.popResourceFromFace(level, pos, Direction.UP, yield);
        }
        player.getCooldowns().addCooldown(tool.getItem(), cooldown);
        damage(player, event.getHand(), tool);
    }

    /** Что вскапывается лопатой из блока; пусто — его не копают. */
    private static ItemStack lumpOf(BlockState state) {
        if (state.is(Blocks.CLAY)) {
            return new ItemStack(Items.CLAY_BALL);
        }
        if (state.is(BlockTags.SAND)) {
            return new ItemStack(ModItems.SAND_LUMP.get());
        }
        return ItemStack.EMPTY;
    }

    private static void damage(Player player, InteractionHand hand, ItemStack tool) {
        if (!player.getAbilities().instabuild) {
            tool.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
    }
}
