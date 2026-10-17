package com.nrdhvnstd.halgventmod.banner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.network.BannerActionPayload;
import com.nrdhvnstd.halgventmod.network.BannersSyncPayload;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.slf4j.Logger;

/**
 * Реестр знамён мира: id → знамя (BannerEntry), по порядку внесения. Правит администрация (/banner),
 * при входе и после каждой правки реестр целиком уходит всем (BannersSyncPayload).
 * Точки карты и книги-гербовники хранят только id: удалённое знамя там просто пропадает,
 * а внесённое снова под тем же id — возвращается.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public class BannerRegistry extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = HalgventMod.MODID + "_banners";
    private static final Factory<BannerRegistry> FACTORY = new Factory<>(BannerRegistry::new, BannerRegistry::load, null);

    /** Реестр на клиенте — приходит с сервера целиком, по порядку внесения. */
    public static Map<String, BannerEntry> clientEntries = Map.of();

    private final Map<String, BannerEntry> entries = new LinkedHashMap<>();

    public static BannerRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Знамя на клиенте по id; нет такого — null. */
    @Nullable
    public static BannerEntry client(String id) {
        return id == null || id.isEmpty() ? null : clientEntries.get(id);
    }

    @Nullable
    public BannerEntry find(String id) {
        return entries.get(id);
    }

    public Collection<BannerEntry> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** Внести новое или заменить существующее (на прежнем месте в порядке) и разослать всем. */
    public void put(MinecraftServer server, BannerEntry entry) {
        entries.put(entry.id(), entry);
        changed(server);
    }

    public boolean remove(MinecraftServer server, String id) {
        BannerEntry removed = entries.remove(id);
        if (removed == null) {
            return false;
        }
        LOGGER.info("Знамя «{}» ({}) убрано из реестра", removed.name(), removed.id());
        changed(server);
        return true;
    }

    private void changed(MinecraftServer server) {
        setDirty();
        PacketDistributor.sendToAllPlayers(new BannersSyncPayload(new ArrayList<>(entries.values())));
    }

    /** Положить в инвентарь, а если места нет — бросить под ноги. */
    public static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Действие из окна реестра: только администрация; ответ — сообщением в чат, как у команд. */
    public static void act(ServerPlayer player, BannerActionPayload payload) {
        if (!Permissions.isAdmin(player)) {
            return;
        }
        BannerRegistry registry = get(player.server);
        BannerEntry entry = registry.find(payload.id());
        if (entry == null) {
            player.sendSystemMessage(BannerCommand.message("unknown", payload.id()));
            return;
        }
        switch (payload.action()) {
            case GIVE -> {
                give(player, entry.makeStack(1));
                player.sendSystemMessage(BannerCommand.message("given", entry.name(), 1, player.getDisplayName()));
            }
            case EDIT -> {
                String name = payload.name().strip();
                String description = payload.description().strip();
                if (name.isEmpty() || name.length() > BannerEntry.NAME_MAX || description.length() > BannerEntry.DESCRIPTION_MAX) {
                    return; // окно не даёт ввести такое — значит, пакет подделан
                }
                registry.put(player.server, entry.withName(name).withDescription(description));
                player.sendSystemMessage(BannerCommand.message("edited", name));
            }
            case REMOVE -> {
                registry.remove(player.server, entry.id());
                player.sendSystemMessage(BannerCommand.message("removed", entry.name(), entry.id()));
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new BannersSyncPayload(new ArrayList<>(get(player.server).entries.values())));
        }
    }

    // ===== Сохранение =====

    // Узоры баннеров — реестр данных: без контекста реестров слои не сохранятся и не прочтутся
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ListTag list = new ListTag();
        for (BannerEntry entry : entries.values()) {
            BannerEntry.CODEC.encodeStart(ops, entry)
                    .resultOrPartial(error -> LOGGER.error("Знамя {} не сохранилось: {}", entry.id(), error))
                    .ifPresent(list::add);
        }
        tag.put("banners", list);
        return tag;
    }

    private static BannerRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        BannerRegistry data = new BannerRegistry();
        for (Tag each : tag.getList("banners", Tag.TAG_COMPOUND)) {
            BannerEntry.CODEC.parse(ops, each)
                    .resultOrPartial(error -> LOGGER.error("Знамя из реестра не прочиталось: {}", error))
                    .ifPresent(entry -> data.entries.put(entry.id(), entry));
        }
        return data;
    }

    /** id всех знамён — для подсказок команд. */
    public List<String> ids() {
        return List.copyOf(entries.keySet());
    }
}
