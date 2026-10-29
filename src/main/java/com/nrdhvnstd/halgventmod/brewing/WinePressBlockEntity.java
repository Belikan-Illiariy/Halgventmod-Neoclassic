package com.nrdhvnstd.halgventmod.brewing;

import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.cooking.KitchenRecipes;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Давильня. E яблоками или ягодами — засыпать (до {@link #CAPACITY}, одно за раз); E пустой рукой — давить:
 * {@link #PRESSES} нажатий на плод, ровно, дожидаясь замаха — в спешке плод с шансом {@link #SPILL_CHANCE}
 * давится мимо. Каждый плод — мера сока; {@link #JUICE_PER_BOTTLE} меры — склянка сусла (E склянкой).
 * Яблоки и груши — сусло для сидра, виноград — для вина, вишня и слива — для вишнёвого и сливового вина, прочие
 * ягоды — ягодное (вино), светящиеся ягоды — сусло для «болотного» вина. Сусло — склянкой, корчагой или кувшином.
 * Shift + E пустой рукой — забрать плоды назад.
 */
public class WinePressBlockEntity extends StationBlockEntity {
    // Меняйте здесь ⚠️
    public static final int CAPACITY = 16;
    public static final int PRESSES = 3;
    public static final float SPILL_CHANCE = 0.25F;
    public static final int JUICE_PER_BOTTLE = 2;
    public static final int MAX_JUICE = 16;
    /** Событие блока «нажали»: у клиентов ворот с винтом делает треть оборота (client/brewing/WinePressRenderer). */
    public static final int PRESS_EVENT = 1;

    @Nullable
    private Item fruit;
    private int count;
    private int presses;
    private int juice;
    private String juiceKind = "";

    // Только у клиента: сколько раз крутили ворот и когда последний раз (время мира) — для отрисовки
    private int turns;
    private long turnStart = Long.MIN_VALUE;

    public WinePressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WINE_PRESS.get(), pos, state);
    }

    @Override
    public List<ItemStack> contents() {
        return fruit != null && count > 0 ? List.of(new ItemStack(fruit, count)) : List.of();
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.brewing." + key, args), true);
    }

    @Nullable
    private static String kindOf(ItemStack stack) {
        if (stack.is(Items.APPLE)) {
            return "apple";
        }
        if (stack.is(ModItems.PEAR.get())) {
            return "pear";
        }
        if (stack.is(ModItems.GRAPE.get())) {
            return "grape";
        }
        if (stack.is(ModItems.CHERRY.get())) {
            return "cherry";
        }
        if (stack.is(ModItems.PLUM.get())) {
            return "plum";
        }
        if (stack.is(KitchenRecipes.BERRY_TAG)) {
            return "berry"; // черника, брусника, клюква, лесная ягода
        }
        return stack.is(Items.GLOW_BERRIES) ? "glowberry" : null;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        String kind = kindOf(stack);
        if (kind != null) {
            if (count > 0 && !stack.is(fruit)) {
                tell(player, "press_mixed");
                return true;
            }
            if (juice > 0 && !kind.equals(juiceKind)) {
                tell(player, "press_take_juice");
                return true;
            }
            int moved = Math.min(CAPACITY - count, stack.getCount());
            if (moved <= 0) {
                tell(player, "press_full");
                return true;
            }
            fruit = stack.getItem();
            count += moved;
            if (!player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
            sound(SoundEvents.SWEET_BERRY_BUSH_PLACE, 0.8F, 1.0F);
            tell(player, "press_fruit", count, juice);
            changed();
            return true;
        }
        // Корчагой или кувшином — налить сусла сколько влезет
        if (Pottery.drinkCapacity(stack) > 0 && stack.getCount() == 1) {
            ItemStack must = Brewing.make(ModItems.MUST.get(), Brew.of(juiceKind));
            int poured = 0;
            while (juice >= JUICE_PER_BOTTLE && Pottery.canPour(stack, must, level)) {
                Pottery.pour(stack, must, level);
                juice -= JUICE_PER_BOTTLE;
                poured++;
            }
            if (poured > 0) {
                sound(SoundEvents.BOTTLE_FILL, 1.0F, 0.9F);
                changed();
            } else {
                tell(player, juice < JUICE_PER_BOTTLE ? "press_no_juice" : "vessel_no", juice, JUICE_PER_BOTTLE);
            }
            return true;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (juice < JUICE_PER_BOTTLE) {
                tell(player, "press_no_juice", juice, JUICE_PER_BOTTLE);
                return true;
            }
            juice -= JUICE_PER_BOTTLE;
            consumeOne(player, stack);
            give(player, Brewing.make(ModItems.MUST.get(), Brew.of(juiceKind)));
            sound(SoundEvents.BOTTLE_FILL, 1.0F, 1.0F);
            changed();
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        if (player.isShiftKeyDown()) {
            if (count > 0) {
                give(player, new ItemStack(fruit, count));
                count = 0;
                presses = 0;
                changed();
            }
            return true;
        }
        if (count <= 0) {
            tell(player, "press_empty", juice);
            return true;
        }
        if (juice >= MAX_JUICE) {
            tell(player, "press_take_juice");
            return true;
        }
        boolean steady = strike(player, hand, ItemStack.EMPTY);
        level.blockEvent(worldPosition, getBlockState().getBlock(), PRESS_EVENT, 0); // клиенты крутят ворот
        sound(SoundEvents.SLIME_SQUISH, 0.6F, 0.8F + 0.3F * level.random.nextFloat());
        crumbs(new ItemStack(fruit), 0.9, 4);
        if (!steady && level.random.nextFloat() < SPILL_CHANCE) {
            count--;
            presses = 0;
            tell(player, "press_spilled", count);
            changed();
            return true;
        }
        if (++presses >= PRESSES) {
            presses = 0;
            count--;
            juice++;
            juiceKind = kindOf(new ItemStack(fruit));
            tell(player, "press_juice", juice, count);
        } else {
            tell(player, "press_turn", presses, PRESSES);
        }
        changed();
        return true;
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == PRESS_EVENT) {
            if (level != null && level.isClientSide) {
                turns++;
                turnStart = level.getGameTime();
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public int turns() {
        return turns;
    }

    public long turnStart() {
        return turnStart;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (fruit != null) {
            tag.putString("Fruit", BuiltInRegistries.ITEM.getKey(fruit).toString());
        }
        tag.putInt("Count", count);
        tag.putInt("Presses", presses);
        tag.putInt("Juice", juice);
        tag.putString("JuiceKind", juiceKind);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fruit = tag.contains("Fruit") ? BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Fruit"))) : null;
        count = tag.getInt("Count");
        presses = tag.getInt("Presses");
        juice = tag.getInt("Juice");
        juiceKind = tag.getString("JuiceKind");
    }
}
