using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;

// Иконки предметов 32x32 в духе Conquest Reforged: каждая вещь — маленькая 3D-сцена из простых тел (поле расстояний),
// которая просвечивается лучами. Свет сверху-слева, объёмная светотень с затенением складок, зерно фактуры,
// блик у блестящего, сквозь стекло видно содержимое, по краю — тёмный контур, как у предметов Conquest.
// Сцены — в SculptIcons.cs.

public delegate double Sdf(double x, double y, double z);

/** Материал: 5 ступеней цвета (глубокая тень, тень, основа, свет, блик) и как он отражает. */
public class Mtl {
    public double[][] Ramp;
    public double Gloss;            // острый блик (металл, стекло, глазурь, влага)
    public double Spec = 26;        // его острота
    public double Sheen = 0.1;      // широкий мягкий отсвет
    public double Grain = 0.06;     // зерно фактуры
    public double GrainScale = 1.6; // его мелкость
    public double Glass;            // 0..1: сколько видно сквозь (содержимое склянки)
    public Func<double, double, double, double> Bump;  // + к светлоте в точке (полосы, прожилки)
    public Func<double, double, double, Mtl> Swap;     // другой материал в точке (крапины, жир, корка)

    public Mtl(string dark, string bas, string light, double gloss = 0, double grain = 0.06) {
        double[] d = H(dark), b = H(bas), l = H(light);
        Ramp = new[] { Mul(d, 0.5), d, b, l, Mix(l, new double[] { 1, 1, 1 }, 0.5) };
        Gloss = gloss; Grain = grain;
    }

    public Mtl With(double sheen = -1, double spec = -1, double grainScale = -1, double glass = -1) {
        var m = (Mtl)MemberwiseClone();
        if (sheen >= 0) m.Sheen = sheen;
        if (spec >= 0) m.Spec = spec;
        if (grainScale >= 0) m.GrainScale = grainScale;
        if (glass >= 0) m.Glass = glass;
        return m;
    }

    public static double[] H(string h) {
        h = h.TrimStart('#');
        return new double[] { Convert.ToInt32(h.Substring(0, 2), 16) / 255.0, Convert.ToInt32(h.Substring(2, 2), 16) / 255.0, Convert.ToInt32(h.Substring(4, 2), 16) / 255.0 };
    }
    public static double[] Mul(double[] a, double k) { return new[] { a[0] * k, a[1] * k, a[2] * k }; }
    public static double[] Mix(double[] a, double[] b, double t) { return new[] { a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t }; }
}

public class Part {
    public Sdf F; public Mtl M;
    public Part(Sdf f, Mtl m) { F = f; M = m; }
}

/** Сцена одной иконки. Оси: X — вправо, Y — вверх, Z — к зрителю. */
public class Scene {
    public List<Part> Parts = new List<Part>();
    public double Elev = 30;      // насколько сверху смотрим, градусы
    public double Yaw = 0;        // поворот вещи вокруг вертикали
    public double Roll = 0;       // поворот картинки (наискосок, как держат инструмент)
    public double Size = 28;      // во сколько пикселей вписать
    public bool Outline = true;   // тёмный контур, как у предметов Conquest
    public int Seed = 1;
    public List<Action<Bitmap, Scene>> Overlays = new List<Action<Bitmap, Scene>>();

    public Scene Add(Sdf f, Mtl m) { Parts.Add(new Part(f, m)); return this; }

    // ---------------- поле сцены

    public double Eval(double x, double y, double z, bool skipGlass, out Part hit) {
        double d = 1e9; hit = null;
        foreach (var p in Parts) {
            if (skipGlass && p.M.Glass > 0) continue;
            double v = p.F(x, y, z);
            if (v < d) { d = v; hit = p; }
        }
        return d;
    }

    // ---------------- камера

    double[] dir, up; double cyaw, syaw, croll, sroll;
    public double Scale, OffU, OffV, MinY, MaxY;
    const int N = 32, SS = 3;
    static readonly double[] Light = Norm(-0.62, 0.66, 0.42);

