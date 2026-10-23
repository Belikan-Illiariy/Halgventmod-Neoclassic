package com.nrdhvnstd.halgventmod.smithing;

/**
 * Разметка этапа ковки: до какой толщины вытягивать (DRAW — профиль вдоль заготовки, 0–1 от исходной), как
 * глубоко выколачивать пластину (RAISE — глубина по полю u, v ∈ [0, 1]), по какой дуге гнуть (BEND), какую
 * закалку и отпуск держит вещь (QUENCH, TEMPER). Профили — кусочно-линейные: {@link #along}.
 */
public enum ForgeShape {
    NONE,
    // ---- вытяжка: профиль (x — от хвоста к острию)
    BAR, ROD, POINT, STRIP, PLATE, RING_STOCK, KNIFE, SHORT_BLADE, BLADE, LONG_BLADE, LEAF_BLADE, CURVED, AXE,
    BROAD_AXE, SPEAR, LEAF_SPEAR, POLE_BLADE, TOOL_HEAD,
    // ---- выколотка: глубина
    SKULLCAP, BASCINET, BARBUTE, SALLET, GREAT_HELM, KETTLE, VISOR, BEVOR, CUIRASS, GAUNTLET, CAULDRON,
    // ---- гибка: дуга
    HOOK, RING, SICKLE, SABRE, SOCKET, EYE, WAVE;

    /** Целевая толщина вытяжки в точке x ∈ [0, 1]. */
    public float target(float x) {
        return switch (this) {
            case BAR -> 0.7F;
            case ROD -> 0.35F;
            case STRIP -> 0.25F;
            case PLATE -> 0.4F;
            case RING_STOCK -> 0.3F;
            case POINT -> along(x, 0, 0.42F, 0.6F, 0.38F, 1, 0.12F);
            case KNIFE -> along(x, 0, 0.3F, 0.28F, 0.3F, 0.32F, 0.55F, 0.8F, 0.45F, 1, 0.15F);
            case SHORT_BLADE -> along(x, 0, 0.3F, 0.22F, 0.3F, 0.26F, 0.62F, 0.85F, 0.42F, 1, 0.14F);
            case BLADE -> along(x, 0, 0.3F, 0.17F, 0.3F, 0.2F, 0.62F, 0.9F, 0.38F, 1, 0.12F);
            case LONG_BLADE -> along(x, 0, 0.32F, 0.14F, 0.32F, 0.17F, 0.64F, 0.92F, 0.4F, 1, 0.12F);
            case LEAF_BLADE -> along(x, 0, 0.32F, 0.15F, 0.32F, 0.18F, 0.55F, 0.7F, 0.66F, 0.92F, 0.36F, 1, 0.12F);
            case CURVED -> along(x, 0, 0.32F, 0.2F, 0.32F, 0.25F, 0.55F, 0.85F, 0.35F, 1, 0.14F);
            case AXE -> along(x, 0, 0.9F, 0.3F, 0.9F, 0.4F, 0.6F, 0.85F, 0.35F, 1, 0.22F);
            case BROAD_AXE -> along(x, 0, 0.9F, 0.25F, 0.9F, 0.35F, 0.55F, 0.75F, 0.4F, 1, 0.2F);
            case SPEAR -> along(x, 0, 0.5F, 0.35F, 0.5F, 0.42F, 0.62F, 0.8F, 0.4F, 1, 0.1F);
            case LEAF_SPEAR -> along(x, 0, 0.5F, 0.35F, 0.5F, 0.42F, 0.5F, 0.62F, 0.7F, 0.88F, 0.32F, 1, 0.1F);
            case POLE_BLADE -> along(x, 0, 0.5F, 0.3F, 0.5F, 0.38F, 0.55F, 0.85F, 0.4F, 1, 0.18F);
            case TOOL_HEAD -> along(x, 0, 0.55F, 0.25F, 0.8F, 0.5F, 0.85F, 0.75F, 0.6F, 1, 0.3F);
            default -> 0.6F;
        };
    }

