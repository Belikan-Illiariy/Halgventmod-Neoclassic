package com.nrdhvnstd.halgventmod.pottery;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/**
 * Форма сосуда по вокселям: сетка 16×16×16 (блок). Без классов Minecraft — общая для сервера, окна круга и отрисовки.
 *
 * <p><b>Тело</b> вытянуто на круге, поэтому круглое: его хранит разрез {@code profile[y]} — по биту на кольцо
 * (8 колец от оси наружу, 16 слоёв снизу вверх). Клетка разреза — целое кольцо вокселей: {@link #RING_VOXELS} штук.
 * <b>Детали</b> (ручки, носики) — отдельные воксели {@code details}: номера {@link #index(int, int, int)} по возрастанию.
 *
 * <p>Правила глины: клетка держится, если под ней (на слой ниже) есть клетка не дальше {@code overhang} колец;
 * деталь держится, если касается тела или другой держащейся детали гранью.
 */
public final class VesselGeometry {
    public static final int SIZE = 16;
    public static final int RINGS = 8;
    public static final int LAYERS = 16;
    public static final int VOXELS = SIZE * SIZE * SIZE;
    /** Глины на круге — с запасом против образца. */
    public static final double BUDGET = 1.15;
    /** Совпадение с образцом, ниже которого сосуд не засчитывается. */
    public static final double MIN_MATCH = 0.70;

    private static final int[] RING_OF = new int[SIZE * SIZE];
    /** Сколько вокселей в кольце одного слоя. */
    public static final int[] RING_VOXELS = new int[RINGS];
    private static final Template[] TEMPLATES = new Template[VesselForm.values().length];

