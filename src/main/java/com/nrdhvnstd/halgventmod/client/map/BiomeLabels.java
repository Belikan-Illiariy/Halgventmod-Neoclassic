package com.nrdhvnstd.halgventmod.client.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import com.mojang.math.Axis;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Подписи биомов на карте — вдоль самого биома, как названия на географических картах.
 *
 * Как строится подпись:
 * 1. Видимая область (с запасом) делится на сетку; шаг сетки растёт при отдалении, чтобы клеток было немного.
 * 2. Соседние клетки одного биома собираются в области.
 * 3. Для каждой клетки области считается расстояние до её края — так находится «середина» биома.
 * 4. Средняя линия: самый длинный путь через область, который держится подальше от краёв
 *    (поиск пути, где клетки у края дороже). Концы у края отрезаются, линия сглаживается.
 *    У реки она изгибается по руслу, у круглого леса — почти прямая.
 * 5. Высота букв — от ширины биома, разрядка — от длины линии. Не влезает или наезжает на
 *    подпись побольше — не рисуется. Каждая буква повёрнута по линии.
 */
final class BiomeLabels {
    private static final float DEPTH = -50.0F;     // подписи глубже всего, что рисуется после них
    private static final int GRID_WIDTH = 160;     // клеток сетки на ширину экрана, не больше
    private static final int MIN_CELLS = 10;       // меньшие области не подписываются
    private static final float EDGE_COST = 6.0F;   // насколько путь избегает краёв
    private static final float TRIM = 0.6F;       // концы линии, где биом уже уже 60% от обычного, отрезаются
    private static final long REFRESH_MILLIS = 1500; // пересчёт — чтобы новые разведанные места тоже подписывались

    private static final float HEIGHT_PART = 0.45F; // высота букв — такая доля ширины биома
    private static final float MIN_SCALE = 0.75F;   // мельче — не подписываем
    private static final float MAX_SCALE = 3.5F;
    private static final float LENGTH_PART = 0.8F;  // подпись занимает не больше этой доли линии
    private static final float MAX_SPACING = 1.0F;  // разрядка — не больше высоты буквы
    private static final int COLOR = 0xFFF4EBD8;
    private static final int OUTLINE = 0xFF20170E;

    /** Подпись: биом, средняя линия (в блоках мира), её длина, ширина биома и площадь области. */
    private record Label(String name, float[] xs, float[] zs, float length, float thickness, int cells) {}

    /** Где уже есть буквы (на экране) — чтобы подписи не налезали друг на друга. */
    private record Box(float x, float y, float radius) {}

    private List<Label> labels = List.of();
    private int step;
    private double minX;
    private double minZ;
    private double maxX;
    private double maxZ;
    private long computedAt;
    private int computedRevision;

    /** Пересчитать подписи, если вид ушёл за посчитанную область, сменился шаг сетки или пора обновить. */
    void update(double viewMinX, double viewMinZ, double viewMaxX, double viewMaxZ) {
        int newStep = 1 << MapRegion.CELL_SHIFT;
        while ((viewMaxX - viewMinX) / newStep > GRID_WIDTH) {
            newStep *= 2;
        }
        boolean inside = viewMinX >= minX && viewMinZ >= minZ && viewMaxX <= maxX && viewMaxZ <= maxZ;
        // Расчёт тяжёлый: по таймеру — только если с прошлого раза в памяти карты поменялись биомы
        boolean stale = Util.getMillis() - computedAt >= REFRESH_MILLIS && WorldMap.biomeRevision() != computedRevision;
        if (newStep == step && inside && !stale) {
            return;
        }
        computedRevision = WorldMap.biomeRevision();
        step = newStep;
        double padX = (viewMaxX - viewMinX) / 2;
        double padZ = (viewMaxZ - viewMinZ) / 2;
        minX = Math.floor((viewMinX - padX) / step) * step;
        minZ = Math.floor((viewMinZ - padZ) / step) * step;
        maxX = Math.ceil((viewMaxX + padX) / step) * step;
        maxZ = Math.ceil((viewMaxZ + padZ) / step) * step;
        computedAt = Util.getMillis();
        labels = compute();
    }

