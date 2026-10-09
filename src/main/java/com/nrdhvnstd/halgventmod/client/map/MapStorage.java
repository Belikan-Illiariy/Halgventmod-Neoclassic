package com.nrdhvnstd.halgventmod.client.map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import org.slf4j.Logger;

/**
 * Файлы карты. Только для фонового потока (MapWorker). В папке карты (WorldMap.directoryFor):
 *   <rx>_<rz>.region  — регион: палитра блоков (имя состояния и его база — средний цвет текстуры при записи),
 *                       палитра биомов, затем цвета, блоки, высоты, биомы. Всё сжато gzip.
 *   lod/<L>_<x>_<z>.lod — тайл уровня отдаления: палитра биомов, цвета, высоты, средние склоны, биомы.
 *   lod/pyramid       — номер версии построенных уровней; нет файла — их надо построить (MapPyramid).
 * Старые регионы (<rx>_<rz>.png, .heights, .biomes, .thumb) читаются, блоков в них нет;
 * при следующей записи региона заменяются файлом .region.
 * Пишется через временный файл: оборванная запись не портит прежний.
 */
final class MapStorage {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final String REGION_EXTENSION = ".region";
    private static final int REGION_VERSION = 1;
    private static final int LOD_VERSION = 2;     // 2: средние склоны для рельефа издалека
    static final int PYRAMID_VERSION = 2;
    private static final String LOD_FOLDER = "lod";
    private static final String PYRAMID_FILE = "pyramid";
    private static final String[] LEGACY_EXTENSIONS = {".png", ".heights", ".biomes", ".thumb"};
    private static final Pattern REGION_FILE = Pattern.compile("(-?\\d+)_(-?\\d+)\\.(region|png)");
    private static final int PIXELS = MapTile.SIZE * MapTile.SIZE;
    private static final int CELLS = MapTile.CELLS * MapTile.CELLS;

    private MapStorage() {}

