package com.nrdhvnstd.halgventmod.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Вкладки над окнами игрока, слева направо. У каждой своя клавиша. */
public enum MenuTab {
    INVENTORY("inventory"),
    CHARACTER("character"),
    MAP("map"),
    SQUAD("squad"),
    CODEX("codex");

    private final String id;

    MenuTab(String id) {
        this.id = id;
    }

    public Component title() {
        return Component.translatable("gui.halgventmod.tab." + id);
    }

    /** Инвентарь — ванильная клавиша E, остальные — клавиши мода. */
    public KeyMapping key() {
        return switch (this) {
            case INVENTORY -> Minecraft.getInstance().options.keyInventory;
            case CHARACTER -> ModKeys.CHARACTER;
            case MAP -> ModKeys.MAP;
            case SQUAD -> ModKeys.SQUAD;
            case CODEX -> ModKeys.CODEX;
        };
    }
}
