package com.nrdhvnstd.halgventmod.client.map;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

/**
 * Рисование разведанного мира: тайлы нужного уровня отдаления (вблизи — регионы, дальше — MapPyramid),
 * пока нужный читается — кусок более грубого, и пометки карандаша поверх. Общее для всех окон карты:
 * технической, карты локации и миниатюры в окне локации. Неразведанное не рисуется (прозрачно).
 */
final class TileRenderer {
    private TileRenderer() {}

    /** Пикселей монитора на блок. */
    static double realZoom(MapView view) {
        return view.zoom() * Minecraft.getInstance().getWindow().getGuiScale();
    }

    /**
     * Уровень отдаления для масштаба: такой, чтобы пиксель тайла был не крупнее пикселя экрана.
     * Вблизи — регионы (уровень 0), дальше — тайлы MapPyramid, где пиксель — среднее по 2^L блокам.
     */
    static int levelForZoom(MapView view) {
        double real = realZoom(view);
        if (real >= 1) {
            return 0;
        }
        int level = (int) Math.round(Math.log(1 / real) / Math.log(2));
        return Mth.clamp(level, 0, MapPyramid.MAX_LEVEL);
    }

    /** Тайлы в прямоугольнике экрана (left, top) — (right, bottom). Каждый — со своей точкой отсчёта (точность вдали от нуля). */
    static void renderTiles(GuiGraphics graphics, MapView view, int left, int top, int right, int bottom) {
        int level = levelForZoom(view);
        WorldMap.setShownLevel(level);
        int shift = MapTile.SHIFT + level;
        int fromX = Mth.floor(view.worldX(left)) >> shift;
        int toX = Mth.floor(view.worldX(right)) >> shift;
        int fromZ = Mth.floor(view.worldZ(top)) >> shift;
        int toZ = Mth.floor(view.worldZ(bottom)) >> shift;
        for (int tx = fromX; tx <= toX; tx++) {
            for (int tz = fromZ; tz <= toZ; tz++) {
                MapTile tile = level == 0 ? WorldMap.region(tx, tz) : WorldMap.lod(level, tx, tz);
                if (tile != null) {
                    ResourceLocation texture = tile.texture(false); // заодно заказывает сборку картинки
                    if (tile.painted()) {
                        blitTile(graphics, view, texture, level, tx, tz, 0, 0, MapTile.SIZE);
                        continue;
                    }
                }
                // Пока нужный тайл читается или собирается — кусок более грубого, если он уже в памяти
                for (int up = 1; up <= 2 && level + up <= MapPyramid.MAX_LEVEL; up++) {
                    MapTile parent = WorldMap.loadedTile(level + up, tx >> up, tz >> up);
                    if (parent != null && parent.painted()) {
                        int part = MapTile.SIZE >> up;
                        int mask = (1 << up) - 1;
                        blitTile(graphics, view, parent.texture(false), level, tx, tz, (tx & mask) * part, (tz & mask) * part, part);
                        break;
                    }
                }
            }
        }
    }

    /** Пометки карандаша — только в регионах, где они есть (их немного). */
    static void renderDrawings(GuiGraphics graphics, MapView view, int left, int top, int right, int bottom) {
        int fromX = Mth.floor(view.worldX(left)) >> MapTile.SHIFT;
        int toX = Mth.floor(view.worldX(right)) >> MapTile.SHIFT;
        int fromZ = Mth.floor(view.worldZ(top)) >> MapTile.SHIFT;
        int toZ = Mth.floor(view.worldZ(bottom)) >> MapTile.SHIFT;
        RenderSystem.enableBlend();
        for (long key : MapDrawing.regionsWithDrawings()) {
            int rx = ChunkPos.getX(key);
            int rz = ChunkPos.getZ(key);
            if (rx < fromX || rx > toX || rz < fromZ || rz > toZ) {
                continue;
            }
            ResourceLocation drawing = MapDrawing.texture(rx, rz);
            if (drawing != null) {
                blitTile(graphics, view, drawing, 0, rx, rz, 0, 0, MapTile.SIZE);
            }
        }
        RenderSystem.disableBlend();
    }

    // Текстура 512 × 512 (или её квадрат part пикселей от u, v) — на место тайла (level, tx, tz)
    private static void blitTile(GuiGraphics graphics, MapView view, ResourceLocation texture, int level, int tx, int tz,
                                 int u, int v, int part) {
        double blocks = (double) MapTile.SIZE * (1 << level); // блоков в стороне тайла
        float scale = (float) (view.zoom() * blocks / part);
        graphics.pose().pushPose();
        graphics.pose().translate(view.screenX(tx * blocks), view.screenY(tz * blocks), 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(texture, 0, 0, part, part, u, v, part, part, MapTile.SIZE, MapTile.SIZE);
        graphics.pose().popPose();
    }
}
