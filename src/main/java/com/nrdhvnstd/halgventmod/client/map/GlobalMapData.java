package com.nrdhvnstd.halgventmod.client.map;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.point.MapPoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import org.slf4j.Logger;

/**
 * Рисунок глобальной карты Хальгвента и его регионы — из ресурсов textures/gui/global_map/:
 * map_<столбец>_<строка>.png — рисунок кусками по TILE пикселей (большие текстуры тянут не все видеокарты);
 * regions.png — маска: каждый регион залит своим цветом без сглаживания, море и пустота — чёрные; размер — как
 * у рисунка или вдвое меньше; regions.json — {"size": [w, h], "regions": [{"id": "vessen", "color": "#RRGGBB"}, ...]}.
 * Названия и описания — переводы region.halgventmod.<id> и region.halgventmod.<id>.description.
 *
 * При загрузке ресурсов один раз: маска — в память (номер региона на пиксель), для каждого региона — прямоугольник,
 * вырезанный из рисунка кусок с прозрачностью по маске и контур (пиксели маски, у которых сосед — другой регион).
 * Код от размера рисунка не зависит.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class GlobalMapData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER = "textures/gui/global_map/";
    public static final int TILE = 2048;
    private static final int MAX_CUTOUT = 4096; // больше — вырезанный регион уменьшается

    /** Регион глобальной карты. Прямоугольник — в пикселях рисунка; текстуры — в пикселях маски (step). */
    public record Region(String id, int index, int color, int minX, int minY, int maxX, int maxY,
                         ResourceLocation cutout, ResourceLocation outline) {
        public Component title() {
            return Component.translatable("region.halgventmod." + id);
        }

        public Component description() {
            return Component.translatable("region.halgventmod." + id + ".description");
        }

        public float centerX() {
            return (minX + maxX + 1) / 2.0F;
        }

        public float centerY() {
            return (minY + maxY + 1) / 2.0F;
        }
    }

    private static int width;
    private static int height;
    private static int maskWidth;
    private static int maskHeight;
    private static byte[] mask = new byte[0]; // номер региона + 1; 0 — пусто
    private static List<Region> regions = List.of();
    private static final List<ResourceLocation> TILES = new ArrayList<>();
    private static int columns;
    private static int rows;
    private static final List<ResourceLocation> OWNED = new ArrayList<>();

    private GlobalMapData() {}

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) GlobalMapData::reload);
    }

    public static boolean loaded() {
        return width > 0 && height > 0;
    }

    public static int width() {
        return width;
    }

    public static int height() {
        return height;
    }

    public static List<Region> regions() {
        return regions;
    }

    public static int columns() {
        return columns;
    }

    public static int rows() {
        return rows;
    }

    /** Кусок рисунка (столбец, строка). */
    public static ResourceLocation tile(int column, int row) {
        return TILES.get(row * columns + column);
    }

    /** Регион под пикселем рисунка; null — море, пустота или за краем. */
    @Nullable
    public static Region regionAt(double x, double y) {
        if (!loaded() || x < 0 || y < 0 || x >= width || y >= height) {
            return null;
        }
        int mx = (int) (x * maskWidth / width);
        int my = (int) (y * maskHeight / height);
        int value = mask[my * maskWidth + mx] & 0xFF;
        return value == 0 || value > regions.size() ? null : regions.get(value - 1);
    }

    /** Регион точки по её месту на рисунке; null — не поставлена или не в регионе. */
    @Nullable
    public static Region regionOf(MapPoint point) {
        return point.onGlobalMap() ? regionAt(point.globalX + 0.5, point.globalY + 0.5) : null;
    }

    // ===== Загрузка =====

    private static void reload(ResourceManager manager) {
        clear();
        try {
            load(manager);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Не удалось загрузить глобальную карту", e);
            clear();
        }
    }

    private static void clear() {
        for (ResourceLocation texture : OWNED) {
            Minecraft.getInstance().getTextureManager().release(texture);
        }
        OWNED.clear();
        TILES.clear();
        regions = List.of();
        mask = new byte[0];
        width = 0;
        height = 0;
    }

    private static void load(ResourceManager manager) throws IOException {
        Optional<Resource> json = manager.getResource(location("regions.json"));
        if (json.isEmpty()) {
            LOGGER.warn("Нет {}regions.json — глобальной карты не будет", FOLDER);
            return;
        }
        JsonObject root;
        try (Reader reader = json.get().openAsReader()) {
            root = JsonParser.parseReader(reader).getAsJsonObject();
        }
        JsonArray size = root.getAsJsonArray("size");
        int w = size.get(0).getAsInt();
        int h = size.get(1).getAsInt();
        List<String> ids = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("regions")) {
            JsonObject region = element.getAsJsonObject();
            ids.add(region.get("id").getAsString());
            colors.add(Integer.parseInt(region.get("color").getAsString().replace("#", ""), 16));
        }
        if (ids.size() > 254) {
            throw new IOException("Слишком много регионов");
        }

        // Маска: цвет пикселя → номер региона
        NativeImage maskImage = read(manager, "regions.png");
        maskWidth = maskImage.getWidth();
        maskHeight = maskImage.getHeight();
        mask = new byte[maskWidth * maskHeight];
        int[] minX = new int[ids.size()];
        int[] minY = new int[ids.size()];
        int[] maxX = new int[ids.size()];
        int[] maxY = new int[ids.size()];
        Arrays.fill(minX, Integer.MAX_VALUE);
        Arrays.fill(minY, Integer.MAX_VALUE);
        Arrays.fill(maxX, -1);
        Arrays.fill(maxY, -1);
        for (int y = 0; y < maskHeight; y++) {
            for (int x = 0; x < maskWidth; x++) {
                int abgr = maskImage.getPixelRGBA(x, y);
                int rgb = (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16) & 0xFF;
                int index = colors.indexOf(rgb);
                if (index >= 0 && (abgr >>> 24) > 127) {
                    mask[y * maskWidth + x] = (byte) (index + 1);
                    minX[index] = Math.min(minX[index], x);
                    minY[index] = Math.min(minY[index], y);
                    maxX[index] = Math.max(maxX[index], x);
                    maxY[index] = Math.max(maxY[index], y);
                }
            }
        }
        maskImage.close();

        // Куски рисунка: обычные текстуры, грузит менеджер текстур
        columns = (w + TILE - 1) / TILE;
        rows = (h + TILE - 1) / TILE;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                TILES.add(location("map_" + column + "_" + row + ".png"));
            }
        }

        // Вырезанные регионы и контуры — по маске, цвет — из кусков рисунка
        NativeImage[] cutouts = new NativeImage[ids.size()];
        NativeImage[] outlines = new NativeImage[ids.size()];
        int[] steps = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            if (maxX[i] < 0) {
                continue; // региона нет на маске
            }
            int boxWidth = maxX[i] - minX[i] + 1;
            int boxHeight = maxY[i] - minY[i] + 1;
            steps[i] = Math.max(1, (Math.max(boxWidth, boxHeight) + MAX_CUTOUT - 1) / MAX_CUTOUT);
            cutouts[i] = new NativeImage((boxWidth + steps[i] - 1) / steps[i], (boxHeight + steps[i] - 1) / steps[i], true);
            outlines[i] = new NativeImage(cutouts[i].getWidth(), cutouts[i].getHeight(), true);
        }
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                NativeImage tile = read(manager, "map_" + column + "_" + row + ".png");
                cutFrom(tile, column * TILE, row * TILE, w, h, cutouts, minX, minY, steps);
                tile.close();
            }
        }
        List<Region> loadedRegions = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            if (cutouts[i] == null) {
                continue;
            }
            drawOutline(outlines[i], i + 1, minX[i], minY[i], steps[i]);
            ResourceLocation cutout = register("global_map/region_" + ids.get(i), cutouts[i]);
            ResourceLocation outline = register("global_map/outline_" + ids.get(i), outlines[i]);
            // Прямоугольник — в пикселях рисунка
            float scaleX = (float) w / maskWidth;
            float scaleY = (float) h / maskHeight;
            loadedRegions.add(new Region(ids.get(i), i + 1, colors.get(i),
                    (int) (minX[i] * scaleX), (int) (minY[i] * scaleY),
                    (int) ((maxX[i] + 1) * scaleX) - 1, (int) ((maxY[i] + 1) * scaleY) - 1, cutout, outline));
        }
        // Номера в маске — по порядку в regions.json; регион без пикселей пропущен, но номера не сдвигаются
        List<Region> byIndex = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Region found = null;
            for (Region region : loadedRegions) {
                if (region.index() == i + 1) {
                    found = region;
                }
            }
            byIndex.add(found);
        }
        regions = byIndex.stream().filter(Objects::nonNull).toList();
        remapMask(byIndex);
        width = w;
        height = h;
        LOGGER.info("Глобальная карта {} × {}, регионов: {}", w, h, regions.size());
    }

    // Номера в маске — в номера списка regions (регион без пикселей выпал)
    private static void remapMask(List<Region> byIndex) {
        byte[] map = new byte[byIndex.size() + 1];
        int next = 1;
        for (int i = 0; i < byIndex.size(); i++) {
            map[i + 1] = byIndex.get(i) == null ? 0 : (byte) next++;
        }
        for (int i = 0; i < mask.length; i++) {
            int value = mask[i] & 0xFF;
            mask[i] = value < map.length ? map[value] : 0;
        }
        List<Region> renumbered = new ArrayList<>();
        int index = 1;
        for (Region region : regions) {
            renumbered.add(new Region(region.id(), index++, region.color(), region.minX(), region.minY(), region.maxX(),
                    region.maxY(), region.cutout(), region.outline()));
        }
        regions = renumbered;
    }

    // Пиксели куска рисунка — в вырезанные регионы, по маске
    private static void cutFrom(NativeImage tile, int originX, int originY, int w, int h, NativeImage[] cutouts,
                                int[] minX, int[] minY, int[] steps) {
        float maskPerPixelX = (float) maskWidth / w;
        float maskPerPixelY = (float) maskHeight / h;
        for (int i = 0; i < cutouts.length; i++) {
            NativeImage cutout = cutouts[i];
            if (cutout == null) {
                continue;
            }
            int step = steps[i];
            for (int cy = 0; cy < cutout.getHeight(); cy++) {
                int maskY = minY[i] + cy * step;
                int pixelY = (int) ((maskY + 0.5F) / maskPerPixelY);
                if (pixelY < originY || pixelY >= originY + tile.getHeight() || maskY >= maskHeight) {
                    continue;
                }
                for (int cx = 0; cx < cutout.getWidth(); cx++) {
                    int maskX = minX[i] + cx * step;
                    int pixelX = (int) ((maskX + 0.5F) / maskPerPixelX);
                    if (pixelX < originX || pixelX >= originX + tile.getWidth() || maskX >= maskWidth
                            || (mask[maskY * maskWidth + maskX] & 0xFF) != i + 1) {
                        continue;
                    }
                    cutout.setPixelRGBA(cx, cy, tile.getPixelRGBA(pixelX - originX, pixelY - originY) | 0xFF000000);
                }
            }
        }
    }

    // Контур: пиксель региона, у которого сосед — не этот регион
    private static void drawOutline(NativeImage outline, int value, int minX, int minY, int step) {
        for (int cy = 0; cy < outline.getHeight(); cy++) {
            for (int cx = 0; cx < outline.getWidth(); cx++) {
                int x = minX + cx * step;
                int y = minY + cy * step;
                if (maskAt(x, y) != value) {
                    continue;
                }
                if (maskAt(x - step, y) != value || maskAt(x + step, y) != value
                        || maskAt(x, y - step) != value || maskAt(x, y + step) != value) {
                    outline.setPixelRGBA(cx, cy, 0xFFFFFFFF);
                }
            }
        }
    }

    private static int maskAt(int x, int y) {
        if (x < 0 || y < 0 || x >= maskWidth || y >= maskHeight) {
            return 0;
        }
        return mask[y * maskWidth + x] & 0xFF;
    }

    private static ResourceLocation register(String path, NativeImage image) {
        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, path);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        OWNED.add(id);
        return id;
    }

    private static NativeImage read(ResourceManager manager, String name) throws IOException {
        Resource resource = manager.getResource(location(name))
                .orElseThrow(() -> new IOException("Нет " + FOLDER + name));
        try (InputStream input = resource.open()) {
            return NativeImage.read(input);
        }
    }

    private static ResourceLocation location(String name) {
        return ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, FOLDER + name);
    }
}
