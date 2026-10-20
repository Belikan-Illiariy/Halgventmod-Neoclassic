package com.nrdhvnstd.halgventmod.cooking;

import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Жёрнов. E пшеницей или рожью — засыпать (одно зерно за раз) (до {@link #CAPACITY}); E пустой рукой — повернуть камень. Каждые {@link #TURNS}
 * поворота — мука. Крутить надо ровно, дожидаясь замаха: в спешке зерно с шансом {@link #SPILL_CHANCE}
 * просыпается мимо. Shift + E пустой рукой — забрать зерно назад.
 */
public class QuernBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int CAPACITY = 16;
    public static final int TURNS = 4;
    public static final float SPILL_CHANCE = 0.3F;

    private int grain;
    private int turns;
    private boolean rye; // что засыпано: рожь или пшеница (вместе не засыпают)

    public QuernBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUERN.get(), pos, state);
    }

    @Override
    public List<ItemStack> contents() {
        return grain > 0 ? List.of(new ItemStack(grainItem(), grain)) : List.of();
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.cooking." + key, args), true);
    }

    private net.minecraft.world.item.Item grainItem() {
        return rye ? ModItems.RYE.get() : Items.WHEAT;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Items.WHEAT) || stack.is(ModItems.RYE.get())) {
            boolean isRye = stack.is(ModItems.RYE.get());
            if (grain > 0 && isRye != rye) {
                tell(player, "quern_mixed");
                return true;
            }
            rye = isRye;
            int moved = Math.min(CAPACITY - grain, stack.getCount());
            if (moved <= 0) {
                tell(player, "quern_full");
                return true;
            }
            grain += moved;
            if (!player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
            sound(SoundEvents.CROP_PLANTED, 0.8F, 1.0F);
            tell(player, "quern_grain", grain);
            changed();
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        if (player.isShiftKeyDown()) {
            if (grain > 0) {
                give(player, new ItemStack(grainItem(), grain));
                grain = 0;
                turns = 0;
                changed();
            }
            return true;
        }
        if (grain <= 0) {
            tell(player, "quern_empty");
            return true;
        }
        boolean steady = strike(player, hand, ItemStack.EMPTY);
        sound(SoundEvents.GRINDSTONE_USE, 0.6F, 0.8F + 0.2F * level.random.nextFloat());
        crumbs(new ItemStack(grainItem()), 0.7, 3);
        if (!steady && level.random.nextFloat() < SPILL_CHANCE) {
            grain--;
            turns = 0;
            tell(player, "quern_spilled", grain);
            changed();
            return true;
        }
        if (++turns >= TURNS) {
            turns = 0;
            grain--;
            give(player, new ItemStack(rye ? ModItems.RYE_FLOUR.get() : ModItems.WHEAT_FLOUR.get()));
            tell(player, "quern_flour", grain);
        } else {
            tell(player, "quern_turn", turns, TURNS);
        }
        changed();
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Grain", grain);
        tag.putInt("Turns", turns);
        tag.putBoolean("Rye", rye);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        grain = tag.getInt("Grain");
        turns = tag.getInt("Turns");
        rye = tag.getBoolean("Rye");
    }
}