    void Camera() {
        double e = Elev * Math.PI / 180;
        dir = new[] { 0, -Math.Sin(e), -Math.Cos(e) };
        up = new[] { 0, Math.Cos(e), -Math.Sin(e) };
        cyaw = Math.Cos(Yaw * Math.PI / 180); syaw = Math.Sin(Yaw * Math.PI / 180);
        croll = Math.Cos(Roll * Math.PI / 180); sroll = Math.Sin(Roll * Math.PI / 180);
    }

    public static double[] Norm(double x, double y, double z) { double l = Math.Sqrt(x * x + y * y + z * z); return new[] { x / l, y / l, z / l }; }

    public class Hit { public double X, Y, Z, T; public double[] N; public Part P; }

    /** Луч из точки картинки (u вправо, v вверх). */
    Hit Trace(double u, double v, bool skipGlass, double from) {
        double ru = u * croll - v * sroll, rv = u * sroll + v * croll;
        double ox = ru, oy = up[1] * rv - dir[1] * 90, oz = up[2] * rv - dir[2] * 90;
        double t = from; Part p;
        for (int i = 0; i < 700 && t < 200; i++) {
            double x = ox + dir[0] * t, y = oy + dir[1] * t, z = oz + dir[2] * t;
            double lx = x * cyaw - z * syaw, lz = x * syaw + z * cyaw;
            double d = Eval(lx, y, lz, skipGlass, out p);
            if (d < 0.002) {
                const double e = 0.02;
                Part q;
                double nx = Eval(lx + e, y, lz, skipGlass, out q) - Eval(lx - e, y, lz, skipGlass, out q);
                double ny = Eval(lx, y + e, lz, skipGlass, out q) - Eval(lx, y - e, lz, skipGlass, out q);
                double nz = Eval(lx, y, lz + e, skipGlass, out q) - Eval(lx, y, lz - e, skipGlass, out q);
                double[] n = Norm(nx, ny, nz);
                return new Hit { X = lx, Y = y, Z = lz, T = t, P = p, N = n };
            }
            t += Math.Max(0.012, d * 0.65);
        }
        return null;
    }

    /** Точка сцены — в пиксель картинки (после вписывания). */
    public double[] Project(double x, double y, double z) {
        double wx = x * cyaw + z * syaw, wz = -x * syaw + z * cyaw;
        double rv = wx * up[0] + y * up[1] + wz * up[2], ru = wx;
        double u = ru * croll + rv * sroll, v = -ru * sroll + rv * croll;
        return new[] { (u - OffU) * Scale + N / 2.0, N / 2.0 - (v - OffV) * Scale };
    }

    void Fit() {
        double minU = 1e9, maxU = -1e9, minV = 1e9, maxV = -1e9;
        MinY = 1e9; MaxY = -1e9;
        for (double u = -30; u <= 30; u += 0.4) for (double v = -30; v <= 30; v += 0.4) {
            var h = Trace(u, v, false, 0);
            if (h == null) continue;
            minU = Math.Min(minU, u); maxU = Math.Max(maxU, u); minV = Math.Min(minV, v); maxV = Math.Max(maxV, v);
            MinY = Math.Min(MinY, h.Y); MaxY = Math.Max(MaxY, h.Y);
        }
        Scale = Size / Math.Max(maxU - minU + 0.4, maxV - minV + 0.4);
        OffU = (minU + maxU) / 2; OffV = (minV + maxV) / 2;
    }

    // ---------------- свет

    double Ao(Hit h) {
        double occ = 0, sca = 1; Part p;
        for (int i = 1; i <= 5; i++) {
            double s = 0.3 * i;
            double d = Eval(h.X + h.N[0] * s, h.Y + h.N[1] * s, h.Z + h.N[2] * s, false, out p);
            occ += (s - d) * sca; sca *= 0.7;
        }
        return Math.Max(0, Math.Min(1, 1 - 0.55 * occ));
    }

