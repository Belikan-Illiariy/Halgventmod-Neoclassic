package com.nrdhvnstd.halgventmod.pottery;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.cooking.HearthBlockEntity;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.KilnMenu;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.smithing.Smithing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Гончарный горн. ПКМ — окно (KilnMenu): жар, дрова, этап обжига, изделия, заслонка, «Разжечь» / «Затушить». На E:
 * <ul>
 *   <li>высохший сырец, кирпич-сырец — загрузить (только в холодный и пустой от обожжённого);</li>
 *   <li>дрова (как в очаге) — подкинуть; огниво, факел — разжечь; Shift + E пустой рукой — заслонка;</li>
 *   <li>пустая рука — сколько жара и как идёт обжиг; когда загружено — вынуть (горячим — переспросит).</li>
 * </ul>
 * Жар (0–100, на экране ×10 — «градусы») тянется к уровню тяги ({@link #DRAFT_TARGETS}, 0–{@link #MAX_DRAFT}): тягу
 * выбирают в окне или Shift + E; чем она сильнее, тем жарче, быстрее и прожорливее огонь. Дрова — только запас: пока
 * они есть, горн держит выбранный жар. Обжиг идёт по кривой:
 * <ol>
 *   <li><b>закал</b>: первые {@link #TEMPER_TICKS} тиков держать жар не выше {@link #TEMPER_MAX} — иначе трещины;</li>
 *   <li><b>обжиг</b>: набрать {@link #SOAK_NEEDED} тиков в {@link #ZONE_LOW}–{@link #ZONE_HIGH}; меньше — недожог;
 *       выше {@link #OVER} — пережог;</li>
 *   <li><b>остывание</b>: вынутое горячим (выше {@link #HOT_OPEN}) трескается.</li>
 * </ol>
 * Слабая тяга (0–1) в жару — дым: залощённый сырец, набравший {@link #SMOKE_NEEDED} тиков дыма, выходит чернолощёным. Итог считается для каждой вещи, когда её
 * вынимают. После {@link #WEAR} обжигов горн разваливается — чинят {@link #REPAIR_BRICKS} кирпичами.
 */
public class KilnBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int MAX_FUEL = 12000;
    public static final int TEMPER_TICKS = 1000;
    public static final float TEMPER_MAX = 35;
    public static final int SOAK_NEEDED = 4000;
    public static final float ZONE_LOW = 60;
    public static final float ZONE_HIGH = 85;
    public static final float OVER = 88;
    public static final int OVER_BAD = 300;        // столько тиков пережога — качество −1
    public static final int OVER_RUINED = 900;     // — ещё −1
    public static final float HOT_OPEN = 40;
    public static final int HOT_CRACK = 50;        // % трещин у вынутого горячим
    public static final int EARLY_TICKS_PER_CRACK = 10; // за столько тиков жара в закал — +1% трещин
    // Тяга: к какому жару тянет каждая ступень (×10 — градусы), сколько дров за тик и как быстро
    public static final float[] DRAFT_TARGETS = {25, 33, 55, 68, 78, 92};
    public static final int MAX_DRAFT = DRAFT_TARGETS.length - 1;
    public static final double BURN_BASE = 0.4;       // дров за тик на тяге 0, ещё BURN_PER_DRAFT за ступень
    public static final double BURN_PER_DRAFT = 0.2;
    public static final double RATE_BASE = 0.004;     // доля разницы до цели за тик, ещё RATE_PER_DRAFT за ступень
    public static final double RATE_PER_DRAFT = 0.001;
    public static final int SMOKE_NEEDED = 600;       // тиков дыма для чернолощёного
    public static final double COOL_RATE = 0.003;
    public static final int WEAR = 20;
    public static final int REPAIR_BRICKS = 4;
    private static final int STEP = 10;

    private final List<ItemStack> items = new ArrayList<>();
    private int fuel;
    private float heat;
    private int draft = 1;
    private int elapsed;
    private int earlyHot;
    private int soak;
    private int over;
    private int smoke;
    private int uses;
    private int repair;
    private long hotWarnUntil;
    // Окно: дрова в ячейку сразу уходят в запас; изделия — вид на список items
    private final SimpleContainer fuelInput = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return HearthBlockEntity.fuelValue(stack) > 0;
        }
    };
    private final Container wares = new Wares();
    public static final int D_HEAT = 0, D_TARGET = 1, D_FUEL = 2, D_DRAFT = 3, D_LIT = 4, D_BROKEN = 5, D_ELAPSED = 6,
            D_SOAK = 7, D_OVER = 8, D_SMOKE = 9, D_USES = 10, D_REPAIR = 11, D_COUNT = 12, D_CAPACITY = 13, D_EARLY = 14, DATA = 15;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case D_HEAT -> Math.round(heat * 10);
                case D_TARGET -> Math.round((lit() ? DRAFT_TARGETS[draft] : 0) * 10);
                case D_FUEL -> fuel;
                case D_DRAFT -> draft;
                case D_LIT -> lit() ? 1 : 0;
                case D_BROKEN -> broken() ? 1 : 0;
                case D_ELAPSED -> Math.min(Short.MAX_VALUE, elapsed);
                case D_SOAK -> Math.min(Short.MAX_VALUE, soak);
                case D_OVER -> Math.min(Short.MAX_VALUE, over);
                case D_SMOKE -> Math.min(Short.MAX_VALUE, smoke);
                case D_USES -> uses;
                case D_REPAIR -> repair;
                case D_COUNT -> items.size();
                case D_CAPACITY -> capacity();
                case D_EARLY -> Math.min(Short.MAX_VALUE, earlyHot);
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

    public KilnBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KILN.get(), pos, state);
        fuelInput.addListener(container -> feedFuel());
    }

    public SimpleContainer fuelInput() {
        return fuelInput;
    }

    public Container wares() {
        return wares;
    }

    public ContainerData data() {
        return data;
    }

    /** Дрова из ячейки окна — в запас, пока лезут. */
    private void feedFuel() {
        ItemStack stack = fuelInput.getItem(0);
        int value = HearthBlockEntity.fuelValue(stack);
        boolean fed = false;
        while (value > 0 && !stack.isEmpty() && fuel + value <= MAX_FUEL) {
            fuel += value;
            stack.shrink(1);
            fed = true;
        }
        if (fed) {
            sound(SoundEvents.WOOD_PLACE, 0.6F, 1.0F);
            changed();
        }
    }

    /** Можно ли сейчас загрузить это (окно и E). */
    private boolean loadable(ItemStack stack) {
        boolean ware = (Pottery.isGreenware(stack) || Pottery.isRawClay(stack)) && Pottery.has(stack, Pottery.DRIED)
                && !Pottery.has(stack, Pottery.CRACKED);
        return (ware || Smithing.roastable(stack)) && heat <= 30 && soak <= 0 && items.size() < capacity() && !broken();
    }

    /** Вид окна на загруженное: вынуть — значит получить обожжённое (как E пустой рукой). */
    private final class Wares implements Container {
        @Override
        public int getContainerSize() {
            return 10;
        }

        @Override
        public boolean isEmpty() {
            return items.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (slot >= items.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            boolean hot = heat > HOT_OPEN;
            ItemStack result = fire(items.remove(slot), hot);
            sound(result.is(ModItems.SHERDS.get()) ? SoundEvents.DECORATED_POT_SHATTER : SoundEvents.DECORATED_POT_HIT,
                    0.8F, 1.0F);
            if (items.isEmpty()) {
                finishFiring();
            }
            changed();
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (stack.isEmpty()) {
                if (slot < items.size()) {
                    items.remove(slot);
                }
            } else if (slot < items.size()) {
                items.set(slot, stack.copyWithCount(1));
            } else {
                items.add(stack.copyWithCount(1));
                sound(SoundEvents.DECORATED_POT_INSERT, 0.8F, 0.9F);
            }
            changed();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return loadable(stack);
        }

        @Override
        public void setChanged() {
            KilnBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(KilnBlockEntity.this, player);
        }

        @Override
        public void clearContent() {
            items.clear();
        }
    }

    /** Сильная тяга: из трубы светлый дымок; слабая — густой. */
    public boolean damperOpen() {
        return draft >= 2;
    }

    private int capacity() {
        return getBlockState().getBlock() instanceof KilnBlock kiln ? kiln.capacity() : 4;
    }

    private boolean lit() {
        return getBlockState().getValue(KilnBlock.LIT);
    }

    private boolean broken() {
        return getBlockState().getValue(KilnBlock.BROKEN);
    }

    /** Жар, к которому тянется горн на этой тяге. */
    public static float targetHeat(int draft) {
        return DRAFT_TARGETS[Math.clamp(draft, 0, MAX_DRAFT)];
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        items.forEach(stack -> all.add(stack.copy()));
        if (!fuelInput.getItem(0).isEmpty()) {
            all.add(fuelInput.getItem(0).copy());
        }
        return all;
    }

    // ---------------------------------------------------------------- ход времени

    public static void serverTick(Level level, BlockPos pos, BlockState state, KilnBlockEntity kiln) {
        if (level.getGameTime() % STEP != 0 || state.getValue(KilnBlock.BROKEN)) {
            return;
        }
        boolean burning = state.getValue(KilnBlock.LIT) && kiln.fuel > 0;
        if (burning) {
            kiln.fuel -= (int) Math.round(STEP * (BURN_BASE + BURN_PER_DRAFT * kiln.draft)); // сильнее тяга — прожорливее
            if (kiln.fuel <= 0) {
                kiln.fuel = 0;
                level.setBlock(pos, state.setValue(KilnBlock.LIT, false), 3);
                kiln.sound(SoundEvents.FIRE_EXTINGUISH, 0.6F, 0.9F);
            }
        }
        float target = burning ? targetHeat(kiln.draft) : 0;
        double rate = burning ? RATE_BASE + RATE_PER_DRAFT * kiln.draft : COOL_RATE;
        kiln.heat = (float) (target + (kiln.heat - target) * Math.pow(1 - rate, STEP));
        if (!kiln.items.isEmpty() && (burning || kiln.heat > 20)) {
            if (burning) {
                kiln.elapsed += STEP;
            }
            if (kiln.elapsed <= TEMPER_TICKS && kiln.heat > TEMPER_MAX) {
                kiln.earlyHot += STEP;
            }
            if (kiln.heat >= ZONE_LOW && kiln.heat <= ZONE_HIGH) {
                kiln.soak += STEP;
            }
            if (kiln.heat > OVER) {
                kiln.over += STEP;
            }
            if (kiln.draft <= 1 && kiln.heat >= ZONE_LOW) {
                kiln.smoke += STEP;
            }
        }
        kiln.setChanged();
    }

    // ---------------------------------------------------------------- E

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (broken()) {
            return repair(player, stack);
        }
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)) {
            return ignite(player, hand, stack);
        }
        int fuelValue = HearthBlockEntity.fuelValue(stack);
        if (fuelValue > 0) {
            if (fuel + fuelValue > MAX_FUEL) {
                Pottery.say(player, "kiln_fuel_full");
                return true;
            }
            fuel += fuelValue;
            consumeOne(player, stack);
            sound(SoundEvents.WOOD_PLACE, 0.6F, 1.0F);
            status(player);
            changed();
            return true;
        }
        if (stack.isEmpty()) {
            if (player.isShiftKeyDown()) {
                setDraft((draft + 1) % (MAX_DRAFT + 1));
                Pottery.say(player, "kiln_draft", draft, Math.round(targetHeat(draft) * 10));
                changed();
                return true;
            }
            return items.isEmpty() ? status(player) : unload(player);
        }
        return load(player, stack);
    }

    private boolean load(Player player, ItemStack stack) {
        if (Smithing.roastable(stack)) {
            return loadOre(player, stack);
        }
        boolean greenware = Pottery.isGreenware(stack);
        boolean brick = Pottery.isRawClay(stack);
        if (!greenware && !brick) {
            if (Pottery.isWare(stack) && !Pottery.metal(stack) && !Pottery.glass(stack)) {
                Pottery.say(player, "kiln_already_fired");
                return true;
            }
            return false;
        }
        if (Pottery.has(stack, Pottery.CRACKED)) {
            Pottery.say(player, "kiln_cracked");
            return true;
        }
        if (!Pottery.has(stack, Pottery.DRIED)) {
            Pottery.say(player, "kiln_wet");
            return true;
        }
        if (heat > 30 || soak > 0) {
            Pottery.say(player, "kiln_busy");
            return true;
        }
        if (items.size() >= capacity()) {
            Pottery.say(player, "kiln_full", capacity());
            return true;
        }
        items.add(stack.copyWithCount(1));
        consumeOne(player, stack);
        sound(SoundEvents.DECORATED_POT_INSERT, 0.8F, 0.9F);
        Pottery.say(player, "kiln_loaded", items.size(), capacity());
        changed();
        return true;
    }

    /** Руда на обжиг (кузнечество): до Smithing.ROAST_STACK штук одного рода в ячейку. */
    private boolean loadOre(Player player, ItemStack stack) {
        if (heat > 30 || soak > 0) {
            Pottery.say(player, "kiln_busy");
            return true;
        }
        for (int i = 0; i < items.size(); i++) {
            ItemStack there = items.get(i);
            if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < Smithing.ROAST_STACK) {
                items.set(i, there.copyWithCount(there.getCount() + 1));
                consumeOne(player, stack);
                sound(SoundEvents.GRAVEL_PLACE, 0.8F, 0.9F);
                changed();
                return true;
            }
        }
        if (items.size() >= capacity()) {
            Pottery.say(player, "kiln_full", capacity());
            return true;
        }
        items.add(stack.copyWithCount(1));
        consumeOne(player, stack);
        sound(SoundEvents.GRAVEL_PLACE, 0.8F, 0.9F);
        Pottery.say(player, "kiln_loaded", items.size(), capacity());
        changed();
        return true;
    }

    private boolean ignite(Player player, InteractionHand hand, ItemStack stack) {
        if (lit()) {
            Pottery.say(player, "kiln_lit_already");
            return true;
        }
        if (fuel <= 0) {
            Pottery.say(player, "kiln_no_fuel");
            return true;
        }
        if (stack.is(Items.FLINT_AND_STEEL)) {
            damage(player, hand, stack, 1);
            sound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
        } else if (stack.is(Items.FIRE_CHARGE)) {
            consumeOne(player, stack);
            sound(SoundEvents.FIRECHARGE_USE, 1.0F, 1.0F);
        } else {
            sound(SoundEvents.FIRE_AMBIENT, 1.0F, 1.0F);
        }
        level.setBlock(worldPosition, getBlockState().setValue(KilnBlock.LIT, true), 3);
        changed();
        return true;
    }

    private boolean status(Player player) {
        int fuelPercent = Math.round(100.0F * fuel / MAX_FUEL);
        int degrees = Math.round(heat * 10);
        if (items.isEmpty()) {
            Pottery.say(player, "kiln_status_empty", degrees, fuelPercent, uses, WEAR);
        } else {
            Pottery.say(player, "kiln_status", degrees, fuelPercent, Math.min(100, 100 * soak / SOAK_NEEDED), draft,
                    Math.round(targetHeat(draft) * 10));
        }
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ — окно горна. */
    @Override
    public void report(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new KilnMenu(id, inventory, this),
                    getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
        }
    }

    /**
     * Этап обжига строкой: жар, к чему тянут дрова, сколько осталось и что делать дальше. Окно горна считает её
     * у себя по тем же числам (heat — жар 0–100).
     */
    public static Component stage(float heat, int fuel, boolean lit, int draft, boolean broken, int repair, int count,
                                  int capacity, int uses, int elapsed, int soak) {
        int degrees = Math.round(heat * 10);
        int target = Math.round(targetHeat(draft) * 10);
        int fuelPercent = Math.round(100.0F * fuel / MAX_FUEL);
        String prefix = "message.halgventmod.pottery.";
        if (broken) {
            return Component.translatable(prefix + "kiln_broken", repair, REPAIR_BRICKS);
        } else if (count == 0) {
            return Component.translatable(prefix + "kiln_status_empty", degrees, fuelPercent, uses, WEAR);
        } else if (soak >= SOAK_NEEDED) {
            return Component.translatable(prefix + (heat > HOT_OPEN ? "kiln_report_cooling" : "kiln_report_ready"),
                    degrees, Math.round(HOT_OPEN * 10));
        } else if (!lit && elapsed == 0) {
            return Component.translatable(prefix + "kiln_report_loaded", count, capacity, Math.round(TEMPER_MAX * 10));
        } else if (elapsed < TEMPER_TICKS) {
            return Component.translatable(prefix + (heat > TEMPER_MAX ? "kiln_report_temper_hot" : "kiln_report_temper"),
                    degrees, Math.round(TEMPER_MAX * 10), minutes(TEMPER_TICKS - elapsed), fuelPercent, target);
        }
        Component damperState = Component.translatable(prefix + "draft", draft);
        String key = !lit ? "kiln_report_out" : heat > OVER ? "kiln_report_over"
                : heat < ZONE_LOW ? "kiln_report_low" : "kiln_report_firing";
        return Component.translatable(prefix + key, Math.min(99, 100 * soak / SOAK_NEEDED), degrees, Math.round(ZONE_LOW * 10),
                Math.round(ZONE_HIGH * 10), fuelPercent, target, damperState, minutes(SOAK_NEEDED - soak));
    }

    private boolean near(Player player) {
        return player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }

    /** «Разжечь»: огниво, огненный заряд или факел из инвентаря. */
    public void igniteFromInventory(ServerPlayer player) {
        int slot = com.nrdhvnstd.halgventmod.cooking.Fire.lighterSlot(player);
        if (!near(player) || broken()) {
            return;
        }
        if (slot < 0) {
            player.displayClientMessage(Component.translatable("message.halgventmod.cooking.need_lighter"), true);
            return;
        }
        ItemStack lighter = player.getInventory().getItem(slot);
        if (lit() || fuel <= 0) {
            ignite(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY); // скажет, почему нельзя
            return;
        }
        if (lighter.is(Items.FLINT_AND_STEEL) && !player.getAbilities().instabuild) {
            lighter.hurtAndBreak(1, player.serverLevel(), player, item -> {});
        } else if (lighter.is(Items.FIRE_CHARGE)) {
            consumeOne(player, lighter);
        }
        sound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
        level.setBlock(worldPosition, getBlockState().setValue(KilnBlock.LIT, true), 3);
        changed();
    }

    /** Тяга на ступень сильнее или слабее (окно). */
    public void changeDraft(ServerPlayer player, int delta) {
        if (near(player)) {
            setDraft(Math.clamp(draft + delta, 0, MAX_DRAFT));
        }
    }

    private void setDraft(int value) {
        if (value == draft) {
            return;
        }
        draft = value;
        sound(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.5F, 0.7F + 0.1F * draft);
        changed();
    }

    /** «Затушить»: огонь гаснет, недогоревшие дрова остаются, горн остывает. */
    public void extinguish(ServerPlayer player) {
        if (!near(player) || !lit()) {
            return;
        }
        level.setBlock(worldPosition, getBlockState().setValue(KilnBlock.LIT, false), 3);
        sound(SoundEvents.FIRE_EXTINGUISH, 1.0F, 1.0F);
        changed();
    }

    /** Реальные минуты на столько тиков (горн считает по тикам, а не по времени суток). */
    private static int minutes(int ticks) {
        return Math.max(1, (int) Math.ceil(ticks / 1200.0));
    }

    private boolean unload(Player player) {
        boolean hot = heat > HOT_OPEN;
        if (hot && level.getGameTime() > hotWarnUntil) {
            hotWarnUntil = level.getGameTime() + 60;
            Pottery.say(player, "kiln_hot", Math.round(heat * 10), Math.min(100, 100 * soak / SOAK_NEEDED));
            return true;
        }
        ItemStack raw = items.remove(items.size() - 1);
        ItemStack result = fire(raw, hot);
        give(player, result);
        sound(result.is(ModItems.SHERDS.get()) ? SoundEvents.DECORATED_POT_SHATTER : SoundEvents.DECORATED_POT_HIT,
                0.8F, 1.0F);
        if (result.is(ModItems.SHERDS.get())) {
            Pottery.say(player, "kiln_cracked_out", raw.getHoverName());
        }
        if (items.isEmpty()) {
            finishFiring();
        }
        changed();
        return true;
    }

    /** Все вынули: обжиг закончен — счёт заново, горн изнашивается. */
    private void finishFiring() {
        if (soak > 0) {
            uses++;
            if (uses >= WEAR) {
                level.setBlock(worldPosition, getBlockState().setValue(KilnBlock.BROKEN, true).setValue(KilnBlock.LIT, false), 3);
                fuel = 0;
                sound(SoundEvents.DECORATED_POT_SHATTER, 1.0F, 0.6F);
            }
        }
        elapsed = earlyHot = soak = over = smoke = 0;
    }

    /** Что вышло из вещи после этого обжига. */
    private ItemStack fire(ItemStack raw, boolean hot) {
        if (Smithing.roastable(raw)) {
            return Smithing.roast(raw, soak >= SOAK_NEEDED * 2 / 3); // руда не трескается: недожог — какой положили
        }
        if (soak < SOAK_NEEDED / 3) {
            return raw; // толком не обожглось — какой положили, такой и вынули
        }
        int heatCracks = earlyHot / EARLY_TICKS_PER_CRACK + (hot ? HOT_CRACK : 0);
        if (level.random.nextInt(100) < Pottery.crackChance(raw, heatCracks)) {
            return Pottery.isRawClay(raw) ? new ItemStack(ModItems.SHERDS.get()) : Pottery.sherds(raw);
        }
        if (raw.is(ModItems.RAW_BRICK.get())) {
            return new ItemStack(Items.BRICK);
        }
        if (raw.is(ModItems.RAW_LOOM_WEIGHT.get())) {
            return new ItemStack(ModItems.LOOM_WEIGHT.get());
        }
        Vessel vessel = Pottery.vessel(raw);
        ItemStack ware = new ItemStack(Pottery.wareItem(vessel.vesselForm()));
        ware.set(ModComponents.VESSEL.get(), vessel);
        int quality = Pottery.quality(raw);
        int flags = 0;
        if (soak < SOAK_NEEDED) {
            flags |= Pottery.UNDERFIRED;
            quality--;
        }
        if (over > OVER_BAD) {
            flags |= Pottery.OVERFIRED;
            quality -= over > OVER_RUINED ? 2 : 1;
        }
        if (Pottery.has(raw, Pottery.BURNISHED)) {
            flags |= Pottery.BURNISHED;
            if (smoke >= SMOKE_NEEDED) {
                flags |= Pottery.BLACK;
                quality++;
            }
        }
        Pottery.setQuality(ware, quality);
        if (flags != 0) {
            ware.set(ModComponents.WARE_FLAGS.get(), flags);
        }
        return ware;
    }

    private boolean repair(Player player, ItemStack stack) {
        if (!stack.is(Items.BRICK)) {
            Pottery.say(player, "kiln_broken", repair, REPAIR_BRICKS);
            return true;
        }
        consumeOne(player, stack);
        sound(SoundEvents.MUD_BRICKS_PLACE, 1.0F, 1.0F);
        if (++repair >= REPAIR_BRICKS) {
            repair = 0;
            uses = 0;
            level.setBlock(worldPosition, getBlockState().setValue(KilnBlock.BROKEN, false), 3);
            Pottery.say(player, "kiln_repaired");
        } else {
            Pottery.say(player, "kiln_broken", repair, REPAIR_BRICKS);
        }
        changed();
        return true;
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        items.forEach(stack -> list.add(stack.save(registries)));
        tag.put("Items", list);
        tag.putInt("Fuel", fuel);
        tag.putFloat("Heat", heat);
        tag.putInt("Draft", draft);
        tag.putInt("Elapsed", elapsed);
        tag.putInt("EarlyHot", earlyHot);
        tag.putInt("Soak", soak);
        tag.putInt("Over", over);
        tag.putInt("Smoke", smoke);
        tag.putInt("Uses", uses);
        tag.putInt("Repair", repair);
        if (!fuelInput.getItem(0).isEmpty()) {
            tag.put("FuelInput", fuelInput.getItem(0).save(registries));
        }
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
        fuel = tag.getInt("Fuel");
        heat = tag.getFloat("Heat");
        // Из прежних версий: открытая заслонка — тяга 3, закрытая — 1
        draft = tag.contains("Draft") ? Math.clamp(tag.getInt("Draft"), 0, MAX_DRAFT)
                : !tag.contains("Damper") || tag.getBoolean("Damper") ? 3 : 1;
        elapsed = tag.getInt("Elapsed");
        earlyHot = tag.getInt("EarlyHot");
        soak = tag.getInt("Soak");
        over = tag.getInt("Over");
        smoke = tag.getInt("Smoke");
        uses = tag.getInt("Uses");
        fuelInput.setItem(0, tag.contains("FuelInput") ? ItemStack.parseOptional(registries, tag.getCompound("FuelInput")) : ItemStack.EMPTY);
        repair = tag.getInt("Repair");
    }
}
