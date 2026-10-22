package com.nrdhvnstd.halgventmod.pottery;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.cooking.HearthBlockEntity;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Гончарный горн. Всё на E:
 * <ul>
 *   <li>высохший сырец, кирпич-сырец — загрузить (только в холодный и пустой от обожжённого);</li>
 *   <li>дрова (как в очаге) — подкинуть; огниво, факел — разжечь; Shift + E пустой рукой — заслонка;</li>
 *   <li>пустая рука — сколько жара и как идёт обжиг; когда загружено — вынуть (горячим — переспросит).</li>
 * </ul>
 * Жар (0–100, на экране ×10 — «градусы») тянется к уровню, который даёт запас дров ({@link #targetHeat}): с открытой
 * заслонкой — вдвое быстрее, но и дрова горят вдвое быстрее. Обжиг идёт по кривой:
 * <ol>
 *   <li><b>закал</b>: первые {@link #TEMPER_TICKS} тиков держать жар не выше {@link #TEMPER_MAX} — иначе трещины;</li>
 *   <li><b>обжиг</b>: набрать {@link #SOAK_NEEDED} тиков в {@link #ZONE_LOW}–{@link #ZONE_HIGH}; меньше — недожог;
 *       выше {@link #OVER} — пережог;</li>
 *   <li><b>остывание</b>: вынутое горячим (выше {@link #HOT_OPEN}) трескается.</li>
 * </ol>
 * Закрытая заслонка в жару — дым: залощённый сырец выходит чернолощёным. Итог считается для каждой вещи, когда её
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
    public static final double OPEN_RATE = 0.008;  // доля разницы до цели за тик
    public static final double CLOSED_RATE = 0.004;
    public static final double COOL_RATE = 0.003;
    public static final int WEAR = 20;
    public static final int REPAIR_BRICKS = 4;
    private static final int STEP = 10;

    private final List<ItemStack> items = new ArrayList<>();
    private int fuel;
    private float heat;
    private boolean damper = true;
    private int elapsed;
    private int earlyHot;
    private int soak;
    private int over;
    private int smoke;
    private int uses;
    private int repair;
    private long hotWarnUntil;

    public KilnBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KILN.get(), pos, state);
    }

    public boolean damperOpen() {
        return damper;
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

    /** Жар, к которому тянется горн при таком запасе дров. */
    public static float targetHeat(int fuel) {
        return Math.min(100, 15 + fuel / 120.0F);
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        items.forEach(stack -> all.add(stack.copy()));
        return all;
    }

    // ---------------------------------------------------------------- ход времени

    public static void serverTick(Level level, BlockPos pos, BlockState state, KilnBlockEntity kiln) {
        if (level.getGameTime() % STEP != 0 || state.getValue(KilnBlock.BROKEN)) {
            return;
        }
        boolean burning = state.getValue(KilnBlock.LIT) && kiln.fuel > 0;
        if (burning) {
            kiln.fuel -= kiln.damper ? STEP : STEP / 2; // с открытой заслонкой — вдвое быстрее
            if (kiln.fuel <= 0) {
                kiln.fuel = 0;
                level.setBlock(pos, state.setValue(KilnBlock.LIT, false), 3);
                kiln.sound(SoundEvents.FIRE_EXTINGUISH, 0.6F, 0.9F);
            }
        }
        float target = burning ? targetHeat(kiln.fuel) : 0;
        double rate = burning ? (kiln.damper ? OPEN_RATE : CLOSED_RATE) : COOL_RATE;
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
            if (!kiln.damper && kiln.heat >= ZONE_LOW) {
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
                damper = !damper;
                sound(SoundEvents.IRON_TRAPDOOR_CLOSE, 0.5F, damper ? 1.2F : 0.8F);
                Pottery.say(player, damper ? "kiln_damper_open" : "kiln_damper_closed");
                changed();
                return true;
            }
            return items.isEmpty() ? status(player) : unload(player);
        }
        return load(player, stack);
    }

    private boolean load(Player player, ItemStack stack) {
        boolean greenware = Pottery.isGreenware(stack);
        boolean brick = stack.is(ModItems.RAW_BRICK.get());
        if (!greenware && !brick) {
            if (Pottery.isWare(stack)) {
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
            Pottery.say(player, damper ? "kiln_status" : "kiln_status_closed", degrees, fuelPercent,
                    Math.min(100, 100 * soak / SOAK_NEEDED), Math.round(targetHeat(fuel) * 10));
        }
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ: этап обжига, жар, к чему тянут дрова, сколько осталось и что делать дальше. */
    @Override
    public void report(Player player) {
        int degrees = Math.round(heat * 10);
        int target = Math.round(targetHeat(fuel) * 10);
        int fuelPercent = Math.round(100.0F * fuel / MAX_FUEL);
        if (broken()) {
            Pottery.say(player, "kiln_broken", repair, REPAIR_BRICKS);
        } else if (items.isEmpty()) {
            Pottery.say(player, "kiln_status_empty", degrees, fuelPercent, uses, WEAR);
        } else if (soak >= SOAK_NEEDED) {
            Pottery.say(player, heat > HOT_OPEN ? "kiln_report_cooling" : "kiln_report_ready", degrees,
                    Math.round(HOT_OPEN * 10));
        } else if (!lit() && elapsed == 0) {
            Pottery.say(player, "kiln_report_loaded", items.size(), capacity(), Math.round(TEMPER_MAX * 10));
        } else if (elapsed < TEMPER_TICKS) {
            Pottery.say(player, heat > TEMPER_MAX ? "kiln_report_temper_hot" : "kiln_report_temper", degrees,
                    Math.round(TEMPER_MAX * 10), minutes(TEMPER_TICKS - elapsed), fuelPercent, target);
        } else {
            Component damperState = Component.translatable(damper ? "message.halgventmod.pottery.damper_open"
                    : "message.halgventmod.pottery.damper_closed");
            String key = !lit() ? "kiln_report_out" : heat > OVER ? "kiln_report_over"
                    : heat < ZONE_LOW ? "kiln_report_low" : "kiln_report_firing";
            Pottery.say(player, key, Math.min(99, 100 * soak / SOAK_NEEDED), degrees, Math.round(ZONE_LOW * 10),
                    Math.round(ZONE_HIGH * 10), fuelPercent, target, damperState, minutes(SOAK_NEEDED - soak));
        }
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
        if (soak < SOAK_NEEDED / 3) {
            return raw; // толком не обожглось — какой положили, такой и вынули
        }
        int heatCracks = earlyHot / EARLY_TICKS_PER_CRACK + (hot ? HOT_CRACK : 0);
        if (level.random.nextInt(100) < Pottery.crackChance(raw, heatCracks)) {
            return raw.is(ModItems.RAW_BRICK.get()) ? new ItemStack(ModItems.SHERDS.get()) : Pottery.sherds(raw);
        }
        if (raw.is(ModItems.RAW_BRICK.get())) {
            return new ItemStack(Items.BRICK);
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
            if (smoke >= soak / 2) {
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
        tag.putBoolean("Damper", damper);
        tag.putInt("Elapsed", elapsed);
        tag.putInt("EarlyHot", earlyHot);
        tag.putInt("Soak", soak);
        tag.putInt("Over", over);
        tag.putInt("Smoke", smoke);
        tag.putInt("Uses", uses);
        tag.putInt("Repair", repair);
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
        damper = !tag.contains("Damper") || tag.getBoolean("Damper");
        elapsed = tag.getInt("Elapsed");
        earlyHot = tag.getInt("EarlyHot");
        soak = tag.getInt("Soak");
        over = tag.getInt("Over");
        smoke = tag.getInt("Smoke");
        uses = tag.getInt("Uses");
        repair = tag.getInt("Repair");
    }
}
