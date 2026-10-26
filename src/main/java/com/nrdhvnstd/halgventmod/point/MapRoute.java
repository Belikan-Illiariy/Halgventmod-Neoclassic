package com.nrdhvnstd.halgventmod.point;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Маршрут — ломаная по миру для будущих НПС. Рисует и видит только администрация (MapRoutes).
 * Вершины (xs; zs) — в блоках мира, от MIN_POINTS до MAX_POINTS. Поля открыты: их правит техническая карта.
 */
public final class MapRoute {
    public static final int NAME_MAX = 32;
    public static final int MIN_POINTS = 2;
    public static final int MAX_POINTS = 256; // ⚠️
    /** Светло-оранжевый — цвет нового маршрута. */
    public static final int DEFAULT_COLOR = 0xFFFFB060; // ⚠️

    /** 0 — ещё не создан (номер даёт сервер). */
    public int id;
    public String name = "";
    /** Измерение — ResourceLocation строкой; ставит сервер. */
    public String dimension = "";
    public List<Integer> xs = new ArrayList<>();
    public List<Integer> zs = new ArrayList<>();
    /** ARGB. */
    public int color = DEFAULT_COLOR;

    public MapRoute copy() {
        MapRoute copy = new MapRoute();
        copy.id = id;
        copy.name = name;
        copy.dimension = dimension;
        copy.xs = new ArrayList<>(xs);
        copy.zs = new ArrayList<>(zs);
        copy.color = color;
        return copy;
    }

    public int size() {
        return xs.size();
    }

    public boolean valid() {
        return name != null && name.length() <= NAME_MAX && !dimension.isEmpty() && xs.size() == zs.size()
                && xs.size() >= MIN_POINTS && xs.size() <= MAX_POINTS;
    }

    // ===== Сеть =====

    public static final StreamCodec<FriendlyByteBuf, MapRoute> STREAM_CODEC = StreamCodec.of(
            (buf, route) -> route.write(buf), MapRoute::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(id);
        buf.writeUtf(name, NAME_MAX * 4);
        buf.writeUtf(dimension);
        int count = Math.min(xs.size(), MAX_POINTS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeVarInt(xs.get(i));
            buf.writeVarInt(zs.get(i));
        }
        buf.writeInt(color);
    }

    private static MapRoute read(FriendlyByteBuf buf) {
        MapRoute route = new MapRoute();
        route.id = buf.readVarInt();
        route.name = buf.readUtf(NAME_MAX * 4);
        route.dimension = buf.readUtf();
        int count = Math.min(buf.readVarInt(), MAX_POINTS);
        for (int i = 0; i < count; i++) {
            route.xs.add(buf.readVarInt());
            route.zs.add(buf.readVarInt());
        }
        route.color = buf.readInt();
        return route;
    }

    // ===== Сохранение =====

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("name", name);
        tag.putString("dimension", dimension);
        tag.put("xs", new IntArrayTag(xs.stream().mapToInt(Integer::intValue).toArray()));
        tag.put("zs", new IntArrayTag(zs.stream().mapToInt(Integer::intValue).toArray()));
        tag.putInt("color", color);
        return tag;
    }

    public static MapRoute load(CompoundTag tag) {
        MapRoute route = new MapRoute();
        route.id = tag.getInt("id");
        route.name = tag.getString("name");
        route.dimension = tag.getString("dimension");
        for (int value : tag.getIntArray("xs")) {
            route.xs.add(value);
        }
        for (int value : tag.getIntArray("zs")) {
            route.zs.add(value);
        }
        route.color = tag.contains("color") ? tag.getInt("color") : DEFAULT_COLOR;
        return route;
    }
}
