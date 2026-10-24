package com.nrdhvnstd.halgventmod.pottery;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Бочка для замеса. Всё на E:
 * <pre>
 *   глина (ком или блок = 4 кома), до {@link #MAX_CLAY}     — засыпать
 *   ведро или кувшин воды ({@link Pottery#BUCKET_MEASURES} меры) — залить
 *   песок или шамот                                 — отощить (лучше всего 1 к 3 глины)
 *   сырец, кирпич- и грузило-сырец (хоть треснувшие) — размочить: глина вернётся
 *   пустая рука                                     — проминать ({@link #KNEADS} ровных нажатий; в спешке — пузыри)
 *   форма для кирпича                               — отформовать кирпич-сырец из кома промятой глины
 *   палочка                                         — скатать ком и проткнуть: грузило-сырец для ткацкого станка
 *   Shift + E пустой рукой                          — забрать: промятую — гончарной глиной, иначе — что положили
 * </pre>
 * Промятая глина, пролежавшая в бочке {@link #AGE_DAYS} суток, становится лучше («вылёживание»).
 */
public class ClayBarrelBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int MAX_CLAY = 12;
    public static final int MAX_TEMPER = 8;
    public static final int KNEADS = 12;
    public static final int AGE_DAYS = 3;
    public static final double IDEAL_RATIO = 1.0 / 3.0;  // отощителя на глину
    public static final double RATIO_TOLERANCE = 0.08;
    public static final double FAT_RATIO = 0.2;          // меньше — жирная: трескается
    public static final double LEAN_RATIO = 0.45;        // больше — тощая: рассыпается
    public static final int FAT_CRACK = 15;              // % к трещинам у жирной
    public static final int GROG_CRACK = -10;            // у глины на шамоте

    private int clay;
    private int recycled;   // размоченный сырец: уже отощён
    private int sand;
    private int grog;
    private boolean water;
    private int kneads;
    private int rushed;
    private int lumps;      // промятой глины в комьях; > 0 — замес готов
    private int readyDay = -1;

    public ClayBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLAY_BARREL.get(), pos, state);
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        if (lumps > 0) {
            all.add(clay(lumps));
        } else {
            add(all, new ItemStack(Items.CLAY_BALL, clay));
            add(all, new ItemStack(ModItems.POTTER_CLAY.get(), recycled));
            add(all, new ItemStack(Items.SAND, sand));
            add(all, new ItemStack(ModItems.GROG.get(), grog));
        }
        return all;
    }

    /** Что видно в бочке: пусто, только вода или глина (ClayBarrelBlock.FILL). */
    private int look() {
        if (clay > 0 || recycled > 0 || sand > 0 || grog > 0 || lumps > 0) {
            return ClayBarrelBlock.CLAY;
        }
        return water ? ClayBarrelBlock.WATER : ClayBarrelBlock.EMPTY;
    }

    /** Сохранить, разослать и поменять вид бочки по содержимому. */
    @Override
    protected void changed() {
        BlockState state = getBlockState();
        if (level != null && !level.isClientSide && state.hasProperty(ClayBarrelBlock.FILL)
                && state.getValue(ClayBarrelBlock.FILL) != look()) {
            level.setBlock(worldPosition, state.setValue(ClayBarrelBlock.FILL, look()), 3);
        }
        super.changed();
    }

    private static void add(List<ItemStack> list, ItemStack stack) {
        if (!stack.isEmpty()) {
            list.add(stack);
        }
    }

    // ---------------------------------------------------------------- E

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.isEmpty()) {
            if (player.isShiftKeyDown()) {
                return takeOut(player);
            }
            return lumps > 0 ? status(player) : knead(player, hand);
        }
        if (stack.is(ModItems.BRICK_MOULD.get())) {
            return mould(player, hand, stack);
        }
        if (stack.is(Items.STICK)) {
            return pierce(player, hand);
        }
        if (Pottery.isWater(stack, Pottery.BUCKET_MEASURES)) {
            if (lumps > 0) {
                Pottery.say(player, "barrel_take_first");
                return true;
            }
            if (water) {
                Pottery.say(player, "barrel_has_water");
                return true;
            }
            Pottery.takeWater(player, hand, stack, Pottery.BUCKET_MEASURES);
            water = true;
            sound(SoundEvents.BUCKET_EMPTY, 1.0F, 0.9F);
            mixStatus(player);
            changed();
            return true;
        }
        int clayIn = stack.is(Items.CLAY_BALL) ? 1 : stack.is(Items.CLAY) ? 4 : 0;
        // Сырец (треснувший или неудачный) и кирпич-сырец размачивают обратно: глина не пропадает
        boolean recycle = Pottery.isGreenware(stack) || Pottery.isRawClay(stack);
        boolean temper = stack.is(Items.SAND) || stack.is(Items.RED_SAND) || stack.is(ModItems.GROG.get());
        if (clayIn == 0 && !recycle && !temper) {
            return false;
        }
        if (lumps > 0) {
            Pottery.say(player, "barrel_take_first");
            return true;
        }
        if (temper) {
            if (sand + grog >= MAX_TEMPER) {
                Pottery.say(player, "barrel_temper_full");
                return true;
            }
            if (stack.is(ModItems.GROG.get())) {
                grog++;
            } else {
                sand++;
            }
            consumeOne(player, stack);
            sound(SoundEvents.SAND_PLACE, 0.7F, 1.0F);
        } else {
            int adding = recycle ? Pottery.lumps(stack) : clayIn;
            if (clay + recycled + adding > MAX_CLAY) {
                Pottery.say(player, "barrel_full", MAX_CLAY);
                return true;
            }
            if (recycle) {
                recycled += adding;
            } else {
                clay += adding;
            }
            consumeOne(player, stack);
            sound(SoundEvents.MUD_PLACE, 0.8F, 1.0F);
        }
        kneads = 0;
        mixStatus(player);
        changed();
        return true;
    }

    private boolean knead(Player player, InteractionHand hand) {
        if (clay + recycled <= 0) {
            Pottery.say(player, "barrel_empty");
            return true;
        }
        if (!water) {
            Pottery.say(player, "barrel_need_water");
            return true;
        }
        boolean steady = strike(player, hand, ItemStack.EMPTY);
        sound(SoundEvents.MUD_STEP, 1.0F, 0.7F + 0.2F * level.random.nextFloat());
        crumbs(new ItemStack(Items.CLAY_BALL), 1.0, 4);
        if (!steady) {
            rushed++;
        }
        if (++kneads >= KNEADS) {
            lumps = clay + recycled;
            readyDay = Freshness.currentDay(level);
            sound(SoundEvents.MUD_BRICKS_PLACE, 1.0F, 0.8F);
            Pottery.say(player, "barrel_ready", lumps);
        } else {
            Pottery.say(player, steady ? "barrel_knead" : "barrel_bubble", kneads, KNEADS);
        }
        changed();
        return true;
    }

    private boolean status(Player player) {
        int aged = Freshness.currentDay(level) - readyDay;
        if (aged >= AGE_DAYS) {
            Pottery.say(player, "barrel_aged", lumps);
        } else {
            Pottery.say(player, "barrel_aging", lumps, aged, AGE_DAYS);
        }
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ: что намешано и в какой пропорции, сколько промято, сколько лежит готовая глина. */
    @Override
    public void report(Player player) {
        if (lumps > 0) {
            status(player);
        } else if (clay + recycled > 0 && water && kneads > 0) {
            Pottery.say(player, "barrel_knead", kneads, KNEADS);
        } else {
            mixStatus(player);
        }
    }

    private void mixStatus(Player player) {
        int total = clay + recycled;
        if (total == 0) {
            Pottery.say(player, "barrel_mix_empty", sand + grog, water ? 1 : 0);
            return;
        }
        String key = !water ? "barrel_mix_dry" : "barrel_mix";
        Pottery.say(player, key, total, sand + grog, Math.round(100 * ratio()));
    }

    /** Отощителя на глину; размоченный сырец уже отощён как надо. */
    private double ratio() {
        int total = clay + recycled;
        return total == 0 ? 0 : (sand + grog + recycled * IDEAL_RATIO) / total;
    }

    private boolean mould(Player player, InteractionHand hand, ItemStack mould) {
        if (lumps <= 0) {
            Pottery.say(player, "barrel_not_ready");
            return true;
        }
        ItemStack brick = new ItemStack(ModItems.RAW_BRICK.get());
        int crack = crack();
        if (crack != 0) {
            brick.set(ModComponents.CLAY_CRACK.get(), crack);
        }
        give(player, brick);
        damage(player, hand, mould, 1);
        player.swing(hand, true);
        sound(SoundEvents.MUD_BRICKS_PLACE, 1.0F, 1.0F);
        if (--lumps <= 0) {
            reset();
        }
        changed();
        return true;
    }

    /** Ком промятой глины, проткнутый палочкой, — грузило-сырец для ткацкого станка. Палочка не тратится. */
    private boolean pierce(Player player, InteractionHand hand) {
        if (lumps <= 0) {
            Pottery.say(player, "barrel_not_ready");
            return true;
        }
        ItemStack weight = new ItemStack(ModItems.RAW_LOOM_WEIGHT.get());
        int crack = crack();
        if (crack != 0) {
            weight.set(ModComponents.CLAY_CRACK.get(), crack);
        }
        give(player, weight);
        player.swing(hand, true);
        sound(SoundEvents.MUD_PLACE, 1.0F, 1.2F);
        if (--lumps <= 0) {
            reset();
        }
        changed();
        return true;
    }

    private boolean takeOut(Player player) {
        if (lumps > 0) {
            give(player, clay(lumps));
            sound(SoundEvents.MUD_BREAK, 0.8F, 1.0F);
            reset();
            changed();
            return true;
        }
        if (clay + recycled + sand + grog == 0) {
            if (water) {
                water = false;
                sound(SoundEvents.BUCKET_FILL, 0.6F, 1.0F);
                changed();
            } else {
                Pottery.say(player, "barrel_empty");
            }
            return true;
        }
        for (ItemStack stack : contents()) {
            give(player, stack);
        }
        reset();
        changed();
        return true;
    }

    private void reset() {
        clay = recycled = sand = grog = kneads = rushed = lumps = 0;
        water = false;
        readyDay = -1;
    }

    // ---------------------------------------------------------------- качество

    /** Гончарная глина этого замеса. */
    private ItemStack clay(int count) {
        ItemStack stack = new ItemStack(ModItems.POTTER_CLAY.get(), count);
        Pottery.setQuality(stack, quality());
        int crack = crack();
        if (crack != 0) {
            stack.set(ModComponents.CLAY_CRACK.get(), crack);
        }
        return stack;
    }

    private int quality() {
        int quality = Pottery.DEFAULT_QUALITY;
        double ratio = ratio();
        if (Math.abs(ratio - IDEAL_RATIO) <= RATIO_TOLERANCE) {
            quality++;
        } else if (ratio < FAT_RATIO || ratio > LEAN_RATIO) {
            quality--;
        }
        quality -= Math.min(2, rushed / 2);
        if (readyDay >= 0 && Freshness.currentDay(level) - readyDay >= AGE_DAYS) {
            quality++;
        }
        return Math.clamp(quality, 0, 5);
    }

    private int crack() {
        int crack = ratio() < FAT_RATIO ? FAT_CRACK : 0;
        if (grog > 0 && grog >= sand) {
            crack += GROG_CRACK;
        }
        return crack;
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Clay", clay);
        tag.putInt("Recycled", recycled);
        tag.putInt("Sand", sand);
        tag.putInt("Grog", grog);
        tag.putBoolean("Water", water);
        tag.putInt("Kneads", kneads);
        tag.putInt("Rushed", rushed);
        tag.putInt("Lumps", lumps);
        tag.putInt("ReadyDay", readyDay);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clay = tag.getInt("Clay");
        recycled = tag.getInt("Recycled");
        sand = tag.getInt("Sand");
        grog = tag.getInt("Grog");
        water = tag.getBoolean("Water");
        kneads = tag.getInt("Kneads");
        rushed = tag.getInt("Rushed");
        lumps = tag.getInt("Lumps");
        readyDay = tag.contains("ReadyDay") ? tag.getInt("ReadyDay") : -1;
    }

    /** Для отрисовки: глины в бочке (сырой или промятой) и есть ли вода. */
    public int fill() {
        return lumps > 0 ? lumps : clay + recycled;
    }

    public boolean wet() {
        return water;
    }

    public boolean kneaded() {
        return lumps > 0;
    }
}
