package com.nrdhvnstd.halgventmod.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Клавиши мода. Появляются в «Настройки → Управление», игрок может их переназначить. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class ModKeys {
    public static final String CATEGORY = "key.categories.halgventmod";

    public static final KeyMapping CALENDAR = new KeyMapping("key.halgventmod.calendar",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    private ModKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CALENDAR);
    }
}