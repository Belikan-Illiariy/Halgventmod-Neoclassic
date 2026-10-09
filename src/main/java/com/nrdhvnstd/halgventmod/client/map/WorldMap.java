package com.nrdhvnstd.halgventmod.client.map;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Карта мира в стиле Xaero's World Map. Каждый чанк, который клиент получил, раскрашивается сверху вниз
 * (MapColors), с водой, сквозь которую видно дно, — один раз, в данные региона (MapRegion); дальше в кадре
 * рисуются готовые текстуры. Чанки рядом с игроком время от времени пересматриваются — так на карту
 * попадают постройки. Заодно запоминаются биомы (клетками 4 × 4) — для подписей (BiomeLabels).
 *
 * Главный поток только сканирует чанки (не дольше SCAN_NANOS за тик) и принимает готовое; чтение и запись
 * файлов (MapStorage), сборка картинок по стилю (MapTile) и уровни отдаления (MapPyramid) — в фоне (MapWorker).
 * В памяти — только нужное: регионы и тайлы, которые давно не рисовались и далеко от игрока, выгружаются.
 * Карта хранится только на клиенте: .minecraft/halgventmod/map/<мир>/<измерение>/.
 * В измерениях с потолком (Незер) рисуется первый пол под потолком.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class WorldMap {
    private static final long SCAN_NANOS = 2_000_000;  // сканирование чанков за тик
    private static final long ACCEPT_NANOS = 3_000_000; // приём готового из фона за тик
    private static final int RESCAN_RADIUS = 5;        // пересмотр чанков вокруг игрока, по одному за тик
    private static final int SAVE_INTERVAL = 20 * 30;  // запись изменённых регионов раз в 30 секунд
    private static final int MAX_PENDING_LOADS = 24;   // больше задач в фоне — окно карты пока не просит новых
    // Регионы и тайлы, которые давно не рисовались и далеко от игрока, выгружаются (регион ~3 МБ)
    private static final int UNLOAD_INTERVAL = 20 * 10;
    private static final long UNLOAD_AFTER_MS = 60 * 1000;

    // Вода: насколько она закрывает дно — у берега и с глубиной
    private static final float WATER_MIN = 0.45F;
    private static final float WATER_PER_BLOCK = 0.06F;
    private static final float WATER_MAX = 0.88F;
    private static final int CEILING_SCAN_BELOW_TOP = 9; // Незер: начинаем чуть ниже верха

    private static final List<ChunkPos> RESCAN_ORDER = rescanOrder();

    private static ClientLevel level;
    private static Path directory;

    // Регионы: в памяти, читаются в фоне, точно нет на диске
    private static final Map<Long, MapRegion> REGIONS = new HashMap<>();
    private static final Set<Long> REGIONS_LOADING = new HashSet<>();
    private static final Set<Long> REGIONS_MISSING = new HashSet<>();
    // Тайлы уровней отдаления — то же, по уровню (индекс 0 не используется)
    @SuppressWarnings("unchecked")
    private static final Map<Long, MapLod>[] LODS = new Map[MapPyramid.MAX_LEVEL + 1];
    @SuppressWarnings("unchecked")
    private static final Set<Long>[] LODS_LOADING = new Set[MapPyramid.MAX_LEVEL + 1];
    @SuppressWarnings("unchecked")
    private static final Set<Long>[] LODS_MISSING = new Set[MapPyramid.MAX_LEVEL + 1];

    static {
        for (int i = 1; i <= MapPyramid.MAX_LEVEL; i++) {
            LODS[i] = new HashMap<>();
            LODS_LOADING[i] = new HashSet<>();
            LODS_MISSING[i] = new HashSet<>();
        }
    }

    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static final ChunkTints TINTS = new ChunkTints();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static int rescanIndex;
    private static int ticksToSave = SAVE_INTERVAL;
    private static int ticksToUnload = UNLOAD_INTERVAL;
    private static boolean texturesReloaded;
    /** Растёт при каждом изменении биомов в памяти — подписи биомов пересчитываются только тогда. */
    private static int biomeRevision;
    /** Уровень, который сейчас показывает окно карты: по нему ищутся биомы и высоты вдали. */
    private static int shownLevel;

    private WorldMap() {}

    // ===== Для окна карты =====

    /** Регион для рисования; нет в памяти — заказывает чтение в фоне и возвращает null. */
    public static MapRegion region(int rx, int rz) {
        if (level == null) {
            return null;
        }
        long key = MapStorage.key(rx, rz);
        MapRegion region = REGIONS.get(key);
        if (region == null && !REGIONS_MISSING.contains(key) && MapWorker.pending() < MAX_PENDING_LOADS) {
            requestRegion(key);
        }
        return region;
    }

    /** Тайл уровня отдаления для рисования; нет в памяти — заказывает чтение и возвращает null. */
    static MapLod lod(int lodLevel, int tx, int tz) {
        if (level == null) {
            return null;
        }
        long key = MapStorage.key(tx, tz);
        MapLod tile = LODS[lodLevel].get(key);
        if (tile == null && !LODS_MISSING[lodLevel].contains(key) && MapWorker.pending() < MAX_PENDING_LOADS) {
            requestLod(lodLevel, key);
        }
        return tile;
    }

    /** Тайл любого уровня, только если уже в памяти: 0 — регион. Для подмены, пока нужный грузится. */
    static MapTile loadedTile(int tileLevel, int tx, int tz) {
        long key = MapStorage.key(tx, tz);
        return tileLevel == 0 ? REGIONS.get(key) : LODS[tileLevel].get(key);
    }

    static void setShownLevel(int lodLevel) {
        shownLevel = lodLevel;
    }

    static int biomeRevision() {
        return biomeRevision;
    }

    /** Высота на карте по памяти: регион или тайл показываемого уровня; NO_HEIGHT — неизвестно. */
    static short loadedHeight(int blockX, int blockZ) {
        MapTile tile = tileAt(blockX, blockZ);
        if (tile == null) {
            return MapTile.NO_HEIGHT;
        }
        int mask = MapTile.SIZE - 1;
        return tile.height((blockX >> tile.level) & mask, (blockZ >> tile.level) & mask);
    }

    /** Биом в этом месте карты: номер в MapPalettes, 0 — неизвестно. Только из памяти. */
    public static int biomeAt(int blockX, int blockZ) {
        MapTile tile = tileAt(blockX, blockZ);
        if (tile == null) {
            return 0;
        }
        int mask = MapTile.CELLS - 1;
        int shift = tile.level + MapTile.CELL_SHIFT;
        return tile.biome((blockX >> shift) & mask, (blockZ >> shift) & mask);
    }

    // Регион, если он в памяти, иначе тайл показываемого уровня
    private static MapTile tileAt(int blockX, int blockZ) {
        MapRegion region = REGIONS.get(MapStorage.key(blockX >> MapTile.SHIFT, blockZ >> MapTile.SHIFT));
        if (region != null || shownLevel == 0) {
            return region;
        }
        int shift = MapTile.SHIFT + shownLevel;
        return LODS[shownLevel].get(MapStorage.key(blockX >> shift, blockZ >> shift));
    }

    /** Биом по номеру из biomeAt. */
    public static ResourceLocation biome(int id) {
        return MapPalettes.biomeName(id);
    }

    /** Нижний ряд (bottom) или правый столбец высот региона, если он в памяти, — для тени на краю соседа. */
    static short[] edge(int rx, int rz, boolean bottom) {
        MapRegion region = REGIONS.get(MapStorage.key(rx, rz));
        if (region == null) {
            return null;
        }
        short[] edge = new short[MapTile.SIZE];
        for (int i = 0; i < MapTile.SIZE; i++) {
            edge[i] = bottom ? region.height(i, MapTile.SIZE - 1) : region.height(MapTile.SIZE - 1, i);
        }
        return edge;
    }

    // ===== Из фонового потока =====

    private static void requestRegion(long key) {
        if (!REGIONS_LOADING.add(key)) {
            return;
        }
        Path dir = directory;
        int generation = MapWorker.generation();
        MapWorker.submit(() -> {
            MapRegion.Data data = MapStorage.readRegion(dir, key);
            MapWorker.toMain(generation, () -> regionLoaded(key, data));
        });
    }

    private static void regionLoaded(long key, MapRegion.Data data) {
        REGIONS_LOADING.remove(key);
        if (data == null) {
            REGIONS_MISSING.add(key);
        } else if (!REGIONS.containsKey(key)) {
            REGIONS.put(key, new MapRegion(ChunkPos.getX(key), ChunkPos.getZ(key), data));
            biomeRevision++;
        }
    }

    private static void requestLod(int lodLevel, long key) {
        if (!LODS_LOADING[lodLevel].add(key)) {
            return;
        }
        Path dir = directory;
        int generation = MapWorker.generation();
        MapWorker.submit(() -> {
            MapLod.Data data = MapStorage.readLod(dir, lodLevel, key);
            MapWorker.toMain(generation, () -> lodLoaded(lodLevel, key, data));
        });
    }

    private static void lodLoaded(int lodLevel, long key, MapLod.Data data) {
        LODS_LOADING[lodLevel].remove(key);
        if (data == null) {
            LODS_MISSING[lodLevel].add(key);
        } else if (!LODS[lodLevel].containsKey(key)) {
            LODS[lodLevel].put(key, new MapLod(lodLevel, ChunkPos.getX(key), ChunkPos.getZ(key), data));
            biomeRevision++;
        }
    }

    /** MapPyramid перестроила тайл: он больше не «отсутствует», а если показан — обновляется. */
    static void lodUpdated(int lodLevel, long key, MapLod.Data data) {
        LODS_MISSING[lodLevel].remove(key);
        MapLod tile = LODS[lodLevel].get(key);
        if (tile != null) {
            tile.replace(data);
            biomeRevision++;
        }
    }

    /** MapPyramid начала строить уровни с нуля — прежние тайлы в памяти больше не верны. */
    static void lodsCleared() {
        for (int i = 1; i <= MapPyramid.MAX_LEVEL; i++) {
            LODS[i].values().forEach(MapTile::close);
            LODS[i].clear();
            LODS_MISSING[i].clear();
        }
        biomeRevision++;
    }

    // ===== События =====

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel clientLevel && event.getChunk() instanceof LevelChunk chunk) {
            switchTo(clientLevel);
            enqueue(chunk.getPos().toLong());
        }
    }

    private static void enqueue(long chunkKey) {
        if (QUEUED.add(chunkKey)) {
            QUEUE.add(chunkKey);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        switchTo(minecraft.level);
        if (level == null || minecraft.player == null) {
            return;
        }
        MapWorker.runMainTasks(ACCEPT_NANOS);
        if (texturesReloaded) {
            texturesReloaded = false;
            recolorAll();
        }

        // Новые чанки — пока не кончилось время тика; чанк, чей регион ещё читается, — в конец очереди
        long deadline = System.nanoTime() + SCAN_NANOS;
        for (int left = QUEUE.size(); left > 0 && !QUEUE.isEmpty() && System.nanoTime() < deadline; left--) {
            long key = QUEUE.poll();
            QUEUED.remove(key);
            if (!scan(ChunkPos.getX(key), ChunkPos.getZ(key))) {
                enqueue(key);
            }
        }
        // Пересмотр вокруг игрока: изменения в мире (постройки, вырубка) появляются на карте
        ChunkPos center = minecraft.player.chunkPosition();
        if (System.nanoTime() < deadline) {
            ChunkPos offset = RESCAN_ORDER.get(rescanIndex);
            rescanIndex = (rescanIndex + 1) % RESCAN_ORDER.size();
            scan(center.x + offset.x, center.z + offset.z);
        }

        if (--ticksToSave <= 0) {
            ticksToSave = SAVE_INTERVAL;
            saveDirty();
        }
        if (--ticksToUnload <= 0) {
            ticksToUnload = UNLOAD_INTERVAL;
            unloadUnused(center);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        switchTo(null);
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            MapColors.clear();
            texturesReloaded = true; // цвета пересчитаем на следующем тике, когда модели уже точно новые
        });
    }

    // ===== Выгрузка и запись =====

    // Регион игрока и соседние остаются всегда; остальное — пока его недавно рисовали или в него писали
    private static void unloadUnused(ChunkPos player) {
        int playerRx = player.x >> (MapTile.SHIFT - 4);
        int playerRz = player.z >> (MapTile.SHIFT - 4);
        long now = Util.getMillis();
        List<MapRegion.Snapshot> toSave = new ArrayList<>();
        Iterator<Map.Entry<Long, MapRegion>> it = REGIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, MapRegion> entry = it.next();
            long key = entry.getKey();
            MapRegion region = entry.getValue();
            boolean nearPlayer = Math.abs(ChunkPos.getX(key) - playerRx) <= 1 && Math.abs(ChunkPos.getZ(key) - playerRz) <= 1;
            if (nearPlayer || now - region.lastUsed() < UNLOAD_AFTER_MS) {
                continue;
            }
            if (region.needsSave()) {
                toSave.add(region.snapshot(key));
            }
            region.close();
            it.remove();
            biomeRevision++;
        }
        save(toSave);
        for (int i = 1; i <= MapPyramid.MAX_LEVEL; i++) {
            Iterator<MapLod> tiles = LODS[i].values().iterator();
            while (tiles.hasNext()) {
                MapLod tile = tiles.next();
                if (now - tile.lastUsed() >= UNLOAD_AFTER_MS) {
                    tile.close();
                    tiles.remove();
                    biomeRevision++;
                }
            }
        }
    }

    private static void saveDirty() {
        List<MapRegion.Snapshot> toSave = new ArrayList<>();
        REGIONS.forEach((key, region) -> {
            if (region.needsSave()) {
                toSave.add(region.snapshot(key));
            }
        });
        save(toSave);
    }

    // Пачкой: регионы на диск, затем уровни отдаления над ними — каждый задетый тайл один раз
    private static void save(List<MapRegion.Snapshot> snapshots) {
        if (snapshots.isEmpty() || directory == null) {
            return;
        }
        Path dir = directory;
        int generation = MapWorker.generation();
        MapWorker.submit(() -> {
            for (MapRegion.Snapshot snapshot : snapshots) {
                MapStorage.writeRegion(dir, snapshot.key(), snapshot.data());
            }
            MapPyramid.update(dir, generation, snapshots);
        });
    }

    // ===== Смена мира, текстур =====

    private static void switchTo(ClientLevel newLevel) {
        if (newLevel == level) {
            return;
        }
        saveDirty();
        REGIONS.values().forEach(MapTile::close);
        REGIONS.clear();
        REGIONS_LOADING.clear();
        REGIONS_MISSING.clear();
        for (int i = 1; i <= MapPyramid.MAX_LEVEL; i++) {
            LODS[i].values().forEach(MapTile::close);
            LODS[i].clear();
            LODS_LOADING[i].clear();
            LODS_MISSING[i].clear();
        }
        QUEUE.clear();
        QUEUED.clear();
        MapWorker.newGeneration();
        biomeRevision++;
        level = newLevel;
        directory = newLevel == null ? null : directoryFor(newLevel);
        Waypoints.switchTo(directory);
        MapTexts.switchTo(directory);
        MapDrawing.switchTo(directory);
        if (directory != null) {
            // Уровни отдаления ещё не строились (карта старого формата) — построить в фоне
            Path dir = directory;
            int generation = MapWorker.generation();
            MapWorker.submit(() -> {
                if (MapStorage.pyramidVersion(dir) != MapStorage.PYRAMID_VERSION) {
                    new MapPyramid.Rebuild(dir, generation).start();
                }
            });
        }
    }

    // Сменились текстуры: цвета в памяти — пересчитать по новым базам блоков; на диске — в фоне, вместе с уровнями
    private static void recolorAll() {
        int count = MapPalettes.blockCount();
        int[] oldBases = new int[count];
        int[] newBases = new int[count];
        boolean changed = false;
        for (int id = 1; id < count; id++) {
            oldBases[id] = MapPalettes.base(id);
            int base = MapColors.base(MapPalettes.blockState(id));
            newBases[id] = base == 0 ? oldBases[id] : base;
            changed |= newBases[id] != oldBases[id];
        }
        if (!changed) {
            return;
        }
        for (int id = 1; id < count; id++) {
            MapPalettes.setBase(id, newBases[id]);
        }
        REGIONS.values().forEach(region -> region.recolor(oldBases, newBases));
        if (directory != null) {
            new MapPyramid.Rebuild(directory, MapWorker.generation()).start();
        }
    }

    /** Высота поверхности в этом месте, если чанк загружен; иначе — null. */
    public static Integer surfaceHeight(int x, int z) {
        if (level == null) {
            return null;
        }
        LevelChunk chunk = level.getChunkSource().getChunk(x >> 4, z >> 4, false);
        if (chunk == null || chunk.isEmpty()) {
            return null;
        }
        int y = surface(chunk, x, z, new BlockPos.MutableBlockPos());
        return y == Integer.MIN_VALUE ? null : y + 1;
    }

    private static Path directoryFor(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        String world;
        IntegratedServer server = minecraft.getSingleplayerServer();
        ServerData serverData = minecraft.getCurrentServer();
        if (server != null) {
            world = "local_" + server.getWorldPath(LevelResource.ROOT).normalize().getFileName();
        } else if (serverData != null) {
            world = "server_" + serverData.ip;
        } else {
            world = "unknown";
        }
        ResourceLocation dimension = level.dimension().location();
        return minecraft.gameDirectory.toPath().resolve(HalgventMod.MODID).resolve("map")
                .resolve(safe(world)).resolve(safe(dimension.getNamespace() + "_" + dimension.getPath()));
    }

    // Только буквы, цифры, точка, минус и подчёркивание — остальное в «_»
    private static String safe(String name) {
        StringBuilder result = new StringBuilder();
        name.codePoints().forEach(c -> result.appendCodePoint(
                Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' ? c : '_'));
        return result.toString();
    }

    // ===== Раскраска чанка =====

    /** Раскрасить чанк. false — его регион ещё читается с диска: попробовать позже. */
    private static boolean scan(int chunkX, int chunkZ) {
        LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (chunk == null || chunk.isEmpty()) {
            return true; // уже выгружен — нечего делать
        }
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        MapRegion region = regionForScan(baseX >> MapTile.SHIFT, baseZ >> MapTile.SHIFT);
        if (region == null) {
            return false;
        }
        region.touch();
        TINTS.begin(level, chunkX, chunkZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int mask = MapTile.SIZE - 1;
        int localX = baseX & mask;
        int localZ = baseZ & mask;
        boolean changed = false;

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int worldX = baseX + x;
                int worldZ = baseZ + z;
                int y = surface(chunk, worldX, worldZ, pos);
                if (y == Integer.MIN_VALUE) {
                    changed |= region.set(localX + x, localZ + z, 0, (short) 0, MapTile.NO_HEIGHT);
                    continue;
                }
                BlockState state = chunk.getBlockState(pos.set(worldX, y, worldZ));
                boolean water = state.getFluidState().is(FluidTags.WATER);
                // Блок, давший цвет: у воды (и водорослей в ней) цвет — от воды
                short block = MapPalettes.block(water ? WATER : state);
                int color = water
                        ? withAlpha(waterColor(chunk, worldX, y, worldZ, pos), MapStyle.ALPHA_TINTED)
                        : withAlpha(MapColors.color(TINTS, pos, state),
                                MapColors.tinted(state) ? MapStyle.ALPHA_TINTED : MapStyle.ALPHA_PLAIN);
                changed |= region.set(localX + x, localZ + z, color, block, y);
                // Биом клетки 4 × 4 — по её середине, на высоте поверхности
                if ((x & 3) == 2 && (z & 3) == 2) {
                    int cellX = (worldX >> MapTile.CELL_SHIFT) & (MapTile.CELLS - 1);
                    int cellZ = (worldZ >> MapTile.CELL_SHIFT) & (MapTile.CELLS - 1);
                    var biome = level.getBiome(pos.set(worldX, y, worldZ)).unwrapKey();
                    if (biome.isPresent() && region.setBiome(cellX, cellZ, MapPalettes.biome(biome.get().location()))) {
                        biomeRevision++;
                    }
                }
            }
        }
        if (changed) {
            region.dataChanged(localX, localZ, localX + 15, localZ + 15);
        }
        return true;
    }

    // Регион для записи: в памяти; точно нет на диске — новый пустой; иначе заказать чтение и подождать
    private static MapRegion regionForScan(int rx, int rz) {
        long key = MapStorage.key(rx, rz);
        MapRegion region = REGIONS.get(key);
        if (region != null) {
            return region;
        }
        if (REGIONS_MISSING.remove(key)) {
            region = new MapRegion(rx, rz, MapRegion.Data.empty());
            REGIONS.put(key, region);
            return region;
        }
        requestRegion(key);
        return null;
    }

    /** Высота верхнего видимого блока; MIN_VALUE — столбец пуст. */
    private static int surface(LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos pos) {
        int min = level.getMinBuildHeight();
        int y;
        if (level.dimensionType().hasCeiling()) {
            y = Math.min(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15),
                    min + level.dimensionType().logicalHeight() - CEILING_SCAN_BELOW_TOP);
            while (y >= min && !chunk.getBlockState(pos.set(x, y, z)).isAir()) {
                y--; // сквозь потолок
            }
        } else {
            y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        }
        for (; y >= min; y--) {
            if (visible(chunk.getBlockState(pos.set(x, y, z)))) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean visible(BlockState state) {
        return state.getRenderShape() != RenderShape.INVISIBLE || !state.getFluidState().isEmpty();
    }

    private static int withAlpha(int color, int alpha) {
        return alpha << 24 | (color & 0xFFFFFF);
    }

    // Вода: цвет воды биома поверх дна, тем плотнее, чем глубже
    private static int waterColor(LevelChunk chunk, int x, int y, int z, BlockPos.MutableBlockPos pos) {
        int water = MapColors.color(TINTS, pos.set(x, y, z), WATER);
        int min = level.getMinBuildHeight();
        int floorY = y - 1;
        while (floorY >= min && chunk.getBlockState(pos.set(x, floorY, z)).getFluidState().is(FluidTags.WATER)) {
            floorY--;
        }
        if (floorY < min) {
            return water;
        }
        int floor = MapColors.color(TINTS, pos, chunk.getBlockState(pos));
        float cover = Mth.clamp(WATER_MIN + (y - floorY) * WATER_PER_BLOCK, WATER_MIN, WATER_MAX);
        return FastColor.ARGB32.lerp(cover, floor, water) | 0xFF000000;
    }

    // Чанки вокруг игрока, от ближних к дальним
    private static List<ChunkPos> rescanOrder() {
        List<ChunkPos> order = new ArrayList<>();
        for (int dx = -RESCAN_RADIUS; dx <= RESCAN_RADIUS; dx++) {
            for (int dz = -RESCAN_RADIUS; dz <= RESCAN_RADIUS; dz++) {
                order.add(new ChunkPos(dx, dz));
            }
        }
        order.sort(Comparator.comparingInt(p -> p.x * p.x + p.z * p.z));
        return order;
    }
}
