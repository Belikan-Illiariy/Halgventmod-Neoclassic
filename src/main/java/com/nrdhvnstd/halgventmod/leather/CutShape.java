package com.nrdhvnstd.halgventmod.leather;

import java.util.ArrayList;
import java.util.List;

/**
 * Контур выкройки для раскроя (CutScreen): мелом по коже или ткани, его ведут ножом или ножницами.
 * Точки — в поле 2×1 (x 0–2 вправо, y 0–1 вниз), замкнутый многоугольник; углы скругляются ({@link #contour}).
 */
public enum CutShape {
    STRAP(0.1, 0.4, 1.9, 0.4, 1.9, 0.6, 0.1, 0.6),
    BELT(0.1, 0.38, 1.7, 0.38, 1.9, 0.5, 1.7, 0.62, 0.1, 0.62),
    POUCH(0.65, 0.12, 1.35, 0.12, 1.35, 0.7, 1.2, 0.9, 0.8, 0.9, 0.65, 0.7),
    BAG(0.5, 0.08, 1.5, 0.08, 1.5, 0.68, 1.3, 0.92, 0.7, 0.92, 0.5, 0.68),
    QUIVER(0.1, 0.3, 1.9, 0.22, 1.9, 0.78, 0.1, 0.7),
    MITTEN(0.82, 0.95, 0.82, 0.5, 0.68, 0.42, 0.62, 0.3, 0.7, 0.24, 0.84, 0.34, 0.86, 0.14, 0.98, 0.05,
            1.12, 0.05, 1.22, 0.14, 1.24, 0.95),
    BODY(0.7, 0.06, 0.88, 0.12, 1.12, 0.12, 1.3, 0.06, 1.62, 0.2, 1.55, 0.42, 1.38, 0.36, 1.38, 0.95,
            0.62, 0.95, 0.62, 0.36, 0.45, 0.42, 0.38, 0.2),
    VEST(0.78, 0.04, 0.9, 0.1, 1.1, 0.1, 1.22, 0.04, 1.3, 0.15, 1.36, 0.96, 0.64, 0.96, 0.7, 0.15),
    HOOD(0.75, 0.95, 0.72, 0.55, 0.8, 0.25, 1.0, 0.08, 1.2, 0.1, 1.32, 0.3, 1.5, 0.55, 1.42, 0.62, 1.3, 0.6, 1.28, 0.95),
    CLOAK(cloak());

    private final double[] points;

    CutShape(double... points) {
        this.points = points;
    }

    /** Плащ: полукруг с вырезом под шею. */
    private static double[] cloak() {
        List<Double> list = new ArrayList<>();
        for (int i = 0; i <= 12; i++) {
            double a = Math.PI * i / 12;
            list.add(1 + 0.85 * Math.cos(a));
            list.add(0.1 + 0.85 * Math.sin(a));
        }
        double[] neck = {0.85, 0.1, 1.0, 0.24, 1.15, 0.1};
        for (double v : neck) {
            list.add(v);
        }
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    /** Контур со скруглёнными углами (два прохода Чайкина), точки парами x, y в поле 2×1. */
    public double[] contour() {
        double[] p = points;
        for (int pass = 0; pass < 2; pass++) {
            int n = p.length / 2;
            double[] q = new double[n * 4];
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double ax = p[2 * i], ay = p[2 * i + 1], bx = p[2 * j], by = p[2 * j + 1];
                q[4 * i] = 0.75 * ax + 0.25 * bx;
                q[4 * i + 1] = 0.75 * ay + 0.25 * by;
                q[4 * i + 2] = 0.25 * ax + 0.75 * bx;
                q[4 * i + 3] = 0.25 * ay + 0.75 * by;
            }
            p = q;
        }
        return p;
    }
}
