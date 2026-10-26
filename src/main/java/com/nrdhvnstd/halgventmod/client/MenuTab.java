package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.Permissions;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Вкладки над окнами игрока, слева направо. У каждой своя клавиша.
 * «Маппа» (техническая карта) — только у администрации; её клавиша по умолчанию не назначена.
 */
public enum MenuTab {
    INVENTORY("inventory"),
    CHARACTER("character"),
    MAP("map"),
    MAPPA("mappa"),
    SQUAD("squad"),
    CODEX("codex");

    private final String id;

    MenuTab(String id) {
        this.id = id;
    }

    /** Видна ли вкладка этому игроку. */
    public boolean available() {
        return this != MAPPA || Permissions.isAdmin(Minecraft.getInstance().player);
    }

    public Component title() {
        return Component.translatable("gui.halgventmod.tab." + id);
    }

    /** Инвентарь — ванильная клавиша инвентаря (у нас по умолчанию I), остальные — клавиши мода. */
    public KeyMapping key() {
        return switch (this) {
            case INVENTORY -> Minecraft.getInstance().options.keyInventory;
            case CHARACTER -> ModKeys.CHARACTER;
            case MAP -> ModKeys.MAP;
            case MAPPA -> ModKeys.MAPPA;
            case SQUAD -> ModKeys.SQUAD;
            case CODEX -> ModKeys.CODEX;
        };
    }
}
