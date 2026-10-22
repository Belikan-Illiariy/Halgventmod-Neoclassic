package com.nrdhvnstd.halgventmod.brewing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.AgingCaskMenu;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Бочка для выдержки. ПКМ — окно (AgingCaskMenu): {@link #CAPACITY} мест под вино, медовуху, сидр, крепкое или
 * настойку. Каждые Brewing.AGE_DAYS суток (в погребе — Brewing.AGE_DAYS_CELLAR) качество каждой склянки +1,
 * до Шедевра. Погреб — куда не попадает небесный свет; под открытым небом выдержка не идёт. Пиво не выдерживают.
 * Вынутое — с набранным качеством. Сутки — календарные, как у еды: идут и без игрока рядом.
 * <p>На E: склянкой — положить; пустой рукой — забрать всё.
 */
public class AgingCaskBlockEntity extends StationBlockEntity {
    public static final int CAPACITY = 6;
    /** «Погребных» суток выдержки на ступень качества. */
    public static final int STEP = Brewing.AGE_DAYS_CELLAR;
    /** «Срока нет» в {@link #D_NEXT_IN} и {@link #D_PLACED_AGO}. */
    public static final int NO_DAY = -1;

    public static final int D_PLACE = 0, D_PROGRESS = 1, D_STEPS = D_PROGRESS + CAPACITY, D_NEXT_IN = D_STEPS + CAPACITY,
            D_PLACED_AGO = D_NEXT_IN + CAPACITY, DATA = D_PLACED_AGO + CAPACITY;
    public static final int PLACE_EXPOSED = 0, PLACE_HOUSE = 1, PLACE_CELLAR = 2;

    private final double[] aged = new double[CAPACITY]; // сколько «погребных» суток выдержки набрала склянка
    private final int[] placedDay = new int[CAPACITY];  // календарный день, когда положили (-1 — неизвестно)
    private int lastDay = -1;                            // по какой день выдержка уже начислена
    private boolean loading;
    /** Склянки. Вынуть — значит получить с набранным качеством. */
    private final SimpleContainer bottles = new SimpleContainer(CAPACITY) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return Brewing.ageable(stack);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (!loading) {
                catchUp();
            }
            super.setItem(slot, stack);
            if (!loading) {
                aged[slot] = 0;
                placedDay[slot] = level == null || stack.isEmpty() ? NO_DAY : Freshness.currentDay(level);
            }
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (getItem(slot).isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            catchUp();
            ItemStack result = withAge(slot);
            super.setItem(slot, ItemStack.EMPTY);
            aged[slot] = 0;
            placedDay[slot] = NO_DAY;
            setChanged();
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index >= D_PLACED_AGO) {
                int day = placedDay[index - D_PLACED_AGO];
                return level == null || day < 0 ? NO_DAY : Math.max(0, Freshness.currentDay(level) - day);
            }
            if (index >= D_NEXT_IN) {
                return nextIn(index - D_NEXT_IN);
            }
            if (index >= D_STEPS) {
                return steps(index - D_STEPS);
            }
            if (index >= D_PROGRESS) {
                return progress(index - D_PROGRESS);
            }
            return switch (index) {
                case D_PLACE -> level == null ? PLACE_HOUSE : place();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public AgingCaskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AGING_CASK.get(), pos, state);
        java.util.Arrays.fill(placedDay, NO_DAY);
        bottles.addListener(container -> {
            if (!loading) {
                changed();
            }
        });
    }

    public SimpleContainer bottles() {
        return bottles;
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < CAPACITY; i++) {
            if (!bottles.getItem(i).isEmpty()) {
                all.add(withAge(i));
            }
        }
        return all;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.brewing." + key, args), true);
    }

    private int place() {
        if (level.canSeeSky(worldPosition.above())) {
            return PLACE_EXPOSED;
        }
        return level.getBrightness(LightLayer.SKY, worldPosition.above()) == 0 ? PLACE_CELLAR : PLACE_HOUSE;
    }

    /** Сколько «погребных» суток дают календарные сутки здесь: погреб — 1, под крышей — меньше, под небом — 0. */
    private double rate() {
        int place = place();
        return place == PLACE_CELLAR ? 1.0 : place == PLACE_HOUSE ? Brewing.AGE_DAYS_CELLAR / (double) Brewing.AGE_DAYS : 0;
    }

    /** Ступеней качества набрано (не выше Шедевра). */
    private int steps(int i) {
        ItemStack stack = bottles.getItem(i);
        if (stack.isEmpty()) {
            return 0;
        }
        return Math.min((int) (aged[i] / STEP), Math.max(0, FoodQuality.MAX - FoodQuality.get(stack)));
    }

    private boolean maxed(int i) {
        return FoodQuality.get(bottles.getItem(i)) + steps(i) >= FoodQuality.MAX;
    }

    /** Полоса до следующей ступени, 0–100: начисленные сутки и доля текущих. */
    private int progress(int i) {
        if (bottles.getItem(i).isEmpty() || level == null) {
            return 0;
        }
        if (maxed(i)) {
            return 100;
        }
        double share = level.getServer() == null ? 0
                : Math.floorMod(level.getServer().overworld().getDayTime(), Calendar.TIME_PER_DAY) / (double) Calendar.TIME_PER_DAY;
        double value = aged[i] + share * rate();
        return (int) Math.min(99, (value % STEP) * 100 / STEP);
    }

    /** Через сколько календарных суток следующая ступень (NO_DAY — не будет: Шедевр или под небом). */
    private int nextIn(int i) {
        if (bottles.getItem(i).isEmpty() || level == null || maxed(i)) {
            return NO_DAY;
        }
        double rate = rate();
        if (rate <= 0) {
            return NO_DAY;
        }
        double need = (Math.floor(aged[i] / STEP) + 1) * STEP - aged[i];
        return (int) Math.ceil(need / rate - 1e-9);
    }

    /** Склянка с набранным качеством: ступень за каждые AGE_DAYS суток (счётчик уже в «погребных» сутках). */
    private ItemStack withAge(int i) {
        ItemStack stack = bottles.getItem(i).copy();
        int steps = steps(i);
        if (steps > 0) {
            FoodQuality.set(stack, FoodQuality.get(stack) + steps);
        }
        return stack;
    }

    /** Начислить выдержку за календарные сутки с прошлого раза (и за время, пока бочка была не загружена). */
    private void catchUp() {
        if (level == null || level.isClientSide()) {
            return;
        }
        int today = Freshness.currentDay(level);
        if (lastDay < 0 || today < lastDay) {
            lastDay = today;
            return;
        }
        if (today == lastDay) {
            return;
        }
        // Вне погреба выдержка медленнее: приводим к «погребным» суткам; под открытым небом не выдерживается
        double gain = (today - lastDay) * rate();
        lastDay = today;
        for (int i = 0; i < CAPACITY; i++) {
            if (!bottles.getItem(i).isEmpty()) {
                aged[i] += gain;
            }
        }
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AgingCaskBlockEntity cask) {
        if (level.getGameTime() % 100 == 0) {
            cask.catchUp();
        }
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (Brewing.ageable(stack)) {
            for (int i = 0; i < CAPACITY; i++) {
                if (bottles.getItem(i).isEmpty()) {
                    bottles.setItem(i, stack.copyWithCount(1));
                    consumeOne(player, stack);
                    sound(SoundEvents.BARREL_CLOSE, 0.6F, 1.1F);
                    status(player);
                    return true;
                }
            }
            tell(player, "cask_full");
            return true;
        }
        if (stack.is(ModItems.BEER.get())) {
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
        for (int i = 0; i < CAPACITY; i++) {
            if (!bottles.getItem(i).isEmpty()) {
                give(player, bottles.removeItem(i, 1));
            }
        }
        sound(SoundEvents.BARREL_OPEN, 0.8F, 1.0F);
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ — окно бочки. */
    @Override
    public void report(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new AgingCaskMenu(id, inventory, this),
                    getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
        }
    }

    private void status(Player player) {
        if (bottles.isEmpty()) {
            tell(player, "cask_empty");
            return;
        }
        int place = place();
        String where = place == PLACE_EXPOSED ? "cask_exposed" : place == PLACE_CELLAR ? "cask_cellar" : "cask_house";
        int count = 0;
        for (int i = 0; i < CAPACITY; i++) {
            count += bottles.getItem(i).isEmpty() ? 0 : 1;
        }
        tell(player, where, count,
                place == PLACE_EXPOSED ? 0 : place == PLACE_CELLAR ? Brewing.AGE_DAYS_CELLAR : Brewing.AGE_DAYS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (int i = 0; i < CAPACITY; i++) {
            if (bottles.getItem(i).isEmpty()) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.put("Item", bottles.getItem(i).save(registries));
            entry.putDouble("AgedDays", aged[i]);
            entry.putInt("PlacedDay", placedDay[i]);
            entry.putByte("Slot", (byte) i);
            list.add(entry);
        }
        tag.put("Bottles", list);
        tag.putInt("LastDay", lastDay);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loading = true;
        for (int i = 0; i < CAPACITY; i++) {
            bottles.setItem(i, ItemStack.EMPTY);
            aged[i] = 0;
            placedDay[i] = NO_DAY;
        }
        ListTag list = tag.getList("Bottles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.contains("Slot") ? entry.getByte("Slot") : i;
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("Item"));
            if (!stack.isEmpty() && slot >= 0 && slot < CAPACITY) {
                bottles.setItem(slot, stack);
                // Прежние версии считали тики
                aged[slot] = entry.contains("AgedDays") ? entry.getDouble("AgedDays")
                        : entry.getLong("Aged") / (double) Calendar.TIME_PER_DAY;
                placedDay[slot] = entry.contains("PlacedDay") ? entry.getInt("PlacedDay") : NO_DAY;
            }
        }
        lastDay = tag.contains("LastDay") ? tag.getInt("LastDay") : -1;
        loading = false;
    }
}
