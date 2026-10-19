package com.nrdhvnstd.halgventmod.gear;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.storage.ContainerType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Снаряжение и контейнеры мода. Заполняется билдером ModItems (.gear(...), .container(...), .carryBonus(...)). */
public final class ModGear {
    private static final Map<ResourceLocation, GearInfo> BY_ID = new HashMap<>();

    private ModGear() {}

    /**
     * gear — куда надевается, container — что внутри (null у пояса, кольца...),
     * capacity — сколько ячеек внутри, carryBonusKg — прибавка к грузоподъёмности, пока надето.
     */
    public record GearInfo(@Nullable GearType gear, @Nullable ContainerType container, int capacity, double carryBonusKg) {}

    public static void put(ResourceLocation itemId, GearInfo info) {
        BY_ID.put(itemId, info);
    }

    @Nullable
    public static GearInfo get(Item item) {
        return BY_ID.get(BuiltInRegistries.ITEM.getKey(item));
    }

    @Nullable
    public static GearInfo get(ItemStack stack) {
        return stack.isEmpty() ? null : get(stack.getItem());
    }

    @Nullable
    public static GearType typeOf(ItemStack stack) {
        GearInfo info = get(stack);
        return info != null ? info.gear() : null;
    }

    @Nullable
    public static ContainerType containerOf(ItemStack stack) {
        GearInfo info = get(stack);
        return info != null ? info.container() : null;
    }

    /** Сколько ячеек внутри вещи; 0 — не контейнер. */
    public static int capacity(ItemStack stack) {
        GearInfo info = get(stack);
        return info != null && info.container() != null ? info.capacity() : 0;
    }
}