    static long key(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    private static Path regionFile(Path dir, long key, String extension) {
        return dir.resolve(ChunkPos.getX(key) + "_" + ChunkPos.getZ(key) + extension);
    }

    private static Path lodFile(Path dir, int level, long key) {
        return dir.resolve(LOD_FOLDER).resolve(level + "_" + ChunkPos.getX(key) + "_" + ChunkPos.getZ(key) + ".lod");
    }

    // ===== Регионы =====

    /** Регион с диска; null — его нет. Цвета пересчитаны под нынешние текстуры. */
    static MapRegion.Data readRegion(Path dir, long key) {
        Path file = regionFile(dir, key, REGION_EXTENSION);
        if (Files.isRegularFile(file)) {
            try (DataInputStream input = open(file)) {
                if (input.readInt() == REGION_VERSION) {
                    return readRegion(input);
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Не удалось прочитать регион карты {}", file, e);
            }
            return null;
        }
        return readLegacy(dir, key);
    }

    private static MapRegion.Data readRegion(DataInputStream input) throws IOException {
        // Палитра блоков: локальный номер → общий; заодно — у каких блоков сменилась база
        int count = input.readInt();
        short[] blockIds = new short[count + 1];
        int[] storedBases = new int[count + 1];
        int[] currentBases = new int[count + 1];
        for (int i = 1; i <= count; i++) {
            BlockState state = parse(input.readUTF());
            storedBases[i] = input.readInt();
            if (state != null) {
                blockIds[i] = MapPalettes.block(state);
                currentBases[i] = MapPalettes.base(blockIds[i]);
            }
        }
        short[] biomeIds = readBiomePalette(input);
        int[] colors = readInts(input, PIXELS);
        short[] blocks = readShorts(input, PIXELS);
        short[] heights = readShorts(input, PIXELS);
        short[] biomes = readShorts(input, CELLS);
        for (int i = 0; i < PIXELS; i++) {
            int local = blocks[i];
            if (local <= 0 || local > count) {
                blocks[i] = 0;
                continue;
            }
            colors[i] = MapPalettes.recolor(colors[i], storedBases[local], currentBases[local]);
            blocks[i] = blockIds[local];
        }
        toGlobal(biomes, biomeIds);
        return new MapRegion.Data(colors, blocks, heights, biomes);
    }

    // Старый формат: картинка с сырыми цветами + высоты + биомы; блоков нет
    private static MapRegion.Data readLegacy(Path dir, long key) {
        Path file = regionFile(dir, key, ".png");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        int[] colors = new int[PIXELS];
        try (InputStream input = Files.newInputStream(file); NativeImage image = NativeImage.read(input)) {
            if (image.getWidth() != MapTile.SIZE || image.getHeight() != MapTile.SIZE || image.format() != NativeImage.Format.RGBA) {
                return null;
            }
            for (int z = 0; z < MapTile.SIZE; z++) {
                for (int x = 0; x < MapTile.SIZE; x++) {
                    colors[z * MapTile.SIZE + x] = FastColor.ABGR32.fromArgb32(image.getPixelRGBA(x, z)); // R и B — обратно
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Не удалось прочитать регион карты {}", file, e);
            return null;
        }
        short[] heights = MapTile.emptyHeights();
        Path heightsFile = regionFile(dir, key, ".heights");
        if (Files.isRegularFile(heightsFile)) {
            try (DataInputStream input = open(heightsFile)) {
                if (input.readInt() == 1) {
                    heights = readShorts(input, PIXELS);
                }
            } catch (IOException e) {
                LOGGER.warn("Не удалось прочитать высоты карты {}", heightsFile, e);
            }
        }
        short[] biomes = new short[CELLS];
        Path biomesFile = regionFile(dir, key, ".biomes");
        if (Files.isRegularFile(biomesFile)) {
            try (DataInputStream input = open(biomesFile)) {
                if (input.readInt() == 1) {
                    short[] ids = readBiomePalette(input);
                    biomes = readShorts(input, CELLS);
                    toGlobal(biomes, ids);
                }
            } catch (IOException e) {
                LOGGER.warn("Не удалось прочитать биомы карты {}", biomesFile, e);
            }
        }
        return new MapRegion.Data(colors, new short[PIXELS], heights, biomes);
    }

    static void writeRegion(Path dir, long key, MapRegion.Data data) {
        Path file = regionFile(dir, key, REGION_EXTENSION);
        try {
            write(file, output -> {
                output.writeInt(REGION_VERSION);
                // Палитра блоков этого региона: имя состояния и база, под которую посчитаны цвета
                short[] blocks = data.blocks();
                Map<Integer, Integer> local = new HashMap<>();
                List<Integer> globals = new ArrayList<>();
                short[] values = new short[PIXELS];
                for (int i = 0; i < PIXELS; i++) {
                    int id = blocks[i];
                    if (id <= 0 || MapPalettes.blockState(id) == null) {
                        continue;
                    }
                    Integer index = local.get(id);
                    if (index == null) {
                        globals.add(id);
                        index = globals.size();
                        local.put(id, index);
                    }
                    values[i] = (short) (int) index;
                }
                output.writeInt(globals.size());
                for (int id : globals) {
                    output.writeUTF(BlockStateParser.serialize(MapPalettes.blockState(id)));
                    output.writeInt(MapPalettes.base(id));
                }
                short[] biomes = writeBiomePalette(output, data.biomes());
                writeInts(output, data.colors());
                writeShorts(output, values);
                writeShorts(output, data.heights());
                writeShorts(output, biomes);
            });
            // Регион теперь в новом формате — старые файлы не нужны
            for (String extension : LEGACY_EXTENSIONS) {
                Files.deleteIfExists(regionFile(dir, key, extension));
            }
        } catch (IOException e) {
            LOGGER.warn("Не удалось сохранить регион карты {}", file, e);
        }
    }

    /** Все регионы на диске, в обоих форматах. */
    static Set<Long> listRegions(Path dir) {
        Set<Long> keys = new HashSet<>();
        if (!Files.isDirectory(dir)) {
            return keys;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path file : files) {
                Matcher matcher = REGION_FILE.matcher(file.getFileName().toString());
                if (matcher.matches()) {
                    keys.add(key(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
                }
            }
        } catch (IOException | NumberFormatException e) {
            LOGGER.warn("Не удалось просмотреть папку карты {}", dir, e);
        }
        return keys;
    }

    // ===== Уровни отдаления =====

    static MapLod.Data readLod(Path dir, int level, long key) {
        Path file = lodFile(dir, level, key);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (DataInputStream input = open(file)) {
            if (input.readInt() != LOD_VERSION) {
                return null;
            }
            short[] biomeIds = readBiomePalette(input);
            int[] colors = readInts(input, PIXELS);
            short[] heights = readShorts(input, PIXELS);
            byte[] slopes = new byte[PIXELS];
            input.readFully(slopes);
            short[] biomes = readShorts(input, CELLS);
            toGlobal(biomes, biomeIds);
            return new MapLod.Data(colors, heights, slopes, biomes);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Не удалось прочитать уровень карты {}", file, e);
            return null;
        }
    }

    static void writeLod(Path dir, int level, long key, MapLod.Data data) {
        Path file = lodFile(dir, level, key);
        try {
            write(file, output -> {
                output.writeInt(LOD_VERSION);
                short[] biomes = writeBiomePalette(output, data.biomes());
                writeInts(output, data.colors());
                writeShorts(output, data.heights());
                output.write(data.slopes());
                writeShorts(output, biomes);
            });
        } catch (IOException e) {
            LOGGER.warn("Не удалось сохранить уровень карты {}", file, e);
        }
    }

    /** Версия построенных уровней; 0 — не построены. */
    static int pyramidVersion(Path dir) {
        Path file = dir.resolve(LOD_FOLDER).resolve(PYRAMID_FILE);
        try {
            return Files.isRegularFile(file) ? Integer.parseInt(Files.readString(file).strip()) : 0;
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    static void setPyramidVersion(Path dir, int version) {
        Path file = dir.resolve(LOD_FOLDER).resolve(PYRAMID_FILE);
        try {
            Files.createDirectories(file.getParent());
            if (version == 0) {
                Files.deleteIfExists(file);
            } else {
                Files.writeString(file, Integer.toString(version));
            }
        } catch (IOException e) {
            LOGGER.warn("Не удалось записать {}", file, e);
        }
    }

    /** Стереть все уровни — перед постройкой заново. */
    static void clearLods(Path dir) {
        Path folder = dir.resolve(LOD_FOLDER);
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.lod")) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            LOGGER.warn("Не удалось очистить уровни карты {}", folder, e);
        }
    }

    // ===== Палитры =====

    private static BlockState parse(String state) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState();
        } catch (CommandSyntaxException e) {
            return null; // блок пропал (мод удалили) — пиксель остаётся со своим цветом, но без блока
        }
    }

    // Палитра биомов файла: число, имена. Возвращает локальный номер → общий
    private static short[] readBiomePalette(DataInputStream input) throws IOException {
        short[] ids = new short[input.readInt() + 1];
        for (int i = 1; i < ids.length; i++) {
            ResourceLocation name = ResourceLocation.tryParse(input.readUTF());
            ids[i] = name == null ? 0 : MapPalettes.biome(name);
        }
        return ids;
    }

    private static void toGlobal(short[] values, short[] ids) {
        for (int i = 0; i < values.length; i++) {
            int local = values[i];
            values[i] = local > 0 && local < ids.length ? ids[local] : 0;
        }
    }

    // Пишет палитру биомов и возвращает клетки в её локальных номерах
    private static short[] writeBiomePalette(DataOutputStream output, short[] biomes) throws IOException {
        Map<Integer, Integer> local = new HashMap<>();
        List<ResourceLocation> names = new ArrayList<>();
        short[] values = new short[biomes.length];
        for (int i = 0; i < biomes.length; i++) {
            int id = biomes[i];
            ResourceLocation name = id > 0 ? MapPalettes.biomeName(id) : null;
            if (name == null) {
                continue;
            }
            Integer index = local.get(id);
            if (index == null) {
                names.add(name);
                index = names.size();
                local.put(id, index);
            }
            values[i] = (short) (int) index;
        }
        output.writeInt(names.size());
        for (ResourceLocation name : names) {
            output.writeUTF(name.toString());
        }
        return values;
    }

    // ===== Потоки и массивы целиком =====

    private interface Writer {
        void write(DataOutputStream output) throws IOException;
    }

    private static DataInputStream open(Path file) throws IOException {
        return new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file), 1 << 16), 1 << 16));
    }

    private static void write(Path file, Writer writer) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(temp), 1 << 16), 1 << 16))) {
            writer.write(output);
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static int[] readInts(DataInputStream input, int count) throws IOException {
        byte[] bytes = new byte[count * 4];
        input.readFully(bytes);
        int[] values = new int[count];
        ByteBuffer.wrap(bytes).asIntBuffer().get(values);
        return values;
    }

    private static short[] readShorts(DataInputStream input, int count) throws IOException {
        byte[] bytes = new byte[count * 2];
        input.readFully(bytes);
        short[] values = new short[count];
        ByteBuffer.wrap(bytes).asShortBuffer().get(values);
        return values;
    }

    private static void writeInts(DataOutputStream output, int[] values) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 4);
        buffer.asIntBuffer().put(values);
        output.write(buffer.array());
    }

    private static void writeShorts(DataOutputStream output, short[] values) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(values.length * 2);
        buffer.asShortBuffer().put(values);
        output.write(buffer.array());
    }
}
