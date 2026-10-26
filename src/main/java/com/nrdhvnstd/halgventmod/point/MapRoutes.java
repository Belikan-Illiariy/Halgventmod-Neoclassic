package com.nrdhvnstd.halgventmod.point;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.network.RoutesSyncPayload;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.slf4j.Logger;

/**
 * Маршруты (MapRoute) мира — для будущих НПС. Хранятся в данных мира, как точки (MapPoints).
 * Видит их только администрация: рассылаются при входе, после каждой правки и когда игроку выдали или сняли права
 * (тогда ему уходит пустой список). Обычный игрок пакета с маршрутами не получает вообще.
 * Прокладывают, правят и удаляют — администраторы в творческом режиме (RouteEditPayload, MapPoints.canEdit).
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public class MapRoutes extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = HalgventMod.MODID + "_routes";
    private static final Factory<MapRoutes> FACTORY = new Factory<>(MapRoutes::new, MapRoutes::load, null);
    private static final int CHECK_INTERVAL = 20;

    /** Маршруты на клиенте — только у администрации. */
    public static List<MapRoute> clientRoutes = List.of();

    // Кому последний раз ушли маршруты
    private static final Set<UUID> VIEWERS = new HashSet<>();

    private final List<MapRoute> routes = new ArrayList<>();
    private int nextId = 1;

    public static MapRoutes get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public static boolean seesRoutes(ServerPlayer player) {
        return Permissions.isAdmin(player);
    }

    // Администрации — все; переставшему быть администратором — пустой список, остальным — ничего
    private static void sync(ServerPlayer player) {
        if (seesRoutes(player)) {
            VIEWERS.add(player.getUUID());
            PacketDistributor.sendToPlayer(player, new RoutesSyncPayload(get(player.server).routes));
        } else if (VIEWERS.remove(player.getUUID())) {
            PacketDistributor.sendToPlayer(player, new RoutesSyncPayload(List.of()));
        }
    }

    public enum Action { CREATE, UPDATE, DELETE }

    /** Правка от игрока: проверить права и маршрут, применить, разослать администрации. */
    public static void edit(ServerPlayer player, Action action, MapRoute route) {
        if (!MapPoints.canEdit(player)) {
            return;
        }
        MapRoutes data = get(player.server);
        switch (action) {
            case CREATE -> {
                route.dimension = player.level().dimension().location().toString();
                if (!route.valid()) {
                    return;
                }
                route.id = data.nextId++;
                data.routes.add(route);
                LOGGER.info("{} проложил маршрут «{}» #{} из {} точек", player.getGameProfile().getName(), route.name,
                        route.id, route.size());
            }
            case UPDATE -> {
                int index = data.indexOf(route.id);
                if (index < 0) {
                    return;
                }
                route.dimension = data.routes.get(index).dimension;
                if (!route.valid()) {
                    return;
                }
                data.routes.set(index, route);
            }
            case DELETE -> {
                int index = data.indexOf(route.id);
                if (index < 0) {
                    return;
                }
                MapRoute removed = data.routes.remove(index);
                LOGGER.info("{} удалил маршрут «{}» #{}", player.getGameProfile().getName(), removed.name, removed.id);
            }
        }
        data.setDirty();
        for (ServerPlayer each : player.server.getPlayerList().getPlayers()) {
            sync(each);
        }
    }

    private int indexOf(int id) {
        for (int i = 0; i < routes.size(); i++) {
            if (routes.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_INTERVAL == 0
                && seesRoutes(player) != VIEWERS.contains(player.getUUID())) {
            sync(player); // выдали или сняли права администратора
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        VIEWERS.remove(event.getEntity().getUUID());
    }

    // ===== Сохранение =====

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MapRoute route : routes) {
            list.add(route.save());
        }
        tag.put("routes", list);
        tag.putInt("next_id", nextId);
        return tag;
    }

    private static MapRoutes load(CompoundTag tag, HolderLookup.Provider registries) {
        MapRoutes data = new MapRoutes();
        for (Tag entry : tag.getList("routes", Tag.TAG_COMPOUND)) {
            data.routes.add(MapRoute.load((CompoundTag) entry));
        }
        data.nextId = Math.max(1, tag.getInt("next_id"));
        return data;
    }
}
