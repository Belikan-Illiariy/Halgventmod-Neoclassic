using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;

// Рисовальщик иконок 32x32 в духе Conquest Reforged: формы по SDF, свет сверху-слева,
// ступенчатая светотень (ограниченная палитра), фактура шумом, цветной тёмный контур.
public class Mat {
    public double[] Dark, Base, Light; public double Noise; public double NoiseScale; public int Levels;
    public Mat(string dark, string bas, string light, double noise, double noiseScale, int levels) {
        Dark = Hex(dark); Base = Hex(bas); Light = Hex(light); Noise = noise; NoiseScale = noiseScale; Levels = levels;
    }
    public static double[] Hex(string h) {
        h = h.TrimStart('#');
        return new double[] { Convert.ToInt32(h.Substring(0, 2), 16) / 255.0, Convert.ToInt32(h.Substring(2, 2), 16) / 255.0, Convert.ToInt32(h.Substring(4, 2), 16) / 255.0 };
    }
}

public class Canvas {
    public const int N = 32;
    public double[,] R = new double[N, N], G = new double[N, N], B = new double[N, N];
    public int[,] Id = new int[N, N];
    int nextId = 1;
    public int Seed;
    public Canvas(int seed) { Seed = seed; for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) Id[x, y] = 0; }

    // ---------------- шум
    static double Hash(int x, int y, int s) {
        unchecked { int h = x * 374761393 + y * 668265263 + s * 982451653; h = (h ^ (h >> 13)) * 1274126177; h ^= h >> 16; return (h & 0x7fffffff) / (double)0x7fffffff; }
    }
    public static double Noise(double x, double y, int s) {
        int xi = (int)Math.Floor(x), yi = (int)Math.Floor(y); double fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        double a = Hash(xi, yi, s), b = Hash(xi + 1, yi, s), c = Hash(xi, yi + 1, s), d = Hash(xi + 1, yi + 1, s);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }
    public double Fbm(double x, double y, int s) { return 0.6 * Noise(x, y, s) + 0.3 * Noise(x * 2.1, y * 2.1, s + 7) + 0.1 * Noise(x * 4.3, y * 4.3, s + 13); }

    // ---------------- формы
    public static Func<double, double, double> Ellipse(double cx, double cy, double rx, double ry, double rotDeg = 0) {
        double a = rotDeg * Math.PI / 180, c = Math.Cos(a), s = Math.Sin(a);
        return (x, y) => { double dx = x - cx, dy = y - cy; double u = dx * c + dy * s, v = -dx * s + dy * c; double k = Math.Sqrt(u * u / (rx * rx) + v * v / (ry * ry)); return (k - 1) * Math.Min(rx, ry); };
    }
    public static Func<double, double, double> Capsule(double x1, double y1, double x2, double y2, double r1, double r2 = -1) {
        if (r2 < 0) r2 = r1;
        return (x, y) => { double px = x - x1, py = y - y1, bx = x2 - x1, by = y2 - y1; double h = Math.Max(0, Math.Min(1, (px * bx + py * by) / (bx * bx + by * by))); double dx = px - bx * h, dy = py - by * h; return Math.Sqrt(dx * dx + dy * dy) - (r1 + (r2 - r1) * h); };
    }
    public static Func<double, double, double> Box(double cx, double cy, double hw, double hh, double rotDeg = 0, double round = 0) {
        double a = rotDeg * Math.PI / 180, c = Math.Cos(a), s = Math.Sin(a);
        return (x, y) => { double dx = x - cx, dy = y - cy; double u = Math.Abs(dx * c + dy * s) - hw + round, v = Math.Abs(-dx * s + dy * c) - hh + round; double ox = Math.Max(u, 0), oy = Math.Max(v, 0); return Math.Sqrt(ox * ox + oy * oy) + Math.Min(Math.Max(u, v), 0) - round; };
    }
    public static Func<double, double, double> Poly(params double[] p) {
        int n = p.Length / 2;
        return (x, y) => {
            double d = double.MaxValue; bool inside = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = p[2 * i], yi = p[2 * i + 1], xj = p[2 * j], yj = p[2 * j + 1];
                double ex = xj - xi, ey = yj - yi, wx = x - xi, wy = y - yi;
                double h = Math.Max(0, Math.Min(1, (wx * ex + wy * ey) / (ex * ex + ey * ey)));
                double dx = wx - ex * h, dy = wy - ey * h; d = Math.Min(d, dx * dx + dy * dy);
                if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) inside = !inside;
            }
            return (inside ? -1 : 1) * Math.Sqrt(d);
        };
    }
    public static Func<double, double, double> Union(params Func<double, double, double>[] f) {
        return (x, y) => { double d = double.MaxValue; foreach (var g in f) d = Math.Min(d, g(x, y)); return d; };
    }
    public static Func<double, double, double> Cut(Func<double, double, double> a, Func<double, double, double> b) {
        return (x, y) => Math.Max(a(x, y), -b(x, y));
    }
    public Func<double, double, double> Rough(Func<double, double, double> f, double amp, double freq, int s) {
        return (x, y) => f(x, y) + amp * (Noise(x * freq, y * freq, Seed + s) * 2 - 1);
    }

    // ---------------- заливка со светотенью
    public int Fill(Func<double, double, double> sdf, Mat m, double bevel = 3.0, double flat = 0.0, int seed = 0) {
        int id = nextId++;
        for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) {
            double px = x + 0.5, py = y + 0.5; double d = sdf(px, py);
            if (d >= 0) continue;
            double gx = sdf(px + 0.5, py) - sdf(px - 0.5, py), gy = sdf(px, py + 0.5) - sdf(px, py - 0.5);
            double gl = Math.Sqrt(gx * gx + gy * gy); if (gl > 1e-6) { gx /= gl; gy /= gl; }
            double h = Math.Min(1, -d / bevel);
            double edge = -(gx + gy) / Math.Sqrt(2) * (1 - h) * (1 - flat);           // край, повёрнутый к свету
            double global = -((px - 16) + (py - 16)) / 40.0;                         // объект светлее сверху-слева
            double s = 0.55 + 0.45 * edge + 0.18 * global + 0.08 * h;
            if (m.Noise > 0) s += m.Noise * (Fbm(px * m.NoiseScale, py * m.NoiseScale, Seed + seed + id * 31) - 0.5) * 2;
            s = Math.Max(0, Math.Min(1, s));
            s = Math.Round(s * (m.Levels - 1)) / (m.Levels - 1);                       // ступени палитры
            double[] c = s < 0.5 ? Lerp(m.Dark, m.Base, s * 2) : Lerp(m.Base, m.Light, (s - 0.5) * 2);
            R[x, y] = c[0]; G[x, y] = c[1]; B[x, y] = c[2]; Id[x, y] = id;
        }
        return id;
    }
    static double[] Lerp(double[] a, double[] b, double t) { return new double[] { a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t }; }

    public void Dot(int x, int y, string hex) {
        if (x < 0 || y < 0 || x >= N || y >= N || Id[x, y] == 0) return;
        double[] c = Mat.Hex(hex); R[x, y] = c[0]; G[x, y] = c[1]; B[x, y] = c[2];
    }
    /** Крапины внутри формы (соль, зола, искры). */
    public void Scatter(Func<double, double, double> sdf, string hex, double density, int s) {
        for (int x = 0; x < N; x++) for (int y = 0; y < N; y++)
            if (sdf(x + 0.5, y + 0.5) < -0.5 && Hash(x, y, Seed + s) < density) Dot(x, y, hex);
    }
    /** Линия поверх (шов, лезвие). */
    public void Line(double x1, double y1, double x2, double y2, string hex) {
        int steps = (int)Math.Ceiling(Math.Max(Math.Abs(x2 - x1), Math.Abs(y2 - y1))) * 2 + 1;
        for (int i = 0; i <= steps; i++) { double t = i / (double)steps; Dot((int)Math.Floor(x1 + (x2 - x1) * t), (int)Math.Floor(y1 + (y2 - y1) * t), hex); }
    }

    // ---------------- контур и сохранение
    public void Save(string path) {
        var bmp = new Bitmap(N, N, PixelFormat.Format32bppArgb);
        bool[,] inside = new bool[N, N];
        for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) inside[x, y] = Id[x, y] != 0;
        double[,] r = (double[,])R.Clone(), g = (double[,])G.Clone(), b = (double[,])B.Clone();
        // шов между разными частями — темнее снизу-справа от стыка
        for (int x = 1; x < N; x++) for (int y = 1; y < N; y++) {
            if (!inside[x, y]) continue;
            if ((inside[x - 1, y] && Id[x - 1, y] != Id[x, y] && Id[x - 1, y] < Id[x, y]) || (inside[x, y - 1] && Id[x, y - 1] != Id[x, y] && Id[x, y - 1] < Id[x, y])) {
                r[x, y] *= 0.8; g[x, y] *= 0.8; b[x, y] *= 0.8;
            }
        }
        for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) {
            if (inside[x, y]) { bmp.SetPixel(x, y, Color.FromArgb(255, C(r[x, y]), C(g[x, y]), C(b[x, y]))); continue; }
            // контур: цвет соседа, сильно затемнённый (не чёрный)
            double sr = 0, sg = 0, sb = 0; int n = 0;
            int[] dx = { -1, 1, 0, 0 }, dy = { 0, 0, -1, 1 };
            for (int k = 0; k < 4; k++) { int nx = x + dx[k], ny = y + dy[k]; if (nx < 0 || ny < 0 || nx >= N || ny >= N || !inside[nx, ny]) continue; sr += R[nx, ny]; sg += G[nx, ny]; sb += B[nx, ny]; n++; }
            if (n > 0) bmp.SetPixel(x, y, Color.FromArgb(255, C(sr / n * 0.32 + 0.03), C(sg / n * 0.30 + 0.025), C(sb / n * 0.28 + 0.02)));
        }
        bmp.Save(path, ImageFormat.Png);
        bmp.Dispose();
    }
    static int C(double v) { return (int)Math.Max(0, Math.Min(255, Math.Round(v * 255))); }
}