    double[] Shade(Hit h, bool inGlass) {
        Mtl m = h.P.M;
        if (m.Swap != null) { Mtl sw = m.Swap(h.X, h.Y, h.Z); if (sw != null) m = sw; }
        // нормаль в мире
        double nx = h.N[0] * cyaw + h.N[2] * syaw, nz = -h.N[0] * syaw + h.N[2] * cyaw, ny = h.N[1];
        double ndl = nx * Light[0] + ny * Light[1] + nz * Light[2];
        double wrap = Math.Max(0, (ndl + 0.35) / 1.35);
        double v = 0.12 + 0.62 * wrap + 0.24 * Math.Max(0, ndl);
        v *= 0.5 + 0.5 * Ao(h);
        double span = Math.Max(1, MaxY - MinY);
        v -= 0.16 * Math.Pow(1 - Math.Min(1, Math.Max(0, (h.Y - MinY) / span)), 2);
        v += m.Grain * (Noise3(h.X * m.GrainScale, h.Y * m.GrainScale, h.Z * m.GrainScale, Seed + 7) - 0.5) * 2;
        if (m.Bump != null) v += m.Bump(h.X, h.Y, h.Z);
        double[] c = Ramp(m.Ramp, v);
        double hx = Light[0] - dir[0], hy = Light[1] - dir[1], hz = Light[2] - dir[2];
        double hl = Math.Sqrt(hx * hx + hy * hy + hz * hz);
        double nh = Math.Max(0, (nx * hx + ny * hy + nz * hz) / hl);
        double s = m.Gloss * Math.Pow(nh, m.Spec) + m.Sheen * Math.Pow(nh, 5);
        c = Mtl.Mix(c, m.Ramp[4], Math.Min(1, s));
        return c;
    }

    static double[] Ramp(double[][] r, double v) {
        v = Math.Max(0, Math.Min(1, v));
        double[] st = { 0, 0.26, 0.55, 0.8, 1 };
        int i = 0; while (i < 3 && v > st[i + 1]) i++;
        double t = (v - st[i]) / (st[i + 1] - st[i]);
        return Mtl.Mix(r[i], r[i + 1], t);
    }

    double[] PixelColor(double u, double v, out bool hitAny) {
        var h = Trace(u, v, false, 0);
        hitAny = h != null;
        if (h == null) return null;
        double[] c = Shade(h, false);
        if (h.P.M.Glass > 0) {
            // сквозь стекло — что внутри, подкрашенное стеклом
            var behind = Trace(u, v, true, h.T + 0.05);
            if (behind != null) {
                double[] b = Shade(behind, true);
                double[] tint = h.P.M.Ramp[3];
                b = new[] { b[0] * (0.55 + 0.45 * tint[0]), b[1] * (0.55 + 0.45 * tint[1]), b[2] * (0.55 + 0.45 * tint[2]) };
                double g = h.P.M.Glass;
                // блик стекла остаётся поверх
                double lum = (c[0] + c[1] + c[2]) / 3, baseLum = (h.P.M.Ramp[2][0] + h.P.M.Ramp[2][1] + h.P.M.Ramp[2][2]) / 3;
                double spec = Math.Max(0, lum - baseLum - 0.12) * 1.6;
                c = Mtl.Mix(Mtl.Mix(c, b, g), new double[] { 1, 1, 1 }, Math.Min(1, spec));
            }
        }
        return c;
    }

    // ---------------- картинка

