package com.nrdhvnstd.halgventmod.glass;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.cooking.Fire;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.GlassFurnaceMenu;
import com.nrdhvnstd.halgventmod.smithing.Minigame;
import com.nrdhvnstd.halgventmod.smithing.Smithing;
import com.nrdhvnstd.halgventmod.smithing.SmithingItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Стекловаренная печь ({@link Glassmaking}). Всё на E:
 * <pre>
 *   дрова (до {@link Fire#SLOTS}), огниво, огненный заряд, факел — топить и разжечь
 *   песок, поташ, флюс, бой стекла                — в шихту (каждого до Glassmaking.MAX_BATCH)
 *   добавка (ярь-медянка, свинцовый пепел, …)     — в ячейку цвета: по одной на изделие
 *   стеклодувная трубка                           — набрать и выдуть выбранное в окне (мини-игра)
 *   пустая рука                                   — забрать отожжённое; Shift — всё, и неотожжённое тоже
 * </pre>
 * От Glassmaking.MELT_TEMP шихта варится в стекломассу (до Glassmaking.MAX_MOLTEN порций): два песка и поташ — порция,
 * с флюсом — чище; бой — порция вдвое быстрее. Выдутое ложится в камеру отжига (Glassmaking.LEHR_SLOTS мест) и
 * отжигается Glassmaking.ANNEAL_TICKS; вынутое раньше — хрупкое (Glassmaking.unannealed). Лопнувшее — в бой.
 * <p>ПКМ — окно (GlassFurnaceMenu): огонь, шихта и стекломасса, что выдувать, цвет, камера отжига.
 */
public class GlassFurnaceBlockEntity extends StationBlockEntity implements Minigame.Host {
    public static final int D_TEMPERATURE = 0, D_BURN_LEFT = 1, D_BURN_TOTAL = 2, D_REMAINING = 3, D_LIT = 4, D_SAND = 5,
            D_POTASH = 6, D_FLUX = 7, D_CULLET = 8, D_MOLTEN = 9, D_MELT = 10, D_FORM = 11, D_QUALITY = 12, D_ANNEAL = 13,
            DATA = D_ANNEAL + Glassmaking.LEHR_SLOTS;

    private final Fire fire = new Fire();
    private int sand;
    private int potash;
    private int flux;
    private int cullet;
    private int molten;
    private int qualitySum;
    private int melt;
    private int form;
    private final int[] anneal = new int[Glassmaking.LEHR_SLOTS];
    private boolean loading;
    private final SimpleContainer colorant = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return Glassmaking.Tint.of(stack) != null;
        }
    };
    /** Ячейка шихты в окне: что положили, печь по одной забирает в шихту. */
    private final SimpleContainer batch = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return Glassmaking.batch(stack);
        }
    };
    /** Камера отжига: вынимают отожжённое как есть, неотожжённое — хрупким. */
    private final SimpleContainer lehr = new SimpleContainer(Glassmaking.LEHR_SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return false;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);
            if (!loading) {
                anneal[slot] = 0;
            }
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack there = getItem(slot);
            if (there.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            boolean ready = annealed(slot);
            super.setItem(slot, ItemStack.EMPTY);
            anneal[slot] = 0;
            setChanged();
            return ready ? there : Glassmaking.unannealed(there);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, getItem(slot).getCount());
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index >= D_ANNEAL) {
                int i = index - D_ANNEAL;
                return lehr.getItem(i).isEmpty() ? 0 : Math.min(100, anneal[i] * 100 / Glassmaking.ANNEAL_TICKS);
            }
            return switch (index) {
                case D_TEMPERATURE -> Math.round(fire.temperature());
                case D_BURN_LEFT -> fire.burnLeft();
                case D_BURN_TOTAL -> fire.burnTotal();
                case D_REMAINING -> Math.min(Short.MAX_VALUE, fire.remaining() / 10);
                case D_LIT -> lit() ? 1 : 0;
                case D_SAND -> sand;
                case D_POTASH -> potash;
                case D_FLUX -> flux;
                case D_CULLET -> cullet;
                case D_MOLTEN -> molten;
                case D_MELT -> Math.min(100, melt * 100 / Glassmaking.MELT_TICKS);
                case D_FORM -> form;
                case D_QUALITY -> quality();
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

    public GlassFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(GlassBlocks.GLASS_FURNACE_ENTITY.get(), pos, state);
        fire.fuel().addListener(container -> setChanged());
        colorant.addListener(container -> setChanged());
        batch.addListener(container -> setChanged());
    }

    public Fire fire() {
        return fire;
    }

    public SimpleContainer colorant() {
        return colorant;
    }

    public SimpleContainer batch() {
        return batch;
    }

    public SimpleContainer lehr() {
        return lehr;
    }

    public ContainerData data() {
        return data;
    }

    public boolean lit() {
        return getBlockState().getValue(GlassFurnaceBlock.LIT);
    }

    private boolean annealed(int slot) {
        return anneal[slot] >= Glassmaking.ANNEAL_TICKS;
    }

    /** Качество стекломассы: среднее по порциям. */
    private int quality() {
        return molten <= 0 ? 0 : Math.round(qualitySum / (float) molten);
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>(); // стекломасса при поломке застывает и пропадает
        for (int i = 0; i < Fire.SLOTS; i++) {
            add(all, fire.fuel().getItem(i).copy());
        }
        add(all, new ItemStack(Items.SAND, sand));
        add(all, new ItemStack(GlassItems.POTASH.get(), potash));
        add(all, new ItemStack(SmithingItems.FLUX.get(), flux));
        add(all, new ItemStack(GlassItems.CULLET.get(), cullet + molten)); // застывшее — бой
        add(all, colorant.getItem(0).copy());
        add(all, batch.getItem(0).copy());
        for (int i = 0; i < Glassmaking.LEHR_SLOTS; i++) {
            add(all, lehr.getItem(i).copy());
        }
        return all;
    }

    private static void add(List<ItemStack> list, ItemStack stack) {
        if (!stack.isEmpty()) {
            list.add(stack);
        }
    }

    private static void tell(Player player, String key, Object... args) {
        Glassmaking.say(player, key, args);
    }

    // ---------------------------------------------------------------- ход времени

    public static void serverTick(Level level, BlockPos pos, BlockState state, GlassFurnaceBlockEntity furnace) {
        Fire fire = furnace.fire;
        boolean sync = false;
        if (furnace.lit()) {
            if (level.getGameTime() % 2 == 0 && !fire.burn(1)) {
                level.setBlock(pos, state.setValue(GlassFurnaceBlock.LIT, false), 3);
                sync = true;
            }
            fire.toward(Glassmaking.MAX_TEMP, Glassmaking.HEAT_UP, 0);
        } else if (fire.temperature() > 0) {
            fire.toward(0, 0, Glassmaking.COOL_DOWN);
        }
        if (level.getGameTime() % 10 == 0 && !furnace.batch.isEmpty() && furnace.addBatch(furnace.batch.getItem(0))) {
            furnace.batch.getItem(0).shrink(1);
            furnace.batch.setChanged();
        }
        // Варка: порция за порцией, бой — первым и вдвое быстрее
        boolean stock = furnace.cullet > 0 || furnace.sand >= Glassmaking.SAND_PER_PORTION
                && furnace.potash >= Glassmaking.POTASH_PER_PORTION;
        if (fire.temperature() >= Glassmaking.MELT_TEMP && stock && furnace.molten < Glassmaking.MAX_MOLTEN) {
            furnace.melt += furnace.cullet > 0 ? 2 : 1;
            if (furnace.melt >= Glassmaking.MELT_TICKS) {
                furnace.melt = 0;
                furnace.meltPortion();
                furnace.sound(SoundEvents.LAVA_POP, 0.6F, 0.8F);
                sync = true;
            }
        }
        // Отжиг идёт сам: камера греется от печи и медленно остывает
        for (int i = 0; i < Glassmaking.LEHR_SLOTS; i++) {
            if (!furnace.lehr.getItem(i).isEmpty() && !furnace.annealed(i)) {
                furnace.anneal[i]++;
                if (furnace.annealed(i)) {
                    sync = true;
                }
            }
        }
        if (sync) {
            furnace.changed();
        } else if (level.getGameTime() % 100 == 0) {
            furnace.setChanged();
        }
    }

    private void meltPortion() {
        if (cullet > 0) {
            cullet--;
            molten++;
            qualitySum += Glassmaking.QUALITY_CULLET;
            return;
        }
        sand -= Glassmaking.SAND_PER_PORTION;
        potash -= Glassmaking.POTASH_PER_PORTION;
        molten++;
        if (flux > 0) {
            flux--;
            qualitySum += Glassmaking.QUALITY_FLUX;
        } else {
            qualitySum += Glassmaking.QUALITY_PLAIN;
        }
    }

    // ---------------------------------------------------------------- E

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)) {
            if (lit()) {
                tell(player, "already_lit");
            } else if (!fire.hasFuel()) {
                tell(player, "no_fuel");
            } else {
                if (stack.is(Items.FLINT_AND_STEEL)) {
                    damage(player, hand, stack, 1);
                } else if (stack.is(Items.FIRE_CHARGE)) {
                    consumeOne(player, stack);
                }
                light();
            }
            return true;
        }
        if (Fire.fuelValue(stack) > 0) {
            if (!fire.add(stack)) {
                tell(player, "fuel_full");
                return true;
            }
            consumeOne(player, stack);
            sound(SoundEvents.WOOD_PLACE, 0.6F, 1.0F);
            return true;
        }
        if (stack.is(GlassItems.BLOWPIPE.get())) {
            return startWork(player);
        }
        if (addBatch(stack)) {
            consumeOne(player, stack);
            sound(SoundEvents.SAND_PLACE, 0.7F, 1.0F);
            status(player);
            return true;
        }
        if (Glassmaking.Tint.of(stack) != null) {
            ItemStack there = colorant.getItem(0);
            if (!there.isEmpty() && !ItemStack.isSameItemSameComponents(there, stack)) {
                tell(player, "colorant_other");
                return true;
            }
            if (there.getCount() >= there.getMaxStackSize()) {
                tell(player, "colorant_full");
                return true;
            }
            colorant.setItem(0, there.isEmpty() ? stack.copyWithCount(1) : there.copyWithCount(there.getCount() + 1));
            consumeOne(player, stack);
            sound(SoundEvents.SAND_PLACE, 0.5F, 1.4F);
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        if (takeLehr(player, player.isShiftKeyDown())) {
            return true;
        }
        status(player);
        return true;
    }

    /** Положить в шихту: песок, поташ, флюс, бой. false — это не шихта или места нет (сказано). */
    private boolean addBatch(ItemStack stack) {
        if (Glassmaking.sand(stack) && sand < Glassmaking.MAX_BATCH) {
            sand++;
        } else if (stack.is(GlassItems.POTASH.get()) && potash < Glassmaking.MAX_BATCH) {
            potash++;
        } else if (stack.is(SmithingItems.FLUX.get()) && flux < Glassmaking.MAX_BATCH) {
            flux++;
        } else if (stack.is(GlassItems.CULLET.get()) && cullet < Glassmaking.MAX_BATCH) {
            cullet++;
        } else {
            return false;
        }
        changed();
        return true;
    }

    /** Забрать из камеры отжига: отожжённое, а с all — и неотожжённое. false — забирать нечего. */
    private boolean takeLehr(Player player, boolean all) {
        boolean took = false;
        for (int i = 0; i < Glassmaking.LEHR_SLOTS; i++) {
            if (!lehr.getItem(i).isEmpty() && (all || annealed(i))) {
                give(player, lehr.removeItem(i, lehr.getItem(i).getCount()));
                took = true;
            }
        }
        if (took) {
            sound(SoundEvents.GLASS_PLACE, 0.6F, 1.2F);
            changed();
        }
        return took;
    }

    /** Трубка при себе: в руке или в инвентаре; пусто — нет. */
    private static ItemStack pipe(Player player) {
        if (player.getMainHandItem().is(GlassItems.BLOWPIPE.get())) {
            return player.getMainHandItem();
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(GlassItems.BLOWPIPE.get())) {
                return player.getInventory().getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Начать выдувание (E трубкой или «Выдувать» в окне): нужна трубка при себе, жар и стекломасса. */
    private boolean startWork(Player player) {
        Glassmaking.Form chosen = Glassmaking.Form.byIndex(form);
        if (pipe(player).isEmpty()) {
            tell(player, "need_pipe");
            return true;
        }
        if (fire.temperature() < Glassmaking.WORK_TEMP) {
            tell(player, "too_cold", Math.round(fire.temperature()), Math.round(Glassmaking.WORK_TEMP));
            return true;
        }
        if (molten < chosen.cost()) {
            tell(player, "not_enough", molten, chosen.cost(), net.minecraft.network.chat.Component.translatable(chosen.langKey()));
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            int heat = Math.round(Math.clamp((fire.temperature() - Glassmaking.WORK_TEMP)
                    / (Glassmaking.MAX_TEMP - Glassmaking.WORK_TEMP), 0F, 1F) * 100);
            int extra = chosen.ware() ? Math.clamp(quality() + tint().bonus(), 0, Smithing.MAX_QUALITY) : 0;
            Minigame.open(serverPlayer, worldPosition, Minigame.GLASS, chosen.ordinal(), Glassmaking.WORK_MIN_TICKS, heat, extra);
        }
        return true;
    }

    /** Цвет, который даст добавка в ячейке (лесное — без неё). */
    private Glassmaking.Tint tint() {
        Glassmaking.Tint tint = Glassmaking.Tint.of(colorant.getItem(0));
        return tint == null ? Glassmaking.Tint.FOREST : tint;
    }

    @Override
    public void minigameResult(ServerPlayer player, Minigame.Session session, boolean done, List<Integer> data) {
        Glassmaking.Form chosen = Glassmaking.Form.byIndex(session.product());
        if (!done || molten < chosen.cost()) {
            report(player); // бросили — масса стекает обратно в горшок; окно печи — снова
            return;
        }
        int total = 0;
        for (int i = 0; i < data.size(); i++) {
            total += Minigame.score(data, i);
        }
        int average = data.isEmpty() ? 0 : total / data.size();
        int meltQuality = quality();
        molten -= chosen.cost();
        qualitySum = Math.max(0, qualitySum - meltQuality * chosen.cost());
        ItemStack pipe = pipe(player);
        if (!pipe.isEmpty() && !player.getAbilities().instabuild) {
            pipe.hurtAndBreak(1, player.serverLevel(), player, item -> {});
        }
        if (average < Glassmaking.FAIL_SCORE) {
            give(player, new ItemStack(GlassItems.CULLET.get(), chosen.cost()));
            tell(player, "burst");
            sound(SoundEvents.GLASS_BREAK, 0.8F, 1.0F);
            changed();
            report(player);
            return;
        }
        Glassmaking.Tint tint = chosen.tinted() ? tint() : Glassmaking.Tint.FOREST;
        if (chosen.tinted() && tint != Glassmaking.Tint.FOREST) {
            colorant.getItem(0).shrink(1);
        }
        int quality = Math.clamp(meltQuality + tint.bonus() + Smithing.workDelta(average), 0, Smithing.MAX_QUALITY);
        ItemStack made = Glassmaking.make(chosen, tint, quality);
        Smithing.say(player, "worked", average);
        sound(SoundEvents.GLASS_PLACE, 0.8F, 0.9F);
        for (int i = 0; i < Glassmaking.LEHR_SLOTS; i++) {
            if (lehr.getItem(i).isEmpty()) {
                lehr.setItem(i, made);
                tell(player, "annealing", made.getHoverName());
                changed();
                report(player);
                return;
            }
        }
        // Камера полна — сразу в руки, неотожжённым
        give(player, Glassmaking.unannealed(made));
        tell(player, "lehr_full");
        changed();
        report(player);
    }

    // ---------------------------------------------------------------- огонь и окно

    private void light() {
        sound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
        level.setBlock(worldPosition, getBlockState().setValue(GlassFurnaceBlock.LIT, true), 3);
        changed();
    }

    private boolean near(Player player) {
        return player.distanceToSqr(worldPosition.getCenter()) <= 100;
    }

    /** «Разжечь»: огниво, огненный заряд или факел из инвентаря. */
    public void igniteFromInventory(ServerPlayer player) {
        if (!near(player)) {
            return;
        }
        int slot = Fire.lighterSlot(player);
        if (lit()) {
            tell(player, "already_lit");
        } else if (!fire.hasFuel()) {
            tell(player, "no_fuel");
        } else if (slot < 0) {
            tell(player, "need_lighter");
        } else {
            ItemStack lighter = player.getInventory().getItem(slot);
            if (lighter.is(Items.FLINT_AND_STEEL) && !player.getAbilities().instabuild) {
                lighter.hurtAndBreak(1, player.serverLevel(), player, item -> {});
            } else if (lighter.is(Items.FIRE_CHARGE)) {
                consumeOne(player, lighter);
            }
            light();
        }
    }

    /** «Затушить»: огонь гаснет, недогоревшее остаётся; печь остывает. */
    public void extinguish(ServerPlayer player) {
        if (!near(player) || !lit()) {
            return;
        }
        level.setBlock(worldPosition, getBlockState().setValue(GlassFurnaceBlock.LIT, false), 3);
        sound(SoundEvents.FIRE_EXTINGUISH, 1.0F, 1.0F);
        changed();
    }

    /** Что выдувать (кнопки окна). */
    public void chooseForm(ServerPlayer player, int index) {
        if (near(player)) {
            form = Glassmaking.Form.byIndex(index).ordinal();
            changed();
        }
    }

    /** «Забрать отожжённое» (окно). */
    /** «Выдувать» (окно). */
    public void blowFromWindow(ServerPlayer player) {
        if (near(player)) {
            startWork(player);
        }
    }

    public void takeFromWindow(ServerPlayer player) {
        if (near(player)) {
            takeLehr(player, false);
        }
    }

    private void status(Player player) {
        tell(player, "status", Math.round(fire.temperature()), sand, potash, flux, cullet, molten, Glassmaking.MAX_MOLTEN);
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ — окно печи. */
    @Override
    public void report(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new GlassFurnaceMenu(id, inventory, this),
                    getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
        }
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        fire.save(tag, registries);
        tag.putInt("Sand", sand);
        tag.putInt("Potash", potash);
        tag.putInt("Flux", flux);
        tag.putInt("Cullet", cullet);
        tag.putInt("Molten", molten);
        tag.putInt("QualitySum", qualitySum);
        tag.putInt("Melt", melt);
        tag.putInt("Form", form);
        if (!batch.getItem(0).isEmpty()) {
            tag.put("Batch", batch.getItem(0).save(registries));
        }
        if (!colorant.getItem(0).isEmpty()) {
            tag.put("Colorant", colorant.getItem(0).save(registries));
        }
        for (int i = 0; i < Glassmaking.LEHR_SLOTS; i++) {
            if (!lehr.getItem(i).isEmpty()) {
                tag.put("Lehr" + i, lehr.getItem(i).save(registries));
                tag.putInt("Anneal" + i, anneal[i]);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fire.load(tag, registries);
        sand = tag.getInt("Sand");
        potash = tag.getInt("Potash");
        flux = tag.getInt("Flux");
        cullet = tag.getInt("Cullet");
        molten = tag.getInt("Molten");
        qualitySum = tag.getInt("QualitySum");
        melt = tag.getInt("Melt");
        form = tag.getInt("Form");
        colorant.setItem(0, tag.contains("Colorant") ? ItemStack.parseOptional(registries, tag.getCompound("Colorant"))
                : ItemStack.EMPTY);
        batch.setItem(0, tag.contains("Batch") ? ItemStack.parseOptional(registries, tag.getCompound("Batch"))
                : ItemStack.EMPTY);
        loading = true;
        for (int i = 0; i < Glassmaking.LEHR_SLOTS; i++) {
            lehr.setItem(i, tag.contains("Lehr" + i) ? ItemStack.parseOptional(registries, tag.getCompound("Lehr" + i))
                    : ItemStack.EMPTY);
            anneal[i] = tag.getInt("Anneal" + i);
        }
        loading = false;
    }
}
