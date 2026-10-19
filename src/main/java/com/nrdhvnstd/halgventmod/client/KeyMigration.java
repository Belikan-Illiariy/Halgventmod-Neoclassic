package com.nrdhvnstd.halgventmod.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.blaze3d.platform.InputConstants;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.lwjgl.glfw.GLFW;

/**
 * Разовая перестановка клавиш под концепт инвентаря: Инвентарь E → Tab, Список игроков Tab → I,
 * Персонаж O → P, Отряд G → O. Освободившаяся E пойдёт под «Подобрать». Привязки уже сохранены в options.txt, поэтому новые значения
 * по умолчанию сами не применятся. Переставляется только то, что стоит на старом месте; дальше игрок волен.
 * Ванильные «Социальные взаимодействия» стоят на P — их снимаем с P, чтобы P не открывала два окна.
 * Отметка, что перестановка сделана, — файл config/halgventmod-keys.txt. Версия 1 ставила инвентарь на I,
 * поэтому версия 2 переносит его на Tab и оттуда.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class KeyMigration {
    private static final String VERSION = "2";
    private static boolean checked;

    private KeyMigration() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (checked) {
            return;
        }
        checked = true;
        Path marker = FMLPaths.CONFIGDIR.get().resolve(HalgventMod.MODID + "-keys.txt");
        try {
            if (Files.exists(marker) && Files.readString(marker).trim().equals(VERSION)) {
                return;
            }
            Options options = Minecraft.getInstance().options;
            move(options.keySocialInteractions, GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_UNKNOWN);
            move(options.keyPlayerList, GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_I);
            move(options.keyInventory, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_TAB);
            move(options.keyInventory, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_TAB); // после версии 1
            move(ModKeys.CHARACTER, GLFW.GLFW_KEY_O, GLFW.GLFW_KEY_P);
            move(ModKeys.SQUAD, GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_O);
            KeyMapping.resetMapping();
            options.save();
            Files.writeString(marker, VERSION);
        } catch (IOException e) {
            HalgventMod.LOGGER.warn("Не удалось записать отметку о перестановке клавиш", e);
        }
    }

    private static void move(KeyMapping key, int from, int to) {
        InputConstants.Key current = key.getKey();
        if (current.getType() == InputConstants.Type.KEYSYM && current.getValue() == from) {
            key.setKey(to == GLFW.GLFW_KEY_UNKNOWN ? InputConstants.UNKNOWN : InputConstants.Type.KEYSYM.getOrCreate(to));
        }
    }
}
