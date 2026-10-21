package com.nrdhvnstd.halgventmod.brewing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

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
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Бочка для выдержки. E вином, медовухой, сидром, крепким или настойкой — положить (до {@link #CAPACITY} склянок).
 * Каждые Brewing.AGE_DAYS суток (в погребе — Brewing.AGE_DAYS_CELLAR) качество каждой склянки +1, до Шедевра.
 * Погреб — куда не попадает небесный свет; под открытым небом выдержка не идёт. Пиво не выдерживают.
 * Пустой рукой — забрать всё (с набранным качеством); Shift + E — узнать, как идёт.
 */
public class AgingCaskBlockEntity extends StationBlockEntity {
    public static final int CAPACITY = 6;
    private static final long DAY = 24000L;

    private final List<ItemStack> bottles = new ArrayList<>();
    private final List<Long> aged = new ArrayList<>(); // сколько тиков выдержки набрала каждая склянка (с учётом погреба)
    private long lastDayTime = -1;

    public AgingCaskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AGING_CASK.get(), pos, state);
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < bottles.size(); i++) {
            all.add(withAge(i));
        }
        return all;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.brewing." + key, args), true);
    }

    private boolean cellar() {
        return level.getBrightness(LightLayer.SKY, worldPosition.above()) == 0;
    }

    private boolean exposed() {
        return level.canSeeSky(worldPosition.above());
    }

    /** Склянка с набранным качеством: ступень за каждые AGE_DAYS суток (счётчик уже в «погребных» сутках). */
    private ItemStack withAge(int i) {
        ItemStack stack = bottles.get(i).copy();
        int steps = (int) (aged.get(i) / (Brewing.AGE_DAYS_CELLAR * DAY));
        if (steps > 0) {
            FoodQuality.set(stack, FoodQuality.get(stack) + steps);
        }
        return stack;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AgingCaskBlockEntity cask) {
        if (level.getGameTime() % 100 != 0 || cask.bottles.isEmpty()) {
            return;
        }
        long now = level.getDayTime();
        long delta = cask.lastDayTime < 0 ? 0 : Math.clamp(now - cask.lastDayTime, 0, 4 * DAY);
        cask.lastDayTime = now;
        if (cask.exposed()) {
            return; // под открытым небом не выдерживается
        }
        // Вне погреба выдержка медленнее: приводим к «погребным» суткам
        long gain = cask.cellar() ? delta : delta * Brewing.AGE_DAYS_CELLAR / Brewing.AGE_DAYS;
        for (int i = 0; i < cask.aged.size(); i++) {
            cask.aged.set(i, cask.aged.get(i) + gain);
        }
        cask.setChanged();
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (Brewing.ageable(stack)) {
            if (bottles.size() >= CAPACITY) {
                tell(player, "cask_full");
                return true;
            }
            bottles.add(stack.copyWithCount(1));
            aged.add(0L);
            consumeOne(player, stack);
            sound(SoundEvents.BARREL_CLOSE, 0.6F, 1.1F);
            status(player);
            changed();
            return true;
        }
        if (stack.is(com.nrdhvnstd.halgventmod.registry.ModItems.BEER.get())) {
            tell(player, "cask_no_beer");
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        if (player.isShiftKeyDown() || bottles.isEmpty()) {
            status(player);
            return true;
        }
        for (int i = 0; i < bottles.size(); i++) {
            give(player, withAge(i));
        }
        bottles.clear();
        aged.clear();
        lastDayTime = -1;
        sound(SoundEvents.BARREL_OPEN, 0.8F, 1.0F);
        changed();
        return true;
    }

    private void status(Player player) {
        if (bottles.isEmpty()) {
            tell(player, "cask_empty");
            return;
        }
        String where = exposed() ? "cask_exposed" : cellar() ? "cask_cellar" : "cask_house";
        tell(player, where, bottles.size(), exposed() ? 0 : cellar() ? Brewing.AGE_DAYS_CELLAR : Brewing.AGE_DAYS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (int i = 0; i < bottles.size(); i++) {
            CompoundTag entry = new CompoundTag();
            entry.put("Item", bottles.get(i).save(registries));
            entry.putLong("Aged", aged.get(i));
            list.add(entry);
        }
        tag.put("Bottles", list);
        tag.putLong("LastDayTime", lastDayTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bottles.clear();
        aged.clear();
        ListTag list = tag.getList("Bottles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("Item"));
            if (!stack.isEmpty()) {
                bottles.add(stack);
                aged.add(entry.getLong("Aged"));
            }
        }
        lastDayTime = tag.contains("LastDayTime") ? tag.getLong("LastDayTime") : -1;
    }
}
