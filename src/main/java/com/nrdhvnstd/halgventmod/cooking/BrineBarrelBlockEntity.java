package com.nrdhvnstd.halgventmod.cooking;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Засолочная бочка. E сырым мясом или рыбой — положить (до {@link #CAPACITY}); E солью — пересыпать (по одной
 * соли на кусок). Когда соли хватает на всё, через {@link #DAYS} суток мясо — солонина, рыба — солёная рыба.
 * Доложили сырого — отсчёт заново. Пустой рукой — забрать готовое (или узнать, сколько ждать);
 * Shift + E — забрать назад несолёное. Испорченное не солят.
 */
public class BrineBarrelBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int CAPACITY = 8;
    public static final int DAYS = 2;

    private final List<ItemStack> items = new ArrayList<>();
    private int salt;
    private int startDay = -1;

    public BrineBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BRINE_BARREL.get(), pos, state);
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        items.forEach(stack -> all.add(stack.copy()));
        if (salt > 0) {
            all.add(new ItemStack(ModItems.SALT.get(), salt));
        }
        return all;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.cooking." + key, args), true);
    }

    private static ItemStack salted(ItemStack raw) {
        return new ItemStack(raw.is(Cookery.Kind.FISH.tag()) ? ModItems.SALTED_FISH.get() : ModItems.SALTED_MEAT.get());
    }

    private boolean ready() {
        return startDay >= 0 && Freshness.currentDay(level) - startDay >= DAYS;
    }

    private void restart() {
        startDay = !items.isEmpty() && salt >= items.size() ? Freshness.currentDay(level) : -1;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(CookingEvents.RAW_FOOD) && (stack.is(Cookery.Kind.MEAT.tag()) || stack.is(Cookery.Kind.FISH.tag()))) {
            if (ready()) {
                tell(player, "barrel_take_first");
                return true;
            }
            if (items.size() >= CAPACITY) {
                tell(player, "barrel_full");
                return true;
            }
            if (Freshness.isSpoiled(stack, level)) {
                tell(player, "barrel_spoiled");
                return true;
            }
            items.add(stack.copyWithCount(1));
            consumeOne(player, stack);
            restart();
            sound(SoundEvents.SLIME_SQUISH_SMALL, 0.6F, 1.0F);
            status(player);
            changed();
            return true;
        }
        if (stack.is(ModItems.SALT.get())) {
            if (items.isEmpty() || salt >= items.size()) {
                tell(player, items.isEmpty() ? "barrel_empty" : "barrel_salt_enough");
                return true;
            }
            salt++;
            consumeOne(player, stack);
            restart();
            sound(SoundEvents.SAND_PLACE, 0.8F, 1.2F);
            status(player);
            changed();
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        if (player.isShiftKeyDown() && !ready()) {
            items.forEach(item -> give(player, item));
            if (salt > 0) {
                give(player, new ItemStack(ModItems.SALT.get(), salt));
            }
            items.clear();
            salt = 0;
            startDay = -1;
            changed();
            return true;
        }
        if (ready()) {
            for (ItemStack item : items) {
                give(player, salted(item));
            }
            tell(player, "barrel_done", items.size());
            items.clear();
            salt = 0;
            startDay = -1;
            sound(SoundEvents.BARREL_OPEN, 0.8F, 1.0F);
            changed();
            return true;
        }
        status(player);
        return true;
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
        if (items.isEmpty()) {
            tell(player, "barrel_empty");
        } else if (salt < items.size()) {
            tell(player, "barrel_need_salt", items.size(), salt);
        } else {
            int left = Math.max(0, DAYS - (Freshness.currentDay(level) - startDay));
            tell(player, "barrel_salting", items.size(), left);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        items.forEach(stack -> list.add(stack.save(registries)));
        tag.put("Items", list);
        tag.putInt("Salt", salt);
        tag.putInt("StartDay", startDay);
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
        salt = tag.getInt("Salt");
        startDay = tag.contains("StartDay") ? tag.getInt("StartDay") : -1;
    }
}