    public void Render(string path) {
        Camera();
        Fit();
        var col = new double[N, N][];
        bool[,] ins = new bool[N, N];
        for (int px = 0; px < N; px++) for (int py = 0; py < N; py++) {
            double[] sum = new double[3]; int hits = 0;
            for (int a = 0; a < SS; a++) for (int b = 0; b < SS; b++) {
                double sx = px + (a + 0.5) / SS, sy = py + (b + 0.5) / SS;
                double u = (sx - N / 2.0) / Scale + OffU, v = (N / 2.0 - sy) / Scale + OffV;
                bool any;
                double[] c = PixelColor(u, v, out any);
                if (!any) continue;
                hits++; sum[0] += c[0]; sum[1] += c[1]; sum[2] += c[2];
            }
            if (hits * 2 >= SS * SS) { ins[px, py] = true; col[px, py] = new[] { sum[0] / hits, sum[1] / hits, sum[2] / hits }; }
        }
        var bmp = new Bitmap(N, N, PixelFormat.Format32bppArgb);
        for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) {
            if (!ins[x, y]) continue;
            double[] c = col[x, y];
            double k = 1 + (Hash(x, y, Seed + 91) - 0.5) * 0.12;
            if (Hash(x, y, Seed + 17) < 0.05) k *= 0.86;
            bool low = y + 1 >= N || !ins[x, y + 1] || x + 1 >= N || !ins[x + 1, y];
            if (low) k *= 0.8;
            bmp.SetPixel(x, y, Color.FromArgb(255, Q(c[0] * k), Q(c[1] * k), Q(c[2] * k)));
        }
        foreach (var o in Overlays) o(bmp, this);
        if (Outline) AddOutline(bmp);
        bmp.Save(path, ImageFormat.Png);
        bmp.Dispose();
    }

    /** Тёмный контур снаружи: цвет соседа, почти чёрный. */
    static void AddOutline(Bitmap bmp) {
        var src = (Bitmap)bmp.Clone();
        int[] dx = { -1, 1, 0, 0 }, dy = { 0, 0, -1, 1 };
        for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) {
            if (src.GetPixel(x, y).A != 0) continue;
            double r = 0, g = 0, b = 0; int n = 0;
            for (int k = 0; k < 4; k++) {
                int nx = x + dx[k], ny = y + dy[k];
                if (nx < 0 || ny < 0 || nx >= N || ny >= N) continue;
                Color c = src.GetPixel(nx, ny);
                if (c.A == 0) continue;
                r += c.R; g += c.G; b += c.B; n++;
            }
            if (n == 0) continue;
            bmp.SetPixel(x, y, Color.FromArgb(255, (int)(r / n * 0.22 + 6), (int)(g / n * 0.2 + 5), (int)(b / n * 0.2 + 4)));
        }
        src.Dispose();
    }

    static int Q(double v) { int i = (int)Math.Max(0, Math.Min(255, Math.Round(v * 255))); return Math.Min(255, (i / 4) * 4 + 2); }

    // ---------------- шум

    public static double Hash(int x, int y, int s) {
        unchecked { int h = x * 374761393 + y * 668265263 + s * 982451653; h = (h ^ (h >> 13)) * 1274126177; h ^= h >> 16; return (h & 0x7fffffff) / (double)0x7fffffff; }
    }
    static double Hash3(int x, int y, int z, int s) { return Hash(x + z * 7919, y - z * 104729, s); }
    public static double Noise3(double x, double y, double z, int s) {
        int xi = (int)Math.Floor(x), yi = (int)Math.Floor(y), zi = (int)Math.Floor(z);
        double fx = x - xi, fy = y - yi, fz = z - zi;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz);
        double r = 0;
        for (int k = 0; k < 8; k++) {
            int ax = k & 1, ay = (k >> 1) & 1, az = (k >> 2) & 1;
            double w = (ax == 1 ? fx : 1 - fx) * (ay == 1 ? fy : 1 - fy) * (az == 1 ? fz : 1 - fz);
            r += w * Hash3(xi + ax, yi + ay, zi + az, s);
        }
        return r;
    }
}