    /** Глубина выколотки в точке (u, v) ∈ [0, 1]²: 0 — плоско, 1 — глубже всего. */
    public float depth(float u, float v) {
        float du = u - 0.5F;
        float dv = v - 0.5F;
        float r = (float) Math.sqrt(du * du + dv * dv) * 2; // 0 в середине, 1 у края круга
        return switch (this) {
            case SKULLCAP -> dome(r, 0.55F);
            case BASCINET -> Math.max(dome(r, 0.85F), v < 0.35F ? dome(Math.abs(du) * 2, 0.6F) * (0.35F - v) * 2.8F : 0);
            case BARBUTE -> dome(r, 0.8F) * (v > 0.85F && Math.abs(du) < 0.12F ? 0.4F : 1);
            case SALLET -> Math.max(dome(r * 1.1F, 0.75F), v > 0.7F ? 0.35F * (1 - Math.abs(du) * 2) : 0);
            case GREAT_HELM -> r < 0.8F ? 0.8F : Math.max(0, 0.8F - (r - 0.8F) * 4);
            case KETTLE -> r < 0.6F ? dome(r / 0.6F, 0.75F) : 0.08F;
            case VISOR -> Math.max(0, 0.65F - Math.abs(du) * 1.6F) * (0.4F + v * 0.6F);
            case BEVOR -> dome(Math.abs(du) * 2, 0.5F) * (1 - v * 0.6F);
            case CUIRASS -> Math.max(0, 0.7F - Math.abs(du) * 1.2F) * (0.5F + 0.5F * (1 - Math.abs(dv) * 2));
            case GAUNTLET -> dome(r * 1.2F, 0.5F);
            case CAULDRON -> dome(r, 0.95F);
            default -> 0;
        };
    }

    private static float dome(float r, float depth) {
        return r >= 1 ? 0 : depth * (float) Math.sqrt(1 - r * r);
    }

    /**
     * Дуга гибки: точка t ∈ [0, 1] вдоль загиба, x и y — доли поля (0 — левый край, 0 — верх). Начало дуги —
     * там, где заготовка лежит на роге.
     */
    public float[] arc(float t) {
        double a;
        return switch (this) {
            case HOOK -> {
                a = Math.PI * 0.75 * t;
                yield new float[] {0.35F + 0.25F * (float) Math.sin(a), 0.3F + 0.25F * (1 - (float) Math.cos(a))};
            }
            case RING -> {
                a = Math.PI * 2 * 0.95 * t;
                yield new float[] {0.5F + 0.3F * (float) Math.sin(a), 0.5F - 0.3F * (float) Math.cos(a)};
            }
            case SICKLE -> {
                a = Math.PI * 1.1 * t;
                yield new float[] {0.2F + 0.35F * (1 - (float) Math.cos(a)), 0.7F - 0.45F * (float) Math.sin(a)};
            }
            case SABRE -> new float[] {0.12F + 0.76F * t, 0.65F - 0.25F * (float) Math.sin(Math.PI * t * 0.9)};
            case SOCKET -> {
                a = Math.PI * 1.6 * t;
                yield new float[] {0.5F + 0.22F * (float) Math.sin(a), 0.5F - 0.22F * (float) Math.cos(a)};
            }
            case EYE -> {
                a = Math.PI * 1.3 * t;
                yield new float[] {0.45F + 0.28F * (float) Math.sin(a), 0.32F + 0.28F * (1 - (float) Math.cos(a))};
            }
            case WAVE -> new float[] {0.1F + 0.8F * t, 0.5F + 0.18F * (float) Math.sin(Math.PI * 6 * t)};
            default -> new float[] {0.15F + 0.7F * t, 0.5F};
        };
    }

    /** Есть ли у формы профиль вытяжки. */
    public boolean drawn() {
        return ordinal() >= BAR.ordinal() && ordinal() <= TOOL_HEAD.ordinal();
    }

    /** Есть ли у формы выколотка. */
    public boolean raised() {
        return ordinal() >= SKULLCAP.ordinal() && ordinal() <= CAULDRON.ordinal();
    }

    /** Есть ли у формы дуга гибки. */
    public boolean bent() {
        return ordinal() >= HOOK.ordinal();
    }

    /** Кусочно-линейная кривая по точкам (x0, y0, x1, y1, …), x по возрастанию. */
    public static float along(float x, float... points) {
        if (x <= points[0]) {
            return points[1];
        }
        for (int i = 2; i < points.length; i += 2) {
            if (x <= points[i]) {
                float t = (x - points[i - 2]) / (points[i] - points[i - 2]);
                return points[i - 1] + (points[i + 1] - points[i - 1]) * t;
            }
        }
        return points[points.length - 1];
    }
}
