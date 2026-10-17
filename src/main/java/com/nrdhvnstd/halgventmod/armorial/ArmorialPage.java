package com.nrdhvnstd.halgventmod.armorial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.nrdhvnstd.halgventmod.banner.BannerEntry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Страница гербовника: LARGE — одно большое знамя с названием и описанием, GRID — сетка 3 × 3.
 * Хранит только id знамён ("" — пусто); названия и описания берутся из реестра знамён,
 * поэтому одно и то же знамя везде одинаковое.
 */
public record ArmorialPage(Type type, String title, List<String> banners) {
    public enum Type {
        LARGE(1), GRID(9);

        private final int slots;

        Type(int slots) {
            this.slots = slots;
        }

        public int slots() {
            return slots;
        }
    }

    public static final int TITLE_MAX = 32;

    public ArmorialPage {
        banners = List.copyOf(banners);
    }

    /** Пустая страница с одним большим знаменем. */
    public static ArmorialPage empty() {
        return new ArmorialPage(Type.LARGE, "", List.of(""));
    }

    /** Ровно столько ячеек, сколько у типа: лишние отрезаются, недостающие — пустые. */
    public static List<String> fit(Type type, List<String> banners) {
        List<String> result = new ArrayList<>(Collections.nCopies(type.slots(), ""));
        for (int i = 0; i < Math.min(type.slots(), banners.size()); i++) {
            result.set(i, banners.get(i));
        }
        return result;
    }

    /** Длины, число ячеек и id; есть ли знамёна в реестре, проверяет ArmorialBooks. */
    public boolean valid() {
        if (title.length() > TITLE_MAX || banners.size() != type.slots()) {
            return false;
        }
        for (String id : banners) {
            if (!id.isEmpty() && !BannerEntry.validId(id)) {
                return false;
            }
        }
        return true;
    }

    // ===== Сеть =====

    void write(FriendlyByteBuf buf) {
        buf.writeEnum(type);
        buf.writeUtf(title, TITLE_MAX * 4);
        buf.writeVarInt(banners.size());
        for (String id : banners) {
            buf.writeUtf(id, BannerEntry.ID_MAX);
        }
    }

    static ArmorialPage read(FriendlyByteBuf buf) {
        Type type = buf.readEnum(Type.class);
        String title = buf.readUtf(TITLE_MAX * 4);
        int count = Math.min(buf.readVarInt(), Type.GRID.slots());
        List<String> banners = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            banners.add(buf.readUtf(BannerEntry.ID_MAX));
        }
        return new ArmorialPage(type, title, banners);
    }

    // ===== Сохранение =====

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        tag.putString("title", title);
        ListTag list = new ListTag();
        for (String id : banners) {
            list.add(StringTag.valueOf(id));
        }
        tag.put("banners", list);
        return tag;
    }

    static ArmorialPage load(CompoundTag tag) {
        Type type = "GRID".equals(tag.getString("type")) ? Type.GRID : Type.LARGE;
        List<String> banners = new ArrayList<>();
        for (Tag each : tag.getList("banners", Tag.TAG_STRING)) {
            banners.add(each.getAsString());
        }
        return new ArmorialPage(type, tag.getString("title"), fit(type, banners));
    }
}