/** Тела и действия над ними. Все размеры — в «пикселях» сцены, потом сцена вписывается в иконку. */
public static class S {
    public static Sdf Sphere(double cx, double cy, double cz, double r) {
        return (x, y, z) => Math.Sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy) + (z - cz) * (z - cz)) - r;
    }
    public static Sdf Ell(double cx, double cy, double cz, double rx, double ry, double rz) {
        return (x, y, z) => {
            double px = (x - cx) / rx, py = (y - cy) / ry, pz = (z - cz) / rz;
            double k0 = Math.Sqrt(px * px + py * py + pz * pz);
            double qx = px / rx, qy = py / ry, qz = pz / rz;
            double k1 = Math.Sqrt(qx * qx + qy * qy + qz * qz);
            return k1 < 1e-9 ? -Math.Min(rx, Math.Min(ry, rz)) : k0 * (k0 - 1) / k1;
        };
    }
    public static Sdf Cap(double x1, double y1, double z1, double x2, double y2, double z2, double r1, double r2 = -1) {
        if (r2 < 0) r2 = r1;
        return (x, y, z) => {
            double bx = x2 - x1, by = y2 - y1, bz = z2 - z1, px = x - x1, py = y - y1, pz = z - z1;
            double h = Math.Max(0, Math.Min(1, (px * bx + py * by + pz * bz) / (bx * bx + by * by + bz * bz)));
            double dx = px - bx * h, dy = py - by * h, dz = pz - bz * h;
            return Math.Sqrt(dx * dx + dy * dy + dz * dz) - (r1 + (r2 - r1) * h);
        };
    }
    /** Цепочка трубок по точкам (x, y, z тройками). */
    public static Sdf Tube(double r1, double r2, params double[] p) {
        int n = p.Length / 3; var parts = new List<Sdf>();
        for (int i = 0; i + 1 < n; i++) {
            double ra = r1 + (r2 - r1) * i / (n - 1), rb = r1 + (r2 - r1) * (i + 1) / (n - 1);
            parts.Add(Cap(p[3 * i], p[3 * i + 1], p[3 * i + 2], p[3 * i + 3], p[3 * i + 4], p[3 * i + 5], ra, rb));
        }
        return Union(parts.ToArray());
    }
    public static Sdf Box(double cx, double cy, double cz, double hx, double hy, double hz, double round = 0) {
        return (x, y, z) => {
            double qx = Math.Abs(x - cx) - hx + round, qy = Math.Abs(y - cy) - hy + round, qz = Math.Abs(z - cz) - hz + round;
            double ox = Math.Max(qx, 0), oy = Math.Max(qy, 0), oz = Math.Max(qz, 0);
            return Math.Sqrt(ox * ox + oy * oy + oz * oz) + Math.Min(Math.Max(qx, Math.Max(qy, qz)), 0) - round;
        };
    }
    /** Цилиндр вдоль Y. */
    public static Sdf Cyl(double cx, double cy, double cz, double r, double hh, double round = 0) {
        return (x, y, z) => {
            double dr = Math.Sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) - r + round, dy = Math.Abs(y - cy) - hh + round;
            return Math.Min(Math.Max(dr, dy), 0) + Math.Sqrt(Math.Max(dr, 0) * Math.Max(dr, 0) + Math.Max(dy, 0) * Math.Max(dy, 0)) - round;
        };
    }
    /** Тор в плоскости XZ. */
    public static Sdf Torus(double cx, double cy, double cz, double R, double r) {
        return (x, y, z) => { double q = Math.Sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz)) - R; return Math.Sqrt(q * q + (y - cy) * (y - cy)) - r; };
    }

    /** Тело вращения вокруг вертикали по профилю (высота, радиус); wall > 0 — полое, открытое сверху. */
    public static Sdf Lathe(double cx, double cy, double cz, double wall, double floor, params double[] hr) {
        int n = hr.Length / 2; double[] h = new double[n], r = new double[n];
        for (int i = 0; i < n; i++) { h[i] = hr[2 * i]; r[i] = hr[2 * i + 1]; }
        Func<double, double> R = yy => {
            if (yy <= h[0]) return r[0];
            if (yy >= h[n - 1]) return r[n - 1];
            int i = 0; while (h[i + 1] < yy) i++;
            double t = (yy - h[i]) / (h[i + 1] - h[i]);
            double p0 = r[Math.Max(0, i - 1)], p1 = r[i], p2 = r[i + 1], p3 = r[Math.Min(n - 1, i + 2)];
            double t2 = t * t, t3 = t2 * t;
            return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
        };
        double top = h[n - 1], bottom = h[0];
        return (x, y, z) => {
            double yy = y - cy, rho = Math.Sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            double ro = R(yy);
            double d = Math.Max(Math.Max(rho - ro, yy - top), bottom - yy) * 0.8;
            if (wall > 0) {
                double cav = Math.Max(rho - (ro - wall), floor + bottom - yy);
                d = Math.Max(d, -cav);
                // скруглённый венчик
                double lip = Math.Sqrt((rho - (R(top) - wall / 2)) * (rho - (R(top) - wall / 2)) + (yy - top) * (yy - top)) - wall * 0.6;
                d = Math.Min(d, lip);
            }
            return d;
        };
    }

    /** Многоугольник в плоскости XZ (точки x, z парами), выдавленный по Y на hh в обе стороны, со скруглением. */
    public static Sdf Slab(double cy, double hh, double round, params double[] p) {
        int n = p.Length / 2;
        return (x, y, z) => {
            double d = double.MaxValue; bool inside = false;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = p[2 * i], zi = p[2 * i + 1], xj = p[2 * j], zj = p[2 * j + 1];
                double ex = xj - xi, ez = zj - zi, wx = x - xi, wz = z - zi;
                double t = Math.Max(0, Math.Min(1, (wx * ex + wz * ez) / (ex * ex + ez * ez)));
                double dx = wx - ex * t, dz = wz - ez * t; d = Math.Min(d, dx * dx + dz * dz);
                if (((zi > z) != (zj > z)) && (x < (xj - xi) * (z - zi) / (zj - zi) + xi)) inside = !inside;
            }
            double d2 = (inside ? -1 : 1) * Math.Sqrt(d) + round;
            double dy = Math.Abs(y - cy) - hh + round;
            return Math.Min(Math.Max(d2, dy), 0) + Math.Sqrt(Math.Max(d2, 0) * Math.Max(d2, 0) + Math.Max(dy, 0) * Math.Max(dy, 0)) - round;
        };
    }

    /** Многоугольник в плоскости XY (точки x, y парами) толщиной 2·hz по Z — клинок, пластина, обращённые к зрителю. */
    public static Sdf Plate(double cz, double hz, double round, params double[] p) {
        Sdf slab = Slab(cz, hz, round, p);
        return (x, y, z) => slab(x, z, y);
    }
    /** Сплющить по высоте (лист, шкура, лепёшка). */
    public static Sdf Squash(Sdf f, double ky) { return (x, y, z) => f(x, y / ky, z) * ky; }

    public static Sdf Union(params Sdf[] f) {
        return (x, y, z) => { double d = double.MaxValue; foreach (var g in f) d = Math.Min(d, g(x, y, z)); return d; };
    }
    /** Плавное слияние (ком, лепка). */
    public static Sdf Blend(double k, params Sdf[] f) {
        return (x, y, z) => {
            double d = f[0](x, y, z);
            for (int i = 1; i < f.Length; i++) {
                double b = f[i](x, y, z), h = Math.Max(0, Math.Min(1, 0.5 + 0.5 * (b - d) / k));
                d = b + (d - b) * h - k * h * (1 - h);
            }
            return d;
        };
    }
    public static Sdf Sub(Sdf a, Sdf b) { return (x, y, z) => Math.Max(a(x, y, z), -b(x, y, z)); }
    public static Sdf Inter(Sdf a, Sdf b) { return (x, y, z) => Math.Max(a(x, y, z), b(x, y, z)); }
    public static Sdf Move(Sdf f, double dx, double dy, double dz) { return (x, y, z) => f(x - dx, y - dy, z - dz); }

    /** Поворот вокруг оси через точку (cx, cy, cz); axis: 'x', 'y', 'z'. */
    public static Sdf Rot(Sdf f, char axis, double deg, double cx = 0, double cy = 0, double cz = 0) {
        double a = -deg * Math.PI / 180, c = Math.Cos(a), s = Math.Sin(a);
        return (x, y, z) => {
            double px = x - cx, py = y - cy, pz = z - cz;
            switch (axis) {
                case 'x': return f(cx + px, cy + py * c - pz * s, cz + py * s + pz * c);
                case 'y': return f(cx + px * c + pz * s, cy + py, cz - px * s + pz * c);
                default: return f(cx + px * c - py * s, cy + px * s + py * c, cz + pz);
            }
        };
    }
    /** Неровность поверхности. */
    public static Sdf Rough(Sdf f, double amp, double freq, int seed) {
        return (x, y, z) => f(x, y, z) + amp * (Scene.Noise3(x * freq, y * freq, z * freq, seed) * 2 - 1);
    }
    /** Волна листа: сдвиг по высоте от положения. */
    public static Sdf Wave(Sdf f, double amp, double freq, int seed) {
        return (x, y, z) => f(x, y - amp * (Scene.Noise3(x * freq, 0.5, z * freq, seed) * 2 - 1), z) * 0.8;
    }
}
