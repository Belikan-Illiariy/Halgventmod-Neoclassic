package com.nrdhvnstd.halgventmod.client.map;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.point.MapPoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;

/**
 * Какое окно карты открыть по M (и по вкладке «Карта») — одинаково для всех, администрация тоже; техническая карта —
 * своя вкладка «Маппа» (MenuTab.MAPPA). Игрок в локации —
 * карту этой локации (в нескольких сразу, если границы пересеклись, — последней открытой, иначе самой маленькой);
 * вне всех локаций (так быть не должно, это запасной случай) — глобальную карту в фокусе на регионе ближайшей локации.
 */
public final class MapScreens {
    /** Последняя открытая этим игроком локация (за эту игру). */
    private static int lastDiscovered;

    private MapScreens() {}

    public static Screen create() {
        MapPoint here = currentLocation();
        if (here != null) {
            return new LocationMapScreen(here.id, null);
        }
        GlobalMapScreen global = new GlobalMapScreen();
        global.focusNearest();
        return global;
    }

    /** Сервер сообщил, что игрок только что открыл место. */
    public static void discovered(int id) {
        lastDiscovered = id;
    }

    /** Локация, в границе которой стоит игрок; null — ни в одной. */
    @Nullable
    public static MapPoint currentLocation() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        MapPoint best = null;
        long bestArea = Long.MAX_VALUE;
        for (MapPoint point : PointShapes.here()) {
            if (point.isSpawn() || !point.contains(player.getX(), player.getZ())) {
                continue;
            }
            if (point.id == lastDiscovered) {
                return point;
            }
            int[] bounds = point.bounds();
            long area = (long) (bounds[2] - bounds[0] + 1) * (bounds[3] - bounds[1] + 1);
            if (area < bestArea) {
                bestArea = area;
                best = point;
            }
        }
        return best;
    }
}
