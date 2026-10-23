package com.nrdhvnstd.halgventmod.carpentry;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Колода для колки. Всё на E:
 * <pre>
 *   бревно      — поставить на колоду (одно; горящее дерево — не багровое и не искажённое)
 *   топор       — колоть: {@link Carpentry#SPLIT_STRIKES} ровных ударов (с полного замаха) — {@link Carpentry#FIREWOOD_PER_LOG}
 *                 полена дров, с неокорённого бревна иногда и кора. Удар в спешке соскальзывает: топор тупится зря
 *   пустая рука — снять бревно (начатое колоть — тоже: поленья не сложились)
 * </pre>
 * ПКМ — как идёт дело.
 */
public class ChoppingBlockEntity extends StationBlockEntity {
    private ItemStack log = ItemStack.EMPTY;
    private int strikes;

    public ChoppingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHOPPING_BLOCK.get(), pos, state);
    }

    // Для отрисовки
    public ItemStack log() {
        return log;
    }

    public int strikes() {
        return strikes;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(ItemTags.AXES)) {
            return chop(player, hand, stack);
        }
        if (stack.is(ItemTags.LOGS)) {
            return put(player, stack);
        }
        if (stack.isEmpty()) {
            return take(player);
        }
        return false;
    }

    private boolean put(Player player, ItemStack stack) {
        if (!log.isEmpty()) {
            Carpentry.say(player, "block_busy");
            return true;
        }
        if (!stack.is(ItemTags.LOGS_THAT_BURN)) {
            Carpentry.say(player, "not_firewood");
            return true;
        }
        log = stack.copyWithCount(1);
        strikes = 0;
        consumeOne(player, stack);
        sound(SoundEvents.WOOD_PLACE, 1.0F, 0.9F);
        Carpentry.say(player, "log_on_block");
        changed();
        return true;
    }

    private boolean chop(Player player, InteractionHand hand, ItemStack axe) {
        if (log.isEmpty()) {
            Carpentry.say(player, "need_log_block");
            return true;
        }
        if (!strike(player, hand, axe)) {
            sound(SoundEvents.WOOD_HIT, 0.8F, 1.4F);
            Carpentry.say(player, "chop_rushed");
            return true;
        }
        strikes++;
        crumbs(log, 1.3, 6);
        if (strikes < Carpentry.SPLIT_STRIKES) {
            sound(SoundEvents.AXE_STRIP, 1.0F, 0.8F + 0.1F * strikes);
            Carpentry.say(player, "chop_progress", strikes, Carpentry.SPLIT_STRIKES);
        } else {
            give(player, new ItemStack(ModItems.FIREWOOD.get(), Carpentry.FIREWOOD_PER_LOG));
            boolean bark = Carpentry.hasBark(log) && level != null && level.random.nextFloat() < Carpentry.BARK_CHANCE;
            if (bark) {
                give(player, new ItemStack(ModItems.BARK.get()));
            }
            sound(SoundEvents.WOOD_BREAK, 1.0F, 0.8F);
            Carpentry.say(player, bark ? "split_bark" : "split", Carpentry.FIREWOOD_PER_LOG);
            log = ItemStack.EMPTY;
            strikes = 0;
        }
        changed();
        return true;
    }

    private boolean take(Player player) {
        if (log.isEmpty()) {
            report(player);
            return true;
        }
        give(player, log);
        log = ItemStack.EMPTY;
        strikes = 0;
        sound(SoundEvents.WOOD_HIT, 0.6F, 1.0F);
        changed();
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    @Override
    public void report(Player player) {
        if (log.isEmpty()) {
            Carpentry.say(player, "report_block_empty");
        } else {
            Carpentry.say(player, "report_block", log.getHoverName(), strikes, Carpentry.SPLIT_STRIKES);
        }
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        if (!log.isEmpty()) {
            all.add(log.copy());
        }
        return all;
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!log.isEmpty()) {
            tag.put("Log", log.save(registries));
        }
        tag.putInt("Strikes", strikes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        log = tag.contains("Log") ? ItemStack.parseOptional(registries, tag.getCompound("Log")) : ItemStack.EMPTY;
        strikes = log.isEmpty() ? 0 : Math.min(tag.getInt("Strikes"), Carpentry.SPLIT_STRIKES - 1);
    }
}
