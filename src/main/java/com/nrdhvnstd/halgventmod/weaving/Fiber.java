package com.nrdhvnstd.halgventmod.weaving;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Волокно: из чего чешут кудель, во что её прядут и что из пряжи ткут. */
public enum Fiber {
    LINEN("linen"),
    WOOL("wool");

    private final String id;

    Fiber(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Item roving() {
        return this == LINEN ? ModItems.FLAX_FIBER.get() : ModItems.WOOL_ROVING.get();
    }

    public Item yarn() {
        return this == LINEN ? ModItems.LINEN_YARN.get() : ModItems.WOOL_YARN.get();
    }

    public Item cloth() {
        return this == LINEN ? ModItems.LINEN_CLOTH.get() : ModItems.WOOLEN_CLOTH.get();
    }

    /** Сырьё для чесалки: стебли льна или шерсть (любого цвета). */
    @Nullable
    public static Fiber ofRaw(ItemStack stack) {
        return stack.is(ModItems.FLAX.get()) ? LINEN : stack.is(ItemTags.WOOL) ? WOOL : null;
    }

    @Nullable
    public static Fiber ofRoving(ItemStack stack) {
        for (Fiber fiber : values()) {
            if (stack.is(fiber.roving())) {
                return fiber;
            }
        }
        return null;
    }

    @Nullable
    public static Fiber ofYarn(ItemStack stack) {
        for (Fiber fiber : values()) {
            if (stack.is(fiber.yarn())) {
                return fiber;
            }
        }
        return null;
    }

    @Nullable
    public static Fiber byId(String id) {
        for (Fiber fiber : values()) {
            if (fiber.id.equals(id)) {
                return fiber;
            }
        }
        return null;
    }
}
