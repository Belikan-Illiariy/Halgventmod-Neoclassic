package com.nrdhvnstd.halgventmod.brewing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.AgingCaskMenu;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Бочка для выдержки. В неё <b>вливают</b> вино, медовуху, сидр, крепкое или настойку — до {@link #CAPACITY} склянок
 * одного питья; разливают обратно в склянки, кувшин, кружку, корчагу (и пьютеровую или серебряную посуду).
 * Каждые Brewing.AGE_DAYS суток (в погребе — Brewing.AGE_DAYS_CELLAR) качество налитого +1, до Шедевра.
 * Погреб — куда не попадает небесный свет; под открытым небом выдержка не идёт. Пиво не выдерживают.
 * Сутки — календарные, как у еды: идут и без игрока рядом.
 * <p>Долили того же питья — качество смешивается: молодое разбавляет выдержанное (среднее по склянкам).
 * Разное в одну бочку не льют.
 * <p>На E: склянкой питья — влить, корчагой или кувшином с питьём — влить сколько влезет; пустой склянкой — налить,
 * пустым сосудом (или Shift + E сосудом с тем же питьём) — налить сколько влезет.
 * <p>ПКМ — окно (AgingCaskMenu): питьё — в ячейку «влить» (пустые склянки выходят в свою ячейку), склянки или сосуд —
 * в ячейку «налить», налитое — в ячейку «налитое»; «Вылить».
 */
public class AgingCaskBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int CAPACITY = 16;
    /** «Погребных» суток выдержки на ступень качества. */
    public static final int STEP = Brewing.AGE_DAYS_CELLAR;
    /** «Срока нет» в {@link #D_NEXT_IN} и {@link #D_FILLED_AGO}. */
    public static final int NO_DAY = -1;

    public static final int SLOT_IN = 0, SLOT_EMPTIES = 1, SLOT_BOTTLES = 2, SLOT_OUT = 3, SLOTS = 4;
    public static final int D_PLACE = 0, D_MEASURES = 1, D_PROGRESS = 2, D_STEPS = 3, D_NEXT_IN = 4, D_FILLED_AGO = 5,
            DATA = 6;
    public static final int PLACE_EXPOSED = 0, PLACE_HOUSE = 1, PLACE_CELLAR = 2;

    /** Что налито: одна склянка с качеством, с которым его влили (выдержка — в {@link #aged}); пусто — ничего. */
    private ItemStack drink = ItemStack.EMPTY;
    private int measures;
    private double aged;             // сколько «погребных» суток выдержки набрало налитое
    private int filledDay = NO_DAY;  // календарный день, когда влили в пустую бочку
    private int lastDay = -1;        // по какой день выдержка уже начислена
    /** Склянки из прежней версии бочки, которые не слились с налитым: отдаются игроку при первом касании. */
    private final List<ItemStack> leftovers = new ArrayList<>();

    private final SimpleContainer slots = new SimpleContainer(SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_IN -> Brewing.ageable(stack) || Pottery.drinkCapacity(stack) > 0;
                case SLOT_BOTTLES -> StillBlockEntity.vessel(stack);
                default -> false;
            };
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case D_PLACE -> level == null ? PLACE_HOUSE : place();
                case D_MEASURES -> measures;
                case D_PROGRESS -> progress();
                case D_STEPS -> steps();
                case D_NEXT_IN -> nextIn();
                case D_FILLED_AGO -> level == null || filledDay < 0 || measures <= 0 ? NO_DAY
                        : Math.max(0, Freshness.currentDay(level) - filledDay);
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
    }

    public SimpleContainer slots() {
        return slots;
    }

    public ContainerData data() {
        return data;
    }

    /** Что налито, с качеством на момент заливки (для окна): пусто — ничего. */
    public ItemStack drink() {
        return drink;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>(); // налитое при поломке бочки разливается, вещи из ячеек — нет
        for (int i = 0; i < slots.getContainerSize(); i++) {
            if (!slots.getItem(i).isEmpty()) {
                all.add(slots.getItem(i).copy());
            }
        }
        leftovers.forEach(stack -> all.add(stack.copy()));
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

    // ---------------------------------------------------------------- выдержка

    /** Ступеней качества набрано (не выше Шедевра). */
    private int steps() {
        if (measures <= 0) {
            return 0;
        }
        return Math.min((int) (aged / STEP), Math.max(0, FoodQuality.MAX - FoodQuality.get(drink)));
    }

    private boolean maxed() {
        return FoodQuality.get(drink) + steps() >= FoodQuality.MAX;
    }

    /** Полоса до следующей ступени, 0–100: начисленные сутки и доля текущих. */
    private int progress() {
        if (measures <= 0 || level == null) {
            return 0;
        }
        if (maxed()) {
            return 100;
        }
        double share = level.getServer() == null ? 0
                : Math.floorMod(level.getServer().overworld().getDayTime(), Calendar.TIME_PER_DAY) / (double) Calendar.TIME_PER_DAY;
        double value = aged + share * rate();
        return (int) Math.min(99, (value % STEP) * 100 / STEP);
    }

    /** Через сколько календарных суток следующая ступень (NO_DAY — не будет: Шедевр или под небом). */
    private int nextIn() {
        if (measures <= 0 || level == null || maxed()) {
            return NO_DAY;
        }
        double rate = rate();
        if (rate <= 0) {
            return NO_DAY;
        }
        double need = (Math.floor(aged / STEP) + 1) * STEP - aged;
        return (int) Math.ceil(need / rate - 1e-9);
    }

    /** Склянка налитого, какой она выйдет сейчас: качество заливки + ступени выдержки. */
    private ItemStack pour() {
        ItemStack stack = drink.copyWithCount(1);
        FoodQuality.set(stack, FoodQuality.get(drink) + steps());
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
        if (measures > 0) {
            aged += gain;
        }
        setChanged();
    }

    // ---------------------------------------------------------------- влить и налить

    /** То же питьё, что в бочке: тот же напиток и состав; качество и свежесть не в счёт. */
    private boolean same(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(plain(stack), plain(drink));
    }

    private static ItemStack plain(ItemStack stack) {
        ItemStack copy = stack.copyWithCount(1);
        copy.remove(ModComponents.FOOD_QUALITY.get());
        copy.remove(ModComponents.CREATED_DAY.get());
        return copy;
    }

    /** Примет ли бочка ещё склянку этого. */
    private boolean accepts(ItemStack stack) {
        return Brewing.ageable(stack) && measures < CAPACITY && (measures == 0 || same(stack));
    }

    /**
     * Влить склянку (сначала accepts). Долитое смешивается с налитым: выдержка (в «погребных» сутках вместе с качеством
     * заливки) — среднее по склянкам, лишнее сверх Шедевра не в счёт.
     */
    private void add(ItemStack stack) {
        catchUp();
        if (measures == 0) {
            drink = plain(stack);
            FoodQuality.set(drink, FoodQuality.get(stack));
            aged = 0;
            filledDay = Freshness.currentDay(level);
            measures = 1;
            changed();
            return;
        }
        double max = FoodQuality.MAX * (double) STEP;
        double before = Math.min(max, FoodQuality.get(drink) * (double) STEP + aged);
        double added = Math.min(max, FoodQuality.get(stack) * (double) STEP);
        double value = (before * measures + added) / (measures + 1);
        int quality = Math.clamp((int) (value / STEP), 0, FoodQuality.MAX);
        FoodQuality.set(drink, quality);
        aged = value - quality * (double) STEP;
        measures++;
        changed();
    }

    /** Отлить склянку (налитое выдерживается дальше). */
    private void take() {
        if (--measures <= 0) {
            clear();
        }
        changed();
    }

    private void clear() {
        drink = ItemStack.EMPTY;
        measures = 0;
        aged = 0;
        filledDay = NO_DAY;
    }

    /** Почему не вливается: полна, другое внутри или это не выдерживают. */
    private void refuse(Player player, ItemStack stack) {
        if (stack.is(ModItems.BEER.get())) {
            tell(player, "cask_no_beer");
        } else if (!Brewing.ageable(stack)) {
            tell(player, "cask_not_ageable");
        } else if (measures >= CAPACITY) {
            tell(player, "cask_full");
        } else {
            tell(player, "cask_mixed");
        }
    }

    /** Окно: питьё из ячейки «влить» — в бочку, пустая склянка — в свою ячейку; из корчаги или кувшина — по склянке. */
    private void pourIn() {
        ItemStack stack = slots.getItem(SLOT_IN);
        ItemStack fromVessel = Pottery.storedDrink(stack, level);
        if (!fromVessel.isEmpty()) {
            if (accepts(fromVessel)) {
                add(fromVessel);
                Pottery.takeDrink(stack, level);
                sound(SoundEvents.BOTTLE_EMPTY, 1.0F, 0.8F);
                slots.setChanged();
            }
            return;
        }
        if (!accepts(stack)) {
            return;
        }
        ItemStack empties = slots.getItem(SLOT_EMPTIES);
        boolean room = empties.isEmpty() || (empties.is(Items.GLASS_BOTTLE) && empties.getCount() < empties.getMaxStackSize());
        if (!room) {
            return;
        }
        add(stack);
        stack.shrink(1);
        if (empties.isEmpty()) {
            slots.setItem(SLOT_EMPTIES, new ItemStack(Items.GLASS_BOTTLE));
        } else {
            empties.grow(1);
        }
        sound(SoundEvents.BOTTLE_EMPTY, 1.0F, 0.9F);
        slots.setChanged();
    }

    /** Окно: налитое — в склянки (в ячейку «налитое») или в сосуд в ячейке «налить». */
    private void drawOut() {
        if (measures <= 0) {
            return;
        }
        ItemStack bottles = slots.getItem(SLOT_BOTTLES);
        ItemStack out = pour();
        if (Pottery.drinkCapacity(bottles) > 0) {
            if (!Pottery.canPour(bottles, out, level)) {
                return;
            }
            Pottery.pour(bottles, out, level);
        } else if (bottles.is(Items.GLASS_BOTTLE)) {
            ItemStack ready = slots.getItem(SLOT_OUT);
            if (ready.isEmpty()) {
                slots.setItem(SLOT_OUT, out);
            } else if (ItemStack.isSameItemSameComponents(ready, out) && ready.getCount() < ready.getMaxStackSize()) {
                ready.grow(1);
            } else {
                return;
            }
            bottles.shrink(1);
        } else {
            return;
        }
        sound(SoundEvents.BOTTLE_FILL, 1.0F, 1.0F);
        take();
        slots.setChanged();
    }

    /** «Вылить» (окно). */
    public void pourOut(Player player) {
        if (measures <= 0 || player.distanceToSqr(worldPosition.getCenter()) > 64) {
            return;
        }
        clear();
        changed();
        sound(SoundEvents.BUCKET_EMPTY, 0.8F, 1.0F);
        tell(player, "cask_poured_out");
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AgingCaskBlockEntity cask) {
        if (level.getGameTime() % 10 == 0) {
            cask.pourIn();
            cask.drawOut();
        }
        if (level.getGameTime() % 100 == 0) {
            cask.catchUp();
        }
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (returnLeftovers(player)) {
            return true;
        }
        boolean vessel = Pottery.drinkCapacity(stack) > 0 && stack.getCount() == 1;
        // Корчагой или кувшином с питьём — влить сколько влезет (Shift — наоборот, долить сосуд из бочки)
        ItemStack fromVessel = vessel ? Pottery.storedDrink(stack, level) : ItemStack.EMPTY;
        if (!fromVessel.isEmpty() && !player.isShiftKeyDown()) {
            if (!accepts(fromVessel)) {
                refuse(player, fromVessel);
                return true;
            }
            while (accepts(fromVessel)) {
                add(fromVessel);
                Pottery.takeDrink(stack, level);
                fromVessel = Pottery.storedDrink(stack, level);
            }
            sound(SoundEvents.BOTTLE_EMPTY, 1.0F, 0.8F);
            status(player);
            return true;
        }
        if (stack.getItem() instanceof com.nrdhvnstd.halgventmod.food.DrinkItem) {
            if (!accepts(stack)) {
                refuse(player, stack);
                return true;
            }
            add(stack);
            consumeOne(player, stack);
            give(player, new ItemStack(Items.GLASS_BOTTLE));
            sound(SoundEvents.BOTTLE_EMPTY, 1.0F, 0.9F);
            status(player);
            return true;
        }
        // Пустой корчагой, кувшином, кружкой — налить сколько влезет
        if (vessel) {
            if (measures <= 0) {
                tell(player, "cask_empty");
                return true;
            }
            int poured = 0;
            while (measures > 0 && Pottery.canPour(stack, pour(), level)) {
                Pottery.pour(stack, pour(), level);
                take();
                poured++;
            }
            if (poured > 0) {
                sound(SoundEvents.BOTTLE_FILL, 1.0F, 0.9F);
                status(player);
            } else {
                tell(player, "vessel_no");
            }
            return true;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (measures <= 0) {
                tell(player, "cask_empty");
                return true;
            }
            consumeOne(player, stack);
            give(player, pour());
            take();
            sound(SoundEvents.BOTTLE_FILL, 1.0F, 1.0F);
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        status(player);
        return true;
    }

    /** Склянки из бочки прежней версии — игроку. */
    private boolean returnLeftovers(Player player) {
        if (leftovers.isEmpty()) {
            return false;
        }
        leftovers.forEach(stack -> give(player, stack));
        leftovers.clear();
        changed();
        sound(SoundEvents.BARREL_OPEN, 0.8F, 1.0F);
        tell(player, "cask_legacy");
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ — окно бочки. */
    @Override
    public void report(Player player) {
        returnLeftovers(player);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new AgingCaskMenu(id, inventory, this),
                    getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
        }
    }

    private void status(Player player) {
        if (measures <= 0) {
            tell(player, "cask_empty");
            return;
        }
        int place = place();
        String where = place == PLACE_EXPOSED ? "cask_exposed" : place == PLACE_CELLAR ? "cask_cellar" : "cask_house";
        tell(player, where, measures, CAPACITY,
                place == PLACE_EXPOSED ? 0 : place == PLACE_CELLAR ? Brewing.AGE_DAYS_CELLAR : Brewing.AGE_DAYS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!drink.isEmpty()) {
            tag.put("Drink", drink.save(registries));
        }
        tag.putInt("Measures", measures);
        tag.putDouble("AgedDays", aged);
        tag.putInt("FilledDay", filledDay);
        tag.putInt("LastDay", lastDay);
        for (int i = 0; i < slots.getContainerSize(); i++) {
            if (!slots.getItem(i).isEmpty()) {
                tag.put("Slot" + i, slots.getItem(i).save(registries));
            }
        }
        if (!leftovers.isEmpty()) {
            ListTag list = new ListTag();
            leftovers.forEach(stack -> list.add(stack.save(registries)));
            tag.put("Leftovers", list);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        drink = tag.contains("Drink") ? ItemStack.parseOptional(registries, tag.getCompound("Drink")) : ItemStack.EMPTY;
        measures = drink.isEmpty() ? 0 : tag.getInt("Measures");
        aged = tag.getDouble("AgedDays");
        filledDay = tag.contains("FilledDay") ? tag.getInt("FilledDay") : NO_DAY;
        lastDay = tag.contains("LastDay") ? tag.getInt("LastDay") : -1;
        for (int i = 0; i < slots.getContainerSize(); i++) {
            slots.setItem(i, tag.contains("Slot" + i) ? ItemStack.parseOptional(registries, tag.getCompound("Slot" + i))
                    : ItemStack.EMPTY);
        }
        leftovers.clear();
        ListTag list = tag.getList("Leftovers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty()) {
                leftovers.add(stack);
            }
        }
        if (tag.contains("Bottles")) {
            loadLegacy(tag.getList("Bottles", Tag.TAG_COMPOUND), registries);
        }
    }

    /**
     * Бочка прежней версии — склянки по местам, у каждой своя выдержка: первое питьё становится налитым (такие же склянки
     * сливаются с ним, выдержка — средняя), остальное отдаётся игроку при первом касании.
     */
    private void loadLegacy(ListTag list, HolderLookup.Provider registries) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("Item"));
            if (stack.isEmpty()) {
                continue;
            }
            double days = entry.contains("AgedDays") ? entry.getDouble("AgedDays")
                    : entry.getLong("Aged") / (double) Calendar.TIME_PER_DAY; // ещё более ранние версии считали тики
            int quality = Math.min(FoodQuality.MAX, FoodQuality.get(stack) + (int) (days / STEP));
            double rest = quality >= FoodQuality.MAX ? 0 : days % STEP;
            if (measures == 0 && Brewing.ageable(stack)) {
                drink = plain(stack);
                FoodQuality.set(drink, quality);
                aged = rest;
                measures = 1;
                filledDay = entry.contains("PlacedDay") ? entry.getInt("PlacedDay") : NO_DAY;
            } else if (measures < CAPACITY && same(stack)) {
                double value = (FoodQuality.get(drink) * (double) STEP + aged) * measures + quality * (double) STEP + rest;
                value /= measures + 1;
                int mixed = Math.clamp((int) (value / STEP), 0, FoodQuality.MAX);
                FoodQuality.set(drink, mixed);
                aged = value - mixed * (double) STEP;
                measures++;
            } else {
                leftovers.add(FoodQuality.set(stack.copyWithCount(1), quality));
            }
        }
    }
}
