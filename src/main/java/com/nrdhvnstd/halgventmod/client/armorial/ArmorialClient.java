package com.nrdhvnstd.halgventmod.client.armorial;

import com.nrdhvnstd.halgventmod.armorial.ArmorialBook;

import net.minecraft.client.Minecraft;

/** Вход из пакета в окно гербовника: пакеты общие для обеих сторон и не должны сами трогать классы интерфейса. */
public final class ArmorialClient {
    private ArmorialClient() {}

    public static void open(String id, ArmorialBook book, boolean editable) {
        Minecraft.getInstance().setScreen(new ArmorialScreen(id, book, editable));
    }
}
