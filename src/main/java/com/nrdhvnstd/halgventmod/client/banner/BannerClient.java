package com.nrdhvnstd.halgventmod.client.banner;

import net.minecraft.client.Minecraft;

/** Вход из пакетов в окна знамён: пакеты общие для обеих сторон и не должны сами трогать классы интерфейса. */
public final class BannerClient {
    private BannerClient() {}

    public static void openRegistry() {
        Minecraft.getInstance().setScreen(BannerRegistryScreen.browse());
    }
}
