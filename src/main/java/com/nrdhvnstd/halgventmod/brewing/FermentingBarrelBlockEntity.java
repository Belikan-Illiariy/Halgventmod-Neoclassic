package com.nrdhvnstd.halgventmod.brewing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Бродильная бочка. E склянкой сусла — влить (до {@link #MAX_PORTIONS}, одно сусло за раз); E мёдом, травами или
 * ягодами — добавка (до {@link #MAX_FLAVOURS} разных: «с мёдом», «на травах», «с ягодами»). Бродит
 * Brewing.*_DAYS суток: летом быстрее, зимой медленнее, зато чище (+1 качество). Доливали — отсчёт заново.
 * Готовое разливают склянкой. <b>Передержали</b> больше Brewing.SOUR_AFTER_DAYS суток — уксус.
 * Крепкое с добавкой — настойка (не киснет). Shift + E пустой рукой — вылить всё.
 */
public class FermentingBarrelBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int MAX_PORTIONS = 8;
    public static final int MAX_FLAVOURS = 2;
    private static final long DAY = 24000L;

    private ItemStack base = ItemStack.EMPTY;
    private int portions;
    private final List<String> flavours = new ArrayList<>();
    private long elapsed;
    private long lastDayTime = -1;
    private double factor = 1.0;
    private boolean winter;

    public FermentingBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FERMENTING_BARREL.get(), pos, state);
    }

    public boolean fermenting() {
        return portions > 0 && !ready() && started();
    }

    @Override
    public List<ItemStack> contents() {
        return List.of(); // жидкость при поломке бочки разливается
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.brewing." + key, args), true);
    }

    private boolean spirit() {
        return base.is(ModItems.SPIRIT.get());
    }

    /** Брожение идёт: у сусла — сразу, у крепкого — только с добавкой (настойка). */
    private boolean started() {
        return portions > 0 && (!spirit() || !flavours.isEmpty());
    }

    private long needed() {
        return (long) (Brewing.ferment(base) * factor * DAY);
    }

    private boolean ready() {
        return started() && elapsed >= needed();
    }

    private boolean sour() {
        return ready() && !spirit() && elapsed >= needed() + (long) (Brewing.SOUR_AFTER_DAYS * DAY);
    }

    private int fillLevel() {
        return portions == 0 ? 0 : portions <= 2 ? 1 : portions <= 5 ? 2 : 3;
    }

    private void updateState() {
        BlockState state = getBlockState();
        if (state.getValue(FermentingBarrelBlock.LEVEL) != fillLevel()) {
            level.setBlock(worldPosition, state.setValue(FermentingBarrelBlock.LEVEL, fillLevel()), 3);
        }
        changed();
    }

    private void restart() {
        elapsed = 0;
        lastDayTime = -1;
        factor = Brewing.seasonFactor(level);
        winter = Brewing.winter(level);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FermentingBarrelBlockEntity barrel) {
        if (level.getGameTime() % 40 != 0 || !barrel.started()) {
            return;
        }
        long now = level.getDayTime();
        long delta = barrel.lastDayTime < 0 ? 0 : Math.clamp(now - barrel.lastDayTime, 0, 4 * DAY);
        barrel.lastDayTime = now;
        boolean wasReady = barrel.ready();
        boolean wasSour = barrel.sour();
        barrel.elapsed += delta;
        if (barrel.ready() != wasReady || barrel.sour() != wasSour) {
            barrel.sound(SoundEvents.BREWING_STAND_BREW, 0.6F, 0.7F);
            barrel.changed();
        } else {
            barrel.setChanged();
        }
    }

    /** Что нальётся сейчас: напиток, настойка или уксус. */
    private ItemStack pour() {
        if (sour()) {
            return new ItemStack(ModItems.VINEGAR.get());
        }
        Brew brew = Brewing.brew(base);
        String kind = brew == null ? "berry" : brew.kind();
        Brew result = new Brew(kind, List.copyOf(flavours));
        ItemStack drink = Brewing.make(spirit() ? ModItems.TINCTURE.get() : Brewing.fermentsInto(kind), result);
        int quality = FoodQuality.has(base) ? FoodQuality.get(base) : FoodQuality.NORMAL;
        return FoodQuality.set(drink, quality + (winter ? 1 : 0));
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        boolean liquid = stack.is(ModItems.MUST.get()) || stack.is(ModItems.SPIRIT.get());
        if (liquid) {
            if (ready()) {
                tell(player, "barrel_pour_first");
                return true;
            }
            if (portions > 0 && !sameLiquid(stack)) {
                tell(player, "barrel_mixed");
                return true;
            }
            if (portions >= MAX_PORTIONS) {
                tell(player, "barrel_full");
                return true;
            }
            if (portions == 0) {
                base = stack.copyWithCount(1);
                flavours.clear();
            }
            portions++;
            restart();
            consumeOne(player, stack);
            give(player, new ItemStack(Items.GLASS_BOTTLE));
            sound(SoundEvents.BOTTLE_EMPTY, 1.0F, 0.9F);
            status(player);
            updateState();
            return true;
        }
        String flavour = Brewing.flavour(stack);
        if (flavour != null && portions > 0) {
            if (ready()) {
                tell(player, "barrel_pour_first");
                return true;
            }
            if (flavours.contains(flavour)) {
                tell(player, "barrel_flavour_twice");
                return true;
            }
            if (flavours.size() >= MAX_FLAVOURS) {
                tell(player, "barrel_flavours_full", MAX_FLAVOURS);
                return true;
            }
            flavours.add(flavour);
            consumeOne(player, stack);
            if (spirit() && flavours.size() == 1) {
                restart(); // настойка начинает настаиваться с первой добавкой
            }
            sound(SoundEvents.GENERIC_SPLASH, 0.4F, 1.3F);
            tell(player, "barrel_flavour", stack.getHoverName());
            changed();
            return true;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (portions <= 0) {
                tell(player, "barrel_empty");
                return true;
            }
            if (!ready()) {
                status(player);
                return true;
            }
            consumeOne(player, stack);
            give(player, pour());
            sound(SoundEvents.BOTTLE_FILL, 1.0F, 1.0F);
            if (--portions == 0) {
                clear();
            }
            updateState();
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        if (player.isShiftKeyDown() && portions > 0) {
            clear();
            sound(SoundEvents.BUCKET_EMPTY, 0.8F, 1.0F);
            tell(player, "barrel_poured_out");
            updateState();
            return true;
        }
        status(player);
        return true;
    }

    private boolean sameLiquid(ItemStack stack) {
        if (!stack.is(base.getItem())) {
            return false;
        }
        Brew a = Brewing.brew(stack);
        Brew b = Brewing.brew(base);
        return a == null || b == null || a.kind().equals(b.kind());
    }

    private void clear() {
        base = ItemStack.EMPTY;
        portions = 0;
        flavours.clear();
        elapsed = 0;
        lastDayTime = -1;
    }

    private void status(Player player) {
        if (portions <= 0) {
            tell(player, "barrel_empty");
        } else if (spirit() && flavours.isEmpty()) {
            tell(player, "barrel_tincture_needs", portions);
        } else if (sour()) {
            tell(player, "barrel_sour", portions);
        } else if (ready()) {
            double left = (needed() + Brewing.SOUR_AFTER_DAYS * DAY - elapsed) / (double) DAY;
            tell(player, spirit() ? "barrel_ready_tincture" : "barrel_ready", portions,
                    String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, left)).replace('.', ','));
        } else {
            double left = (needed() - elapsed) / (double) DAY;
            tell(player, "barrel_fermenting", portions,
                    String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, left)).replace('.', ','));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!base.isEmpty()) {
            tag.put("Base", base.save(registries));
        }
        tag.putInt("Portions", portions);
        ListTag list = new ListTag();
        flavours.forEach(f -> list.add(StringTag.valueOf(f)));
        tag.put("Flavours", list);
        tag.putLong("Elapsed", elapsed);
        tag.putLong("LastDayTime", lastDayTime);
        tag.putDouble("Factor", factor);
        tag.putBoolean("Winter", winter);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        base = tag.contains("Base") ? ItemStack.parseOptional(registries, tag.getCompound("Base")) : ItemStack.EMPTY;
        portions = tag.getInt("Portions");
        flavours.clear();
        ListTag list = tag.getList("Flavours", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            flavours.add(list.getString(i));
        }
        elapsed = tag.getLong("Elapsed");
        lastDayTime = tag.contains("LastDayTime") ? tag.getLong("LastDayTime") : -1;
        factor = tag.contains("Factor") ? tag.getDouble("Factor") : 1.0;
        winter = tag.getBoolean("Winter");
    }
}
