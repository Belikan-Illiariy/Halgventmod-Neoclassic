package com.nrdhvnstd.halgventmod.map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Снимок карты локации (LocationMaps): столбцы прямоугольника вокруг её границы — верхний блок, высота, глубина воды
 * и дно, биомы клетками 4 × 4. Столбцы вне границы и в несгенерированных чанках — «неизвестно» (номер 0).
 * Один и тот же файл лежит на сервере (<мир>/data/halgventmod/location_maps/<id>.bin) и в кэше клиента;
 * по сети он идёт как есть, кусками.
 *
 * Внутри — gzip:
 * <pre>
 * int    MAGIC, FORMAT
 * int    pointId, mapVersion
 * int    minX, minZ, width, height          // в блоках; minX, minZ, width, height кратны 4
 * int    blockCount; blockCount × UTF        // палитра: BlockStateParser.serialize; номер 0 — «неизвестно»
 * int    biomeCount; biomeCount × UTF        // палитра биомов; номер 0 — «неизвестно»
 * width×height × { varint block, short height, byte depth, [varint floor, если depth > 0] }   // по z, потом по x
 * (width/4)×(height/4) × varint biome
 * </pre>
 */
public final class LocationMapData {
    public static final int MAGIC = 'H' << 24 | 'G' << 16 | 'L' << 8 | 'M';
    public static final int FORMAT = 1;
    public static final int CELL = 4;
    /** Больше палитры не бывает; номера — short. */
    private static final int MAX_PALETTE = Short.MAX_VALUE;

    public final int pointId;
    public final int mapVersion;
    public final int minX;
    public final int minZ;
    public final int width;
    public final int height;
    /** Палитра блоков: [0] — null («неизвестно»). На клиенте — разобранные состояния, на сервере — из мира. */
    public final List<BlockState> blockPalette;
    public final List<ResourceLocation> biomePalette;
    /** Номер блока в палитре по столбцам (z * width + x); 0 — неизвестно. */
    public final short[] blocks;
    public final short[] heights;
    /** Глубина воды, 0–255. */
    public final byte[] depths;
    /** Номер дна в палитре; 0 — дна нет. */
    public final short[] floors;
    /** Номер биома по клеткам 4 × 4 (cellZ * width/4 + cellX). */
    public final short[] biomes;

    public LocationMapData(int pointId, int mapVersion, int minX, int minZ, int width, int height) {
        this(pointId, mapVersion, minX, minZ, width, height, new ArrayList<>(), new ArrayList<>());
        blockPalette.add(null);
        biomePalette.add(null);
    }

    /** Те же данные с другой версией (массивы общие) — снятое получает номер версии в самом конце. */
    public LocationMapData(LocationMapData source, int mapVersion) {
        this.pointId = source.pointId;
        this.mapVersion = mapVersion;
        this.minX = source.minX;
        this.minZ = source.minZ;
        this.width = source.width;
        this.height = source.height;
        this.blockPalette = source.blockPalette;
        this.biomePalette = source.biomePalette;
        this.blocks = source.blocks;
        this.heights = source.heights;
        this.depths = source.depths;
        this.floors = source.floors;
        this.biomes = source.biomes;
    }

    private LocationMapData(int pointId, int mapVersion, int minX, int minZ, int width, int height,
                            List<BlockState> blockPalette, List<ResourceLocation> biomePalette) {
        this.pointId = pointId;
        this.mapVersion = mapVersion;
        this.minX = minX;
        this.minZ = minZ;
        this.width = width;
        this.height = height;
        this.blockPalette = blockPalette;
        this.biomePalette = biomePalette;
        this.blocks = new short[width * height];
        this.heights = new short[width * height];
        this.depths = new byte[width * height];
        this.floors = new short[width * height];
        this.biomes = new short[(width / CELL) * (height / CELL)];
    }

    public int cellsX() {
        return width / CELL;
    }

    public int depth(int index) {
        return depths[index] & 0xFF;
    }

    @Nullable
    public BlockState block(int index) {
        return blockPalette.get(blocks[index]);
    }

    @Nullable
    public BlockState floor(int index) {
        return blockPalette.get(floors[index]);
    }

    /** Биом клетки, в которой лежит столбец (x; z) мира; null — неизвестен или за прямоугольником. */
    @Nullable
    public ResourceLocation biomeAt(int x, int z) {
        int cellX = Math.floorDiv(x - minX, CELL);
        int cellZ = Math.floorDiv(z - minZ, CELL);
        if (cellX < 0 || cellZ < 0 || cellX >= cellsX() || cellZ >= height / CELL) {
            return null;
        }
        return biomePalette.get(biomes[cellZ * cellsX() + cellX]);
    }

    // ===== Запись =====