    // ===== Расчёт =====

    private List<Label> compute() {
        int w = (int) ((maxX - minX) / step);
        int h = (int) ((maxZ - minZ) / step);
        int[] biomes = new int[w * h];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                biomes[j * w + i] = WorldMap.biomeAt((int) minX + i * step + step / 2, (int) minZ + j * step + step / 2);
            }
        }

        int[] area = new int[w * h];     // номер области клетки, -1 — пока ни в какой
        Arrays.fill(area, -1);
        int[] edge = new int[w * h];     // расстояние до края области, в клетках
        float[] cost = new float[w * h]; // для поиска пути
        int[] parent = new int[w * h];
        int[] queue = new int[w * h];
        List<Label> result = new ArrayList<>();
        int areaCount = 0;

        for (int start = 0; start < biomes.length; start++) {
            if (biomes[start] == 0 || area[start] != -1) {
                continue;
            }
            // Область: все соседние клетки того же биома
            int id = areaCount++;
            int biome = biomes[start];
            int size = 0;
            queue[size++] = start;
            area[start] = id;
            for (int head = 0; head < size; head++) {
                int cell = queue[head];
                for (int next : neighbours(cell, w, h)) {
                    if (next >= 0 && area[next] == -1 && biomes[next] == biome) {
                        area[next] = id;
                        queue[size++] = next;
                    }
                }
            }
            if (size < MIN_CELLS) {
                continue;
            }
            int[] cells = Arrays.copyOf(queue, size);
            Label label = label(biome, cells, id, area, edge, cost, parent, w, h);
            if (label != null) {
                result.add(label);
            }
        }
        result.sort(Comparator.comparingInt(Label::cells).reversed());
        return result;
    }

    // Соседи по четырём сторонам; -1 — за краем сетки
    private static int[] neighbours(int cell, int w, int h) {
        int x = cell % w;
        int y = cell / w;
        return new int[] {
                x > 0 ? cell - 1 : -1,
                x < w - 1 ? cell + 1 : -1,
                y > 0 ? cell - w : -1,
                y < h - 1 ? cell + w : -1};
    }

    private Label label(int biome, int[] cells, int id, int[] area, int[] edge, float[] cost, int[] parent, int w, int h) {
        // Расстояние до края: от клеток на границе области — внутрь
        int[] queue = new int[cells.length];
        int size = 0;
        for (int cell : cells) {
            edge[cell] = 0;
            for (int next : neighbours(cell, w, h)) {
                if (next < 0 || area[next] != id) {
                    edge[cell] = 1;
                    queue[size++] = cell;
                    break;
                }
            }
        }
        for (int head = 0; head < size; head++) {
            int cell = queue[head];
            for (int next : neighbours(cell, w, h)) {
                if (next >= 0 && area[next] == id && edge[next] == 0) {
                    edge[next] = edge[cell] + 1;
                    queue[size++] = next;
                }
            }
        }

        // Самый «серединный» длинный путь: от середины к дальнему концу A, от A — к дальнему концу B
        int middle = cells[0];
        for (int cell : cells) {
            if (edge[cell] > edge[middle]) {
                middle = cell;
            }
        }
        int a = farthest(middle, cells, id, area, edge, cost, parent, w, h);
        int b = farthest(a, cells, id, area, edge, cost, parent, w, h);
        List<Integer> path = new ArrayList<>();
        for (int cell = b; cell != -1; cell = parent[cell]) {
            path.add(cell);
        }
        if (path.size() < 2) {
            return null;
        }

        // Концы у края — прочь: подпись должна стоять там, где биом в полную ширину
        int[] widths = path.stream().mapToInt(cell -> edge[cell]).sorted().toArray();
        float typical = widths[widths.length / 2];
        int from = 0;
        int to = path.size() - 1;
        while (from < to && edge[path.get(from)] < typical * TRIM && from < path.size() / 4) {
            from++;
        }
        while (to > from && edge[path.get(to)] < typical * TRIM && to > path.size() * 3 / 4) {
            to--;
        }
        int count = to - from + 1;
        if (count < 2) {
            return null;
        }
        float[] xs = new float[count];
        float[] zs = new float[count];
        for (int i = 0; i < count; i++) {
            int cell = path.get(from + i);
            xs[i] = (float) (minX + (cell % w + 0.5) * step);
            zs[i] = (float) (minZ + (cell / w + 0.5) * step);
        }
        int window = Math.max(1, Math.round(typical));
        for (int pass = 0; pass < 3; pass++) {
            xs = smooth(xs, window);
            zs = smooth(zs, window);
        }
        // Читается слева направо
        if (xs[count - 1] < xs[0]) {
            reverse(xs);
            reverse(zs);
        }
        float length = 0;
        for (int i = 1; i < count; i++) {
            length += Mth.sqrt(Mth.square(xs[i] - xs[i - 1]) + Mth.square(zs[i] - zs[i - 1]));
        }
        ResourceLocation key = WorldMap.biome(biome);
        if (key == null) {
            return null;
        }
        String name = Component.translatable(Util.makeDescriptionId("biome", key)).getString();
        return new Label(name, xs, zs, length, (2 * typical - 1) * step, cells.length);
    }

    /** Поиск пути от start по области; клетки у края дороже. Возвращает самую дальнюю клетку, parent — путь к ней. */
    private static int farthest(int start, int[] cells, int id, int[] area, int[] edge, float[] cost, int[] parent,
                                int w, int h) {
        for (int cell : cells) {
            cost[cell] = Float.MAX_VALUE;
            parent[cell] = -1;
        }
        cost[start] = 0;
        PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingDouble(entry -> Float.intBitsToFloat((int) entry[1])));
        open.add(new long[] {start, Float.floatToIntBits(0)});
        int far = start;
        while (!open.isEmpty()) {
            long[] entry = open.poll();
            int cell = (int) entry[0];
            float known = Float.intBitsToFloat((int) entry[1]);
            if (known > cost[cell]) {
                continue;
            }
            if (cost[cell] > cost[far]) {
                far = cell;
            }
            for (int next : neighbours(cell, w, h)) {
                if (next < 0 || area[next] != id) {
                    continue;
                }
                float step = cost[cell] + 1 + EDGE_COST / edge[next];
                if (step < cost[next]) {
                    cost[next] = step;
                    parent[next] = cell;
                    open.add(new long[] {next, Float.floatToIntBits(step)});
                }
            }
        }
        return far;
    }

    private static float[] smooth(float[] values, int window) {
        float[] result = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            int from = Math.max(0, i - window);
            int to = Math.min(values.length - 1, i + window);
            float sum = 0;
            for (int j = from; j <= to; j++) {
                sum += values[j];
            }
            result[i] = sum / (to - from + 1);
        }
        return result;
    }

    private static void reverse(float[] values) {
        for (int i = 0, j = values.length - 1; i < j; i++, j--) {
            float swap = values[i];
            values[i] = values[j];
            values[j] = swap;
        }
    }

    // ===== Рисование =====

    /** Перевод из блоков мира в экран. */
    interface Projection {
        float x(double worldX);

        float y(double worldZ);
    }

    void render(GuiGraphics graphics, Font font, Projection projection, float zoom) {
        List<Box> taken = new ArrayList<>();
        // Буквы с обводкой пишут глубину — сдвигаем их назад, иначе панели окна, нарисованные позже, окажутся под ними
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, DEPTH);
        for (Label label : labels) {
            drawLabel(graphics, font, projection, zoom, label, taken);
        }
        graphics.flush();
        graphics.pose().popPose();
    }

    private static void drawLabel(GuiGraphics graphics, Font font, Projection projection, float zoom, Label label,
                                  List<Box> taken) {
        int count = label.xs().length;
        float[] sx = new float[count];
        float[] sy = new float[count];
        float[] along = new float[count]; // длина линии от начала до точки, в пикселях экрана
        for (int i = 0; i < count; i++) {
            sx[i] = projection.x(label.xs()[i]);
            sy[i] = projection.y(label.zs()[i]);
            along[i] = i == 0 ? 0 : along[i - 1] + Mth.sqrt(Mth.square(sx[i] - sx[i - 1]) + Mth.square(sy[i] - sy[i - 1]));
        }
        float length = along[count - 1];
        String text = label.name();
        int textWidth = font.width(text);
        if (textWidth == 0 || length <= 0) {
            return;
        }

        // Размер: по ширине биома, но так, чтобы влезло в длину
        float scale = Math.min(label.thickness() * zoom * HEIGHT_PART / font.lineHeight, MAX_SCALE);
        scale = Math.min(scale, length * LENGTH_PART / textWidth);
        if (scale < MIN_SCALE) {
            return;
        }
        // Разрядка: длинный биом — буквы шире расставлены
        int letters = text.length();
        float spacing = letters < 2 ? 0 : Mth.clamp((length * LENGTH_PART - textWidth * scale) / (letters - 1),
                0, MAX_SPACING * font.lineHeight * scale);
        float total = textWidth * scale + spacing * (letters - 1);

        // Места букв; если хоть одна налезает на уже нарисованную подпись — не рисуем
        float[] centers = new float[letters];
        float cursor = (length - total) / 2;
        for (int i = 0; i < letters; i++) {
            float charWidth = font.width(String.valueOf(text.charAt(i))) * scale;
            centers[i] = cursor + charWidth / 2;
            cursor += charWidth + spacing;
        }
        float radius = font.lineHeight * scale / 2;
        List<Box> boxes = new ArrayList<>(letters);
        for (float center : centers) {
            float[] point = pointAt(sx, sy, along, center);
            Box box = new Box(point[0], point[1], radius);
            for (Box other : taken) {
                if (Math.abs(other.x() - box.x()) < other.radius() + box.radius()
                        && Math.abs(other.y() - box.y()) < other.radius() + box.radius()) {
                    return;
                }
            }
            boxes.add(box);
        }
        taken.addAll(boxes);

        for (int i = 0; i < letters; i++) {
            String letter = String.valueOf(text.charAt(i));
            float charWidth = font.width(letter);
            float reach = Math.max(2, charWidth * scale * 0.6F);
            float[] point = pointAt(sx, sy, along, centers[i]);
            float[] before = pointAt(sx, sy, along, centers[i] - reach);
            float[] after = pointAt(sx, sy, along, centers[i] + reach);
            float angle = (float) Math.atan2(after[1] - before[1], after[0] - before[0]);

            graphics.pose().pushPose();
            graphics.pose().translate(point[0], point[1], 0);
            graphics.pose().mulPose(Axis.ZP.rotation(angle));
            graphics.pose().scale(scale, scale, 1);
            font.drawInBatch8xOutline(FormattedCharSequence.forward(letter, Style.EMPTY),
                    -charWidth / 2, -font.lineHeight / 2.0F + 1, COLOR, OUTLINE,
                    graphics.pose().last().pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT);
            graphics.pose().popPose();
        }
    }

    // Точка на ломаной на расстоянии distance от начала
    private static float[] pointAt(float[] xs, float[] ys, float[] along, float distance) {
        int last = along.length - 1;
        if (distance <= 0) {
            return new float[] {xs[0], ys[0]};
        }
        if (distance >= along[last]) {
            return new float[] {xs[last], ys[last]};
        }
        int i = 1;
        while (along[i] < distance) {
            i++;
        }
        float part = (distance - along[i - 1]) / Math.max(along[i] - along[i - 1], 1.0E-4F);
        return new float[] {Mth.lerp(part, xs[i - 1], xs[i]), Mth.lerp(part, ys[i - 1], ys[i])};
    }
}
