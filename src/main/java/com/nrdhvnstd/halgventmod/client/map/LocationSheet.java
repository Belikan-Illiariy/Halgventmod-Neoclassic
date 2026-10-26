package com.nrdhvnstd.halgventmod.client.map;

import java.util.Arrays;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.point.MapPoint;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Лист карты одной локации: прямоугольник вокруг её границы плюс поля SHEET_MARGIN блоков. Внутри границы —
 * настоящая карта (TileRenderer), под ней туман: он виден там, где столбцы «неизвестно»; снаружи границы — пустая
 * бумага поверх карты, по границе — неровный край. Неизведанная локация — только туман и «Локация неизведана».
 * Бумага и туман заливаются построчно по экрану (как граница в PointShapes), одной пачкой на текстуру.
 * Общий для окна карты локации и миниатюры в окне локации на глобальной карте.
 */
final class LocationSheet {
    static final int SHEET_MARGIN = 24; // ⚠️ блоков бумаги вокруг границы

    private static final String FOLDER = "textures/gui/location_map/";
    static final ResourceLocation TABLE = texture("table.png");
    private static final ResourceLocation PAPER = texture("paper.png");
    private static final ResourceLocation FOG = texture("fog.png");
    private static final ResourceLocation FRAME = texture("sheet_frame.png");
    static final int TABLE_SIZE = 256;
    private static final int PAPER_SIZE = 256;
    private static final int FOG_SIZE = 256;
    private static final int FRAME_SIZE = 64;
    private static final int FRAME_CORNER = 16;  // углы рамки в текстуре
    private static final int FRAME_OUT = 6;      // рамка выступает за лист на экране
    private static final int SHADOW = 0x60000000;
    private static final int SHADOW_OFFSET = 4;

    private static final int COLOR_EDGE = 0xC05A4632;   // неровный край по границе
    private static final float EDGE_STEP = 4;          // ⚠️ пикселей экрана между изломами края
    private static final float EDGE_JITTER = 1.5F;     // ⚠️ насколько край гуляет
    static final int COLOR_TITLE = 0xFFFFE8A0;

