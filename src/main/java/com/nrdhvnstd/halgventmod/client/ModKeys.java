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

    // Вкладки окон игрока (MenuTabs); инвентарь — ванильная клавиша, переставленная на Tab (KeyMigration)
    public static final KeyMapping CHARACTER = new KeyMapping("key.halgventmod.character",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, CATEGORY);
    // Маппа — техническая карта администрации; по умолчанию без клавиши
    public static final KeyMapping MAPPA = new KeyMapping("key.halgventmod.mappa",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping MAP = new KeyMapping("key.halgventmod.map",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
    public static final KeyMapping SQUAD = new KeyMapping("key.halgventmod.squad",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, CATEGORY);
    public static final KeyMapping CODEX = new KeyMapping("key.halgventmod.codex",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);

    // Подобрать вещь под прицелом (pickup/Pickup); Shift — подобрать и надеть. E освободилась от инвентаря (KeyMigration)
    public static final KeyMapping PICKUP = new KeyMapping("key.halgventmod.pickup",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, CATEGORY);

    // Поднять или опустить забрало надетого шлема (network/ToggleVisorPayload); у шлема без забрала ничего не делает
    public static final KeyMapping VISOR = new KeyMapping("key.halgventmod.visor",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    // Бой (docs/TZ_chivalry_combat.md, 2). Удар — ванильная «Атака» (ЛКМ), сторона — по движению мыши перед кликом;
    // блок — ванильное «Использовать» (ПКМ), сторона — так же. Укол — на боковой кнопке мыши (KeyMigration снимает
    // со средней кнопки ванильное «Выбрать блок», с F — «Поменять руки»)
    public static final String COMBAT_CATEGORY = "key.categories.halgventmod.combat";
    public static final KeyMapping STAB = new KeyMapping("key.halgventmod.stab",
            KeyConflictContext.IN_GAME, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, COMBAT_CATEGORY);
    public static final KeyMapping SPECIAL = new KeyMapping("key.halgventmod.special",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, COMBAT_CATEGORY);
    // Финт — ПКМ во время замаха; отдельная клавиша по умолчанию не назначена
    public static final KeyMapping FEINT = new KeyMapping("key.halgventmod.feint",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), COMBAT_CATEGORY);
    public static final KeyMapping KICK = new KeyMapping("key.halgventmod.kick",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, COMBAT_CATEGORY);
    public static final KeyMapping GRIP = new KeyMapping("key.halgventmod.grip",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, COMBAT_CATEGORY);

    private ModKeys() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CALENDAR);
        event.register(CHARACTER);
        event.register(MAP);
        event.register(MAPPA);
        event.register(SQUAD);
        event.register(CODEX);
        event.register(PICKUP);
        event.register(VISOR);
        event.register(STAB);
        event.register(SPECIAL);
        event.register(FEINT);
        event.register(KICK);
        event.register(GRIP);
    }
}