    static {
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                double dx = x + 0.5 - SIZE / 2.0;
                double dz = z + 0.5 - SIZE / 2.0;
                int r = (int) Math.floor(Math.sqrt(dx * dx + dz * dz));
                RING_OF[z * SIZE + x] = Math.min(r, RINGS);
                if (r < RINGS) {
                    RING_VOXELS[r]++;
                }
            }
        }
        for (VesselForm form : VesselForm.values()) {
            TEMPLATES[form.ordinal()] = buildTemplate(form);
        }
    }

    private VesselGeometry() {}

    /** Образец формы: разрез тела и детали (ручки — со стороны +x). */
    public record Template(int[] profile, int[] details) {}

    // ---------------------------------------------------------------- воксели

    public static int index(int x, int y, int z) {
        return (y * SIZE + z) * SIZE + x;
    }

    public static int x(int index) {
        return index % SIZE;
    }

    public static int z(int index) {
        return index / SIZE % SIZE;
    }

    public static int y(int index) {
        return index / (SIZE * SIZE);
    }

    public static boolean inGrid(int x, int y, int z) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE && z >= 0 && z < SIZE;
    }

    /** Кольцо столбца (x, z); {@link #RINGS} — угол сетки вне круга. */
    public static int ring(int x, int z) {
        return RING_OF[z * SIZE + x];
    }

    public static boolean cell(int[] profile, int r, int y) {
        return y >= 0 && y < LAYERS && r >= 0 && r < RINGS && (profile[y] >> r & 1) != 0;
    }

    public static void setCell(int[] profile, int r, int y, boolean filled) {
        if (filled) {
            profile[y] |= 1 << r;
        } else {
            profile[y] &= ~(1 << r);
        }
    }

    public static boolean body(int[] profile, int x, int y, int z) {
        return inGrid(x, y, z) && cell(profile, ring(x, z), y);
    }

    public static BitSet voxels(int[] profile, int[] details) {
        BitSet set = new BitSet(VOXELS);
        for (int y = 0; y < LAYERS; y++) {
            if (profile[y] == 0) {
                continue;
            }
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    if (cell(profile, ring(x, z), y)) {
                        set.set(index(x, y, z));
                    }
                }
            }
        }
        for (int i : details) {
            if (i >= 0 && i < VOXELS) {
                set.set(i);
            }
        }
        return set;
    }

    public static int bodyVolume(int[] profile) {
        int volume = 0;
        for (int y = 0; y < LAYERS; y++) {
            for (int r = 0; r < RINGS; r++) {
                if (cell(profile, r, y)) {
                    volume += RING_VOXELS[r];
                }
            }
        }
        return volume;
    }

    public static int volume(int[] profile, int[] details) {
        return voxels(profile, details).cardinality();
    }

    /** Сколько глины дают на круге под эту форму. */
    public static int budget(VesselForm form) {
        Template t = template(form);
        return (int) Math.round(volume(t.profile(), t.details()) * BUDGET);
    }

    public static Template template(VesselForm form) {
        return TEMPLATES[form.ordinal()];
    }

    public static int height(int[] profile) {
        for (int y = LAYERS - 1; y >= 0; y--) {
            if (profile[y] != 0) {
                return y + 1;
            }
        }
        return 0;
    }

    // ---------------------------------------------------------------- опора

    /** Держится ли клетка: на дне — всегда, выше — если под ней есть клетка не дальше overhang колец. */
    public static boolean supported(int[] profile, int r, int y, int overhang) {
        if (y == 0) {
            return true;
        }
        for (int d = -overhang; d <= overhang; d++) {
            if (cell(profile, r + d, y - 1)) {
                return true;
            }
        }
        return false;
    }

    /** Обвалить всё, что не держится (снизу вверх, обвал тянет за собой верх). @return объём обвалившегося */
    public static int collapse(int[] profile, int overhang) {
        int fallen = 0;
        for (int y = 1; y < LAYERS; y++) {
            for (int r = 0; r < RINGS; r++) {
                if (cell(profile, r, y) && !supported(profile, r, y, overhang)) {
                    setCell(profile, r, y, false);
                    fallen += RING_VOXELS[r];
                }
            }
        }
        return fallen;
    }

    /** Детали, которые держатся (касаются тела или держащейся детали); остальные отваливаются. */
    public static int[] attachedDetails(int[] profile, int[] details) {
        BitSet set = new BitSet(VOXELS);
        for (int i : details) {
            if (i >= 0 && i < VOXELS && !body(profile, x(i), y(i), z(i))) {
                set.set(i);
            }
        }
        BitSet kept = new BitSet(VOXELS);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1)) {
            if (touchesBody(profile, i)) {
                kept.set(i);
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            for (int n : neighbours(i)) {
                if (n >= 0 && set.get(n) && !kept.get(n)) {
                    kept.set(n);
                    queue.add(n);
                }
            }
        }
        return kept.stream().toArray();
    }

    public static boolean touchesBody(int[] profile, int i) {
        int x = x(i), y = y(i), z = z(i);
        return body(profile, x + 1, y, z) || body(profile, x - 1, y, z) || body(profile, x, y + 1, z)
                || body(profile, x, y - 1, z) || body(profile, x, y, z + 1) || body(profile, x, y, z - 1);
    }

    /** Соседи по граням; −1 — за сеткой. */
    public static int[] neighbours(int i) {
        int x = x(i), y = y(i), z = z(i);
        return new int[] {
                x + 1 < SIZE ? index(x + 1, y, z) : -1, x > 0 ? index(x - 1, y, z) : -1,
                y + 1 < SIZE ? index(x, y + 1, z) : -1, y > 0 ? index(x, y - 1, z) : -1,
                z + 1 < SIZE ? index(x, y, z + 1) : -1, z > 0 ? index(x, y, z - 1) : -1};
    }

    // ---------------------------------------------------------------- ком на круге

    /**
     * Ком глины на круге: приземистый цилиндр у оси, не больше данного объёма (последний слой — от оси наружу,
     * сколько влезет). Что не влезло — в обрезках.
     */
    public static int[] lump(int volume) {
        int[] profile = new int[LAYERS];
        int rings = 5;
        while (rings < RINGS && perLayer(rings) * (LAYERS / 2) < volume) {
            rings++;
        }
        int left = volume;
        for (int y = 0; y < LAYERS && left >= RING_VOXELS[0]; y++) {
            for (int r = 0; r < rings && left >= RING_VOXELS[r]; r++) {
                setCell(profile, r, y, true);
                left -= RING_VOXELS[r];
            }
        }
        return profile;
    }

    private static int perLayer(int rings) {
        int sum = 0;
        for (int r = 0; r < rings; r++) {
            sum += RING_VOXELS[r];
        }
        return sum;
    }

    // ---------------------------------------------------------------- сравнение с образцом

    /**
     * Насколько сосуд похож на образец: доля общих вокселей от объединения (0–1). Тело круглое, поэтому сравнивается
     * как есть, а детали образца примеряются с четырёх сторон и со сдвигом на воксель вбок — берётся лучшее.
     */
    public static double match(VesselForm form, int[] profile, int[] details) {
        Template t = template(form);
        BitSet mine = voxels(profile, details);
        BitSet body = voxels(t.profile(), new int[0]);
        double best = 0;
        for (int turn = 0; turn < 4; turn++) {
            for (int shift = -1; shift <= 1; shift++) {
                if (t.details().length == 0 && (turn > 0 || shift != 0)) {
                    continue;
                }
                BitSet target = (BitSet) body.clone();
                for (int i : t.details()) {
                    int moved = transform(i, turn, shift);
                    if (moved >= 0) {
                        target.set(moved);
                    }
                }
                BitSet both = (BitSet) mine.clone();
                both.and(target);
                BitSet either = (BitSet) mine.clone();
                either.or(target);
                if (either.cardinality() > 0) {
                    best = Math.max(best, (double) both.cardinality() / either.cardinality());
                }
            }
        }
        return best;
    }

    /** Повернуть воксель вокруг оси круга на turn×90° (сдвинув вбок на shift до поворота); −1 — вышел за сетку. */
    public static int transform(int i, int turn, int shift) {
        int x = x(i), y = y(i), z = z(i) + shift;
        if (z < 0 || z >= SIZE) {
            return -1;
        }
        for (int k = 0; k < turn; k++) {
            int nx = SIZE - 1 - z;
            z = x;
            x = nx;
        }
        return index(x, y, z);
    }

    // ---------------------------------------------------------------- проверка

    /**
     * Что не так с сосудом: null — годен, иначе ключ причины (gui.halgventmod.wheel.problem.*):
     * no_bottom — нет дна, loose — деталь не держится, details — не хватает ручек или носика, too_much — глины больше, чем дали.
     */
    public static String problem(VesselForm form, int[] profile, int[] details, int budget) {
        if (!cell(profile, 0, 0)) {
            return "no_bottom";
        }
        if (collapse(profile.clone(), 1) > 0) {
            return "loose";
        }
        int[] kept = attachedDetails(profile, details);
        if (kept.length != countOutsideBody(profile, details)) {
            return "loose";
        }
        if (volume(profile, details) > budget) {
            return "too_much";
        }
        if (goodDetails(profile, details, form.handles()) < form.details()) {
            return "details";
        }
        return null;
    }

    private static int countOutsideBody(int[] profile, int[] details) {
        int n = 0;
        for (int i : details) {
            if (i >= 0 && i < VOXELS && !body(profile, x(i), y(i), z(i))) {
                n++;
            }
        }
        return n;
    }

    /**
     * Сколько отдельных деталей держится как надо: ручка касается тела в двух местах, разнесённых хотя бы на
     * два слоя или три вокселя; носику хватает одного касания.
     */
    public static int goodDetails(int[] profile, int[] details, boolean handles) {
        int good = 0;
        for (List<Integer> part : parts(profile, details)) {
            List<Integer> contacts = new ArrayList<>();
            for (int i : part) {
                if (touchesBody(profile, i)) {
                    contacts.add(i);
                }
            }
            if (contacts.isEmpty()) {
                continue;
            }
            if (!handles) {
                good++;
                continue;
            }
            boolean apart = false;
            for (int a = 0; a < contacts.size() && !apart; a++) {
                for (int b = a + 1; b < contacts.size() && !apart; b++) {
                    int ia = contacts.get(a), ib = contacts.get(b);
                    int dist = Math.abs(x(ia) - x(ib)) + Math.abs(y(ia) - y(ib)) + Math.abs(z(ia) - z(ib));
                    apart = Math.abs(y(ia) - y(ib)) >= 2 || dist >= 3;
                }
            }
            if (apart) {
                good++;
            }
        }
        return good;
    }

    /** Детали, разбитые на связные куски. */
    public static List<List<Integer>> parts(int[] profile, int[] details) {
        BitSet set = new BitSet(VOXELS);
        for (int i : details) {
            if (i >= 0 && i < VOXELS && !body(profile, x(i), y(i), z(i))) {
                set.set(i);
            }
        }
        List<List<Integer>> parts = new ArrayList<>();
        BitSet seen = new BitSet(VOXELS);
        for (int start = set.nextSetBit(0); start >= 0; start = set.nextSetBit(start + 1)) {
            if (seen.get(start)) {
                continue;
            }
            List<Integer> part = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            seen.set(start);
            while (!queue.isEmpty()) {
                int i = queue.poll();
                part.add(i);
                for (int n : neighbours(i)) {
                    if (n >= 0 && set.get(n) && !seen.get(n)) {
                        seen.set(n);
                        queue.add(n);
                    }
                }
            }
            parts.add(part);
        }
        return parts;
    }

    // ---------------------------------------------------------------- образцы

    private static Template buildTemplate(VesselForm form) {
        int[] p = new int[LAYERS];
        List<Integer> d = new ArrayList<>();
        switch (form) {
            case CUP -> {
                rings(p, 0, 0, 5);
                for (int y = 1; y <= 12; y++) {
                    rings(p, y, 4, 5);
                }
                // Ручка: перекладины у стенки и стойка снаружи
                for (int y = 3; y <= 10; y++) {
                    plane(d, 15, y);
                }
                plane(d, 13, 3);
                plane(d, 14, 3);
                plane(d, 13, 10);
                plane(d, 14, 10);
            }
            case BOWL -> {
                rings(p, 0, 0, 5);
                rings(p, 1, 5, 6);
                rings(p, 2, 6, 7);
                rings(p, 3, 6, 7);
                rings(p, 4, 7, 8);
                rings(p, 5, 7, 8);
            }
            case JUG -> {
                rings(p, 0, 0, 5);
                rings(p, 1, 5, 6);
                for (int y = 2; y <= 6; y++) {
                    rings(p, y, 6, 7);
                }
                rings(p, 7, 5, 6);
                rings(p, 8, 4, 5);
                for (int y = 9; y <= 12; y++) {
                    rings(p, y, 3, 4);
                }
                rings(p, 13, 4, 5);
                // Ручка от горла к пузу
                for (int x = 12; x <= 14; x++) {
                    plane(d, x, 11);
                }
                for (int y = 5; y <= 11; y++) {
                    plane(d, 15, y);
                }
            }
            case POT -> {
                rings(p, 0, 0, 5);
                rings(p, 1, 5, 6);
                for (int y = 2; y <= 7; y++) {
                    rings(p, y, 6, 7);
                }
                rings(p, 8, 5, 6);
                rings(p, 9, 5, 7);
            }
            case AMPHORA -> {
                rings(p, 0, 0, 3);
                rings(p, 1, 3, 4);
                rings(p, 2, 4, 5);
                rings(p, 3, 5, 6);
                rings(p, 4, 6, 7);
                for (int y = 5; y <= 10; y++) {
                    rings(p, y, 7, 8);
                }
                rings(p, 11, 6, 7);
                rings(p, 12, 5, 6);
                rings(p, 13, 4, 5);
                rings(p, 14, 3, 4);
                rings(p, 15, 3, 5);
                // Два ушка: от горла к плечу, с двух сторон
                for (int x = 12; x <= 15; x++) {
                    plane(d, x, 14);
                    plane(d, SIZE - 1 - x, 14);
                }
                for (int y = 11; y <= 13; y++) {
                    plane(d, 15, y);
                    plane(d, 0, y);
                }
            }
            case LAMP -> {
                rings(p, 0, 0, 5);
                rings(p, 1, 5, 6);
                rings(p, 2, 5, 6);
                rings(p, 3, 4, 5);
                rings(p, 4, 3, 4);
                rings(p, 5, 2, 3);
                // Носик для фитиля
                for (int y = 1; y <= 2; y++) {
                    plane(d, 14, y);
                    plane(d, 15, y);
                }
            }
        }
        int[] details = d.stream().mapToInt(Integer::intValue).filter(i -> !body(p, x(i), y(i), z(i)))
                .sorted().distinct().toArray();
        return new Template(p, details);
    }

    private static void rings(int[] profile, int y, int inner, int outer) {
        for (int r = inner; r < outer; r++) {
            setCell(profile, r, y, true);
        }
    }

    /** Деталь толщиной в два вокселя посередине (z = 7 и 8). */
    private static void plane(List<Integer> details, int x, int y) {
        details.add(index(x, y, SIZE / 2 - 1));
        details.add(index(x, y, SIZE / 2));
    }

    /** Отсортированный список без повторов. */
    public static int[] normalize(int[] details) {
        return Arrays.stream(details).filter(i -> i >= 0 && i < VOXELS).sorted().distinct().toArray();
    }
}
