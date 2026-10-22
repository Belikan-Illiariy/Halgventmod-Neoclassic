package com.nrdhvnstd.halgventmod.cooking;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.OvenMenu;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Хлебная печь. ПКМ — окно (OvenMenu): жар, дрова, что печётся и сколько ждать, «Разжечь» / «Затушить». На E:
 * <ul>
 *   <li>дрова — в {@link Fire#SLOTS} ячейки, по полену; огниво, огненный заряд или факел — растопить. Печь
 *       разогревается за {@link #HEAT_UP} тиков до полного жара и остывает, когда дрова прогорели; «Затушить» — жар 0;</li>
 *   <li>тесто или сырой пирог — посадить (до {@link #SLOTS}, в окне — ячейки печи); печётся, пока жар не ниже
 *       {@link #MIN_TEMPERATURE}, и тем быстрее, чем жарче. Полный жар — {@link #BAKE_TIME} тиков;</li>
 *   <li>пустой рукой — вынуть самое пропечённое (из окна — любое): выходит то, что успело испечься.</li>
 * </ul>
 * Пропечённость: меньше {@link #UNDERBAKED} — сырое (вернётся как есть), до 1 — недопечено (качество 1),
 * 1…{@link #OVERBAKED} — в самый раз (3), дальше — пересушено (1), с {@link #BURNT} — горелое.
 * Пар из устья — пора вынимать, чёрный дым — уже горит.
 */
public class OvenBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int SLOTS = 3;
    public static final int HEAT_UP = 1200;
    public static final int COOL_DOWN = 2400;
    public static final float MIN_TEMPERATURE = 40;
    public static final int BAKE_TIME = 1800;
    public static final float UNDERBAKED = 0.7F;
    public static final float OVERBAKED = 1.3F;
    public static final float BURNT = 1.6F;
    public static final float MALT_BURNT = 2.2F;  // солод терпит дольше: 1,3–1,6 — янтарный, 1,6–2,2 — тёмный
    public static final float DEGREES = 2.5F;     // жар 0–100 в окне — градусами (до 250°)

    public static final int BAND_RAW = 0;
    public static final int BAND_GOOD = 1;
    public static final int BAND_OVER = 2;

    // Окно: номера в data()
    public static final int D_TEMPERATURE = 0, D_BURN_LEFT = 1, D_BURN_TOTAL = 2, D_REMAINING = 3, D_LIT = 4,
            D_PROGRESS = 5, DATA = D_PROGRESS + SLOTS;

    private final Fire fire = new Fire();
    private final float[] progress = new float[SLOTS];
    private boolean loading;
    /** Посаженное. Вынуть (окно, E) — значит получить выпечку по пропечённости, а не сырое. */
    private final SimpleContainer items = new SimpleContainer(SLOTS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !baked(stack).isEmpty();
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);
            if (!loading) {
                progress[slot] = 0;
            }
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack raw = getItem(slot);
            if (raw.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack result = result(raw, progress[slot]);
            super.setItem(slot, ItemStack.EMPTY);
            progress[slot] = 0;
            setChanged();
            return result;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index >= D_PROGRESS) {
                return Math.round(progress[index - D_PROGRESS] * 100);
            }
            return switch (index) {
                case D_TEMPERATURE -> Math.round(fire.temperature() * 10);
                case D_BURN_LEFT -> fire.burnLeft();
                case D_BURN_TOTAL -> fire.burnTotal();
                case D_REMAINING -> Math.min(Short.MAX_VALUE, fire.remaining() / 10);
                case D_LIT -> lit() ? 1 : 0;
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

    public OvenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OVEN.get(), pos, state);
        fire.fuel().addListener(container -> changed());
        items.addListener(container -> changed());
    }

    public Fire fire() {
        return fire;
    }

    public SimpleContainer items() {
        return items;
    }

    public ContainerData data() {
        return data;
    }

    public boolean lit() {
        return getBlockState().getValue(OvenBlock.LIT);
    }

    /** Худшая пропечённость среди посаженного — для частиц. */
    public int worstBand() {
        int worst = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (!items.getItem(i).isEmpty()) {
                worst = Math.max(worst, band(progress[i]));
            }
        }
        return worst;
    }

    private static int band(float done) {
        return done >= OVERBAKED ? BAND_OVER : done >= 1.0F ? BAND_GOOD : BAND_RAW;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (!items.getItem(i).isEmpty()) {
                all.add(items.getItem(i).copy());
            }
            if (!fire.fuel().getItem(i).isEmpty()) {
                all.add(fire.fuel().getItem(i).copy());
            }
        }
        return all;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.cooking." + key, args), true);
    }

    /** Что выходит из этого: тесто — хлеб, сырой пирог — пирог по начинке, сырая булочка — сладкая, заготовка — блюдо. Пусто — не печётся. */
    public static ItemStack baked(ItemStack raw) {
        if (raw.is(ModItems.GREEN_MALT.get())) {
            return new ItemStack(ModItems.PALE_MALT.get()); // по пропечённости — светлый, янтарный или тёмный (result)
        }
        if (raw.is(ModItems.RAW_BUN.get())) {
            return new ItemStack(ModItems.SWEET_BUN.get());
        }
        if (raw.is(ModItems.RYE_DOUGH.get())) {
            return new ItemStack(ModItems.RYE_BREAD.get()); // чёрный хлеб
        }
        if (raw.is(ModItems.DOUGH.get())) {
            return new ItemStack(ModItems.WHEAT_BREAD.get());
        }
        if (raw.is(ModItems.RAW_DISH.get())) {
            RawDishItem.Kind kind = RawDishItem.kind(raw);
            return kind == null ? ItemStack.EMPTY : new ItemStack(kind.baked());
        }
        if (raw.is(ModItems.RAW_PIE.get())) {
            String filling = raw.getOrDefault(ModComponents.PIE_FILLING.get(), "berry");
            Item pie = switch (filling) {
                case "meat" -> ModItems.MEAT_PIE.get();
                case "mushroom" -> ModItems.MUSHROOM_PIE.get();
                default -> ModItems.BERRY_PIE.get();
            };
            return new ItemStack(pie);
        }
        return ItemStack.EMPTY;
    }

    /** Что выйдет, если вынуть сейчас: сырое, выпечка с качеством по пропечённости или горелое. */
    public static ItemStack result(ItemStack raw, float done) {
        if (raw.is(ModItems.GREEN_MALT.get()) && done >= UNDERBAKED && done < MALT_BURNT) {
            // Солод: чем дольше в печи, тем темнее
            return new ItemStack(done < OVERBAKED ? ModItems.PALE_MALT.get()
                    : done < BURNT ? ModItems.AMBER_MALT.get() : ModItems.DARK_MALT.get());
        }
        if (done < UNDERBAKED) {
            return raw.copy();
        }
        if (done >= BURNT) {
            return new ItemStack(ModItems.BURNT_FOOD.get());
        }
        ItemStack result = baked(raw);
        FoodExtras extras = raw.get(ModComponents.FOOD_EXTRAS.get());
        if (extras != null) {
            result.set(ModComponents.FOOD_EXTRAS.get(), extras); // добавки остаются в начинке
        }
        int quality = done < 1.0F ? 1 : done < OVERBAKED ? 3 : 1;
        // Тесто с кухонного стола несёт своё качество: хорошо замешанное — лучше хлеб
        if (FoodQuality.has(raw)) {
            quality += FoodQuality.get(raw) - FoodQuality.NORMAL;
        }
        FoodQuality.set(result, quality);
        return result;
    }

    // ---------------------------------------------------------------- ход времени

    public static void serverTick(Level level, BlockPos pos, BlockState state, OvenBlockEntity oven) {
        boolean sync = false;
        Fire fire = oven.fire;
        if (oven.lit()) {
            // Дрова прогорают через тик
            if (level.getGameTime() % 2 == 0 && !fire.burn(1)) {
                level.setBlock(pos, state.setValue(OvenBlock.LIT, false), 3);
                sync = true;
            }
            fire.toward(100, 100.0F / HEAT_UP, 0);
        } else if (fire.temperature() > 0) {
            fire.toward(0, 0, 100.0F / COOL_DOWN);
        }
        if (fire.temperature() >= MIN_TEMPERATURE) {
            for (int i = 0; i < SLOTS; i++) {
                if (oven.items.getItem(i).isEmpty()) {
                    continue;
                }
                float before = oven.progress[i];
                float after = before + fire.temperature() / 100.0F / BAKE_TIME;
                oven.progress[i] = after;
                sync |= band(before) != band(after);
            }
        }
        if (sync) {
            oven.changed();
        } else if (level.getGameTime() % 100 == 0) {
            oven.setChanged();
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
            status(player);
            return true;
        }
        if (!baked(stack).isEmpty()) {
            for (int i = 0; i < SLOTS; i++) {
                if (items.getItem(i).isEmpty()) {
                    items.setItem(i, stack.copyWithCount(1));
                    consumeOne(player, stack);
                    sound(SoundEvents.WOOD_PLACE, 0.5F, 1.4F);
                    if (fire.temperature() < MIN_TEMPERATURE) {
                        tell(player, "oven_cold", Math.round(fire.temperature()));
                    }
                    return true;
                }
            }
            tell(player, "oven_full");
            return true;
        }
        if (stack.isEmpty()) {
            if (player.isShiftKeyDown() || items.isEmpty()) {
                status(player);
            } else {
                takeOut(player);
            }
            return true;
        }
        return false;
    }

    private void light() {
        sound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
        level.setBlock(worldPosition, getBlockState().setValue(OvenBlock.LIT, true), 3);
        changed();
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ — окно печи. */
    @Override
    public void report(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new OvenMenu(id, inventory, this),
                    getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
        }
    }

    private boolean near(Player player) {
        return player.distanceToSqr(worldPosition.getCenter()) <= 64;
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

    /** «Затушить»: огонь гаснет, недогоревшее остаётся, жар — 0. */
    public void extinguish(ServerPlayer player) {
        if (!near(player) || !lit()) {
            return;
        }
        fire.extinguish();
        level.setBlock(worldPosition, getBlockState().setValue(OvenBlock.LIT, false), 3);
        sound(SoundEvents.FIRE_EXTINGUISH, 1.0F, 1.0F);
        changed();
    }

    private void status(Player player) {
        StringBuilder done = new StringBuilder();
        for (int i = 0; i < SLOTS; i++) {
            if (items.getItem(i).isEmpty()) {
                continue;
            }
            if (!done.isEmpty()) {
                done.append(", ");
            }
            done.append(Math.round(progress[i] * 100)).append('%');
        }
        tell(player, items.isEmpty() ? "oven_status_empty" : "oven_status", Math.round(fire.temperature()),
                fire.loaded(), done.toString());
    }

    private void takeOut(Player player) {
        int best = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (!items.getItem(i).isEmpty() && (best < 0 || progress[i] > progress[best])) {
                best = i;
            }
        }
        if (best < 0) {
            return;
        }
        float done = progress[best];
        ItemStack result = items.removeItem(best, 1);
        if (result.is(ModItems.PALE_MALT.get()) || result.is(ModItems.AMBER_MALT.get()) || result.is(ModItems.DARK_MALT.get())) {
            tell(player, "baked_malt", result.getHoverName());
        } else if (done < UNDERBAKED) {
            tell(player, "baked_raw");
        } else if (result.is(ModItems.BURNT_FOOD.get())) {
            tell(player, "baked_burnt");
        } else {
            tell(player, done < 1.0F ? "baked_under" : done < OVERBAKED ? "baked_good" : "baked_over", result.getHoverName());
        }
        give(player, result);
        sound(SoundEvents.WOOD_HIT, 0.6F, 1.2F);
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        fire.save(tag, registries);
        ListTag list = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            if (items.getItem(i).isEmpty()) {
                continue;
            }
            CompoundTag slot = new CompoundTag();
            slot.put("Item", items.getItem(i).save(registries));
            slot.putFloat("Done", progress[i]);
            slot.putByte("Slot", (byte) i);
            list.add(slot);
        }
        tag.put("Slots", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loading = true;
        fire.load(tag, registries);
        if (!tag.contains("Heat")) {
            fire.setTemperature(tag.getFloat("Temperature")); // из прежних версий
        }
        for (int i = 0; i < SLOTS; i++) {
            items.setItem(i, ItemStack.EMPTY);
            progress[i] = 0;
        }
        ListTag list = tag.getList("Slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag slot = list.getCompound(i);
            int index = slot.contains("Slot") ? slot.getByte("Slot") : i;
            ItemStack stack = ItemStack.parseOptional(registries, slot.getCompound("Item"));
            if (!stack.isEmpty() && index >= 0 && index < SLOTS) {
                items.setItem(index, stack);
                progress[index] = slot.getFloat("Done");
            }
        }
        loading = false;
    }
}
