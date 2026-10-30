package com.nrdhvnstd.halgventmod.leather;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import com.nrdhvnstd.halgventmod.combat.Blood;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Дубильный чан. Время считается по игровым суткам ({@code dayTime}): ночь, проспанная в кровати, тоже идёт.
 * <pre>
 *   вода (ведро) + 3 золы          → щёлок:  мездрёная шкура   → золёная        за {@link #LIME_DAYS} сут.
 *   вода + 4 коры                  → корьё:  голье             → мокрая (дубл.) за {@link #TAN_DAYS} сут.
 *   вода + 2 квасцов + 1 соль      → квасцы: голье             → мокрая (мягк.) за {@link #TAW_DAYS} сут.
 *   вода + 2 сала                  → жир:    мездрёная с мехом → мокрая (мех)   за {@link #FUR_DAYS} сут.
 *   вода над огнём, пусто          → соль ×{@link #SALT_YIELD}                  за полсуток
 *   вода над огнём + мездра/гниль  → мездровый клей                              за полсуток
 *   вода над огнём + дублёная кожа → варёная кожа                                за {@link #BOIL_TICKS} тиков
 * </pre>
 * Раствора хватает на {@link #USES} шкур. Корьё, вынутое после половины срока, даёт «недодублённую» кожу (качество −1).
 */
public class TanningVatBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int SLOTS = 4;
    public static final int USES = 4;
    public static final double LIME_DAYS = 3;
    public static final double TAN_DAYS = 8;
    public static final double TAW_DAYS = 3;
    public static final double FUR_DAYS = 2;
    public static final double HEAT_DAYS = 0.5;  // выпаривание соли и варка клея
    public static final int BOIL_TICKS = 600;    // варка кожи — 30 секунд над огнём
    public static final int SALT_YIELD = 4;
    public static final int ASH_NEEDED = 3;
    public static final int BARK_NEEDED = 4;
    public static final int ALUM_NEEDED = 2;
    public static final int SALT_NEEDED = 1;
    public static final int FAT_NEEDED = 2;
    private static final long DAY = 24000L;

    /** Что происходит со шкурой в чане. */
    enum Process {
        LIME(LIME_DAYS, false), TAN(TAN_DAYS, false), TAW(TAW_DAYS, false), FUR(FUR_DAYS, false),
        GLUE(HEAT_DAYS, true), BOIL(0, true);

        final double days;
        final boolean heat;

        Process(double days, boolean heat) {
            this.days = days;
            this.heat = heat;
        }

        long required() {
            return this == BOIL ? BOIL_TICKS : (long) (days * DAY);
        }

        /** Расходует раствор (щёлок, корьё, квасцы, жир); вода — нет. */
        boolean usesSolution() {
            return !heat;
        }
    }

    private Solution solution = Solution.NONE;
    private int uses;
    private int ash, bark, alum, salt, fat;
    private final ItemStack[] slots = new ItemStack[SLOTS];
    private final long[] progress = new long[SLOTS];
    private final boolean[] done = new boolean[SLOTS];
    private long lastDayTime = -1;
    private long boilEvaporation;

    public TanningVatBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANNING_VAT.get(), pos, state);
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
    }

    /**
     * Чан как закалочная бочка (кузнечество, SmithArea): 1 — вода, 2 — рассол (вода с солью, без квасцов), 3 — жир
     * (закалка в масле); 0 — в таком растворе не закаливают.
     */
    public int quenchLiquid() {
        if (solution == Solution.WATER) {
            return salt > 0 && alum == 0 ? 2 : 1;
        }
        return solution == Solution.FAT ? 3 : 0;
    }

    public Solution solution() {
        return solution;
    }

    /** Что лежит в чане — для отрисовки. */
    public List<ItemStack> items() {
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        return items;
    }

    @Override
    public List<ItemStack> contents() {
        return items().stream().map(ItemStack::copy).toList();
    }

    public static boolean heated(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return CampfireBlock.isLitCampfire(below) || below.is(BlockTags.FIRE) || below.is(Blocks.MAGMA_BLOCK)
                || below.getFluidState().is(FluidTags.LAVA);
    }

    // ---------------------------------------------------------------- ход времени

    public static void serverTick(Level level, BlockPos pos, BlockState state, TanningVatBlockEntity vat) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        long now = level.getDayTime();
        long delta = vat.lastDayTime < 0 ? 0 : Math.clamp(now - vat.lastDayTime, 0, 4 * DAY);
        vat.lastDayTime = now;
        boolean heat = heated(level, pos);
        boolean changed = false;
        boolean empty = true;
        for (int i = 0; i < SLOTS; i++) {
            if (vat.slots[i].isEmpty()) {
                continue;
            }
            empty = false;
            if (vat.done[i]) {
                continue;
            }
            Process process = vat.process(vat.slots[i]);
            if (process == null || (process.heat && !heat)) {
                continue;
            }
            vat.progress[i] += process == Process.BOIL ? 20 : delta;
            if (vat.progress[i] >= process.required()) {
                vat.complete(i, process);
                changed = true;
            }
        }
        // Пустой чан с водой над огнём выпаривается в соль
        if (empty && vat.solution == Solution.WATER && heat && vat.noAdditives()) {
            vat.boilEvaporation += delta;
            if (vat.boilEvaporation >= (long) (HEAT_DAYS * DAY)) {
                vat.slots[0] = new ItemStack(ModItems.SALT.get(), SALT_YIELD);
                vat.done[0] = true;
                vat.solution = Solution.NONE;
                vat.boilEvaporation = 0;
                changed = true;
            }
        } else {
            vat.boilEvaporation = 0;
        }
        if (changed) {
            vat.sound(SoundEvents.BREWING_STAND_BREW, 0.6F, 0.8F);
            vat.changed();
        } else {
            vat.setChanged();
        }
    }

    @Nullable
    private Process process(ItemStack stack) {
        boolean hide = stack.is(ModItems.RAW_HIDE.get()) || stack.is(ModItems.SALTED_HIDE.get());
        if (solution == Solution.WATER) {
            if (stack.is(ModItems.TANNED_LEATHER.get())) {
                return Process.BOIL;
            }
            if (stack.is(ModItems.FLESH.get()) || (level != null && rotten(stack))) {
                return Process.GLUE;
            }
            return null;
        }
        return switch (solution) {
            case LYE -> hide ? Process.LIME : null;
            case BARK -> stack.is(ModItems.PELT.get()) ? Process.TAN : null;
            case ALUM -> stack.is(ModItems.PELT.get()) ? Process.TAW : null;
            case FAT -> hide ? Process.FUR : null;
            default -> null;
        };
    }

    private boolean rotten(ItemStack stack) {
        return stack.has(ModComponents.HIDE.get()) && Freshness.isSpoiled(stack, level);
    }

    private void complete(int i, Process process) {
        ItemStack from = slots[i];
        Hide hide = from.get(ModComponents.HIDE.get());
        ItemStack result = switch (process) {
            case LIME -> {
                ItemStack limed = LeatherQuality.carry(from, new ItemStack(ModItems.LIMED_HIDE.get()));
                if (hide != null) {
                    limed.set(ModComponents.HIDE.get(), hide.withoutFur());
                }
                yield limed;
            }
            case TAN -> wet(from, Tannage.TANNED);
            case TAW -> wet(from, Tannage.SOFT);
            case FUR -> wet(from, Tannage.FUR);
            case GLUE -> new ItemStack(ModItems.HIDE_GLUE.get(),
                    from.is(ModItems.FLESH.get()) ? Math.max(1, from.getCount() / 2) : 2);
            case BOIL -> LeatherQuality.carry(from, new ItemStack(ModItems.BOILED_LEATHER.get(), from.getCount()));
        };
        slots[i] = result;
        done[i] = true;
        progress[i] = 0;
        if (process.usesSolution()) {
            uses--;
            if (uses <= 0) {
                solution = Solution.NONE;
            }
        }
    }

    private static ItemStack wet(ItemStack from, Tannage tannage) {
        ItemStack wet = LeatherQuality.carry(from, new ItemStack(ModItems.WET_LEATHER.get()));
        wet.set(ModComponents.TANNAGE.get(), tannage.id());
        return wet;
    }

    // ---------------------------------------------------------------- щелчки

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        // Чистая вода отмывает кровь (combat/Blood): вещь в руке, а Shift+E пустой рукой — всю надетую броню
        if (solution == Solution.WATER && items().isEmpty()) {
            boolean washed = stack.isEmpty() ? player.isShiftKeyDown() && Blood.washArmor(player, 1) : Blood.wash(stack);
            if (washed) {
                sound(SoundEvents.BUCKET_EMPTY, 0.8F, 1.2F);
                player.displayClientMessage(Component.translatable("message.halgventmod.blood.washed"), true);
                return true;
            }
        }
        if (stack.isEmpty()) {
            return takeOut(player);
        }
        if (Pottery.isWater(stack, Pottery.BUCKET_MEASURES)) {
            if (solution != Solution.NONE || !items().isEmpty()) {
                say(player, "vat_busy");
                return true;
            }
            solution = Solution.WATER;
            resetAdditives();
            Pottery.takeWater(player, hand, stack, Pottery.BUCKET_MEASURES); // ведро или полный кувшин
            sound(SoundEvents.BUCKET_EMPTY, 1.0F, 1.0F);
            changed();
            return true;
        }
        if (stack.is(Items.BUCKET)) {
            if (solution == Solution.NONE || !items().isEmpty()) {
                say(player, solution == Solution.NONE ? "vat_dry" : "vat_take_first");
                return true;
            }
            if (solution == Solution.WATER && !player.getAbilities().instabuild) {
                stack.shrink(1);
                give(player, new ItemStack(Items.WATER_BUCKET));
            }
            solution = Solution.NONE;
            resetAdditives();
            sound(SoundEvents.BUCKET_FILL, 1.0F, 1.0F);
            changed();
            return true;
        }
        if (addAdditive(player, stack)) {
            return true;
        }
        return putIn(player, stack);
    }

    private boolean addAdditive(Player player, ItemStack stack) {
        boolean additive = stack.is(ModItems.ASH.get()) || stack.is(ModItems.BARK.get()) || stack.is(ModItems.ALUM.get())
                || stack.is(ModItems.SALT.get()) || stack.is(ModItems.SALO.get());
        if (!additive) {
            return false;
        }
        if (solution != Solution.WATER) {
            say(player, solution == Solution.NONE ? "vat_dry" : "vat_already", Component.translatable(solution.langKey()));
            return true;
        }
        if (!items().isEmpty()) {
            say(player, "vat_take_first");
            return true;
        }
        consumeOne(player, stack);
        if (stack.is(ModItems.ASH.get())) ash++;
        else if (stack.is(ModItems.BARK.get())) bark++;
        else if (stack.is(ModItems.ALUM.get())) alum++;
        else if (stack.is(ModItems.SALT.get())) salt++;
        else fat++;
        sound(SoundEvents.GENERIC_SPLASH, 0.5F, 1.2F);
        Solution made = ash >= ASH_NEEDED ? Solution.LYE
                : bark >= BARK_NEEDED ? Solution.BARK
                : alum >= ALUM_NEEDED && salt >= SALT_NEEDED ? Solution.ALUM
                : fat >= FAT_NEEDED ? Solution.FAT
                : null;
        if (made != null) {
            solution = made;
            uses = USES;
            resetAdditives();
            sound(SoundEvents.BREWING_STAND_BREW, 0.8F, 1.0F);
            say(player, "solution_ready", Component.translatable(made.langKey()));
        } else {
            say(player, "additives", ash, ASH_NEEDED, bark, BARK_NEEDED, alum, ALUM_NEEDED, salt, fat, FAT_NEEDED);
        }
        changed();
        return true;
    }

    private boolean putIn(Player player, ItemStack stack) {
        boolean candidate = stack.is(ModItems.RAW_HIDE.get()) || stack.is(ModItems.SALTED_HIDE.get())
                || stack.is(ModItems.PELT.get()) || stack.is(ModItems.FLESH.get())
                || stack.is(ModItems.TANNED_LEATHER.get()) || stack.is(ModItems.LIMED_HIDE.get());
        if (!candidate) {
            return false;
        }
        if (solution == Solution.NONE) {
            say(player, "vat_dry");
            return true;
        }
        int free = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i].isEmpty()) {
                free = i;
                break;
            }
        }
        if (free < 0) {
            say(player, "vat_full");
            return true;
        }
        Hide hide = stack.get(ModComponents.HIDE.get());
        boolean rawOrSalted = stack.is(ModItems.RAW_HIDE.get()) || stack.is(ModItems.SALTED_HIDE.get());
        if (rawOrSalted && hide != null && !hide.fleshed() && !rotten(stack)
                && (solution == Solution.LYE || solution == Solution.FAT)) {
            say(player, "flesh_first");
            return true;
        }
        if (solution == Solution.FAT && rawOrSalted && hide != null && !hide.fur()) {
            say(player, "fat_needs_fur");
            return true;
        }
        if (rotten(stack) && solution != Solution.WATER) {
            say(player, "rotten");
            return true;
        }
        Process process = process(stack);
        if (process == null) {
            say(player, "vat_wrong", Component.translatable(solution.langKey()));
            return true;
        }
        if (process.usesSolution() && inProcess() >= uses) {
            say(player, "vat_spent");
            return true;
        }
        boolean wholeStack = stack.is(ModItems.FLESH.get()) || stack.is(ModItems.TANNED_LEATHER.get());
        slots[free] = wholeStack ? stack.copy() : stack.copyWithCount(1);
        if (!player.getAbilities().instabuild) {
            stack.shrink(wholeStack ? stack.getCount() : 1);
        }
        progress[free] = 0;
        done[free] = false;
        sound(SoundEvents.GENERIC_SPLASH, 0.7F, 0.9F);
        say(player, process.heat && !heated(level, worldPosition) ? "needs_fire" : "soaking",
                Component.translatable(solution.langKey()));
        changed();
        return true;
    }

    private boolean takeOut(Player player) {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (slots[i].isEmpty()) {
                continue;
            }
            ItemStack out = slots[i];
            if (!done[i]) {
                Process process = process(out);
                long required = process == null ? 1 : process.required();
                if (process == Process.TAN && progress[i] >= required / 2) {
                    // Недодублена: кожа будет, но хуже
                    out = LeatherQuality.change(wet(out, Tannage.TANNED), -1);
                    uses--;
                    if (uses <= 0) {
                        solution = Solution.NONE;
                    }
                    say(player, "undertanned");
                } else if (process != null && progress[i] > 0) {
                    say(player, "too_early", percent(progress[i], required));
                }
            }
            slots[i] = ItemStack.EMPTY;
            progress[i] = 0;
            done[i] = false;
            give(player, out);
            sound(SoundEvents.BUCKET_FILL, 0.6F, 1.2F);
            changed();
            return true;
        }
        if (solution == Solution.NONE) {
            say(player, "vat_dry");
        } else if (solution == Solution.WATER) {
            say(player, "additives", ash, ASH_NEEDED, bark, BARK_NEEDED, alum, ALUM_NEEDED, salt, fat, FAT_NEEDED);
        } else {
            say(player, "vat_status", Component.translatable(solution.langKey()), uses);
        }
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ: какой раствор и на сколько его хватит, и по каждой шкуре — проценты, сколько ждать, нужен ли огонь. */
    @Override
    public void report(Player player) {
        MutableComponent line = solution == Solution.NONE ? Component.translatable("message.halgventmod.leather.vat_dry")
                : solution == Solution.WATER ? Component.translatable("message.halgventmod.leather.additives",
                        ash, ASH_NEEDED, bark, BARK_NEEDED, alum, ALUM_NEEDED, salt, fat, FAT_NEEDED)
                : Component.translatable("message.halgventmod.leather.vat_status", Component.translatable(solution.langKey()), uses);
        boolean heat = heated(level, worldPosition);
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i].isEmpty()) {
                continue;
            }
            Process process = done[i] ? null : process(slots[i]);
            Component state;
            if (done[i]) {
                state = Component.translatable("message.halgventmod.leather.vat_report_done");
            } else if (process == null) {
                state = Component.translatable("message.halgventmod.leather.vat_report_idle");
            } else if (process.heat && !heat) {
                state = Component.translatable("message.halgventmod.leather.vat_report_fire", percent(progress[i], process.required()));
            } else {
                long left = Math.max(0, process.required() - progress[i]);
                long minutes = process == Process.BOIL ? Math.max(1, (left + 1199) / 1200) : realMinutes(left);
                state = Component.translatable("message.halgventmod.leather.vat_report_progress",
                        percent(progress[i], process.required()), minutes);
            }
            line.append(Component.literal(" · ")).append(slots[i].getHoverName()).append(": ").append(state);
        }
        player.displayClientMessage(line, true);
    }

    private int inProcess() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) {
            if (!slots[i].isEmpty() && !done[i]) {
                Process process = process(slots[i]);
                if (process != null && process.usesSolution()) {
                    n++;
                }
            }
        }
        return n;
    }

    private static int percent(long value, long required) {
        return (int) Math.min(99, value * 100 / Math.max(1, required));
    }

    private boolean noAdditives() {
        return ash + bark + alum + salt + fat == 0;
    }

    private void resetAdditives() {
        ash = bark = alum = salt = fat = 0;
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Solution", solution.name());
        tag.putInt("Uses", uses);
        tag.putIntArray("Additives", new int[] {ash, bark, alum, salt, fat});
        for (int i = 0; i < SLOTS; i++) {
            if (!slots[i].isEmpty()) {
                tag.put("Slot" + i, slots[i].save(registries));
                tag.putLong("Progress" + i, progress[i]);
                tag.putBoolean("Done" + i, done[i]);
            }
        }
        tag.putLong("LastDayTime", lastDayTime);
        tag.putLong("Evaporation", boilEvaporation);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            solution = Solution.valueOf(tag.getString("Solution"));
        } catch (IllegalArgumentException e) {
            solution = Solution.NONE;
        }
        uses = tag.getInt("Uses");
        int[] add = tag.getIntArray("Additives");
        if (add.length == 5) {
            ash = add[0];
            bark = add[1];
            alum = add[2];
            salt = add[3];
            fat = add[4];
        }
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = tag.contains("Slot" + i)
                    ? ItemStack.parseOptional(registries, tag.getCompound("Slot" + i)) : ItemStack.EMPTY;
            progress[i] = tag.getLong("Progress" + i);
            done[i] = tag.getBoolean("Done" + i);
        }
        lastDayTime = tag.contains("LastDayTime") ? tag.getLong("LastDayTime") : -1;
        boilEvaporation = tag.getLong("Evaporation");
    }
}
