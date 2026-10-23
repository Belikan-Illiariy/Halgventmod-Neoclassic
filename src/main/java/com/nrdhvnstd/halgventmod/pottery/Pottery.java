package com.nrdhvnstd.halgventmod.pottery;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.quality.QualityDisplay;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Гончарное дело: глина → бочка для замеса (гончарная глина) → гончарный круг (сырец) → полка (сушка) →
 * горн (посуда). Здесь — общее: качество, признаки, сроки хранения, вода в посуде.
 *
 * <p>Качество глины, сырца и посуды 0–5 по общей шкале (Ужасное … Шедевр, обычное — Посредственное, 2),
 * компонент {@link ModComponents#POTTERY_QUALITY}, в подсказке — плашкой.
 */
public final class Pottery {
    // Признаки посуды (компонент WARE_FLAGS)
    public static final int DRIED = 1;        // сырец высох — можно в горн
    public static final int CRACKED = 2;      // сырец треснул при сушке — только размочить в бочке;
                                              // посуда треснула от пользования — только опорожнить или разбить
    public static final int BURNISHED = 4;    // залощён костью или кремнём — в дыму горна станет чернолощёным
    public static final int BLACK = 8;        // чернолощёная
    public static final int UNDERFIRED = 16;  // недожог: хрупкая, горшок служит вдвое меньше
    public static final int OVERFIRED = 32;   // пережог: повело

    // Меняйте здесь
    public static final int DEFAULT_QUALITY = 2;
    public static final int BUCKET_MEASURES = 4;      // ведро = 4 меры воды
    public static final int CUP_MEASURES = 1;
    public static final int JUG_MEASURES = 4;
    public static final int AMPHORA_MEASURES = 16;
    public static final int DRINK_THIRST = 25;        // мера воды из кружки или кувшина
    public static final int COOL_WATER_BONUS = 5;     // кувшин летом холодит воду
    public static final int POT_CAPACITY = 16;        // еды в горшке
    public static final int AMPHORA_DRINKS = 8;       // склянок питья в корчаге
    public static final int JUG_DRINKS = 4;           // и в кувшине
    public static final int CUP_DRINKS = 1;           // и в кружке — одна мера питья
    public static final double POT_RATE = 0.5;        // в горшке еда портится вдвое медленнее
    public static final double SEALED_RATE = 0.25;    // запечатанном воском
    public static final int POT_USES_BASE = 10;       // варок у Ужасного горшка
    public static final int POT_USES_PER_LEVEL = 6;   // и за каждую ступень качества
    // Шанс, что посуда треснет за одно пользование, в тысячных — по качеству от Ужасного до Шедевра; недожог — вдвое
    public static final int[] WEAR_CRACK = {60, 40, 25, 15, 8, 3};
    public static final int LAMP_SALO = 6000;         // тиков света от куска сала
    public static final int LAMP_WAX = 9000;          // от воска
    public static final int LAMP_MAX_FUEL = 18000;

    private Pottery() {}

    // ---------------------------------------------------------------- качество и признаки

    public static int quality(ItemStack stack) {
        Integer quality = stack.get(ModComponents.POTTERY_QUALITY.get());
        return quality == null ? DEFAULT_QUALITY : quality;
    }

    public static ItemStack setQuality(ItemStack stack, int quality) {
        stack.set(ModComponents.POTTERY_QUALITY.get(), Math.clamp(quality, 0, 5));
        QualityDisplay.restoreRarity(stack);
        return stack;
    }

    public static int crack(ItemStack stack) {
        Integer crack = stack.get(ModComponents.CLAY_CRACK.get());
        return crack == null ? 0 : crack;
    }

    public static int flags(ItemStack stack) {
        Integer flags = stack.get(ModComponents.WARE_FLAGS.get());
        return flags == null ? 0 : flags;
    }

    public static boolean has(ItemStack stack, int flag) {
        return (flags(stack) & flag) != 0;
    }

    public static void addFlags(ItemStack stack, int flags) {
        stack.set(ModComponents.WARE_FLAGS.get(), flags(stack) | flags);
    }

    /**
     * Сырец или кирпич-сырец высох (или треснул при сушке): признак и уже не [Сырой] — сырость видно по признакам,
     * названию и цвету.
     */
    public static void dried(ItemStack stack, boolean cracked) {
        addFlags(stack, cracked ? CRACKED : DRIED);
        java.util.List<String> traits = stack.get(ModComponents.TRAITS.get());
        if (traits != null && traits.contains(com.nrdhvnstd.halgventmod.item.ModTraits.RAW.id())) {
            stack.set(ModComponents.TRAITS.get(), traits.stream()
                    .filter(id -> !id.equals(com.nrdhvnstd.halgventmod.item.ModTraits.RAW.id())).toList());
        }
    }

    /** Посуда треснула от пользования: пользоваться нельзя, только опорожнить или разбить на черепки. */
    public static boolean cracked(ItemStack stack) {
        return isWare(stack) && has(stack, CRACKED);
    }

    /** Шанс в тысячных, что посудина треснет за одно пользование. */
    public static int wearChance(ItemStack ware) {
        int chance = WEAR_CRACK[Math.clamp(quality(ware), 0, WEAR_CRACK.length - 1)];
        return has(ware, UNDERFIRED) ? chance * 2 : chance;
    }

    /**
     * Посудой попользовались — может треснуть: вода выливается, светильник гаснет. Бросок только на сервере.
     * @return треснула ли сейчас
     */
    public static boolean wear(ItemStack ware, Player player) {
        if (player.level().isClientSide || player.hasInfiniteMaterials() || !isWare(ware) || has(ware, CRACKED)
                || player.getRandom().nextInt(1000) >= wearChance(ware)) {
            return false;
        }
        addFlags(ware, CRACKED);
        ware.remove(ModComponents.WATER.get());
        ware.remove(ModComponents.LAMP_LIT.get());
        player.level().playSound(null, player.blockPosition(), SoundEvents.DECORATED_POT_BREAK, SoundSource.PLAYERS, 0.8F, 1.3F);
        say(player, "ware_cracked", ware.getHoverName());
        return true;
    }

    public static void removeFlags(ItemStack stack, int flags) {
        int left = flags(stack) & ~flags;
        if (left == 0) {
            stack.remove(ModComponents.WARE_FLAGS.get());
        } else {
            stack.set(ModComponents.WARE_FLAGS.get(), left);
        }
    }

    // ---------------------------------------------------------------- форма

    public static boolean isGreenware(ItemStack stack) {
        return stack.is(ModItems.GREENWARE.get());
    }

    /** Сырая глина, отформованная руками: кирпич-сырец и грузило-сырец. Сохнут и обжигаются, как сырец. */
    public static boolean isRawClay(ItemStack stack) {
        return stack.is(ModItems.RAW_BRICK.get()) || stack.is(ModItems.RAW_LOOM_WEIGHT.get());
    }

    public static boolean isWare(ItemStack stack) {
        return stack.getItem() instanceof WareItem;
    }

    /** Форма сырца или посудины; null — не посуда. */
    @Nullable
    public static VesselForm form(ItemStack stack) {
        if (stack.getItem() instanceof WareItem ware) {
            return ware.form();
        }
        if (isGreenware(stack)) {
            return vessel(stack).vesselForm();
        }
        return null;
    }

    /** Форма по вокселям; у вещи без своей — образец. */
    public static Vessel vessel(ItemStack stack) {
        Vessel vessel = stack.get(ModComponents.VESSEL.get());
        if (vessel != null) {
            return vessel;
        }
        return Vessel.template(stack.getItem() instanceof WareItem ware ? ware.form() : VesselForm.CUP);
    }

    public static Item wareItem(VesselForm form) {
        return switch (form) {
            case CUP -> ModItems.CLAY_CUP.get();
            case BOWL -> ModItems.CLAY_BOWL.get();
            case JUG -> ModItems.CLAY_JUG.get();
            case POT -> ModItems.CLAY_POT.get();
            case AMPHORA -> ModItems.AMPHORA.get();
            case LAMP -> ModItems.OIL_LAMP.get();
            case CRUCIBLE -> ModItems.CLAY_CRUCIBLE.get();
        };
    }

    /** Сколько глины в вещи — столько черепков, если разобьётся, и комьев, если размочить. */
    public static int lumps(ItemStack stack) {
        VesselForm form = form(stack);
        return form != null ? form.lumps() : 1;
    }

    /** Черепки от разбитой вещи. */
    public static ItemStack sherds(ItemStack stack) {
        return new ItemStack(ModItems.SHERDS.get(), lumps(stack));
    }

    /** Гончарная глина того же замеса: что получится, если сырец размочить. */
    public static ItemStack clayOf(ItemStack stack, int count) {
        ItemStack clay = new ItemStack(ModItems.POTTER_CLAY.get(), count);
        setQuality(clay, quality(stack));
        int crack = crack(stack);
        if (crack != 0) {
            clay.set(ModComponents.CLAY_CRACK.get(), crack);
        }
        return clay;
    }

    /** Шанс треснуть в процентах: от условий, глины и приставных деталей (у ручек тонкие места). */
    public static int crackChance(ItemStack stack, int base) {
        VesselForm form = form(stack);
        int details = form != null ? form.details() * 5 : 0;
        return Math.clamp(base + crack(stack) + details, 0, 95);
    }

    // ---------------------------------------------------------------- вода

    /** Сколько мер воды вмещает посудина; 0 — воду не держит. */
    public static int waterCapacity(ItemStack stack) {
        VesselForm form = form(stack);
        if (form == null || !isWare(stack) || has(stack, CRACKED)) {
            return 0;
        }
        return switch (form) {
            case CUP -> CUP_MEASURES;
            case JUG -> JUG_MEASURES;
            case AMPHORA -> AMPHORA_MEASURES;
            default -> 0;
        };
    }

    public static int water(ItemStack stack) {
        Integer water = stack.get(ModComponents.WATER.get());
        return water == null ? 0 : water;
    }

    public static void setWater(ItemStack stack, int measures) {
        if (measures <= 0) {
            stack.remove(ModComponents.WATER.get());
        } else {
            stack.set(ModComponents.WATER.get(), measures);
        }
    }

    /**
     * Взять воду из того, что в руке: ведро (≤ {@link #BUCKET_MEASURES} мер — пустеет целиком) или посудина с водой.
     * @return true — вода взята
     */
    public static boolean takeWater(Player player, net.minecraft.world.InteractionHand hand, ItemStack stack, int measures) {
        if (stack.is(net.minecraft.world.item.Items.WATER_BUCKET) && measures <= BUCKET_MEASURES) {
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, new ItemStack(net.minecraft.world.item.Items.BUCKET));
            }
            return true;
        }
        if (waterCapacity(stack) > 0 && water(stack) >= measures) {
            if (!player.getAbilities().instabuild) {
                setWater(stack, water(stack) - measures);
            }
            wear(stack, player);
            return true;
        }
        return false;
    }

    /** Есть ли в руке вода на столько мер — для подсказок станций. */
    public static boolean isWater(ItemStack stack, int measures) {
        return (stack.is(net.minecraft.world.item.Items.WATER_BUCKET) && measures <= BUCKET_MEASURES)
                || (waterCapacity(stack) > 0 && water(stack) >= measures);
    }

    // ---------------------------------------------------------------- хранение

    /** Во сколько раз медленнее портится внутри: доля обычной скорости порчи. */
    public static double rate(ItemStack vessel, boolean sealed) {
        return sealed ? SEALED_RATE : POT_RATE;
    }

    /** Вынуть содержимое: срок годности сдвигается на сбережённые сутки. */
    /**
     * Можно ли налить ещё склянку этого питья в посудину: корчага, кувшин или кружка, целая, без воды и печати, пустая или
     * с тем же питьём, не полная. Наливают перегонный куб, бродильная бочка, давильня и горшок на огне.
     */
    public static boolean canPour(ItemStack vessel, ItemStack drink, Level level) {
        if (drinkCapacity(vessel) <= 0 || !isWare(vessel) || cracked(vessel) || water(vessel) > 0) {
            return false;
        }
        Stored stored = vessel.get(ModComponents.STORED.get());
        if (stored == null) {
            return true;
        }
        ItemStack inside = unpack(stored, vessel.copy(), level);
        return !stored.sealed() && inside.getCount() < drinkCapacity(vessel) && ItemStack.isSameItemSameComponents(inside, drink);
    }

    /** Питьё в корчаге, кувшине или кружке (одна склянка, сколько всего — getCount); пусто — нет или запечатано. */
    public static ItemStack storedDrink(ItemStack vessel, Level level) {
        if (drinkCapacity(vessel) <= 0) {
            return ItemStack.EMPTY;
        }
        Stored stored = vessel.get(ModComponents.STORED.get());
        return stored == null || stored.sealed() ? ItemStack.EMPTY : unpack(stored, vessel, level);
    }

    /** Отлить одну склянку питья из корчаги или кувшина (вылить в бочку, куб). */
    public static void takeDrink(ItemStack vessel, Level level) {
        ItemStack inside = storedDrink(vessel, level);
        if (inside.isEmpty()) {
            return;
        }
        if (inside.getCount() <= 1) {
            vessel.remove(ModComponents.STORED.get());
        } else {
            vessel.set(ModComponents.STORED.get(), new Stored(inside.copyWithCount(inside.getCount() - 1),
                    Freshness.currentDay(level), false));
        }
    }

    /** Сколько склянок питья держит посудина: корчага, кувшин, кружка; 0 — питьё не держит. */
    public static int drinkCapacity(ItemStack vessel) {
        VesselForm form = form(vessel);
        if (form == null) {
            return 0;
        }
        return switch (form) {
            case AMPHORA -> AMPHORA_DRINKS;
            case JUG -> JUG_DRINKS;
            case CUP -> CUP_DRINKS;
            default -> 0;
        };
    }

    /** Налить склянку питья в корчагу или кувшин (сначала canPour). */
    public static void pour(ItemStack vessel, ItemStack drink, Level level) {
        Stored stored = vessel.get(ModComponents.STORED.get());
        ItemStack inside = stored == null ? ItemStack.EMPTY : unpack(stored, vessel, level);
        ItemStack next = inside.isEmpty() ? drink.copyWithCount(1) : inside.copyWithCount(inside.getCount() + 1);
        vessel.set(ModComponents.STORED.get(), new Stored(next, Freshness.currentDay(level), false));
    }

    public static ItemStack unpack(Stored stored, ItemStack vessel, Level level) {
        ItemStack stack = stored.stack().copy();
        Integer created = stack.get(ModComponents.CREATED_DAY.get());
        if (created != null) {
            int elapsed = Math.max(0, Freshness.currentDay(level) - stored.since());
            int saved = (int) Math.floor(elapsed * (1.0 - rate(vessel, stored.sealed())));
            stack.set(ModComponents.CREATED_DAY.get(), created + saved);
        }
        return stack;
    }

    /** Сколько варок выдержит горшок на очаге. */
    public static int potUses(ItemStack pot) {
        int uses = POT_USES_BASE + POT_USES_PER_LEVEL * quality(pot);
        return has(pot, UNDERFIRED) ? uses / 2 : uses;
    }

    public static int potUsed(ItemStack pot) {
        Integer used = pot.get(ModComponents.POT_USES.get());
        return used == null ? 0 : used;
    }

    // ---------------------------------------------------------------- сообщения

    public static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.pottery." + key, args), true);
    }
}
