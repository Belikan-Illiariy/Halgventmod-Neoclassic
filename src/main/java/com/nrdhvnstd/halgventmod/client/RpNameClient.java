package com.nrdhvnstd.halgventmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** RP-имя на клиенте: пересобрать имя игрока, когда пришло новое. */
public final class RpNameClient {
    private RpNameClient() {}

    // Зовётся из чтения пакета, до записи нового имени в игрока, — поэтому в очередь
    public static void refreshLater(Player player) {
        Minecraft.getInstance().tell(player::refreshDisplayName);
    }
}
