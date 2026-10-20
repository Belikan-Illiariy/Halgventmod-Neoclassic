package com.nrdhvnstd.halgventmod.leather;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Откуда берётся сырьё кожевника — всё в мире, без окон:
 * <ul>
 *   <li>шкура — со зверя из {@link Animals} вместо ванильной кожи; всегда; чистое убийство (≤{@link #CLEAN_KILL_HITS} удара) — качество +1,
 *       искромсанный зверь (≥{@link #MANGLED_HITS} ударов) — кусок кожи меньше;</li>
 *   <li>кора — ПКМ топором по бревну (обдирает его, как в ваниле);</li>
 *   <li>зола — ПКМ лопатой по горящему костру (тушит его);</li>
 *   <li>квасцы — из кальцита.</li>
 * </ul>
 * Топор и лопата — действие предмета (ПКМ), работают и в режиме приключения.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class LeatherEvents {
    // Меняйте здесь
    public static final int CLEAN_KILL_HITS = 2;
    public static final int MANGLED_HITS = 6;
    public static final float EXTRA_ALUM_CHANCE = 0.5F;
    private static final String HITS = HalgventMod.MODID + ":hits";

    private LeatherEvents() {}

    // ---------------------------------------------------------------- шкуры

    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide && Animals.get(entity.getType()) != null) {
            entity.getPersistentData().putInt(HITS, entity.getPersistentData().getInt(HITS) + 1);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        Animals.Kind kind = Animals.get(entity.getType());
        if (kind == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        // Ванильная кожа и кроличья шкурка — больше не падают: вместо них парная шкура
        event.getDrops().removeIf(drop -> drop.getItem().is(Items.LEATHER) || drop.getItem().is(Items.RABBIT_HIDE));
        if (entity instanceof AgeableMob mob && mob.isBaby()) {
            return;
        }
        int pieces = kind.pieces();
        int quality = kind.baseQuality();
        int hits = entity.getPersistentData().getInt(HITS);
        if (hits > 0 && hits <= CLEAN_KILL_HITS) {
            quality++;
        } else if (hits >= MANGLED_HITS) {
            pieces--;
        }
        ItemStack hide = new ItemStack(ModItems.RAW_HIDE.get());
        hide.set(ModComponents.HIDE.get(), new Hide(kind.id(), Math.max(1, pieces), kind.fur(), false));
        LeatherQuality.set(hide, quality);
        event.getDrops().add(new ItemEntity(level, entity.getX(), entity.getY() + 0.3, entity.getZ(), hide));
    }

    // ---------------------------------------------------------------- кора и зола

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack tool = event.getItemStack();
        if (tool.isEmpty() || player.isSpectator()) {
            return;
        }
        // Лопатой по горящему костру — выгрести золу
        if (CampfireBlock.isLitCampfire(state) && tool.canPerformAction(ItemAbilities.SHOVEL_DOUSE)) {
            if (!level.isClientSide) {
                CampfireBlock.dowse(player, level, pos, state);
                level.setBlock(pos, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL_IMMEDIATE);
                level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
                Block.popResource(level, pos.above(), new ItemStack(ModItems.ASH.get(), 1 + level.random.nextInt(2)));
                damage(player, event.getHand(), tool);
            }
            finish(event, level);
            return;
        }
        // Топором по бревну — ободрать кору
        if (state.is(BlockTags.LOGS) && tool.canPerformAction(ItemAbilities.AXE_STRIP)) {
            UseOnContext context = new UseOnContext(player, event.getHand(), event.getHitVec());
            BlockState stripped = state.getToolModifiedState(context, ItemAbilities.AXE_STRIP, false);
            if (stripped == null || stripped == state) {
                return;
            }
            if (!level.isClientSide) {
                level.setBlock(pos, stripped, Block.UPDATE_ALL_IMMEDIATE);
                level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
                Block.popResourceFromFace(level, pos, event.getFace(), new ItemStack(ModItems.BARK.get()));
                damage(player, event.getHand(), tool);
            }
            finish(event, level);
        }
    }

    private static void finish(PlayerInteractEvent.RightClickBlock event, Level level) {
        event.getEntity().swing(event.getHand());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }

    private static void damage(Player player, InteractionHand hand, ItemStack tool) {
        if (!player.getAbilities().instabuild && tool.isDamageableItem()) {
            tool.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
    }

    // ---------------------------------------------------------------- квасцы

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!event.getState().is(Blocks.CALCITE) || !(event.getBreaker() instanceof Player)) {
            return;
        }
        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        int count = 1 + (level.random.nextFloat() < EXTRA_ALUM_CHANCE ? 1 : 0);
        event.getDrops().add(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new ItemStack(ModItems.ALUM.get(), count)));
    }
}
