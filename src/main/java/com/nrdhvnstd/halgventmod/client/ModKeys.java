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

    // Вкладки окон игрока (MenuTabs); инвентарь — ванильная E
    public static final KeyMapping CHARACTER = new KeyMapping("key.halgventmod.character",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);
    public static final KeyMapping MAP = new KeyMapping("key.halgventmod.map",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
    public static final KeyMapping CODEX = new KeyMapping("key.halgventmod.codex",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    // Новая метка там, где стоит игрок (client/map/WaypointsInWorld)
    public static final KeyMapping WAYPOINT = new KeyMapping("key.halgventmod.waypoint",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    private ModKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CALENDAR);
        event.register(CHARACTER);
        event.register(MAP);
        event.register(CODEX);
        event.register(WAYPOINT);
    }
}