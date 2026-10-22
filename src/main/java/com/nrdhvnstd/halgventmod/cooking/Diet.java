package com.nrdhvnstd.halgventmod.cooking;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Разнообразие стола: игрок помнит последние {@link #MEMORY} съеденных блюд (вложение halgventmod:recent_meals).
 * Одно и то же подряд приедается — каждое повторение среди запомненных: сытость −{@link #REPEAT_PENALTY}
 * (не ниже {@link #MIN_FACTOR}). Если среди запомненных не меньше {@link #VARIED} разных — насыщение
 * ×{@link #VARIETY_BONUS}. Питьё не считается.
 */
public final class Diet {
    // Меняйте здесь
    public static final int MEMORY = 6;
    public static final float REPEAT_PENALTY = 0.2F;
    public static final float MIN_FACTOR = 0.4F;
    public static final int VARIED = 5;
    public static final float VARIETY_BONUS = 1.2F;

    /** Множители сытости и насыщения для этой еды. */
    public record Effect(float nutrition, float saturation) {}

    private Diet() {}

    /** Учесть съеденное и вернуть множители. Только на сервере. */
    public static Effect eat(Player player, ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        List<String> recent = new ArrayList<>(player.getData(ModAttachments.RECENT_MEALS));
        int repeats = (int) recent.stream().filter(id::equals).count();
        float nutrition = Math.max(MIN_FACTOR, 1.0F - REPEAT_PENALTY * repeats);
        if (repeats > 0) {
            player.displayClientMessage(Component.translatable("message.halgventmod.cooking.diet_bored",
                    stack.getHoverName(), Math.round(nutrition * 100)), true);
        }
        recent.add(id);
        while (recent.size() > MEMORY) {
            recent.remove(0);
        }
        player.setData(ModAttachments.RECENT_MEALS, List.copyOf(recent));
        boolean varied = new HashSet<>(recent).size() >= VARIED;
        return new Effect(nutrition, varied ? nutrition * VARIETY_BONUS : nutrition);
    }
}
