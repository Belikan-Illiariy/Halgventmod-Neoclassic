package com.nrdhvnstd.halgventmod.cooking;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Крюк. E сырым мясом или рыбой — подвесить коптиться (до {@link #CAPACITY}): коптится, только пока ниже, не дальше
 * {@link #SMOKE_REACH} блоков, горит очаг или костёр; {@link #SMOKE_TIME} тиков дыма — копчёное.
 * E травами, грибами или солёной рыбой (провялится) — подвесить сушиться: летом сохнут за сутки, весной и осенью за двое, зимой за трое;
 * под дождём не сохнут. Мясо и травы вместе не вешают. Пустой рукой — снять готовое или узнать, сколько осталось;
 * Shift + E — снять всё как есть.
 */
public class HookBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int CAPACITY = 4;
    public static final int SMOKE_REACH = 4;
    public static final int SMOKE_TIME = 6000;
    public static final double SUMMER_DAYS = 1;
    public static final double MILD_DAYS = 2;
    public static final double WINTER_DAYS = 3;
    private static final long DAY = 24000L;

    private final List<ItemStack> items = new ArrayList<>();
    private long progress;
    private long lastDayTime = -1;
    private boolean done;

    public HookBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMOKING_HOOK.get(), pos, state);
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        items.forEach(stack -> all.add(done ? result(stack) : stack.copy()));
        return all;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.cooking." + key, args), true);
    }

    private HookBlock.Load load() {
        return getBlockState().getValue(HookBlock.LOAD);
    }

    private static boolean smokable(ItemStack stack) {
        return stack.is(CookingEvents.RAW_FOOD) && (stack.is(Cookery.Kind.MEAT.tag()) || stack.is(Cookery.Kind.FISH.tag()));
    }

    private static boolean dryable(ItemStack stack) {
        return stack.is(ModItems.HERBS.get()) || stack.is(ModItems.WET_GRAIN.get()) || stack.is(ModItems.SALTED_FISH.get())
                || (stack.is(Cookery.Kind.MUSHROOM.tag()) && !stack.is(ModItems.DRIED_MUSHROOMS.get()));
    }

    private static ItemStack result(ItemStack raw) {
        if (smokable(raw)) {
            return new ItemStack(raw.is(Cookery.Kind.FISH.tag()) ? ModItems.SMOKED_FISH.get() : ModItems.SMOKED_MEAT.get());
        }
        if (raw.is(ModItems.WET_GRAIN.get())) {
            return new ItemStack(ModItems.GREEN_MALT.get()); // проросло
        }
        if (raw.is(ModItems.SALTED_FISH.get())) {
            return new ItemStack(ModItems.DRIED_FISH.get()); // провялилась
        }
        return new ItemStack(raw.is(ModItems.HERBS.get()) ? ModItems.DRIED_HERBS.get() : ModItems.DRIED_MUSHROOMS.get());
    }

    /** Идёт ли снизу дым: горящий очаг или костёр не дальше SMOKE_REACH блоков. */
    public static boolean smoky(Level level, BlockPos pos) {
        for (int i = 1; i <= SMOKE_REACH; i++) {
            BlockState below = level.getBlockState(pos.below(i));
            if (CampfireBlock.isLitCampfire(below)
                    || (below.getBlock() instanceof HearthBlock && below.getValue(HearthBlock.LIT))) {
                return true;
            }
            if (below.isSolidRender(level, pos.below(i))) {
                return false;
            }
        }
        return false;
    }

    private static double daysToDry(Level level) {
        return switch (Calendar.date(Calendar.today(level)).season()) {
            case SUMMER -> SUMMER_DAYS;
            case WINTER -> WINTER_DAYS;
            default -> MILD_DAYS;
        };
    }

    private long needed() {
        return load() == HookBlock.Load.SMOKING ? SMOKE_TIME : (long) (daysToDry(level) * DAY);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HookBlockEntity hook) {
        if (level.getGameTime() % 20 != 0 || hook.items.isEmpty() || hook.done) {
            return;
        }
        HookBlock.Load load = state.getValue(HookBlock.LOAD);
        if (load == HookBlock.Load.SMOKING) {
            if (smoky(level, pos)) {
                hook.progress += 20;
            }
        } else {
            long now = level.getDayTime();
            long delta = hook.lastDayTime < 0 ? 0 : Math.clamp(now - hook.lastDayTime, 0, 4 * DAY);
            hook.lastDayTime = now;
            if (!level.isRainingAt(pos) && !level.isRainingAt(pos.above())) {
                hook.progress += delta;
            }
        }
        if (hook.progress >= hook.needed()) {
            hook.done = true;
            hook.sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.6F, 1.3F);
            hook.changed();
        } else {
            hook.setChanged();
        }
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        boolean smoke = smokable(stack);
        boolean dry = dryable(stack);
        if (smoke || dry) {
            HookBlock.Load wanted = smoke ? HookBlock.Load.SMOKING : HookBlock.Load.DRYING;
            if (done) {
                tell(player, "hook_take_first");
                return true;
            }
            if (load() != HookBlock.Load.EMPTY && load() != wanted) {
                tell(player, "hook_mixed");
                return true;
            }
            if (items.size() >= CAPACITY) {
                tell(player, "hook_full");
                return true;
            }
            items.add(stack.copyWithCount(1));
            consumeOne(player, stack);
            progress = 0; // свежее подвесили — отсчёт заново
            lastDayTime = -1;
            if (load() != wanted) {
                level.setBlock(worldPosition, getBlockState().setValue(HookBlock.LOAD, wanted), 3);
            }
            sound(SoundEvents.CHAIN_PLACE, 0.6F, 1.2F);
            if (smoke && !smoky(level, worldPosition)) {
                tell(player, "hook_no_smoke");
            }
            changed();
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        if (items.isEmpty()) {
            tell(player, "hook_empty");
            return true;
        }
        if (done || player.isShiftKeyDown()) {
            for (ItemStack item : items) {
                give(player, done ? result(item) : item);
            }
            if (done) {
                tell(player, load() == HookBlock.Load.SMOKING ? "hook_smoked" : "hook_dried", items.size());
            }
            items.clear();
            progress = 0;
            done = false;
            lastDayTime = -1;
            level.setBlock(worldPosition, getBlockState().setValue(HookBlock.LOAD, HookBlock.Load.EMPTY), 3);
            changed();
            return true;
        }
        report(player);
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ: коптится или сохнет, на сколько процентов, есть ли дым. */
    @Override
    public void report(Player player) {
        if (items.isEmpty()) {
            tell(player, "hook_empty");
            return;
        }
        if (done) {
            tell(player, "hook_ready", items.size());
            return;
        }
        int percent = (int) Math.min(99, 100 * progress / Math.max(1, needed()));
        if (load() == HookBlock.Load.SMOKING) {
            tell(player, smoky(level, worldPosition) ? "hook_smoking" : "hook_no_smoke_status", percent);
        } else {
            tell(player, "hook_drying", percent);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        items.forEach(stack -> list.add(stack.save(registries)));
        tag.put("Items", list);
        tag.putLong("Progress", progress);
        tag.putLong("LastDayTime", lastDayTime);
        tag.putBoolean("Done", done);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        progress = tag.getLong("Progress");
        lastDayTime = tag.contains("LastDayTime") ? tag.getLong("LastDayTime") : -1;
        done = tag.getBoolean("Done");
    }
}
