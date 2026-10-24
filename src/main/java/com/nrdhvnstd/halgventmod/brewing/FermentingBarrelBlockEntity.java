package com.nrdhvnstd.halgventmod.brewing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.alchemy.Alchemy;
import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.FermentingBarrelMenu;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
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
 * Бродильная бочка. E склянкой сусла — влить (до {@link #MAX_PORTIONS}, одно сусло за раз); E мёдом, травами или
 * ягодами — добавка (до {@link #MAX_FLAVOURS} разных: «с мёдом», «на травах», «с ягодами»). Бродит
 * Brewing.*_DAYS суток: летом быстрее, зимой медленнее, зато чище (+1 качество). Доливали — отсчёт заново.
 * Готовое разливают склянкой. <b>Передержали</b> больше Brewing.SOUR_AFTER_DAYS суток — уксус.
 * Крепкое с добавкой — настойка (не киснет). Shift + E пустой рукой — вылить всё.
 * <p>Бродит только <b>запечатанная</b> бочка: «Запечатать» в окне — сверху крышка; запечатанную не доливают
 * и не разливают — сначала «Распечатать». Открытая не бродит и не киснет.
 * <p>ПКМ — окно (FermentingBarrelMenu): сусло или крепкое — в ячейку «влить» (пустые склянки выходят в ячейку склянок),
 * добавка — в свою ячейку; готовое само наливается в склянки (в ячейку «готовое»), в корчагу или кувшин в ячейке склянок.
 * <p><b>Алхимия</b> (alchemy/Alchemy.steeps): крепкое, уксус или вода (склянкой, ведром, кувшином) + реагент — по одному
 * на каждую склянку основы (E реагентом или в ячейку добавки) — настаивается, сгущается или осаждается под печатью.
 * Жидкое (бальзамы, териак, эссенции) разливают, твёрдое (краска, ярь-медянка, купорос) выгребают пустой рукой или оно
 * само выходит в ячейку «готовое». Алхимическое не киснет.
 */
public class FermentingBarrelBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int MAX_PORTIONS = 8;
    public static final int MAX_FLAVOURS = 2;
    /** «Срока нет» в {@link #D_DAYS_LEFT} (настойка не киснет). */
    public static final int NO_DAY = -1;

    private ItemStack base = ItemStack.EMPTY;
    private int portions;
    private final List<String> flavours = new ArrayList<>();
    /** Алхимический реагент (одна штука — образец) и сколько положено: по одному на склянку основы. */
    private ItemStack reagent = ItemStack.EMPTY;
    private int reagents;
    // Бродит по календарю, как портится еда: сутки под печатью до последней распечатки + с дня запечатывания
    private int daysBefore;
    private int sealedDay = -1;
    private static final int LEGACY_SEAL = -2; // запечатана в прежней версии: день печати — при первом тике
    private double factor = 1.0;
    private boolean winter;
    private boolean sealed;

    public static final int SLOT_IN = 0, SLOT_FLAVOUR = 1, SLOT_BOTTLES = 2, SLOT_OUT = 3;
    public static final int D_PORTIONS = 0, D_STATE = 1, D_PROGRESS = 2, D_DAYS_LEFT = 3, D_FLAVOURS = 4, D_SEALED = 5,
            D_REAGENTS = 6, DATA = 7;
    public static final int STATE_EMPTY = 0, STATE_NEEDS_FLAVOUR = 1, STATE_FERMENTING = 2, STATE_READY = 3, STATE_SOUR = 4,
            STATE_NEEDS_REAGENT = 5;
    private final SimpleContainer slots = new SimpleContainer(4) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_IN -> liquid(stack) || Pottery.drinkCapacity(stack) > 0 || Pottery.isWater(stack, 1);
                case SLOT_FLAVOUR -> Brewing.flavour(stack) != null || Alchemy.reagent(stack);
                case SLOT_BOTTLES -> StillBlockEntity.vessel(stack);
                default -> false;
            };
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case D_PORTIONS -> portions;
                case D_STATE -> portions <= 0 ? STATE_EMPTY : needsReagent() ? STATE_NEEDS_REAGENT
                        : spirit() && flavours.isEmpty() && reagents == 0 ? STATE_NEEDS_FLAVOUR
                        : sour() ? STATE_SOUR : ready() ? STATE_READY : STATE_FERMENTING;
                case D_PROGRESS -> progress();
                case D_DAYS_LEFT -> !started() || (ready() && !souring()) ? NO_DAY
                        : Math.max(0, (ready() ? souredAt() : needed()) - elapsed());
                case D_FLAVOURS -> flavours.size();
                case D_SEALED -> sealed ? 1 : 0;
                case D_REAGENTS -> reagents;
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

    public FermentingBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FERMENTING_BARREL.get(), pos, state);
    }

    public SimpleContainer slots() {
        return slots;
    }

    public ContainerData data() {
        return data;
    }

    /** Что налито (для окна): пусто — ничего. */
    public ItemStack base() {
        return base;
    }

    public List<String> flavours() {
        return flavours;
    }

    /** Алхимический реагент (для окна): пусто — нет. */
    public ItemStack reagent() {
        return reagent;
    }

    /** Что выйдет по алхимии (для окна): пусто — не алхимия. */
    public ItemStack steepResult() {
        Alchemy.Steep steep = steep();
        return steep == null ? ItemStack.EMPTY : steep.result().apply(reagent);
    }

    // ---------------------------------------------------------------- алхимия

    /** Настой по основе, реагенту и добавкам; null — обычное брожение (или ещё нет реагента). */
    @javax.annotation.Nullable
    private Alchemy.Steep steep() {
        return portions <= 0 ? null : Alchemy.steep(Alchemy.base(base), reagent, flavours);
    }

    /** Основа — уксус или вода: без реагента в них ничего не выйдет. */
    private boolean alchemyOnly() {
        Alchemy.Base kind = Alchemy.base(base);
        return kind == Alchemy.Base.VINEGAR || kind == Alchemy.Base.WATER;
    }

    /** Ждёт реагента: уксус или вода без него, реагентов меньше, чем склянок, или к нему нет нужной добавки (мёда). */
    private boolean needsReagent() {
        if (portions <= 0) {
            return false;
        }
        if (reagent.isEmpty()) {
            return alchemyOnly();
        }
        return reagents < portions || steep() == null;
    }

    /** Подходит ли реагент к налитому (добавку, например мёд к териаку, можно положить и после). */
    private boolean reagentFits(ItemStack stack) {
        Alchemy.Base kind = Alchemy.base(base);
        return kind != null && Alchemy.steeps().stream().anyMatch(s -> s.base() == kind && s.reagent().test(stack));
    }

    /** Готовое — твёрдое: выгребают руками, а не разливают. */
    public boolean solid() {
        Alchemy.Steep steep = steep();
        return steep != null && steep.solid();
    }

    /** Может ли готовое скиснуть: только перебродившее сусло. */
    private boolean souring() {
        return !spirit() && steep() == null;
    }

    public boolean sealed() {
        return sealed;
    }

    public boolean fermenting() {
        return sealed && portions > 0 && !ready() && started();
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>(); // жидкость при поломке бочки разливается, вещи из ячеек — нет
        for (int i = 0; i < slots.getContainerSize(); i++) {
            if (!slots.getItem(i).isEmpty()) {
                all.add(slots.getItem(i).copy());
            }
        }
        return all;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.brewing." + key, args), true);
    }

    private boolean spirit() {
        return base.is(ModItems.SPIRIT.get());
    }

    /**
     * Брожение идёт: у сусла — сразу, у крепкого — только с добавкой (настойка) или реагентом; алхимия — когда
     * реагентов хватает на все склянки.
     */
    private boolean started() {
        if (portions <= 0 || needsReagent()) {
            return false;
        }
        return steep() != null || (!spirit() && !alchemyOnly()) || !flavours.isEmpty();
    }

    /** Сколько календарных суток бродить (с поправкой на время года), не меньше суток. */
    private int needed() {
        Alchemy.Steep steep = steep();
        return Math.max(1, (int) Math.round((steep != null ? steep.days() : Brewing.ferment(base)) * factor));
    }

    /** На какие сутки брожения готовое скисает. */
    private int souredAt() {
        return needed() + (int) Math.round(Brewing.SOUR_AFTER_DAYS);
    }

    /** Сколько календарных суток бочка простояла запечатанной с последней доливки. */
    private int elapsed() {
        int today = level == null ? sealedDay : Freshness.currentDay(level);
        return daysBefore + (sealed && sealedDay >= 0 ? Math.max(0, today - sealedDay) : 0);
    }

    private boolean ready() {
        return started() && elapsed() >= needed();
    }

    private boolean sour() {
        return ready() && souring() && elapsed() >= souredAt();
    }

    /** Полоса брожения, 0–100: целые сутки и доля текущих (пока под печатью). */
    private int progress() {
        if (!started()) {
            return 0;
        }
        if (ready()) {
            return 100;
        }
        double share = 0;
        if (sealed && level != null && level.getServer() != null) {
            share = Math.floorMod(level.getServer().overworld().getDayTime(), Calendar.TIME_PER_DAY) / (double) Calendar.TIME_PER_DAY;
        }
        return (int) Math.min(99, 100 * (elapsed() + share) / needed());
    }

    private int fillLevel() {
        return portions == 0 ? 0 : portions <= 2 ? 1 : portions <= 5 ? 2 : 3;
    }

    private void updateState() {
        BlockState state = getBlockState();
        BlockState next = state.setValue(FermentingBarrelBlock.LEVEL, fillLevel()).setValue(FermentingBarrelBlock.SEALED, sealed);
        if (next != state) {
            level.setBlock(worldPosition, next, 3);
        }
        changed();
    }

    private void restart() {
        daysBefore = 0;
        sealedDay = sealed ? Freshness.currentDay(level) : -1;
        factor = Brewing.seasonFactor(level);
        winter = Brewing.winter(level);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FermentingBarrelBlockEntity barrel) {
        if (level.getGameTime() % 10 == 0 && !barrel.sealed) {
            barrel.pourIn();
            barrel.addFlavour();
            barrel.drawOut();
        }
        if (level.getGameTime() % 40 == 0 && state.getValue(FermentingBarrelBlock.SEALED) != barrel.sealed) {
            barrel.updateState(); // бочки из прежних версий
        }
        if (barrel.sealedDay == LEGACY_SEAL) {
            barrel.sealedDay = Freshness.currentDay(level);
        }
        if (level.getGameTime() % 100 != 0 || !barrel.started() || !barrel.sealed) {
            return;
        }
        // Сутки идут по календарю и без бочки рядом; тут только отмечаем, что перебродило или скисло
        int stage = barrel.sour() ? 2 : barrel.ready() ? 1 : 0;
        if (stage != barrel.lastStage) {
            if (barrel.lastStage >= 0) {
                barrel.sound(SoundEvents.BREWING_STAND_BREW, 0.6F, 0.7F);
            }
            barrel.lastStage = stage;
            barrel.changed();
        }
    }

    private int lastStage = -1;

    /** Что вливают в бочку: сусло, крепкое; для алхимии — ещё уксус и склянка воды. */
    public static boolean liquid(ItemStack stack) {
        return stack.is(ModItems.MUST.get()) || stack.is(ModItems.SPIRIT.get()) || stack.is(ModItems.VINEGAR.get())
                || Alchemy.water(stack);
    }

    /**
     * Влить воду из ведра (до {@link Pottery#BUCKET_MEASURES} склянок, ведро пустеет целиком) или из кувшина, кружки,
     * корчаги (мера — склянка), сколько влезет.
     * @return сколько влито; 0 — не вода или не влезает
     */
    private int pourWater(ItemStack stack, java.util.function.Consumer<ItemStack> emptyBucket) {
        ItemStack water = Alchemy.waterBottle();
        if (!accepts(water)) {
            return 0;
        }
        int poured = 0;
        if (stack.is(Items.WATER_BUCKET)) {
            poured = Math.min(Pottery.BUCKET_MEASURES, MAX_PORTIONS - portions);
            emptyBucket.accept(new ItemStack(Items.BUCKET));
        } else {
            while (Pottery.waterCapacity(stack) > 0 && Pottery.water(stack) > 0 && portions + poured < MAX_PORTIONS) {
                Pottery.setWater(stack, Pottery.water(stack) - 1);
                poured++;
            }
        }
        for (int i = 0; i < poured; i++) {
            addLiquid(water);
        }
        return poured;
    }

    /**
     * Положить реагент (по одному на склянку основы; один и тот же). Сначала — основа.
     * @return null — положен, иначе ключ сообщения, почему нет
     */
    @javax.annotation.Nullable
    private String addReagent(ItemStack stack) {
        if (portions <= 0) {
            return "barrel_reagent_first";
        }
        if (ready()) {
            return "barrel_pour_first";
        }
        if (!reagentFits(stack)) {
            return "barrel_reagent_wrong";
        }
        if (!reagent.isEmpty() && !ItemStack.isSameItemSameComponents(reagent, stack.copyWithCount(1))) {
            return "barrel_reagent_mixed";
        }
        if (reagents >= portions) {
            return "barrel_reagent_enough";
        }
        reagent = stack.copyWithCount(1);
        reagents++;
        restart(); // настаивается с последнего реагента
        return null;
    }

    /** Примет ли бочка ещё склянку этого. */
    private boolean accepts(ItemStack liquid) {
        return liquid(liquid) && !ready() && portions < MAX_PORTIONS && (portions == 0 || sameLiquid(liquid));
    }

    /** Влить склянку (сначала accepts). */
    private void addLiquid(ItemStack liquid) {
        if (portions == 0) {
            base = liquid.copyWithCount(1);
            flavours.clear();
            reagent = ItemStack.EMPTY;
            reagents = 0;
        }
        portions++;
        restart();
    }

    /** Окно: сусло или крепкое из ячейки — в бочку, пустая склянка — в ячейку склянок; из кувшина или корчаги — по склянке. */
    private void pourIn() {
        ItemStack stack = slots.getItem(SLOT_IN);
        ItemStack fromVessel = Pottery.storedDrink(stack, level);
        if (!fromVessel.isEmpty()) {
            if (accepts(fromVessel)) {
                addLiquid(fromVessel);
                Pottery.takeDrink(stack, level);
                sound(SoundEvents.BOTTLE_EMPTY, 1.0F, 0.9F);
                slots.setChanged();
                updateState();
            }
            return;
        }
        if (Pottery.isWater(stack, 1)) {
            // Вода из ведра или кувшина — для купороса
            if (pourWater(stack, bucket -> slots.setItem(SLOT_IN, bucket)) > 0) {
                sound(SoundEvents.BUCKET_EMPTY, 0.8F, 1.0F);
                slots.setChanged();
                updateState();
            }
            return;
        }
        if (!liquid(stack)) {
            return;
        }
        ItemStack bottles = slots.getItem(SLOT_BOTTLES);
        boolean room = bottles.isEmpty() || (bottles.is(Items.GLASS_BOTTLE) && bottles.getCount() < bottles.getMaxStackSize());
        if (stack.isEmpty() || !room || !accepts(stack)) {
            return;
        }
        addLiquid(stack);
        stack.shrink(1);
        if (bottles.isEmpty()) {
            slots.setItem(SLOT_BOTTLES, new ItemStack(Items.GLASS_BOTTLE));
        } else {
            bottles.grow(1);
        }
        sound(SoundEvents.BOTTLE_EMPTY, 1.0F, 0.9F);
        slots.setChanged();
        updateState();
    }

    /** Окно: добавка (или алхимический реагент) из ячейки. */
    private void addFlavour() {
        ItemStack stack = slots.getItem(SLOT_FLAVOUR);
        if (Alchemy.reagent(stack)) {
            if (addReagent(stack) == null) {
                stack.shrink(1);
                sound(SoundEvents.GENERIC_SPLASH, 0.4F, 1.1F);
                slots.setChanged();
                changed();
            }
            return;
        }
        String flavour = Brewing.flavour(stack);
        if (flavour == null || portions <= 0 || ready() || flavours.contains(flavour) || flavours.size() >= MAX_FLAVOURS) {
            return;
        }
        flavours.add(flavour);
        stack.shrink(1);
        if (spirit() && flavours.size() == 1) {
            restart();
        }
        sound(SoundEvents.GENERIC_SPLASH, 0.4F, 1.3F);
        slots.setChanged();
        changed();
    }

    /** Окно: готовое — в склянки (в ячейку «готовое») или в корчагу. */
    private void drawOut() {
        if (portions <= 0 || !ready()) {
            return;
        }
        ItemStack bottles = slots.getItem(SLOT_BOTTLES);
        ItemStack drink = pour();
        if (solid()) {
            // Осадок — прямо в «готовое», без склянок
            ItemStack out = slots.getItem(SLOT_OUT);
            if (out.isEmpty()) {
                slots.setItem(SLOT_OUT, drink);
            } else if (ItemStack.isSameItemSameComponents(out, drink) && out.getCount() + drink.getCount() <= out.getMaxStackSize()) {
                out.grow(drink.getCount());
            } else {
                return;
            }
        } else if (Pottery.drinkCapacity(bottles) > 0) {
            if (!Pottery.canPour(bottles, drink, level)) {
                return;
            }
            Pottery.pour(bottles, drink, level);
        } else if (bottles.is(Items.GLASS_BOTTLE)) {
            ItemStack out = slots.getItem(SLOT_OUT);
            if (out.isEmpty()) {
                slots.setItem(SLOT_OUT, drink);
            } else if (ItemStack.isSameItemSameComponents(out, drink) && out.getCount() < out.getMaxStackSize()) {
                out.grow(1);
            } else {
                return;
            }
            bottles.shrink(1);
        } else {
            return;
        }
        sound(SoundEvents.BOTTLE_FILL, 1.0F, 1.0F);
        if (--portions == 0) {
            clear();
        }
        slots.setChanged();
        updateState();
    }

    /** «Запечатать» / «Распечатать» (окно). */
    public void toggleSeal(Player player) {
        if (player.distanceToSqr(worldPosition.getCenter()) > 64) {
            return;
        }
        if (!sealed && portions <= 0) {
            tell(player, "barrel_empty");
            return;
        }
        int today = Freshness.currentDay(level);
        if (sealed) {
            daysBefore = elapsed(); // распечатали — прошедшие сутки сохраняются, дальше не бродит
            sealedDay = -1;
        } else {
            sealedDay = today;
        }
        sealed = !sealed;
        sound(sealed ? SoundEvents.BARREL_CLOSE : SoundEvents.BARREL_OPEN, 0.9F, sealed ? 0.8F : 1.0F);
        tell(player, sealed ? "barrel_sealed_now" : "barrel_unsealed");
        updateState();
    }

    /** «Вылить» (окно). */
    public void pourOut(Player player) {
        if (portions <= 0 || player.distanceToSqr(worldPosition.getCenter()) > 64) {
            return;
        }
        if (sealed) {
            tell(player, "barrel_sealed");
            return;
        }
        clear();
        sound(SoundEvents.BUCKET_EMPTY, 0.8F, 1.0F);
        tell(player, "barrel_poured_out");
        updateState();
    }

    /** Что нальётся (или выгребется) сейчас: напиток, настойка, уксус или то, что вышло по алхимии. */
    private ItemStack pour() {
        Alchemy.Steep steep = steep();
        if (steep != null) {
            return steep.result().apply(reagent);
        }
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
        boolean liquid = liquid(stack);
        boolean touches = liquid || Brewing.flavour(stack) != null || stack.is(Items.GLASS_BOTTLE) || Alchemy.reagent(stack)
                || Pottery.drinkCapacity(stack) > 0 || Pottery.isWater(stack, 1) || (stack.isEmpty() && player.isShiftKeyDown());
        if (sealed && touches && portions > 0) {
            tell(player, "barrel_sealed");
            return true;
        }
        // Из кувшина или корчаги с суслом или крепким — влить сколько влезет
        ItemStack fromVessel = stack.getCount() == 1 ? Pottery.storedDrink(stack, level) : ItemStack.EMPTY;
        if (liquid(fromVessel)) {
            if (ready()) {
                tell(player, "barrel_pour_first");
                return true;
            }
            if (!accepts(fromVessel)) {
                tell(player, portions >= MAX_PORTIONS ? "barrel_full" : "barrel_mixed");
                return true;
            }
            while (accepts(fromVessel)) {
                addLiquid(fromVessel);
                Pottery.takeDrink(stack, level);
                fromVessel = Pottery.storedDrink(stack, level);
            }
            sound(SoundEvents.BOTTLE_EMPTY, 1.0F, 0.8F);
            status(player);
            updateState();
            return true;
        }
        // Ведром или кувшином воды — влить воду (для купороса)
        if (Pottery.isWater(stack, 1) && stack.getCount() == 1) {
            if (ready()) {
                tell(player, "barrel_pour_first");
                return true;
            }
            if (pourWater(stack, bucket -> {
                if (!player.getAbilities().instabuild) {
                    player.setItemInHand(hand, bucket);
                }
            }) <= 0) {
                tell(player, portions >= MAX_PORTIONS ? "barrel_full" : "barrel_mixed");
                return true;
            }
            sound(SoundEvents.BUCKET_EMPTY, 0.8F, 1.0F);
            status(player);
            updateState();
            return true;
        }
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
            addLiquid(stack);
            consumeOne(player, stack);
            give(player, new ItemStack(Items.GLASS_BOTTLE));
            sound(SoundEvents.BOTTLE_EMPTY, 1.0F, 0.9F);
            status(player);
            updateState();
            return true;
        }
        // Алхимический реагент — по одному на склянку основы
        if (Alchemy.reagent(stack)) {
            String why = addReagent(stack);
            if (why != null) {
                tell(player, why);
                return true;
            }
            consumeOne(player, stack);
            sound(SoundEvents.GENERIC_SPLASH, 0.4F, 1.1F);
            tell(player, "barrel_reagent_added", reagent.getHoverName(), reagents, portions);
            changed();
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
        // Корчагой или кувшином — разлить готовое сколько влезет
        if (Pottery.drinkCapacity(stack) > 0 && stack.getCount() == 1) {
            if (sealed) {
                tell(player, "barrel_sealed");
                return true;
            }
            if (portions <= 0 || !ready()) {
                status(player);
                return true;
            }
            if (solid()) {
                tell(player, "barrel_solid");
                return true;
            }
            int poured = 0;
            while (portions > 0 && Pottery.canPour(stack, pour(), level)) {
                Pottery.pour(stack, pour(), level);
                poured++;
                if (--portions == 0) {
                    clear();
                }
            }
            if (poured > 0) {
                sound(SoundEvents.BOTTLE_FILL, 1.0F, 0.9F);
                updateState();
            } else {
                tell(player, "vessel_no");
            }
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
            if (solid()) {
                tell(player, "barrel_solid");
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
        // Осело твёрдое — выгрести руками всё
        if (!sealed && ready() && solid()) {
            ItemStack out = pour();
            out.setCount(out.getCount() * portions);
            tell(player, "barrel_took_solid", out.getHoverName(), out.getCount());
            give(player, out);
            clear();
            sound(SoundEvents.GRAVEL_BREAK, 0.6F, 1.2F);
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
        sealed = false;
        flavours.clear();
        reagent = ItemStack.EMPTY;
        reagents = 0;
        daysBefore = 0;
        sealedDay = -1;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ — окно бочки. */
    @Override
    public void report(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new FermentingBarrelMenu(id, inventory, this),
                    getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
        }
    }

    private void status(Player player) {
        Alchemy.Steep steep = steep();
        if (portions <= 0) {
            tell(player, "barrel_empty");
        } else if (needsReagent()) {
            tell(player, "barrel_needs_reagent", portions, reagents);
        } else if (!sealed && !ready()) {
            tell(player, "barrel_open", portions);
        } else if (spirit() && flavours.isEmpty() && reagent.isEmpty()) {
            tell(player, "barrel_tincture_needs", portions);
        } else if (sour()) {
            tell(player, "barrel_sour", portions);
        } else if (ready() && steep != null) {
            tell(player, steep.solid() ? "barrel_ready_solid" : "barrel_ready_tincture", portions);
        } else if (ready() && spirit()) {
            tell(player, "barrel_ready_tincture", portions);
        } else if (ready()) {
            tell(player, sealed ? "barrel_ready" : "barrel_ready_open", portions, dueDate(souredAt()));
        } else {
            tell(player, "barrel_fermenting", portions, dueDate(needed()));
        }
    }

    /** Дата, когда бочка простоит под печатью day суток с доливки. */
    private Component dueDate(int day) {
        return Calendar.dateText(Freshness.currentDay(level) + Math.max(0, day - elapsed()));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!base.isEmpty()) {
            tag.put("Base", base.save(registries));
        }
        tag.putInt("Portions", portions);
        if (!reagent.isEmpty()) {
            tag.put("Reagent", reagent.save(registries));
        }
        tag.putInt("Reagents", reagents);
        ListTag list = new ListTag();
        flavours.forEach(f -> list.add(StringTag.valueOf(f)));
        tag.put("Flavours", list);
        tag.putInt("DaysBefore", daysBefore);
        tag.putInt("SealedDay", sealedDay);
        tag.putDouble("Factor", factor);
        tag.putBoolean("Winter", winter);
        tag.putBoolean("Sealed", sealed);
        for (int i = 0; i < slots.getContainerSize(); i++) {
            if (!slots.getItem(i).isEmpty()) {
                tag.put("Slot" + i, slots.getItem(i).save(registries));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        base = tag.contains("Base") ? ItemStack.parseOptional(registries, tag.getCompound("Base")) : ItemStack.EMPTY;
        portions = tag.getInt("Portions");
        reagent = tag.contains("Reagent") ? ItemStack.parseOptional(registries, tag.getCompound("Reagent")) : ItemStack.EMPTY;
        reagents = tag.getInt("Reagents");
        flavours.clear();
        ListTag list = tag.getList("Flavours", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            flavours.add(list.getString(i));
        }
        factor = tag.contains("Factor") ? tag.getDouble("Factor") : 1.0;
        winter = tag.getBoolean("Winter");
        // Бочки из прежних версий бродили без печати: с налитым — считаем запечатанными
        sealed = tag.contains("Sealed") ? tag.getBoolean("Sealed") : portions > 0;
        if (tag.contains("DaysBefore")) {
            daysBefore = tag.getInt("DaysBefore");
            sealedDay = tag.getInt("SealedDay");
        } else {
            // Прежние версии считали тиками: перебродившие сутки — в зачёт, дальше — с первого тика по календарю
            daysBefore = (int) (tag.getLong("Elapsed") / Calendar.TIME_PER_DAY);
            sealedDay = sealed ? LEGACY_SEAL : -1;
        }
        for (int i = 0; i < slots.getContainerSize(); i++) {
            slots.setItem(i, tag.contains("Slot" + i) ? ItemStack.parseOptional(registries, tag.getCompound("Slot" + i))
                    : ItemStack.EMPTY);
        }
    }
}
