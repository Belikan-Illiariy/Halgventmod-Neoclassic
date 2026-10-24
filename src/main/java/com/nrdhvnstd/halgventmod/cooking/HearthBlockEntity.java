package com.nrdhvnstd.halgventmod.cooking;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.HearthMenu;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModBlocks;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * Очаг и костёр (FirePitBlock — тот же очаг на земле). ПКМ — окно (HearthMenu): огонь, а с горшком — и горшок.
 * На E:
 * <ul>
 *   <li><b>огонь</b> ({@link Fire}): дрова (бревно, доски, палки, уголь) — в {@link Fire#SLOTS} ячейки, по полену;
 *       огниво, огненный заряд или факел — разжечь. Температура тянется к {@link #TARGETS} по числу поленьев,
 *       жар 1–3 — по температуре; под дождём горит втрое быстрее. «Затушить» в окне — огонь гаснет, недогоревшее
 *       остаётся, температура — 0. Прогоревшее даёт золу — забрать лопатой;</li>
 *   <li><b>горшок</b> (глиняный горшок на огонь, выглядит котелком): ведро или кувшин воды, до {@link Cookery#CAPACITY}
 *       продуктов (в окне — ячейки горшка), варится {@link #COOK_TIME} единиц жара. Каждые {@link #STIR_EVERY} тиков
 *       варево надо помешать половником — иначе через {@link #STIR_GRACE} тиков пригорает (качество −1). Готовое
 *       черпают миской, деревянной или глиняной (отвар — склянкой). Горшок изнашивается: выварен — трескается
 *       (Pottery.potUses). Shift + E пустой рукой — вынуть последнее положенное (пока не начало вариться) или снять
 *       пустой горшок; в окне — «Опорожнить»;</li>
 *   <li><b>жарка</b> (без горшка): мясо, рыбу, картофель кладут на угли — до {@link #CELLS} кусков, каждый в свою
 *       четверть. Жарится нижняя сторона; E пустой рукой по куску — перевернуть, по прожаренному с двух сторон — снять
 *       (Shift + E — снять сразу). Сторона готова с {@link #READY}, пережарена с {@link #OVER}, сгорела с
 *       {@link #BURNT}. Что пора переворачивать, видно по дыму: белый — готово, чёрный — горит.</li>
 * </ul>
 */
public class HearthBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final float[] TARGETS = {0, 350, 500, 650}; // температура по числу поленьев в огне и запасе
    public static final float HEAT_3 = 550;       // жар 3 от этой температуры
    public static final float HEAT_2 = 400;
    public static final float HEAT_1 = 150;
    public static final float WARM_UP = 1.5F;     // градусов за тик
    public static final float COOL_DOWN = 3.0F;
    public static final int ASH_EVERY = 3200;     // столько топлива на одну золу
    public static final int MAX_ASH = 8;
    public static final int COOK_TIME = 2400;     // единиц жара: при жаре 2 — минута
    public static final int STIR_EVERY = 400;
    public static final int STIR_GRACE = 200;
    public static final int MAX_SCORCH = 3;
    public static final int SIDE_TIME = 1200;     // единиц жара на сторону: при жаре 2 — 30 секунд
    public static final float READY = 1.0F;
    public static final float OVER = 1.5F;
    public static final float BURNT = 2.0F;
    public static final int CELLS = 4;
    public static final float CELL = 0.13F;       // половина шага четвертей на углях

    // Окно (HearthMenu): что показывать — номера в data()
    public static final int D_TEMPERATURE = 0, D_BURN_LEFT = 1, D_BURN_TOTAL = 2, D_REMAINING = 3, D_LIT = 4,
            D_POT = 5, D_WATER = 6, D_PROGRESS = 7, D_STIR = 8, D_DUE = 9, D_SCORCHED = 10, D_PORTIONS = 11,
            D_RESULT = 12, D_HEAT = 13, D_LADLED = 14, DATA = 15;

    // огонь
    private final Fire fire = new Fire();
    private int burned;
    private int ash;
    // горшок на огне — с формой и износом (пустой стак у старых очагов с горшком: снимется новым горшком)
    private ItemStack clayPot = ItemStack.EMPTY;
    private boolean water;
    private boolean loading;
    private int potCount;
    private final SimpleContainer contents = new SimpleContainer(Cookery.CAPACITY) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return Cookery.kind(stack) != null && !stack.is(Items.MILK_BUCKET);
        }
    };
    private int progress;
    private int scorched;
    private int stirTimer;
    private int dueTimer = -1;
    private int portions;
    private boolean ladled;
    private ItemStack portion = ItemStack.EMPTY;
    // жарка: что лежит и прожарка сторон (0 — нижняя сейчас)
    private final ItemStack[] pieces = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final float[][] sides = new float[CELLS][2];
    private final boolean[] flipped = new boolean[CELLS];

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case D_TEMPERATURE -> Math.round(fire.temperature());
                case D_BURN_LEFT -> fire.burnLeft();
                case D_BURN_TOTAL -> fire.burnTotal();
                case D_REMAINING -> Math.min(Short.MAX_VALUE, fire.remaining() / 10); // ×10 тиков
                case D_LIT -> lit() ? 1 : 0;
                case D_POT -> hasPot() ? 1 : 0;
                case D_WATER -> water ? 1 : 0;
                case D_PROGRESS -> progress;
                case D_STIR -> stirTimer;
                case D_DUE -> dueTimer;
                case D_SCORCHED -> scorched;
                case D_PORTIONS -> portions;
                case D_RESULT -> {
                    Cookery.Result result = portions > 0 ? resultOfPortion() : null;
                    yield result == null ? -1 : result.ordinal();
                }
                case D_HEAT -> heat();
                case D_LADLED -> ladled ? 1 : 0;
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

    public HearthBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.HEARTH.get(), pos, state);
    }

    public HearthBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        fire.fuel().addListener(container -> changed());
        contents.addListener(container -> potChanged());
    }

    /** Костёр: тот же очаг. */
    public static HearthBlockEntity firePit(BlockPos pos, BlockState state) {
        return new HearthBlockEntity(ModBlockEntities.FIRE_PIT.get(), pos, state);
    }

    public Fire fire() {
        return fire;
    }

    public SimpleContainer potContents() {
        return contents;
    }

    public ContainerData data() {
        return data;
    }

    /** Положенное в горшок поменялось (окно или E): доложили в готовое — доваривается заново, но быстрее. */
    private void potChanged() {
        if (loading) {
            return;
        }
        int count = potCount();
        if (count > potCount && portions > 0) {
            portions = 0;
            portion = ItemStack.EMPTY;
            progress = COOK_TIME / 2;
        }
        potCount = count;
        changed();
    }

    private int potCount() {
        int n = 0;
        for (int i = 0; i < contents.getContainerSize(); i++) {
            n += contents.getItem(i).isEmpty() ? 0 : 1;
        }
        return n;
    }

    private List<ItemStack> potList() {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < contents.getContainerSize(); i++) {
            if (!contents.getItem(i).isEmpty()) {
                list.add(contents.getItem(i));
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- для отрисовки и частиц

    public static float cellOffset(int i) {
        return i == 0 ? -CELL : CELL;
    }

    public boolean lit() {
        return getBlockState().getValue(HearthBlock.LIT);
    }

    public boolean hasPot() {
        return getBlockState().getValue(HearthBlock.POT);
    }

    /** Горшок на огне (пустой стак — ничего или горшок из прежних версий). */
    public ItemStack clayPot() {
        return clayPot;
    }

    public boolean hasWater() {
        return water;
    }

    public int heat() {
        float t = fire.temperature();
        return !lit() ? 0 : t >= HEAT_3 ? 3 : t >= HEAT_2 ? 2 : t >= HEAT_1 ? 1 : 0;
    }

    public boolean stirDue() {
        return dueTimer >= 0;
    }

    public boolean cooking() {
        return water && !contents.isEmpty() && portions == 0;
    }

    /** Порция, которую черпают сейчас (пусто — нечего черпать). */
    public ItemStack portion() {
        return portions > 0 ? portion : ItemStack.EMPTY;
    }

    public ItemStack piece(int cell) {
        return pieces[cell];
    }

    /** Прожарка нижней стороны куска; −1 — пусто. */
    public float downSide(int cell) {
        return pieces[cell].isEmpty() ? -1 : sides[cell][flipped[cell] ? 1 : 0];
    }

    /** Прожарка верхней (видимой) стороны куска. */
    public float upSide(int cell) {
        return pieces[cell].isEmpty() ? -1 : sides[cell][flipped[cell] ? 0 : 1];
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        if (hasPot()) {
            all.add(clayPot.isEmpty() ? new ItemStack(ModItems.CLAY_POT.get()) : clayPot.copy());
        }
        if (portions == 0) {
            potList().forEach(stack -> all.add(stack.copy()));
        }
        for (int i = 0; i < Fire.SLOTS; i++) {
            if (!fire.fuel().getItem(i).isEmpty()) {
                all.add(fire.fuel().getItem(i).copy());
            }
        }
        for (ItemStack piece : pieces) {
            if (!piece.isEmpty()) {
                all.add(piece.copy());
            }
        }
        if (ash > 0) {
            all.add(new ItemStack(ModItems.ASH.get(), ash));
        }
        return all;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.cooking." + key, args), true);
    }

    // ---------------------------------------------------------------- ход времени

    public static void serverTick(Level level, BlockPos pos, BlockState state, HearthBlockEntity hearth) {
        if (!hearth.lit()) {
            if (hearth.fire.temperature() > 0) {
                hearth.fire.toward(0, 0, COOL_DOWN);
            }
            return;
        }
        boolean exposed = level.isRainingAt(pos.above());
        // Дрова прогорают через тик
        int burn = level.getGameTime() % 2 != 0 ? 0 : exposed ? 3 : 1;
        if (burn > 0 && !hearth.fire.burn(burn)) {
            hearth.dueTimer = -1;
            level.setBlock(pos, state.setValue(HearthBlock.LIT, false), 3);
            hearth.sound(SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.0F);
            hearth.changed();
            return;
        }
        hearth.burned += burn;
        if (hearth.burned >= ASH_EVERY) {
            hearth.burned -= ASH_EVERY;
            hearth.ash = Math.min(MAX_ASH, hearth.ash + 1);
        }
        hearth.fire.toward(TARGETS[Math.min(3, hearth.fire.loaded())], WARM_UP, WARM_UP / 3);
        int heat = hearth.heat();
        boolean sync = state.getValue(HearthBlock.POT) ? hearth.tickPot(heat) : hearth.tickGrill(heat);
        if (sync) {
            hearth.changed();
        } else if (level.getGameTime() % 100 == 0) {
            hearth.setChanged();
        }
    }

    /** @return надо ли сообщить клиенту (сменились подсказки) */
    private boolean tickPot(int heat) {
        if (!water || contents.isEmpty()) {
            return false;
        }
        boolean sync = false;
        if (portions == 0) {
            progress += heat;
            if (progress >= COOK_TIME) {
                finishCooking();
                sync = true;
            }
        }
        // Мешать надо, пока в котле что-то стоит на огне — и пока варится, и пока готовое не вычерпано
        if (dueTimer >= 0) {
            if (++dueTimer >= STIR_GRACE) {
                scorched = Math.min(MAX_SCORCH, scorched + 1);
                if (portions > 0) {
                    FoodQuality.set(portion, FoodQuality.get(portion) - 1);
                }
                dueTimer = -1;
                stirTimer = 0;
                sound(SoundEvents.FIRE_EXTINGUISH, 0.4F, 0.6F);
                sync = true;
            }
        } else if (++stirTimer >= STIR_EVERY) {
            dueTimer = 0;
            sync = true;
        }
        return sync;
    }

    private void finishCooking() {
        Cookery.Outcome outcome = Cookery.cook(potList(), scorched, level);
        if (outcome == null) {
            progress = COOK_TIME; // одна соль или молоко: варить нечего, ждём, пока положат основу
            return;
        }
        portion = outcome.portion();
        portions = outcome.portions();
        ladled = false;
        if (!clayPot.isEmpty()) {
            clayPot.set(ModComponents.POT_USES.get(), Pottery.potUsed(clayPot) + 1); // глиняный горшок изнашивается
        }
        sound(SoundEvents.BREWING_STAND_BREW, 0.6F, 0.8F);
    }

    private boolean tickGrill(int heat) {
        boolean sync = false;
        for (int cell = 0; cell < CELLS; cell++) {
            if (pieces[cell].isEmpty()) {
                continue;
            }
            int down = flipped[cell] ? 1 : 0;
            float before = sides[cell][down];
            float after = before + (float) heat / SIDE_TIME;
            sides[cell][down] = after;
            sync |= band(before) != band(after);
        }
        return sync;
    }

    private static int band(float done) {
        return done >= BURNT ? 3 : done >= OVER ? 2 : done >= READY ? 1 : 0;
    }

    // ---------------------------------------------------------------- E

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        return use(player, hand, stack, worldPosition.getCenter());
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack, Vec3 hit) {
        if (useFire(player, hand, stack)) {
            return true;
        }
        if (stack.is(ModItems.STILL.get()) && getBlockState().is(ModBlocks.HEARTH.get())) {
            return placeStill(player, stack);
        }
        if (stack.is(ModItems.CLAY_POT.get())) {
            return placePot(player, stack);
        }
        return hasPot() ? usePot(player, stack) : useGrill(player, stack, hit);
    }

    /** Огонь: разжечь, подложить дров, выгрести золу. Им пользуется и перегонный куб над очагом. false — не про огонь. */
    public boolean useFire(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)) {
            return ignite(player, hand, stack);
        }
        if (Fire.fuelValue(stack) > 0) {
            if (!fire.add(stack)) {
                tell(player, "fuel_full");
                return true;
            }
            consumeOne(player, stack);
            sound(SoundEvents.WOOD_PLACE, 0.6F, 1.0F);
            tell(player, lit() ? "fuel_added" : "fuel_unlit", fire.loaded(), Fire.SLOTS);
            return true;
        }
        if (stack.canPerformAction(ItemAbilities.SHOVEL_DIG) && ash > 0) {
            give(player, new ItemStack(ModItems.ASH.get(), ash));
            ash = 0;
            damage(player, hand, stack, 1);
            sound(SoundEvents.SAND_BREAK, 0.8F, 1.0F);
            changed();
            return true;
        }
        return false;
    }

    private boolean ignite(Player player, InteractionHand hand, ItemStack stack) {
        if (lit()) {
            tell(player, "already_lit");
            return true;
        }
        if (!fire.hasFuel()) {
            tell(player, "no_fuel");
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
        level.setBlock(worldPosition, getBlockState().setValue(HearthBlock.LIT, true), 3);
        changed();
        return true;
    }

    /** Сколько тиков горения даёт предмет; 0 — не топливо (Fire.fuelValue). */
    public static int fuelValue(ItemStack stack) {
        return Fire.fuelValue(stack);
    }

    // ---------------------------------------------------------------- горшок

    /** E перегонным кубом — поставить его на очаг (блок над очагом, модель опущена на огонь). */
    private boolean placeStill(Player player, ItemStack stack) {
        if (hasPot()) {
            tell(player, "still_pot");
            return true;
        }
        for (ItemStack piece : pieces) {
            if (!piece.isEmpty()) {
                tell(player, "grill_busy");
                return true;
            }
        }
        BlockPos above = worldPosition.above();
        if (!level.getBlockState(above).canBeReplaced()) {
            tell(player, "still_blocked");
            return true;
        }
        BlockState still = ModBlocks.STILL.get().defaultBlockState()
                .setValue(com.nrdhvnstd.halgventmod.brewing.StillBlock.FACING, player.getDirection().getOpposite())
                .setValue(com.nrdhvnstd.halgventmod.brewing.StillBlock.ON_HEARTH, true);
        level.setBlock(above, still, 3);
        consumeOne(player, stack);
        sound(SoundEvents.LANTERN_PLACE, 1.0F, 0.7F);
        return true;
    }

    private boolean placePot(Player player, ItemStack stack) {
        if (hasPot()) {
            tell(player, "pot_already");
            return true;
        }
        for (ItemStack piece : pieces) {
            if (!piece.isEmpty()) {
                tell(player, "grill_busy");
                return true;
            }
        }
        if (Pottery.cracked(stack)) {
            Pottery.say(player, "ware_cracked_use");
            return true;
        }
        if (stack.has(ModComponents.STORED.get()) || Pottery.water(stack) > 0) {
            tell(player, "clay_pot_empty_first");
            return true;
        }
        clayPot = stack.copyWithCount(1);
        consumeOne(player, stack);
        level.setBlock(worldPosition, getBlockState().setValue(HearthBlock.POT, true), 3);
        sound(SoundEvents.DECORATED_POT_PLACE, 1.0F, 0.8F);
        changed();
        return true;
    }

    /** Последнюю порцию вычерпали: износившийся глиняный горшок трескается — остаются черепки. */
    private void wearOut(Player player) {
        if (clayPot.isEmpty() || Pottery.potUsed(clayPot) < Pottery.potUses(clayPot)) {
            return;
        }
        give(player, Pottery.sherds(clayPot));
        clayPot = ItemStack.EMPTY;
        level.setBlock(worldPosition, getBlockState().setValue(HearthBlock.POT, false), 3);
        sound(SoundEvents.DECORATED_POT_SHATTER, 1.0F, 1.0F);
        tell(player, "clay_pot_cracked");
    }

    private boolean usePot(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            if (player.isShiftKeyDown()) {
                return takeFromPot(player);
            }
            potStatus(player);
            return true;
        }
        if (Pottery.isWater(stack, Pottery.BUCKET_MEASURES)) {
            if (water) {
                tell(player, "pot_has_water");
                return true;
            }
            water = true;
            Pottery.takeWater(player, InteractionHand.MAIN_HAND, stack, Pottery.BUCKET_MEASURES);
            sound(SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
            changed();
            return true;
        }
        if (stack.is(ModItems.LADLE.get())) {
            if (dueTimer >= 0) {
                dueTimer = -1;
                stirTimer = 0;
                sound(SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.6F, 1.2F);
                tell(player, "stirred");
                changed();
            } else {
                potStatus(player);
            }
            return true;
        }
        if (portions > 0 && Pottery.drinkCapacity(stack) > 0 && stack.getCount() == 1) {
            // Питьё (отвар, сусло, взвар) — корчагой или кувшином, сколько влезет; похлёбки — только миской
            Cookery.Result result = resultOfPortion();
            if (result == null || result.container() != Items.GLASS_BOTTLE) {
                tell(player, "need_bowl");
                return true;
            }
            int poured = 0;
            while (portions > 0 && Pottery.canPour(stack, portion, level)) {
                Pottery.pour(stack, portion, level);
                ladled = true;
                poured++;
                portions--;
            }
            if (poured == 0) {
                tell(player, "vessel_no");
                return true;
            }
            sound(SoundEvents.BOTTLE_FILL, 0.8F, 0.9F);
            if (portions == 0) {
                clearPot();
                wearOut(player);
            } else {
                tell(player, "portions_left", portions);
            }
            changed();
            return true;
        }
        if (portions > 0) {
            Cookery.Result result = resultOfPortion();
            // В глиняную миску и пьютеровое блюдо тоже черпают: посудина запоминается в порции и вернётся после еды
            boolean clayBowl = result != null && result.container() == Items.BOWL && Pottery.isBowl(stack);
            if (clayBowl && Pottery.cracked(stack)) {
                Pottery.say(player, "ware_cracked_use");
                return true;
            }
            if (result != null && (stack.is(result.container()) || clayBowl)) {
                ItemStack served = portion.copy();
                if (clayBowl) {
                    served.set(ModComponents.SERVED_IN.get(), stack.copyWithCount(1));
                }
                consumeOne(player, stack);
                give(player, served);
                ladled = true;
                sound(SoundEvents.BOTTLE_FILL, 0.8F, 0.9F);
                if (--portions == 0) {
                    clearPot();
                    wearOut(player);
                } else {
                    tell(player, "portions_left", portions);
                }
                changed();
                return true;
            }
            if (stack.is(Items.BOWL) || stack.is(Items.GLASS_BOTTLE) || Pottery.isBowl(stack)) {
                tell(player, result == Cookery.Result.HERBAL_TEA ? "need_bottle" : "need_bowl");
                return true;
            }
        }
        Cookery.Kind kind = Cookery.kind(stack);
        if (kind == null) {
            return false;
        }
        if (!water) {
            tell(player, "need_water");
            return true;
        }
        if (ladled) {
            tell(player, "pot_serving");
            return true;
        }
        int free = -1;
        for (int i = 0; i < contents.getContainerSize() && free < 0; i++) {
            free = contents.getItem(i).isEmpty() ? i : -1;
        }
        if (free < 0) {
            tell(player, "pot_full");
            return true;
        }
        contents.setItem(free, stack.copyWithCount(1)); // доложили в готовое — доваривается (potChanged)
        if (stack.is(Items.MILK_BUCKET)) {
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            }
        } else {
            consumeOne(player, stack);
        }
        sound(SoundEvents.GENERIC_SPLASH, 0.5F, 1.2F);
        return true;
    }

    /** Можно ли сейчас класть в горшок из окна: вода есть, готовое не начали черпать. */
    public boolean potAccepts() {
        return hasPot() && water && !ladled;
    }

    /** Можно ли вынуть положенное из окна: ещё не начало вариться. */
    public boolean potOpen() {
        return progress == 0 && portions == 0;
    }

    private Cookery.Result resultOfPortion() {
        for (Cookery.Result result : Cookery.Result.values()) {
            if (portion.is(result.item())) {
                return result;
            }
        }
        return null;
    }

    private boolean takeFromPot(Player player) {
        if (!contents.isEmpty() && progress == 0 && portions == 0) {
            for (int i = contents.getContainerSize() - 1; i >= 0; i--) {
                if (!contents.getItem(i).isEmpty()) {
                    give(player, contents.removeItemNoUpdate(i));
                    break;
                }
            }
            potChanged();
            return true;
        }
        if (!water && contents.isEmpty()) {
            give(player, clayPot.isEmpty() ? new ItemStack(ModItems.CLAY_POT.get()) : clayPot);
            clayPot = ItemStack.EMPTY;
            clearPot();
            level.setBlock(worldPosition, getBlockState().setValue(HearthBlock.POT, false), 3);
            changed();
            return true;
        }
        tell(player, "pot_busy");
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ — окно очага (с горшком — и горшка). */
    @Override
    public void report(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new HearthMenu(id, inventory, this),
                    getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
        }
    }

    private boolean near(Player player) {
        return player.distanceToSqr(worldPosition.getCenter()) <= 64;
    }

    // ---------------------------------------------------------------- кнопки окна

    /** «Разжечь»: огниво, огненный заряд или факел из инвентаря. */
    public void igniteFromInventory(ServerPlayer player) {
        int slot = Fire.lighterSlot(player);
        if (!near(player)) {
            return;
        }
        if (slot < 0) {
            tell(player, "need_lighter");
            return;
        }
        ItemStack lighter = player.getInventory().getItem(slot);
        if (lit() || !fire.hasFuel()) {
            ignite(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY); // скажет, почему нельзя
            return;
        }
        if (lighter.is(Items.FLINT_AND_STEEL) && !player.getAbilities().instabuild) {
            lighter.hurtAndBreak(1, player.serverLevel(), player, item -> {});
        } else if (lighter.is(Items.FIRE_CHARGE)) {
            consumeOne(player, lighter);
        }
        sound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
        level.setBlock(worldPosition, getBlockState().setValue(HearthBlock.LIT, true), 3);
        changed();
    }

    /** «Затушить»: огонь гаснет, недогоревшее остаётся, температура — 0. */
    public void extinguish(ServerPlayer player) {
        if (!near(player) || !lit()) {
            return;
        }
        fire.extinguish();
        dueTimer = -1;
        stirTimer = 0;
        level.setBlock(worldPosition, getBlockState().setValue(HearthBlock.LIT, false), 3);
        sound(SoundEvents.FIRE_EXTINGUISH, 1.0F, 1.0F);
        changed();
    }

    /** «Помешать»: половник из инвентаря. */
    public void stir(ServerPlayer player) {
        if (!near(player) || !hasPot()) {
            return;
        }
        if (player.getInventory().countItem(ModItems.LADLE.get()) <= 0 && !player.getAbilities().instabuild) {
            tell(player, "need_ladle");
            return;
        }
        if (dueTimer >= 0) {
            dueTimer = -1;
            stirTimer = 0;
            sound(SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.6F, 1.2F);
            tell(player, "stirred");
            changed();
        } else {
            tell(player, "stir_not_yet");
        }
    }

    /** «Опорожнить горшок»: вылить всё — воду, положенное, готовое. */
    public void emptyPot(ServerPlayer player) {
        if (!near(player) || !hasPot() || (!water && contents.isEmpty())) {
            return;
        }
        loading = true;
        clearPot();
        loading = false;
        potCount = 0;
        sound(SoundEvents.BUCKET_EMPTY, 1.0F, 0.8F);
        tell(player, "pot_emptied");
        changed();
    }

    /** «Снять горшок»: только пустой. */
    public void removePot(ServerPlayer player) {
        if (near(player) && hasPot()) {
            takeFromPot(player);
        }
    }

    private void potStatus(Player player) {
        if (!water) {
            tell(player, "need_water");
        } else if (contents.isEmpty()) {
            tell(player, "pot_water_only");
        } else if (portions > 0) {
            tell(player, "pot_ready", portion.getHoverName(), portions);
        } else if (heat() == 0) {
            tell(player, "pot_cold", potCount(), Cookery.CAPACITY);
        } else {
            tell(player, dueTimer >= 0 ? "pot_stir" : "pot_cooking",
                    Math.min(99, 100 * progress / COOK_TIME), potCount(), Cookery.CAPACITY);
        }
    }

    private void clearPot() {
        water = false;
        contents.clearContent();
        progress = 0;
        scorched = 0;
        stirTimer = 0;
        dueTimer = -1;
        portions = 0;
        ladled = false;
        portion = ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- жарка

    private int cellAt(Vec3 hit) {
        int x = hit.x - worldPosition.getX() < 0.5 ? 0 : 1;
        int z = hit.z - worldPosition.getZ() < 0.5 ? 0 : 1;
        return x + z * 2;
    }

    private boolean useGrill(Player player, ItemStack stack, Vec3 hit) {
        int cell = cellAt(hit);
        if (stack.isEmpty()) {
            if (pieces[cell].isEmpty()) {
                tell(player, lit() ? "fire_status" : "fire_out", Math.round(fire.temperature()), fire.loaded(), Fire.SLOTS);
                return true;
            }
            boolean bothDone = sides[cell][0] >= READY && sides[cell][1] >= READY;
            if (bothDone || player.isShiftKeyDown()) {
                give(player, takePiece(cell));
            } else {
                flipped[cell] = !flipped[cell];
                sound(SoundEvents.FIRE_EXTINGUISH, 0.2F, 1.8F);
            }
            changed();
            return true;
        }
        if (Cookery.grilled(stack, level).isEmpty()) {
            return false;
        }
        if (!pieces[cell].isEmpty()) {
            cell = -1;
            for (int i = 0; i < CELLS && cell < 0; i++) {
                cell = pieces[i].isEmpty() ? i : -1;
            }
            if (cell < 0) {
                tell(player, "grill_full");
                return true;
            }
        }
        pieces[cell] = stack.copyWithCount(1);
        sides[cell][0] = 0;
        sides[cell][1] = 0;
        flipped[cell] = false;
        consumeOne(player, stack);
        sound(SoundEvents.FIRE_EXTINGUISH, 0.3F, 1.5F);
        if (!lit()) {
            tell(player, "fire_out", 0, fire.loaded(), Fire.SLOTS);
        }
        changed();
        return true;
    }

    /** Снять кусок: сгоревший — угли, непрожаренный — сырой (с риском), пережаренный — хуже. */
    private ItemStack takePiece(int cell) {
        ItemStack raw = pieces[cell];
        float a = sides[cell][0];
        float b = sides[cell][1];
        pieces[cell] = ItemStack.EMPTY;
        sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.5F, 1.4F);
        if (Math.max(a, b) >= BURNT) {
            return new ItemStack(ModItems.BURNT_FOOD.get());
        }
        if (a < READY / 2 && b < READY / 2) {
            return raw; // почти не тронуто огнём
        }
        ItemStack cooked = Cookery.grilled(raw, level);
        int quality = 3;
        boolean underdone = false;
        for (float side : new float[] {a, b}) {
            if (side < READY) {
                quality -= 2;
                underdone = true;
            } else if (side >= OVER) {
                quality -= 1;
            }
        }
        FoodQuality.set(cooked, Mth.clamp(quality, 0, FoodQuality.MAX));
        return FoodQuality.markRaw(cooked, underdone);
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        fire.save(tag, registries);
        tag.putInt("Burned", burned);
        tag.putInt("Ash", ash);
        tag.putBoolean("Water", water);
        if (!clayPot.isEmpty()) {
            tag.put("ClayPot", clayPot.save(registries));
        }
        ListTag list = new ListTag();
        potList().forEach(stack -> list.add(stack.save(registries)));
        tag.put("Contents", list);
        tag.putInt("Progress", progress);
        tag.putInt("Scorched", scorched);
        tag.putInt("StirTimer", stirTimer);
        tag.putInt("DueTimer", dueTimer);
        tag.putInt("Portions", portions);
        tag.putBoolean("Ladled", ladled);
        if (!portion.isEmpty()) {
            tag.put("Portion", portion.save(registries));
        }
        ListTag grill = new ListTag();
        for (int i = 0; i < CELLS; i++) {
            CompoundTag cell = new CompoundTag();
            if (!pieces[i].isEmpty()) {
                cell.put("Item", pieces[i].save(registries));
            }
            cell.putFloat("A", sides[i][0]);
            cell.putFloat("B", sides[i][1]);
            cell.putBoolean("Flipped", flipped[i]);
            grill.add(cell);
        }
        tag.put("Grill", grill);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loading = true;
        fire.load(tag, registries);
        burned = tag.getInt("Burned");
        ash = tag.getInt("Ash");
        water = tag.getBoolean("Water");
        clayPot = tag.contains("ClayPot") ? ItemStack.parseOptional(registries, tag.getCompound("ClayPot")) : ItemStack.EMPTY;
        contents.clearContent();
        ListTag list = tag.getList("Contents", Tag.TAG_COMPOUND);
        for (int i = 0, slot = 0; i < list.size() && slot < Cookery.CAPACITY; i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty()) {
                contents.setItem(slot++, stack);
            }
        }
        progress = tag.getInt("Progress");
        scorched = tag.getInt("Scorched");
        stirTimer = tag.getInt("StirTimer");
        dueTimer = tag.contains("DueTimer") ? tag.getInt("DueTimer") : -1;
        portions = tag.getInt("Portions");
        ladled = tag.getBoolean("Ladled");
        portion = tag.contains("Portion") ? ItemStack.parseOptional(registries, tag.getCompound("Portion")) : ItemStack.EMPTY;
        potCount = potCount();
        loading = false;
        ListTag grill = tag.getList("Grill", Tag.TAG_COMPOUND);
        for (int i = 0; i < CELLS; i++) {
            CompoundTag cell = i < grill.size() ? grill.getCompound(i) : new CompoundTag();
            pieces[i] = cell.contains("Item") ? ItemStack.parseOptional(registries, cell.getCompound("Item")) : ItemStack.EMPTY;
            sides[i][0] = cell.getFloat("A");
            sides[i][1] = cell.getFloat("B");
            flipped[i] = cell.getBoolean("Flipped");
        }
    }
}
