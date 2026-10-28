package com.nrdhvnstd.halgventmod.digging;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.pickup.Pickup;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Добыча инструментом на E (ЛКМ и ПКМ отданы бою, docs/TZ_chivalry_combat.md), блок при этом остаётся — песок, глина и деревья не кончаются:
 * <ul>
 *   <li>лопата (ModItems.SHOVEL) по песку — комок песка, по глине — комок глины;</li>
 *   <li>топор (ModItems.AXE) по дереву (тег logs) — бревно того же блока.</li>
 * </ul>
 * Добытое ложится в инвентарь, а если места нет — падает над блоком.
 * Действие предмета через событие, а не useOn: так оно работает и в режиме приключения. E по блоку — это использование
 * блока с отметкой Pickup.isUseKey (PickupInput.useBlock); простой ПКМ инструментом по блоку ничего не добывает.
 * Подсказку «Рубить: E» / «Копать: E» рисует UseHint (выбор цели — Interaction через gatherable).
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class ToolGathering {
    // Меняйте здесь
    public static final int SHOVEL_COOLDOWN_TICKS = 10; // пауза между копками ⚠️
    public static final int AXE_COOLDOWN_TICKS = 20;    // пауза между рубками ⚠️

    private ToolGathering() {}

    /** Что даст E этим инструментом по этому блоку: пусто — ничего (для подсказки и выбора цели E). */
    public static ItemStack gatherable(ItemStack tool, BlockState state) {
        if (tool.is(ModItems.SHOVEL.get())) {
            return lumpOf(state);
        }
        if (tool.is(ModItems.AXE.get())) {
            return state.is(BlockTags.LOGS) ? new ItemStack(state.getBlock().asItem()) : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    /** Ключ действия для подсказки: chop или dig. */
    public static String action(ItemStack tool) {
        return tool.is(ModItems.AXE.get()) ? "chop" : "dig";
    }

    // Раньше Pickup.onRightClickBlock: он снимает отметку E
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        ItemStack tool = event.getItemStack();
        if (player.isSpectator() || event.getHand() != InteractionHand.MAIN_HAND || !Pickup.isUseKey(player)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack yield = gatherable(tool, state);
        if (yield.isEmpty()) {
            return;
        }
        boolean shovel = tool.is(ModItems.SHOVEL.get());
        SoundEvent sound = shovel ? SoundEvents.SHOVEL_FLATTEN : SoundEvents.AXE_STRIP;
        int cooldown = shovel ? SHOVEL_COOLDOWN_TICKS : AXE_COOLDOWN_TICKS;
        Pickup.clearUseKey(player);
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
