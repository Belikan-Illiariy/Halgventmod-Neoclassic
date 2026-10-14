package com.nrdhvnstd.halgventmod;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Player;

/** Кто считается администратором: уровень прав 2 и выше, как у ванильных /gamemode и F3+F4. */
public final class Permissions {
    public static final int ADMIN_LEVEL = 2;

    private Permissions() {}

    public static boolean isAdmin(@Nullable Player player) {
        return player != null && player.hasPermissions(ADMIN_LEVEL);
    }
}
