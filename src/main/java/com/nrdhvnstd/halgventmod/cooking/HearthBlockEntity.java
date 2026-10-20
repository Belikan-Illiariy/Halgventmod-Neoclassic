package com.nrdhvnstd.halgventmod.cooking;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;

/**
 * Очаг. Всё на E:
 * <ul>
 *   <li><b>огонь</b>: дрова (бревно, доски, палки, уголь) — запас топлива; огниво, огненный заряд или факел — разжечь.
 *       Жар 1–3 зависит от запаса; без подкладки огонь гаснет, под дождём горит втрое быстрее. Прогоревшее даёт
 *       золу — забрать лопатой;</li>
 *   <li><b>котелок</b> (предмет «Котелок» на очаг): ведро воды, до {@link Cookery#CAPACITY} продуктов, варится
 *       {@link #COOK_TIME} единиц жара. Каждые {@link #STIR_EVERY} тиков варево надо помешать половником — иначе через
 *       {@link #STIR_GRACE} тиков пригорает (качество −1). Готовое черпают миской (отвар — склянкой).
 *       Shift + E пустой рукой — вынуть последнее положенное (пока не начало вариться) или снять пустой котелок;</li>
 *   <li><b>жарка</b> (без котелка): мясо, рыбу, картофель кладут на угли — до {@link #CELLS} кусков, каждый в свою
 *       четверть. Жарится нижняя сторона; E пустой рукой по куску — перевернуть, по прожаренному с двух сторон — снять
 *       (Shift + E — снять сразу). Сторона готова с {@link #READY}, пережарена с {@link #OVER}, сгорела с
 *       {@link #BURNT}. Что пора переворачивать, видно по дыму: белый — готово, чёрный — горит.</li>
 * </ul>
 */