    private LocationSheet() {}

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, FOLDER + name);
    }

    /** Лист в блоках мира: {minX, minZ, maxX, maxZ}, max — не включительно. */
    static int[] rect(MapPoint point) {
        int[] bounds = point.bounds();
        return new int[] {bounds[0] - SHEET_MARGIN, bounds[1] - SHEET_MARGIN,
                bounds[2] + 1 + SHEET_MARGIN, bounds[3] + 1 + SHEET_MARGIN};
    }

    /** Лист на экране: {left, top, right, bottom}. */
    static float[] screenRect(MapView view, MapPoint point) {
        int[] rect = rect(point);
        return new float[] {view.screenX(rect[0]), view.screenY(rect[1]), view.screenX(rect[2]), view.screenY(rect[3])};
    }

    /** Тень и рамка листа — под ним; рисовать до render. */
    static void renderFrame(GuiGraphics graphics, MapView view, MapPoint point) {
        float[] sheet = screenRect(view, point);
        int left = Mth.floor(sheet[0]);
        int top = Mth.floor(sheet[1]);
        int right = Mth.ceil(sheet[2]);
        int bottom = Mth.ceil(sheet[3]);
        graphics.fill(left + SHADOW_OFFSET, top + SHADOW_OFFSET, right + SHADOW_OFFSET + FRAME_OUT,
                bottom + SHADOW_OFFSET + FRAME_OUT, SHADOW);
        nineSlice(graphics, left - FRAME_OUT, top - FRAME_OUT, right + FRAME_OUT, bottom + FRAME_OUT);
    }

    /**
     * Лист в пределах clip (left, top, right, bottom экрана): бумага, туман внутри границы, карта (если изведана),
     * бумага снаружи границы поверх карты, край. Пометки, метки и игроков окно рисует потом само.
     */
    static void render(GuiGraphics graphics, MapView view, MapPoint point, boolean explored,
                       int clipLeft, int clipTop, int clipRight, int clipBottom) {
        float[] sheet = screenRect(view, point);
        int left = Math.max(clipLeft, Mth.floor(sheet[0]));
        int top = Math.max(clipTop, Mth.floor(sheet[1]));
        int right = Math.min(clipRight, Mth.ceil(sheet[2]));
        int bottom = Math.min(clipBottom, Mth.ceil(sheet[3]));
        if (right <= left || bottom <= top) {
            return;
        }
        float[] outline = PointShapes.outline(view, point);
        graphics.flush();
        graphics.enableScissor(left, top, right, bottom);
        RenderSystem.enableBlend();
        Matrix4f matrix = graphics.pose().last().pose();

        // Бумага под всем листом, туман — внутри границы
        Strips paper = new Strips(matrix, PAPER, PAPER_SIZE, sheet[0], sheet[1]);
        paper.rect(left, top, right, bottom);
        paper.draw();
        Strips fog = new Strips(matrix, FOG, FOG_SIZE, sheet[0], sheet[1]);
        scan(outline, top, bottom, left, right, true, fog);
        fog.draw();

        if (explored) {
            TileRenderer.renderTiles(graphics, view, left, top, right, bottom);
            graphics.flush();
            // Всё, что за границей, закрывает пустая бумага
            Strips cover = new Strips(matrix, PAPER, PAPER_SIZE, sheet[0], sheet[1]);
            scan(outline, top, bottom, left, right, false, cover);
            cover.draw();
        }
        RenderSystem.enableBlend();
        roughEdge(graphics, outline, view);
        RenderSystem.disableBlend();
        graphics.disableScissor();
    }

    /** «Локация неизведана» — по середине границы. */
    static void renderUnexplored(GuiGraphics graphics, Font font, MapView view, MapPoint point) {
        int[] bounds = point.bounds();
        float x = view.screenX((bounds[0] + bounds[2] + 1) / 2.0);
        float y = view.screenY((bounds[1] + bounds[3] + 1) / 2.0);
        graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.location_map.unexplored"),
                Math.round(x), Math.round(y) - font.lineHeight / 2, COLOR_TITLE);
    }

    // ===== Построчная заливка =====

    // Строки экрана от top до bottom: inside — отрезки внутри фигуры (чёт-нечет), иначе — снаружи, в пределах [left, right)
    private static void scan(float[] shape, int top, int bottom, int left, int right, boolean inside, Strips strips) {
        int count = shape.length / 2;
        if (count < 3) {
            if (!inside) {
                strips.rect(left, top, right, bottom);
            }
            return;
        }
        float[] crossings = new float[count];
        for (int row = top; row < bottom; row++) {
            float y = row + 0.5F;
            int found = 0;
            for (int i = 0, j = count - 1; i < count; j = i++) {
                float yi = shape[2 * i + 1];
                float yj = shape[2 * j + 1];
                if ((yi > y) != (yj > y)) {
                    float xi = shape[2 * i];
                    float xj = shape[2 * j];
                    crossings[found++] = xi + (y - yi) / (yj - yi) * (xj - xi);
                }
            }
            Arrays.sort(crossings, 0, found);
            if (inside) {
                for (int k = 0; k + 1 < found; k += 2) {
                    strips.span(Math.max(left, Math.round(crossings[k])), Math.min(right, Math.round(crossings[k + 1])), row);
                }
            } else {
                int from = left;
                for (int k = 0; k + 1 < found; k += 2) {
                    strips.span(from, Math.min(right, Math.round(crossings[k])), row);
                    from = Math.max(from, Math.round(crossings[k + 1]));
                }
                strips.span(from, right, row);
            }
        }
    }

    /** Прямоугольники одной текстуры — одной пачкой; текстура привязана к углу листа и повторяется. */
    private static final class Strips {
        private final Matrix4f matrix;
        private final ResourceLocation texture;
        private final float size;
        private final float originX;
        private final float originY;
        private final BufferBuilder builder;
        private boolean empty = true;

        Strips(Matrix4f matrix, ResourceLocation texture, int size, float originX, float originY) {
            this.matrix = matrix;
            this.texture = texture;
            this.size = size;
            this.originX = originX;
            this.originY = originY;
            this.builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        }

        void span(int from, int to, int row) {
            if (to > from) {
                rect(from, row, to, row + 1);
            }
        }

        void rect(float x0, float y0, float x1, float y1) {
            float u0 = (x0 - originX) / size;
            float u1 = (x1 - originX) / size;
            float v0 = (y0 - originY) / size;
            float v1 = (y1 - originY) / size;
            builder.addVertex(matrix, x0, y0, 0).setUv(u0, v0);
            builder.addVertex(matrix, x0, y1, 0).setUv(u0, v1);
            builder.addVertex(matrix, x1, y1, 0).setUv(u1, v1);
            builder.addVertex(matrix, x1, y0, 0).setUv(u1, v0);
            empty = false;
        }

        void draw() {
            MeshData mesh = builder.build();
            if (empty || mesh == null) {
                if (mesh != null) {
                    mesh.close();
                }
                return;
            }
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            BufferUploader.drawWithShader(mesh);
        }
    }

    // ===== Край и рамка =====

    // По границе — ломаная с изломами через EDGE_STEP пикселей, отступ излома постоянен для места (не дрожит)
    private static void roughEdge(GuiGraphics graphics, float[] shape, MapView view) {
        int count = shape.length / 2;
        if (count < 2) {
            return;
        }
        float[] points = new float[0];
        int used = 0;
        for (int i = 0; i < count; i++) {
            float x1 = shape[2 * i];
            float y1 = shape[2 * i + 1];
            float dx = shape[2 * ((i + 1) % count)] - x1;
            float dy = shape[2 * ((i + 1) % count) + 1] - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            int steps = Math.max(1, Math.round(length / EDGE_STEP));
            float nx = length == 0 ? 0 : -dy / length;
            float ny = length == 0 ? 0 : dx / length;
            if (used + 2 * steps > points.length) {
                points = Arrays.copyOf(points, Math.max(2 * points.length, used + 2 * steps));
            }
            for (int s = 0; s < steps; s++) {
                float px = x1 + dx * s / steps;
                float py = y1 + dy * s / steps;
                float jitter = (noise(view.worldX(px), view.worldZ(py)) - 0.5F) * 2 * EDGE_JITTER;
                points[used++] = px + nx * jitter;
                points[used++] = py + ny * jitter;
            }
        }
        int total = used / 2;
        for (int i = 0; i < total; i++) {
            int j = (i + 1) % total;
            PointShapes.line(graphics, points[2 * i], points[2 * i + 1], points[2 * j], points[2 * j + 1], COLOR_EDGE);
        }
    }

    // Шум 0..1 по месту в мире (блоками по 2), чтобы край не дрожал при сдвиге карты
    private static float noise(double worldX, double worldZ) {
        int x = Mth.floor(worldX / 2);
        int z = Mth.floor(worldZ / 2);
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535.0F;
    }

    // Рамка из 9 частей: углы как есть, края и середина растянуты; середину закроет лист
    private static void nineSlice(GuiGraphics graphics, int left, int top, int right, int bottom) {
        int c = FRAME_CORNER;
        int inner = FRAME_SIZE - 2 * c;
        int width = right - left;
        int height = bottom - top;
        if (width < 2 * c || height < 2 * c) {
            return;
        }
        RenderSystem.enableBlend();
        // Углы
        graphics.blit(FRAME, left, top, 0, 0, c, c, FRAME_SIZE, FRAME_SIZE);
        graphics.blit(FRAME, right - c, top, FRAME_SIZE - c, 0, c, c, FRAME_SIZE, FRAME_SIZE);
        graphics.blit(FRAME, left, bottom - c, 0, FRAME_SIZE - c, c, c, FRAME_SIZE, FRAME_SIZE);
        graphics.blit(FRAME, right - c, bottom - c, FRAME_SIZE - c, FRAME_SIZE - c, c, c, FRAME_SIZE, FRAME_SIZE);
        // Края
        graphics.blit(FRAME, left + c, top, width - 2 * c, c, c, 0, inner, c, FRAME_SIZE, FRAME_SIZE);
        graphics.blit(FRAME, left + c, bottom - c, width - 2 * c, c, c, FRAME_SIZE - c, inner, c, FRAME_SIZE, FRAME_SIZE);
        graphics.blit(FRAME, left, top + c, c, height - 2 * c, 0, c, c, inner, FRAME_SIZE, FRAME_SIZE);
        graphics.blit(FRAME, right - c, top + c, c, height - 2 * c, FRAME_SIZE - c, c, c, inner, FRAME_SIZE, FRAME_SIZE);
        RenderSystem.disableBlend();
    }
}
