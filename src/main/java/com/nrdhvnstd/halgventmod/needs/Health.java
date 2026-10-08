package com.nrdhvnstd.halgventmod.needs;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Здоровье игрока — 100 вместо 20. Урон не масштабируется: зомби по-прежнему бьёт на 3.
 * Базовое значение задаёт ModAttributes; здесь — перевод игроков из старых сохранений.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Health {
    public static final double MAX = 100;

    private static final double VANILLA_MAX = 20;

    private Health() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();

        // В старом сохранении записано ванильное 20 — поднимаем до 100, текущее здоровье в той же доле
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getBaseValue() == VANILLA_MAX) {
            float ratio = player.getHealth() / player.getMaxHealth();
            maxHealth.setBaseValue(MAX);
            player.setHealth(player.getMaxHealth() * ratio);
        }

        // Ванильный голод отключён и должен стоять на полном, иначе он мешает есть и бегать
        player.getFoodData().setFoodLevel(20);
    }
}
