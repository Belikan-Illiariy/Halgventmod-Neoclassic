package com.nrdhvnstd.halgventmod.client.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Общие на всю игру номера блоков и биомов для данных карты. В памяти пиксель хранит номер,
 * на диске у каждого файла своя палитра с именами (MapStorage) — номера переводятся при чтении и записи.
 * Номер 0 — «неизвестно». Палитры дополняются и из главного потока, и из фонового (MapWorker), поэтому
 * все методы синхронизированы; вызываются они не на каждый пиксель, а на новый блок или биом.
 *
 * У блока запоминается и «база» — средний цвет его текстуры (MapColors.base), под который посчитаны
 * цвета карты в памяти. Сменились текстуры — цвета пересчитываются отношением новой базы к старой.
 */
final class MapPalettes {
    private static final List<BlockState> BLOCKS = new ArrayList<>(Collections.singletonList(null)); // номер 0 — «неизвестно»
    private static final Map<BlockState, Integer> BLOCK_IDS = new IdentityHashMap<>();
    private static int[] bases = new int[64];

    private static final List<ResourceLocation> BIOMES = new ArrayList<>(Collections.singletonList(null));
    private static final Map<ResourceLocation, Integer> BIOME_IDS = new HashMap<>();

    private MapPalettes() {}

    // ===== Блоки =====

    /** Номер блока; новый блок получает номер и базу — текущий средний цвет его текстуры. */
    static synchronized short block(BlockState state) {
        Integer id = BLOCK_IDS.get(state);
        if (id == null) {
            id = BLOCKS.size();
            if (id > Short.MAX_VALUE) {
                return 0; // столько разных блоков не бывает; на всякий случай — «неизвестно»
            }
            BLOCKS.add(state);
            BLOCK_IDS.put(state, id);
            if (id >= bases.length) {
                bases = java.util.Arrays.copyOf(bases, bases.length * 2);
            }
            bases[id] = MapColors.base(state);
        }
        return (short) (int) id;
    }

    static synchronized BlockState blockState(int id) {
        return id > 0 && id < BLOCKS.size() ? BLOCKS.get(id) : null;
    }

    /** База, под которую посчитаны цвета этого блока в памяти. */
    static synchronized int base(int id) {
        return id > 0 && id < BLOCKS.size() ? bases[id] : 0;
    }

    static synchronized void setBase(int id, int base) {
        if (id > 0 && id < BLOCKS.size()) {
            bases[id] = base;
        }
    }

    static synchronized int blockCount() {
        return BLOCKS.size();
    }

    // ===== Биомы =====

    static synchronized short biome(ResourceLocation biome) {
        Integer id = BIOME_IDS.get(biome);
        if (id == null) {
            id = BIOMES.size();
            BIOMES.add(biome);
            BIOME_IDS.put(biome, id);
        }
        return (short) (int) id;
    }

    static synchronized ResourceLocation biomeName(int id) {
        return id > 0 && id < BIOMES.size() ? BIOMES.get(id) : null;
    }

    // ===== Пересчёт цвета =====

    /** Цвет пикселя (ARGB, вид в альфе) после смены базы его блока: каждый канал — в отношении новой базы к старой. */
    static int recolor(int color, int oldBase, int newBase) {
        if (oldBase == newBase || oldBase == 0 || newBase == 0 || color >>> 24 == 0) {
            return color;
        }
        int result = color & 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int value = (color >> shift) & 0xFF;
            int from = (oldBase >> shift) & 0xFF;
            int to = (newBase >> shift) & 0xFF;
            int channel = from == 0 ? to : Math.min(255, value * to / from);
            result |= channel << shift;
        }
        return result;
    }
}