public class HearthBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int MAX_FUEL = 9600;
    public static final int HOT_FUEL = 4800;      // жар 3 выше этого запаса
    public static final int WARM_FUEL = 1600;     // жар 2 выше этого
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

    // огонь
    private int fuel;
    private int burned;
    private int ash;
    // котелок
    private boolean water;
    private final List<ItemStack> contents = new ArrayList<>();
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

    public HearthBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HEARTH.get(), pos, state);
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

    public int heat() {
        return !lit() ? 0 : fuel > HOT_FUEL ? 3 : fuel > WARM_FUEL ? 2 : 1;
    }

    public boolean stirDue() {
        return dueTimer >= 0;
    }

    public boolean cooking() {
        return water && !contents.isEmpty() && portions == 0;
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
            all.add(new ItemStack(ModItems.COOKING_POT.get()));
        }
        if (portions == 0) {
            contents.forEach(stack -> all.add(stack.copy()));
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
            return;
        }
        boolean exposed = level.isRainingAt(pos.above());
        int burn = exposed ? 3 : 1;
        hearth.fuel -= burn;
        hearth.burned += burn;
        if (hearth.burned >= ASH_EVERY) {
            hearth.burned -= ASH_EVERY;
            hearth.ash = Math.min(MAX_ASH, hearth.ash + 1);
        }
        if (hearth.fuel <= 0) {
            hearth.fuel = 0;
            hearth.dueTimer = -1;
            level.setBlock(pos, state.setValue(HearthBlock.LIT, false), 3);
            hearth.sound(SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.0F);
            hearth.changed();
            return;
        }
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
        Cookery.Outcome outcome = Cookery.cook(contents, scorched, level);
        if (outcome == null) {
            progress = COOK_TIME; // одна соль или молоко: варить нечего, ждём, пока положат основу
            return;
        }
        portion = outcome.portion();
        portions = outcome.portions();
        ladled = false;
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
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)) {
            return ignite(player, hand, stack);
        }
        int fuelValue = fuelValue(stack);
        if (fuelValue > 0) {
            if (fuel + fuelValue > MAX_FUEL) {
                tell(player, "fuel_full");
                return true;
            }
            fuel += fuelValue;
            consumeOne(player, stack);
            sound(SoundEvents.WOOD_PLACE, 0.6F, 1.0F);
            tell(player, lit() ? "fuel_added" : "fuel_unlit", Math.round(100.0F * fuel / MAX_FUEL));
            changed();
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
        if (stack.is(ModItems.COOKING_POT.get())) {
            return placePot(player, stack);
        }
        return hasPot() ? usePot(player, stack) : useGrill(player, stack, hit);
    }

    private boolean ignite(Player player, InteractionHand hand, ItemStack stack) {
        if (lit()) {
            tell(player, "already_lit");
            return true;
        }
        if (fuel <= 0) {
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

    /** Сколько тиков горения даёт предмет; 0 — не топливо. Общее для очага и печи. */
    public static int fuelValue(ItemStack stack) {
        if (stack.is(ModItems.FIREWOOD.get())) {
            return 2000; // дрова — основное топливо
        }
        if (stack.is(ItemTags.LOGS_THAT_BURN)) {
            return 1600;
        }
        if (stack.is(ItemTags.PLANKS)) {
            return 600;
        }
        if (stack.is(Items.STICK)) {
            return 150;
        }
        if (stack.is(Items.CHARCOAL) || stack.is(Items.COAL)) {
            return 3200;
        }
        if (stack.is(ModItems.BURNT_FOOD.get())) {
            return 400;
        }
        return 0;
    }

    // ---------------------------------------------------------------- котелок

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
        consumeOne(player, stack);
        level.setBlock(worldPosition, getBlockState().setValue(HearthBlock.POT, true), 3);
        sound(SoundEvents.LANTERN_PLACE, 1.0F, 0.8F);
        changed();
        return true;
    }

    private boolean usePot(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            if (player.isShiftKeyDown()) {
                return takeFromPot(player);
            }
            potStatus(player);
            return true;
        }
        if (stack.is(Items.WATER_BUCKET)) {
            if (water) {
                tell(player, "pot_has_water");
                return true;
            }
            water = true;
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            }
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
        if (portions > 0) {
            Cookery.Result result = resultOfPortion();
            if (result != null && stack.is(result.container())) {
                consumeOne(player, stack);
                give(player, portion.copy());
                ladled = true;
                sound(SoundEvents.BOTTLE_FILL, 0.8F, 0.9F);
                if (--portions == 0) {
                    clearPot();
                } else {
                    tell(player, "portions_left", portions);
                }
                changed();
                return true;
            }
            if (stack.is(Items.BOWL) || stack.is(Items.GLASS_BOTTLE)) {
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
        if (contents.size() >= Cookery.CAPACITY) {
            tell(player, "pot_full");
            return true;
        }
        contents.add(stack.copyWithCount(1));
        if (stack.is(Items.MILK_BUCKET)) {
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            }
        } else {
            consumeOne(player, stack);
        }
        if (portions > 0) {
            // Доложили в готовое: доваривается заново, но быстрее
            portions = 0;
            portion = ItemStack.EMPTY;
            progress = COOK_TIME / 2;
        }
        sound(SoundEvents.GENERIC_SPLASH, 0.5F, 1.2F);
        changed();
        return true;
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
            give(player, contents.remove(contents.size() - 1));
            changed();
            return true;
        }
        if (!water && contents.isEmpty()) {
            give(player, new ItemStack(ModItems.COOKING_POT.get()));
            clearPot();
            level.setBlock(worldPosition, getBlockState().setValue(HearthBlock.POT, false), 3);
            changed();
            return true;
        }
        tell(player, "pot_busy");
        return true;
    }

    private void potStatus(Player player) {
        if (!water) {
            tell(player, "need_water");
        } else if (contents.isEmpty()) {
            tell(player, "pot_water_only");
        } else if (portions > 0) {
            tell(player, "pot_ready", portion.getHoverName(), portions);
        } else if (heat() == 0) {
            tell(player, "pot_cold", contents.size(), Cookery.CAPACITY);
        } else {
            tell(player, dueTimer >= 0 ? "pot_stir" : "pot_cooking",
                    Math.min(99, 100 * progress / COOK_TIME), contents.size(), Cookery.CAPACITY);
        }
    }

    private void clearPot() {
        water = false;
        contents.clear();
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
                tell(player, lit() ? "fire_status" : "fire_out", heat(), Math.round(100.0F * fuel / MAX_FUEL));
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
            tell(player, "fire_out", 0, Math.round(100.0F * fuel / MAX_FUEL));
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
        tag.putInt("Fuel", fuel);
        tag.putInt("Burned", burned);
        tag.putInt("Ash", ash);
        tag.putBoolean("Water", water);
        ListTag list = new ListTag();
        contents.forEach(stack -> list.add(stack.save(registries)));
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
        fuel = tag.getInt("Fuel");
        burned = tag.getInt("Burned");
        ash = tag.getInt("Ash");
        water = tag.getBoolean("Water");
        contents.clear();
        ListTag list = tag.getList("Contents", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty()) {
                contents.add(stack);
            }
        }
        progress = tag.getInt("Progress");
        scorched = tag.getInt("Scorched");
        stirTimer = tag.getInt("StirTimer");
        dueTimer = tag.contains("DueTimer") ? tag.getInt("DueTimer") : -1;
        portions = tag.getInt("Portions");
        ladled = tag.getBoolean("Ladled");
        portion = tag.contains("Portion") ? ItemStack.parseOptional(registries, tag.getCompound("Portion")) : ItemStack.EMPTY;
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
