package com.nrdhvnstd.halgventmod.leather;

import java.util.List;

import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Рама для сушки: одна мокрая кожа или голье.
 * <ul>
 *   <li>сохнет по игровому времени: лето — {@link #SUMMER_DAYS} сут., весна и осень — {@link #MILD_DAYS},
 *       зима — {@link #WINTER_DAYS};</li>
 *   <li>под открытым небом в дождь не сохнет; намокла второй раз — качество −1 (один раз);</li>
 *   <li>высохшую можно отделать: колотушка ({@link #MALLET_STROKES} ровных удара) и жир или воск — по +1;</li>
 *   <li>пустая рука — снять (недосохшая — вернётся как была, сушка с нуля).</li>
 * </ul>
 * Из шкуры в N кусков снимается N штук кожи (Animals).
 */
public class DryingRackBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final double SUMMER_DAYS = 1;
    public static final double MILD_DAYS = 2;
    public static final double WINTER_DAYS = 4;
    public static final int MALLET_STROKES = 3;
    private static final long DAY = 24000L;

    private ItemStack item = ItemStack.EMPTY;
    private long progress;
    private long lastDayTime = -1;
    private boolean dry;
    private boolean wetOnce;
    private boolean rainPenalty;
    private boolean softened;
    private boolean oiled;
    private int malletStrokes;

    public DryingRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRYING_RACK.get(), pos, state);
    }

    public boolean isDry() {
        return dry;
    }

    public ItemStack item() {
        return item;
    }

    @Override
    public List<ItemStack> contents() {
        return item.isEmpty() ? List.of() : List.of(dry ? result() : item.copy());
    }

    /** Сколько суток сохнет кожа в этот сезон. */
    public static double daysToDry(Level level) {
        Calendar.Season season = Calendar.date(Calendar.today(level)).season();
        return switch (season) {
            case SUMMER -> SUMMER_DAYS;
            case WINTER -> WINTER_DAYS;
            default -> MILD_DAYS;
        };
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DryingRackBlockEntity rack) {
        if (level.getGameTime() % 40 != 0) {
            return;
        }
        long now = level.getDayTime();
        long delta = rack.lastDayTime < 0 ? 0 : Math.clamp(now - rack.lastDayTime, 0, 4 * DAY);
        rack.lastDayTime = now;
        if (rack.item.isEmpty() || rack.dry) {
            return;
        }
        if (level.isRainingAt(pos.above()) || level.isRainingAt(pos)) {
            // Дождь: не сохнет. Повторное намокание портит кожу
            if (!rack.wetOnce) {
                rack.wetOnce = true;
                rack.setChanged();
            } else if (!rack.rainPenalty && rack.progress > 0) {
                rack.rainPenalty = true;
                rack.setChanged();
            }
            return;
        }
        rack.progress += delta;
        if (rack.progress >= (long) (daysToDry(level) * DAY)) {
            rack.dry = true;
            rack.sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 1.3F);
            rack.changed();
        } else {
            rack.setChanged();
        }
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (item.isEmpty()) {
            return place(player, stack);
        }
        if (stack.isEmpty()) {
            if (dry) {
                give(player, result());
                say(player, "rack_taken");
            } else {
                give(player, item);
                say(player, "rack_not_dry", percent());
            }
            clear();
            return true;
        }
        if (!dry) {
            if (stack.is(ModItems.LEATHER_MALLET.get()) || isOil(stack)) {
                say(player, "rack_not_dry", percent());
                return true;
            }
            return false;
        }
        if (stack.is(ModItems.LEATHER_MALLET.get())) {
            if (softened) {
                say(player, "already_softened");
                return true;
            }
            boolean steady = strike(player, hand, stack);
            sound(SoundEvents.WOOD_HIT, 1.0F, 0.8F + 0.3F * level.random.nextFloat());
            if (!steady) {
                say(player, "mallet_rush");
                return true;
            }
            malletStrokes++;
            if (malletStrokes >= MALLET_STROKES) {
                softened = true;
                say(player, "softened");
            } else {
                say(player, "stroke", malletStrokes, MALLET_STROKES);
            }
            setChanged();
            return true;
        }
        if (isOil(stack)) {
            if (oiled) {
                say(player, "already_oiled");
                return true;
            }
            consumeOne(player, stack);
            oiled = true;
            sound(SoundEvents.HONEY_DRINK, 0.8F, 1.0F);
            say(player, "oiled");
            setChanged();
            return true;
        }
        return false;
    }

    private boolean place(Player player, ItemStack stack) {
        boolean accepted = stack.is(ModItems.WET_LEATHER.get()) || stack.is(ModItems.PELT.get());
        if (!accepted) {
            if (stack.isEmpty() || stack.is(ModItems.LEATHER_MALLET.get())) {
                say(player, "rack_empty");
                return true;
            }
            return false;
        }
        if (Freshness.isSpoiled(stack, level)) {
            say(player, "rotten");
            return true;
        }
        item = stack.copyWithCount(1);
        consumeOne(player, stack);
        progress = 0;
        lastDayTime = level.getDayTime();
        dry = wetOnce = rainPenalty = softened = oiled = false;
        malletStrokes = 0;
        sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 0.8F);
        say(player, "rack_placed", Component.literal(String.valueOf(daysToDry(level))));
        setLoaded(true);
        changed();
        return true;
    }

    /** Что снимается с высохшей рамы: N кусков кожи нужного вида с качеством. */
    private ItemStack result() {
        Tannage tannage = item.is(ModItems.PELT.get()) ? Tannage.RAWHIDE : Tannage.of(item);
        if (tannage == null) {
            tannage = Tannage.TANNED;
        }
        Hide hide = item.get(ModComponents.HIDE.get());
        int pieces = hide == null ? 1 : hide.pieces();
        ItemStack result = LeatherQuality.carry(item, new ItemStack(tannage.result(), pieces));
        int delta = (softened ? 1 : 0) + (oiled ? 1 : 0) - (rainPenalty ? 1 : 0);
        return LeatherQuality.change(result, delta);
    }

    private static boolean isOil(ItemStack stack) {
        return stack.is(ModItems.SALO.get()) || stack.is(Items.HONEYCOMB);
    }

    private int percent() {
        long required = (long) (daysToDry(level) * DAY);
        return (int) Math.min(99, progress * 100 / Math.max(1, required));
    }

    private void clear() {
        item = ItemStack.EMPTY;
        progress = 0;
        dry = wetOnce = rainPenalty = softened = oiled = false;
        malletStrokes = 0;
        sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.0F);
        setLoaded(false);
        changed();
    }

    private void setLoaded(boolean loaded) {
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(DryingRackBlock.LOADED) && state.getValue(DryingRackBlock.LOADED) != loaded) {
            level.setBlock(worldPosition, state.setValue(DryingRackBlock.LOADED, loaded), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!item.isEmpty()) {
            tag.put("Item", item.save(registries));
        }
        tag.putLong("Progress", progress);
        tag.putLong("LastDayTime", lastDayTime);
        tag.putBoolean("Dry", dry);
        tag.putBoolean("WetOnce", wetOnce);
        tag.putBoolean("RainPenalty", rainPenalty);
        tag.putBoolean("Softened", softened);
        tag.putBoolean("Oiled", oiled);
        tag.putInt("MalletStrokes", malletStrokes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        item = tag.contains("Item") ? ItemStack.parseOptional(registries, tag.getCompound("Item")) : ItemStack.EMPTY;
        progress = tag.getLong("Progress");
        lastDayTime = tag.contains("LastDayTime") ? tag.getLong("LastDayTime") : -1;
        dry = tag.getBoolean("Dry");
        wetOnce = tag.getBoolean("WetOnce");
        rainPenalty = tag.getBoolean("RainPenalty");
        softened = tag.getBoolean("Softened");
        oiled = tag.getBoolean("Oiled");
        malletStrokes = tag.getInt("MalletStrokes");
    }
}
