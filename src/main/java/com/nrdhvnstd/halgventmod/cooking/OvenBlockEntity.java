package com.nrdhvnstd.halgventmod.cooking;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Хлебная печь. Всё на E:
 * <ul>
 *   <li>дрова — запас; огниво, огненный заряд или факел — растопить. Печь разогревается за {@link #HEAT_UP} тиков
 *       до полного жара и остывает, когда дрова прогорели;</li>
 *   <li>тесто или сырой пирог — посадить (до {@link #SLOTS}); печётся, пока жар не ниже {@link #MIN_TEMPERATURE},
 *       и тем быстрее, чем жарче. Полный жар — {@link #BAKE_TIME} тиков;</li>
 *   <li>пустой рукой — вынуть самое пропечённое; Shift + E — узнать жар и как идёт выпечка.</li>
 * </ul>
 * Пропечённость: меньше {@link #UNDERBAKED} — сырое (вернётся как есть), до 1 — недопечено (качество 1),
 * 1…{@link #OVERBAKED} — в самый раз (3), дальше — пересушено (1), с {@link #BURNT} — горелое.
 * Пар из устья — пора вынимать, чёрный дым — уже горит.
 */
public class OvenBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int SLOTS = 3;
    public static final int MAX_FUEL = 9600;
    public static final int HEAT_UP = 1200;
    public static final int COOL_DOWN = 2400;
    public static final float MIN_TEMPERATURE = 40;
    public static final int BAKE_TIME = 1800;
    public static final float UNDERBAKED = 0.7F;
    public static final float OVERBAKED = 1.3F;
    public static final float BURNT = 1.6F;
    public static final float MALT_BURNT = 2.2F;  // солод терпит дольше: 1,3–1,6 — янтарный, 1,6–2,2 — тёмный

    public static final int BAND_RAW = 0;
    public static final int BAND_GOOD = 1;
    public static final int BAND_OVER = 2;

    private int fuel;
    private float temperature;
    private final List<ItemStack> items = new ArrayList<>();
    private final List<Float> progress = new ArrayList<>();

    public OvenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OVEN.get(), pos, state);
    }

    public boolean lit() {
        return getBlockState().getValue(OvenBlock.LIT);
    }

    /** Худшая пропечённость среди посаженного — для частиц. */
    public int worstBand() {
        int worst = -1;
        for (float done : progress) {
            worst = Math.max(worst, band(done));
        }
        return worst;
    }

    private static int band(float done) {
        return done >= OVERBAKED ? BAND_OVER : done >= 1.0F ? BAND_GOOD : BAND_RAW;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        items.forEach(stack -> all.add(stack.copy()));
        return all;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.cooking." + key, args), true);
    }

    /** Что выходит из этого: тесто — хлеб, сырой пирог — пирог по начинке, сырая булочка — сладкая, заготовка — блюдо. Пусто — не печётся. */
    public static ItemStack baked(ItemStack raw) {
        if (raw.is(ModItems.GREEN_MALT.get())) {
            return new ItemStack(ModItems.PALE_MALT.get()); // по пропечённости — светлый, янтарный или тёмный (takeOut)
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

    // ---------------------------------------------------------------- ход времени

    public static void serverTick(Level level, BlockPos pos, BlockState state, OvenBlockEntity oven) {
        boolean sync = false;
        if (oven.lit()) {
            if (level.getGameTime() % 2 == 0) {
                oven.fuel--; // дрова прогорают через тик
            }
            oven.temperature = Math.min(100, oven.temperature + 100.0F / HEAT_UP);
            if (oven.fuel <= 0) {
                oven.fuel = 0;
                level.setBlock(pos, state.setValue(OvenBlock.LIT, false), 3);
                sync = true;
            }
        } else if (oven.temperature > 0) {
            oven.temperature = Math.max(0, oven.temperature - 100.0F / COOL_DOWN);
        }
        if (oven.temperature >= MIN_TEMPERATURE) {
            for (int i = 0; i < oven.progress.size(); i++) {
                float before = oven.progress.get(i);
                float after = before + oven.temperature / 100.0F / BAKE_TIME;
                oven.progress.set(i, after);
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
            } else if (fuel <= 0) {
                tell(player, "no_fuel");
            } else {
                if (stack.is(Items.FLINT_AND_STEEL)) {
                    damage(player, hand, stack, 1);
                } else if (stack.is(Items.FIRE_CHARGE)) {
                    consumeOne(player, stack);
                }
                sound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
                level.setBlock(worldPosition, getBlockState().setValue(OvenBlock.LIT, true), 3);
                changed();
            }
            return true;
        }
        int fuelValue = HearthBlockEntity.fuelValue(stack);
        if (fuelValue > 0) {
            if (fuel + fuelValue > MAX_FUEL) {
                tell(player, "fuel_full");
                return true;
            }
            fuel += fuelValue;
            consumeOne(player, stack);
            sound(SoundEvents.WOOD_PLACE, 0.6F, 1.0F);
            status(player);
            changed();
            return true;
        }
        if (!baked(stack).isEmpty()) {
            if (items.size() >= SLOTS) {
                tell(player, "oven_full");
                return true;
            }
            items.add(stack.copyWithCount(1));
            progress.add(0.0F);
            consumeOne(player, stack);
            sound(SoundEvents.WOOD_PLACE, 0.5F, 1.4F);
            if (temperature < MIN_TEMPERATURE) {
                tell(player, "oven_cold", Math.round(temperature));
            }
            changed();
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

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ — как идёт дело (то же, что E пустой рукой). */
    @Override
    public void report(Player player) {
        status(player);
    }

    private void status(Player player) {
        StringBuilder done = new StringBuilder();
        for (float p : progress) {
            if (!done.isEmpty()) {
                done.append(", ");
            }
            done.append(Math.round(p * 100)).append('%');
        }
        tell(player, items.isEmpty() ? "oven_status_empty" : "oven_status", Math.round(temperature),
                Math.round(100.0F * fuel / MAX_FUEL), done.toString());
    }

    private void takeOut(Player player) {
        int best = 0;
        for (int i = 1; i < progress.size(); i++) {
            if (progress.get(i) > progress.get(best)) {
                best = i;
            }
        }
        ItemStack raw = items.remove(best);
        float done = progress.remove(best);
        if (raw.is(ModItems.GREEN_MALT.get()) && done >= UNDERBAKED && done < MALT_BURNT) {
            // Солод: чем дольше в печи, тем темнее
            ItemStack malt = new ItemStack(done < OVERBAKED ? ModItems.PALE_MALT.get()
                    : done < BURNT ? ModItems.AMBER_MALT.get() : ModItems.DARK_MALT.get());
            tell(player, "baked_malt", malt.getHoverName());
            give(player, malt);
            sound(SoundEvents.WOOD_HIT, 0.6F, 1.2F);
            changed();
            return;
        }
        ItemStack result;
        if (done < UNDERBAKED) {
            result = raw;
            tell(player, "baked_raw");
        } else if (done >= BURNT) {
            result = new ItemStack(ModItems.BURNT_FOOD.get());
            tell(player, "baked_burnt");
        } else {
            result = baked(raw);
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
            tell(player, done < 1.0F ? "baked_under" : done < OVERBAKED ? "baked_good" : "baked_over",
                    result.getHoverName());
        }
        give(player, result);
        sound(SoundEvents.WOOD_HIT, 0.6F, 1.2F);
        changed();
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Fuel", fuel);
        tag.putFloat("Temperature", temperature);
        ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            CompoundTag slot = new CompoundTag();
            slot.put("Item", items.get(i).save(registries));
            slot.putFloat("Done", progress.get(i));
            list.add(slot);
        }
        tag.put("Slots", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuel = tag.getInt("Fuel");
        temperature = tag.getFloat("Temperature");
        items.clear();
        progress.clear();
        ListTag list = tag.getList("Slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag slot = list.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, slot.getCompound("Item"));
            if (!stack.isEmpty()) {
                items.add(stack);
                progress.add(slot.getFloat("Done"));
            }
        }
    }
}
