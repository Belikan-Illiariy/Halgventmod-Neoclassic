package com.nrdhvnstd.halgventmod.npc;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Точки интереса мира — магазин, рынок, трактир, площадь: куда ходят горожане (VisitPoiGoal). Хранятся в данных
 * мира, общие на все измерения (у точки своё измерение). Ставит и снимает администрация жезлом НПС.
 */
public class NpcPois extends SavedData {
    /** Точка: вид, измерение и место. */
    public record Poi(PoiType type, ResourceKey<Level> dimension, BlockPos pos) {}

    private static final String NAME = HalgventMod.MODID + "_pois";
    private static final Factory<NpcPois> FACTORY = new Factory<>(NpcPois::new, NpcPois::load, null);
    public static final int MAX_POIS = 256; // ⚠️ на мир

    private final List<Poi> pois = new ArrayList<>();

    public static NpcPois get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** Поставить точку; false — достигнут предел. */
    public boolean add(PoiType type, ServerLevel level, BlockPos pos) {
        if (pois.size() >= MAX_POIS) {
            return false;
        }
        pois.add(new Poi(type, level.dimension(), pos.immutable()));
        setDirty();
        return true;
    }

    /** Ближайшая точка этого измерения в радиусе (блоков) от места; null — нет. */
    @Nullable
    public Poi nearest(ServerLevel level, BlockPos pos, double radius) {
        Poi best = null;
        double bestDistance = radius * radius;
        for (Poi poi : pois) {
            double distance = poi.dimension() == level.dimension() ? poi.pos().distSqr(pos) : Double.MAX_VALUE;
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = poi;
            }
        }
        return best;
    }

    public void remove(Poi poi) {
        if (pois.remove(poi)) {
            setDirty();
        }
    }

    /** Точки этого измерения в радиусе (блоков) от места. */
    public List<Poi> near(ServerLevel level, BlockPos pos, double radius) {
        List<Poi> result = new ArrayList<>();
        for (Poi poi : pois) {
            if (poi.dimension() == level.dimension() && poi.pos().distSqr(pos) <= radius * radius) {
                result.add(poi);
            }
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Poi poi : pois) {
            CompoundTag entry = new CompoundTag();
            entry.putString("type", poi.type().id());
            entry.putString("dimension", poi.dimension().location().toString());
            entry.putLong("pos", poi.pos().asLong());
            list.add(entry);
        }
        tag.put("pois", list);
        return tag;
    }

    private static NpcPois load(CompoundTag tag, HolderLookup.Provider registries) {
        NpcPois data = new NpcPois();
        for (Tag raw : tag.getList("pois", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            PoiType type = PoiType.byId(entry.getString("type"));
            var dimension = net.minecraft.resources.ResourceLocation.tryParse(entry.getString("dimension"));
            if (type != null && dimension != null) {
                data.pois.add(new Poi(type, ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension),
                        BlockPos.of(entry.getLong("pos"))));
            }
        }
        return data;
    }
}
