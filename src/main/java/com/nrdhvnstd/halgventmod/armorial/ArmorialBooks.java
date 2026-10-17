package com.nrdhvnstd.halgventmod.armorial;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.banner.BannerRegistry;
import com.nrdhvnstd.halgventmod.network.ArmorialContentPayload;
import com.nrdhvnstd.halgventmod.network.ArmorialTitlesPayload;
import com.nrdhvnstd.halgventmod.point.MapPoints;

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
import net.neoforged.neoforge.network.PacketDistributor;

import org.slf4j.Logger;

/**
 * Книги-гербовники мира: id книги → ArmorialBook. Предмет хранит только id (компонент armorial_book, нет — MAIN).
 * Содержимое уходит игроку, когда он открывает книгу (ArmorialContentPayload); названия книг — всем,
 * для подсказки предмета (ArmorialTitlesPayload). Правит администрация в творческом (ArmorialEditPayload).
 * Книга MAIN есть всегда: пока её не создали командой, она пустая и без названия.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public class ArmorialBooks extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAME = HalgventMod.MODID + "_armorials";
    private static final Factory<ArmorialBooks> FACTORY = new Factory<>(ArmorialBooks::new, ArmorialBooks::load, null);
    public static final String MAIN = "main";

    /** Названия книг на клиенте (для подсказки предмета). */
    public static Map<String, String> clientTitles = Map.of();

    private final Map<String, ArmorialBook> books = new LinkedHashMap<>();

    public static ArmorialBooks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Книга по id; MAIN, которую ещё не создавали, — пустая. Нет такой — null. */
    @Nullable
    public ArmorialBook find(String id) {
        ArmorialBook book = books.get(id);
        return book == null && MAIN.equals(id) ? ArmorialBook.EMPTY : book;
    }

    public boolean exists(String id) {
        return books.containsKey(id);
    }

    public Collection<String> ids() {
        return List.copyOf(books.keySet());
    }

    public Map<String, ArmorialBook> all() {
        return Collections.unmodifiableMap(books);
    }

    public void put(String id, ArmorialBook book) {
        books.put(id, book);
        changed();
    }

    public boolean remove(String id) {
        if (books.remove(id) == null) {
            return false;
        }
        changed();
        return true;
    }

    private void changed() {
        setDirty();
        PacketDistributor.sendToAllPlayers(new ArmorialTitlesPayload(titles()));
    }

    private Map<String, String> titles() {
        Map<String, String> result = new LinkedHashMap<>();
        books.forEach((id, book) -> result.put(id, book.title()));
        return result;
    }

    // ===== Открытие и правка =====

    /** Игрок открыл книгу id: отправить содержимое; администрация в творческом получает её сразу в правку. */
    public static void open(ServerPlayer player, String id) {
        ArmorialBook book = get(player.server).find(id);
        if (book == null) {
            player.sendSystemMessage(ArmorialCommand.message("missing", id));
            return;
        }
        PacketDistributor.sendToPlayer(player, new ArmorialContentPayload(id, book, MapPoints.canEdit(player)));
    }

    /**
     * Правка от игрока: права (администрация в творческом), длины, число страниц, знамёна только из реестра.
     * Название книги здесь не меняется (только командой rename).
     */
    public static void edit(ServerPlayer player, String id, ArmorialBook edited) {
        if (!MapPoints.canEdit(player)) {
            return;
        }
        ArmorialBooks data = get(player.server);
        ArmorialBook old = data.find(id);
        if (old == null || edited.pages().size() > ArmorialBook.PAGES_MAX) {
            return;
        }
        BannerRegistry registry = BannerRegistry.get(player.server);
        for (ArmorialPage page : edited.pages()) {
            if (!page.valid()) {
                return;
            }
            for (String banner : page.banners()) {
                if (!banner.isEmpty() && registry.find(banner) == null) {
                    return;
                }
            }
        }
        data.put(id, old.withPages(new ArrayList<>(edited.pages())));
        LOGGER.info("{} поправил гербовник {} ({} стр.)", player.getGameProfile().getName(), id, edited.pages().size());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new ArmorialTitlesPayload(get(player.server).titles()));
        }
    }

    // ===== Сохранение =====

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        books.forEach((id, book) -> {
            CompoundTag entry = book.save();
            entry.putString("id", id);
            list.add(entry);
        });
        tag.put("books", list);
        return tag;
    }

    private static ArmorialBooks load(CompoundTag tag, HolderLookup.Provider registries) {
        ArmorialBooks data = new ArmorialBooks();
        for (Tag each : tag.getList("books", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) each;
            data.books.put(entry.getString("id"), ArmorialBook.load(entry));
        }
        return data;
    }
}
