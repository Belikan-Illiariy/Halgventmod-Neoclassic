package com.nrdhvnstd.halgventmod.point;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.network.PointsDiscoveredPayload;
import com.nrdhvnstd.halgventmod.network.PointsSyncPayload;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.slf4j.Logger;

/**
 * Точки карты (MapPoint) мира: хранятся в данных мира, при входе и после каждой правки рассылаются всем.
 * Точки появления получает только администрация (seesSpawns): остальным они не нужны — новый игрок выбирает их
 * в окне создания персонажа, куда сервер присылает их сам (CharacterCreation). Стал игрок администратором
 * или перестал — точки пересылаются ему при ближайшей проверке.
 * Ставят, правят и удаляют только администраторы в творческом режиме (PointEditPayload, canEdit); они же видят границы.
 * Раз в CHECK_INTERVAL тиков у каждого игрока проверяется, не зашёл ли он в неоткрытое место своего измерения;
 * зашёл — место открыто для него навсегда, на экране «Открытая локация» и его название (Announcements).
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public class MapPoints extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = HalgventMod.MODID + "_points";
    private static final Factory<MapPoints> FACTORY = new Factory<>(MapPoints::new, MapPoints::load, null);
    private static final int CHECK_INTERVAL = 10;

    /** Точки на клиенте — все измерения; приходят с сервера (PointsSyncPayload). Точки появления — только у администрации. */
    public static List<MapPoint> clientPoints = List.of();
    /** Открытые этим игроком точки на клиенте (PointsDiscoveredPayload). */
    public static Set<Integer> clientDiscovered = Set.of();

    private final List<MapPoint> points = new ArrayList<>();
    private int nextId = 1;

    public static MapPoints get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    // Кому последний раз ушли точки вместе с точками появления
    private static final Set<UUID> SPAWN_VIEWERS = new HashSet<>();

    public List<MapPoint> all() {
        return Collections.unmodifiableList(points);
    }

    // ===== Рассылка =====

    /** Точки появления на карте видит только администрация. */
    public static boolean seesSpawns(ServerPlayer player) {
        return Permissions.isAdmin(player);
    }

    /** Послать игроку точки, которые он может видеть. */
    private static void sync(ServerPlayer player) {
        boolean spawns = seesSpawns(player);
        if (spawns) {
            SPAWN_VIEWERS.add(player.getUUID());
        } else {
            SPAWN_VIEWERS.remove(player.getUUID());
        }
        List<MapPoint> visible = get(player.server).points.stream()
                .filter(point -> spawns || !point.isSpawn())
                .toList();
        PacketDistributor.sendToPlayer(player, new PointsSyncPayload(visible));
    }

    // ===== Правка администратором =====

    public enum Action { CREATE, UPDATE, DELETE }

    /**
     * Ставить, править, удалять точки и видеть их границы на карте — только администрация в творческом режиме.
     * Остальные видят на карте и компасе лишь значок (неоткрытая — «?»).
     */
    public static boolean canEdit(@Nullable Player player) {
        return Permissions.isAdmin(player) && player.isCreative();
    }

    /** Правка от игрока: проверить права и саму точку, применить, разослать всем. */
    public static void edit(ServerPlayer player, Action action, MapPoint point) {
        if (!canEdit(player)) {
            return;
        }
        MapPoints data = get(player.server);
        switch (action) {
            case CREATE -> {
                point.dimension = player.level().dimension().location().toString();
                if (!point.valid()) {
                    return;
                }
                point.id = data.nextId++;
                data.points.add(point);
                LOGGER.info("{} поставил {} «{}» #{} в {} {} {}", player.getGameProfile().getName(),
                        point.isSpawn() ? "точку появления" : "точку", point.name, point.id, point.x, point.y, point.z);
            }
            case UPDATE -> {
                int index = data.indexOf(point.id);
                if (index < 0) {
                    return;
                }
                point.dimension = data.points.get(index).dimension; // измерение и тип не меняются
                point.kind = data.points.get(index).kind;
                if (!point.valid()) {
                    return;
                }
                data.points.set(index, point);
            }
            case DELETE -> {
                int index = data.indexOf(point.id);
                if (index < 0) {
                    return;
                }
                MapPoint removed = data.points.remove(index);
                LOGGER.info("{} удалил точку «{}» #{}", player.getGameProfile().getName(), removed.name, removed.id);
            }
        }
        data.setDirty();
        for (ServerPlayer each : player.server.getPlayerList().getPlayers()) {
            sync(each);
        }
    }

    private int indexOf(int id) {
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    // ===== Открытие точек =====

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        if (seesSpawns(player) != SPAWN_VIEWERS.contains(player.getUUID())) {
            sync(player); // выдали или сняли права администратора
        }
        if (player.isSpectator()) {
            return;
        }
        String dimension = player.level().dimension().location().toString();
        PointDiscovery discovery = player.getData(ModAttachments.POINT_DISCOVERY);
        int discovered = 0;
        for (MapPoint point : get(player.server).points) {
            if (!point.isSpawn() && !discovery.has(point.id) && point.dimension.equals(dimension)
                    && point.contains(player.getX(), player.getZ())) {
                discovery.add(point.id);
                discovered = point.id;
            }
        }
        if (discovered != 0) {
            // Надпись на экране — о последней открытой (две сразу бывают, только если границы пересекаются)
            PacketDistributor.sendToPlayer(player, new PointsDiscoveredPayload(Set.copyOf(discovery.ids()), discovered));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
            PacketDistributor.sendToPlayer(player,
                    new PointsDiscoveredPayload(Set.copyOf(player.getData(ModAttachments.POINT_DISCOVERY).ids()), 0));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SPAWN_VIEWERS.remove(event.getEntity().getUUID());
    }

    // ===== Сохранение =====

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MapPoint point : points) {
            list.add(point.save());
        }
        tag.put("points", list);
        tag.putInt("next_id", nextId);
        return tag;
    }

    private static MapPoints load(CompoundTag tag, HolderLookup.Provider registries) {
        MapPoints data = new MapPoints();
        for (Tag entry : tag.getList("points", Tag.TAG_COMPOUND)) {
            data.points.add(MapPoint.load((CompoundTag) entry));
        }
        data.nextId = Math.max(1, tag.getInt("next_id"));
        return data;
    }
}