    /** Записать файл через временный — недописанный файл никогда не подменит готовый. */
    public void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(temp), 1 << 16), 1 << 16))) {
            output.writeInt(MAGIC);
            output.writeInt(FORMAT);
            output.writeInt(pointId);
            output.writeInt(mapVersion);
            output.writeInt(minX);
            output.writeInt(minZ);
            output.writeInt(width);
            output.writeInt(height);
            output.writeInt(blockPalette.size() - 1);
            for (int i = 1; i < blockPalette.size(); i++) {
                output.writeUTF(BlockStateParser.serialize(blockPalette.get(i)));
            }
            output.writeInt(biomePalette.size() - 1);
            for (int i = 1; i < biomePalette.size(); i++) {
                output.writeUTF(biomePalette.get(i).toString());
            }
            for (int i = 0; i < blocks.length; i++) {
                writeVarInt(output, blocks[i]);
                output.writeShort(heights[i]);
                output.writeByte(depths[i]);
                if (depths[i] != 0) {
                    writeVarInt(output, floors[i]);
                }
            }
            for (short biome : biomes) {
                writeVarInt(output, biome);
            }
        }
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    // ===== Чтение =====

    public static LocationMapData read(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return read(input);
        }
    }

    public static LocationMapData read(byte[] bytes) throws IOException {
        return read(new ByteArrayInputStream(bytes));
    }

    /** Только заголовок: {pointId, mapVersion}; null — файл не снимок. */
    @Nullable
    public static int[] readHeader(Path file) {
        try (DataInputStream input = open(Files.newInputStream(file))) {
            if (input.readInt() != MAGIC || input.readInt() != FORMAT) {
                return null;
            }
            return new int[] {input.readInt(), input.readInt()};
        } catch (IOException e) {
            return null;
        }
    }

    private static LocationMapData read(InputStream raw) throws IOException {
        DataInputStream input = open(raw);
        if (input.readInt() != MAGIC || input.readInt() != FORMAT) {
            throw new IOException("Не снимок карты локации");
        }
        int pointId = input.readInt();
        int mapVersion = input.readInt();
        int minX = input.readInt();
        int minZ = input.readInt();
        int width = input.readInt();
        int height = input.readInt();
        if (width <= 0 || height <= 0 || width % CELL != 0 || height % CELL != 0 || (long) width * height > 1 << 26) {
            throw new IOException("Неверный размер снимка " + width + " × " + height);
        }
        List<BlockState> blockPalette = new ArrayList<>();
        blockPalette.add(null);
        int blockCount = input.readInt();
        if (blockCount < 0 || blockCount > MAX_PALETTE) {
            throw new IOException("Неверная палитра блоков");
        }
        for (int i = 0; i < blockCount; i++) {
            blockPalette.add(parse(input.readUTF())); // блок пропал (мод удалили) — «неизвестно»
        }
        List<ResourceLocation> biomePalette = new ArrayList<>();
        biomePalette.add(null);
        int biomeCount = input.readInt();
        if (biomeCount < 0 || biomeCount > MAX_PALETTE) {
            throw new IOException("Неверная палитра биомов");
        }
        for (int i = 0; i < biomeCount; i++) {
            biomePalette.add(ResourceLocation.tryParse(input.readUTF()));
        }
        LocationMapData data = new LocationMapData(pointId, mapVersion, minX, minZ, width, height,
                blockPalette, biomePalette);
        for (int i = 0; i < data.blocks.length; i++) {
            data.blocks[i] = paletteIndex(readVarInt(input), blockPalette.size());
            data.heights[i] = input.readShort();
            data.depths[i] = input.readByte();
            if (data.depths[i] != 0) {
                data.floors[i] = paletteIndex(readVarInt(input), blockPalette.size());
            }
        }
        for (int i = 0; i < data.biomes.length; i++) {
            data.biomes[i] = paletteIndex(readVarInt(input), biomePalette.size());
        }
        return data;
    }

    private static short paletteIndex(int value, int size) throws IOException {
        if (value < 0 || value >= size) {
            throw new IOException("Номер вне палитры: " + value);
        }
        return (short) value;
    }

    @Nullable
    private static BlockState parse(String state) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    private static DataInputStream open(InputStream raw) throws IOException {
        return new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw, 1 << 16), 1 << 16));
    }

    // ===== Палитры при снятии =====

    /** Номер блока в палитре; новый — дописывается. */
    public short blockIndex(BlockState state, Map<BlockState, Short> index) {
        Short id = index.get(state);
        if (id == null) {
            if (blockPalette.size() > MAX_PALETTE) {
                return 0;
            }
            id = (short) blockPalette.size();
            blockPalette.add(state);
            index.put(state, id);
        }
        return id;
    }

    public short biomeIndex(ResourceLocation biome, Map<ResourceLocation, Short> index) {
        Short id = index.get(biome);
        if (id == null) {
            if (biomePalette.size() > MAX_PALETTE) {
                return 0;
            }
            id = (short) biomePalette.size();
            biomePalette.add(biome);
            index.put(biome, id);
        }
        return id;
    }

    // ===== varint =====

    private static void writeVarInt(DataOutputStream output, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            output.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output.writeByte(value);
    }

    private static int readVarInt(DataInputStream input) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = input.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new IOException("Слишком длинный varint");
    }
}
