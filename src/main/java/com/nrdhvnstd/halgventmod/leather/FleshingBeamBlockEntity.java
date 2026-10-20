package com.nrdhvnstd.halgventmod.leather;

import java.util.List;

import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Колода: на ней лежит одна шкура (или кусок сыромяти).
 * <ul>
 *   <li>соль по парной шкуре — солёная (хранится 16 суток);</li>
 *   <li>мездряк по парной или солёной — снять мездру. На шкуре лежат клочья мездры (сетка {@link #GRID}×{@link #GRID},
 *       у каждого клочка 1–{@link #MAX_THICKNESS} слоя): бить нужно точно по ним, каждый ровный удар снимает слой.
 *       Удар по чистому месту скребёт голую шкуру и может её прорезать;</li>
 *   <li>струг по золёной — снять волос ({@link #HAIR_STROKES} ударов), получается голье; с овцы падает шерсть;</li>
 *   <li>струг по сыромяти мелкой шкуры — пергамент ({@link #PARCHMENT_STROKES} удара);</li>
 *   <li>пустая рука — забрать.</li>
 * </ul>
 * Ритм: удар до того, как замах восстановился, с шансом {@link #CUT_CHANCE} режет шкуру — качество −1
 * (не больше {@link #MAX_CUTS} раз за работу).
 */
public class FleshingBeamBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int HAIR_STROKES = 5;
    public static final int PARCHMENT_STROKES = 3;
    public static final float CUT_CHANCE = 0.4F;
    public static final float CLEAN_SPOT_CUT_CHANCE = 0.25F; // ровный удар мимо мездры (спешный — CUT_CHANCE)
    public static final int MAX_CUTS = 2;
    public static final int GRID = 3;              // участков по стороне шкуры
    public static final float HIDE_HALF = 0.375F;  // полуширина шкуры на колоде, блоков
    public static final int MIN_PATCHES = 3;
    public static final int MAX_PATCHES = 6;
    public static final int MAX_THICKNESS = 3;

    private ItemStack hide = ItemStack.EMPTY;
    private int strokes;
    private int cuts;
    /** Толщина мездры на участках (индекс x + z * GRID, в осях мира), 0 — чисто. */
    private int[] flesh = new int[GRID * GRID];

    public FleshingBeamBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLESHING_BEAM.get(), pos, state);
    }

    public ItemStack hide() {
        return hide;
    }

    /** Толщина мездры на участке — для отрисовки. */
    public int flesh(int cell) {
        return flesh[cell];
    }

    /** Центр участка по x или z относительно центра блока. */
    public static float cellCenter(int i) {
        float size = HIDE_HALF * 2 / GRID;
        return -HIDE_HALF + size * (i + 0.5F);
    }

    @Override
    public List<ItemStack> contents() {
        return hide.isEmpty() ? List.of() : List.of(hide.copy());
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        return use(player, hand, stack, worldPosition.getCenter());
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack, Vec3 hit) {
        if (hide.isEmpty()) {
            return place(player, stack);
        }
        if (stack.isEmpty()) {
            give(player, hide);
            hide = ItemStack.EMPTY;
            reset();
            sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 1.0F);
            changed();
            return true;
        }
        if (Freshness.isSpoiled(hide, level)) {
            say(player, "rotten");
            return true;
        }
        Hide info = hide.get(ModComponents.HIDE.get());
        if (stack.is(ModItems.SALT.get())) {
            if (!hide.is(ModItems.RAW_HIDE.get())) {
                say(player, "salt_only_raw");
                return true;
            }
            consumeOne(player, stack);
            hide = LeatherQuality.carry(hide, new ItemStack(ModItems.SALTED_HIDE.get()));
            sound(SoundEvents.SAND_PLACE, 0.8F, 1.2F);
            crumbs(new ItemStack(ModItems.SALT.get()), 1.1, 8);
            say(player, "salted");
            changed();
            return true;
        }
        if (stack.is(ModItems.FLESHING_KNIFE.get())) {
            if (!(hide.is(ModItems.RAW_HIDE.get()) || hide.is(ModItems.SALTED_HIDE.get())) || info == null) {
                say(player, "knife_wrong");
                return true;
            }
            if (info.fleshed()) {
                say(player, "already_fleshed");
                return true;
            }
            flesh(player, hand, stack, info, cellAt(hit));
            changed();
            return true;
        }
        if (stack.is(ModItems.SCRAPER.get())) {
            if (hide.is(ModItems.LIMED_HIDE.get()) && info != null) {
                work(player, hand, stack, HAIR_STROKES);
                crumbs(new ItemStack(Items.WHITE_WOOL), 1.1, 3);
                if (strokes >= HAIR_STROKES) {
                    hide = LeatherQuality.carry(hide, new ItemStack(ModItems.PELT.get()));
                    hide.set(ModComponents.HIDE.get(), info.withoutFur());
                    if ("sheep".equals(info.animal())) {
                        give(player, new ItemStack(Items.WHITE_WOOL, 1 + level.random.nextInt(2)));
                    }
                    say(player, "dehaired");
                    finish();
                }
                changed();
                return true;
            }
            if (hide.is(ModItems.RAWHIDE.get())) {
                if (info == null || !info.parchmentGrade()) {
                    say(player, "parchment_small_only");
                    return true;
                }
                work(player, hand, stack, PARCHMENT_STROKES);
                crumbs(hide, 1.1, 4);
                if (strokes >= PARCHMENT_STROKES) {
                    hide = LeatherQuality.carry(hide, new ItemStack(ModItems.PARCHMENT.get()));
                    say(player, "parchment");
                    finish();
                }
                changed();
                return true;
            }
            say(player, "scraper_wrong");
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- мездрение

    /** Удар мездряком по участку cell. */
    private void flesh(Player player, InteractionHand hand, ItemStack knife, Hide info, int cell) {
        if (fleshLeft() == 0) {
            spreadFlesh(info); // шкура, положенная до этой версии
        }
        boolean steady = strike(player, hand, knife);
        if (flesh[cell] == 0) {
            // Мимо мездры: нож скребёт голую шкуру
            float chance = steady ? CLEAN_SPOT_CUT_CHANCE : CUT_CHANCE;
            if (cuts < MAX_CUTS && level.random.nextFloat() < chance) {
                cutHide(player);
            } else {
                sound(SoundEvents.WOOL_HIT, 0.8F, 1.4F);
                say(player, "flesh_miss", fleshLeft());
            }
            return;
        }
        if (!steady && cuts < MAX_CUTS && level.random.nextFloat() < CUT_CHANCE) {
            cutHide(player);
            return;
        }
        flesh[cell]--;
        sound(SoundEvents.SLIME_SQUISH_SMALL, 0.9F, 0.7F + 0.4F * level.random.nextFloat());
        crumbsAt(cell);
        if (flesh[cell] > 0) {
            say(player, "flesh_layer", flesh[cell]);
        } else if (fleshLeft() > 0) {
            say(player, "flesh_left", fleshLeft());
        } else {
            hide.set(ModComponents.HIDE.get(), info.withFleshed());
            give(player, new ItemStack(ModItems.FLESH.get(), 1 + Math.max(0, info.pieces() / 3)));
            say(player, "fleshed");
            finish();
        }
    }

    private int cellAt(Vec3 hit) {
        float size = HIDE_HALF * 2 / GRID;
        int x = Mth.clamp((int) Math.floor((hit.x - worldPosition.getX() - 0.5 + HIDE_HALF) / size), 0, GRID - 1);
        int z = Mth.clamp((int) Math.floor((hit.z - worldPosition.getZ() - 0.5 + HIDE_HALF) / size), 0, GRID - 1);
        return x + z * GRID;
    }

    private int fleshLeft() {
        int left = 0;
        for (int layer : flesh) {
            if (layer > 0) {
                left++;
            }
        }
        return left;
    }

    /** Разбросать клочья мездры по шкуре: на большой — больше. */
    private void spreadFlesh(Hide info) {
        flesh = new int[GRID * GRID];
        int patches = Mth.clamp(MIN_PATCHES + info.pieces() / 2, MIN_PATCHES, MAX_PATCHES);
        for (int placed = 0; placed < patches; ) {
            int cell = level.random.nextInt(flesh.length);
            if (flesh[cell] == 0) {
                flesh[cell] = 1 + level.random.nextInt(MAX_THICKNESS);
                placed++;
            }
        }
    }

    private void cutHide(Player player) {
        cuts++;
        LeatherQuality.change(hide, -1);
        sound(SoundEvents.SHEEP_SHEAR, 1.0F, 0.6F);
        say(player, "cut");
    }

    /** Клочья мездры отлетают от участка. */
    private void crumbsAt(int cell) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ModItems.FLESH.get())),
                    worldPosition.getX() + 0.5 + cellCenter(cell % GRID), worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5 + cellCenter(cell / GRID), 6, 0.05, 0.02, 0.05, 0.08);
        }
    }

    // ---------------------------------------------------------------- прочее

    private boolean place(Player player, ItemStack stack) {
        boolean accepted = stack.is(ModItems.RAW_HIDE.get()) || stack.is(ModItems.SALTED_HIDE.get())
                || stack.is(ModItems.LIMED_HIDE.get()) || stack.is(ModItems.RAWHIDE.get());
        if (!accepted) {
            if (stack.isEmpty() || isLeatherTool(stack)) {
                say(player, "beam_empty");
                return true;
            }
            return false;
        }
        hide = stack.copyWithCount(1);
        consumeOne(player, stack);
        reset();
        Hide info = hide.get(ModComponents.HIDE.get());
        if (info != null && !info.fleshed() && !hide.is(ModItems.LIMED_HIDE.get()) && !hide.is(ModItems.RAWHIDE.get())) {
            spreadFlesh(info);
        }
        sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 0.8F);
        changed();
        return true;
    }

    /** Один удар стругом: ритм, порез, звук. */
    private void work(Player player, InteractionHand hand, ItemStack tool, int total) {
        boolean steady = strike(player, hand, tool);
        strokes++;
        if (!steady && cuts < MAX_CUTS && level.random.nextFloat() < CUT_CHANCE) {
            cutHide(player);
        } else {
            sound(SoundEvents.SLIME_SQUISH_SMALL, 0.8F, 0.8F + 0.4F * level.random.nextFloat());
            say(player, "stroke", strokes, total);
        }
    }

    private void reset() {
        strokes = 0;
        cuts = 0;
        flesh = new int[GRID * GRID];
    }

    private void finish() {
        reset();
        sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.2F);
    }

    public static boolean isLeatherTool(ItemStack stack) {
        return stack.is(ModItems.FLESHING_KNIFE.get()) || stack.is(ModItems.SCRAPER.get())
                || stack.is(ModItems.SALT.get());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!hide.isEmpty()) {
            tag.put("Hide", hide.save(registries));
        }
        tag.putInt("Strokes", strokes);
        tag.putInt("Cuts", cuts);
        tag.putIntArray("Flesh", flesh);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hide = tag.contains("Hide") ? ItemStack.parseOptional(registries, tag.getCompound("Hide")) : ItemStack.EMPTY;
        strokes = tag.getInt("Strokes");
        cuts = tag.getInt("Cuts");
        int[] saved = tag.getIntArray("Flesh");
        flesh = saved.length == GRID * GRID ? saved : new int[GRID * GRID];
    }
}
