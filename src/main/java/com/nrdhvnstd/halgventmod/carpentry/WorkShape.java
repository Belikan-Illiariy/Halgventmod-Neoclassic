package com.nrdhvnstd.halgventmod.carpentry;

/**
 * Разметка заготовки для этапа: гнездо под долото (маска клеток) или профиль под нож (толщина вдоль заготовки).
 * Только числа, без классов Minecraft — геометрию читает окно работы (client/carpentry).
 */
public enum WorkShape {
    NONE,
    // Гнёзда под долото: поле COLS × ROWS клеток
    CAVITY,         // челнок: прямоугольное гнездо под шпульку
    BOWL,           // половник: овальный черпак
    MOUTH,          // рубанок: узкий леток под железку
    DISH,           // деревянная миска: широкая круглая чаша
    TANG,           // рукоять меча: узкий долгий паз под хвостовик клинка
    // Профили под нож: радиус (полутолщина) вдоль заготовки, x от 0 до 1, в единицах до MAX_RADIUS
    SPINDLE,        // веретено: тонкое древко, толще в нижней трети, оба конца сведены на нет
    HANDLE,         // рукоять: хват, поясок, хвостовик под насадку
    SHUTTLE,        // челнок: лодочка с острыми концами
    LADLE_HANDLE,   // ручка половника: тонкая, у черпака расширяется
    BOWL_SIDE,      // миска снаружи: донце, круглый бок, венчик
    AXE_HANDLE,     // топорище: толстое под проушину, тонкая шейка, утолщение-упор на конце
    HAFT,           // черенок: ровный, конец сведён под втулку
    POLE,           // древко: длинное, к наконечнику тоньше
    GRIP;           // рукоять меча: веретёнцем, толще посередине

    public static final int COLS = 16;
    public static final int ROWS = 8;
    public static final float MAX_RADIUS = 6.0F;

    /** Клетка гнезда: вынуть её долотом. */
    public boolean hollow(int col, int row) {
        return switch (this) {
            case CAVITY -> col >= 5 && col <= 10 && row >= 2 && row <= 5;
            case BOWL -> {
                double dx = (col + 0.5 - 6.0) / 4.4;
                double dy = (row + 0.5 - 4.0) / 3.1;
                yield dx * dx + dy * dy <= 1.0;
            }
            case MOUTH -> col >= 7 && col <= 8 && row >= 1 && row <= 6 || col >= 6 && col <= 9 && row >= 3 && row <= 4;
            case DISH -> {
                double dx = (col + 0.5 - 8.0) / 6.3;
                double dy = (row + 0.5 - 4.0) / 3.4;
                yield dx * dx + dy * dy <= 1.0;
            }
            case TANG -> col >= 2 && col <= 13 && row >= 3 && row <= 4;
            default -> false;
        };
    }

    /** Сколько клеток вынимать. */
    public int hollowCells() {
        int n = 0;
        for (int col = 0; col < COLS; col++) {
            for (int row = 0; row < ROWS; row++) {
                if (hollow(col, row)) {
                    n++;
                }
            }
        }
        return n;
    }

    /** Профиль: до какой полутолщины обстругать в точке x (0 — один конец, 1 — другой). */
    public float radius(float x) {
        return switch (this) {
            case SPINDLE -> (float) (0.7 + 2.6 * Math.pow(Math.sin(Math.PI * Math.min(1.0, x * 1.15)), 0.7)
                    * (x < 0.33 ? 1.0 : 1.0 - 0.35 * (x - 0.33)));
            case HANDLE -> x < 0.62 ? 3.4F + 0.5F * (float) Math.sin(Math.PI * x / 0.62)
                    : x < 0.72 ? 4.6F
                    : 2.4F;
            case SHUTTLE -> (float) Math.max(0.5, 4.4 * Math.pow(Math.sin(Math.PI * x), 0.55));
            case LADLE_HANDLE -> x < 0.78F ? 1.7F + 0.3F * x : 1.9F + (x - 0.78F) / 0.22F * 2.6F;
            case BOWL_SIDE -> along(x, 0, 2.6F, 0.12F, 3.0F, 0.2F, 4.4F, 0.5F, 5.3F, 0.8F, 4.6F, 0.92F, 4.9F, 1, 4.4F);
            case AXE_HANDLE -> along(x, 0, 3.4F, 0.14F, 3.4F, 0.3F, 2.3F, 0.82F, 2.7F, 0.92F, 3.7F, 1, 3.1F);
            case HAFT -> along(x, 0, 2.4F, 0.88F, 2.4F, 1, 1.7F);
            case POLE -> along(x, 0, 2.9F, 0.9F, 2.4F, 1, 1.6F);
            case GRIP -> (float) (2.3 + 1.1 * Math.sin(Math.PI * x));
            default -> MAX_RADIUS;
        };
    }

    /** Ломаная по точкам (x, радиус) парами, x по возрастанию. */
    private static float along(float x, float... points) {
        for (int i = 2; i < points.length; i += 2) {
            if (x <= points[i]) {
                float t = (x - points[i - 2]) / (points[i] - points[i - 2]);
                return points[i - 1] + (points[i + 1] - points[i - 1]) * t;
            }
        }
        return points[points.length - 1];
    }
}
