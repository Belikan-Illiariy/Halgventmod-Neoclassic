using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;

// Иконки посуды 32x32 в духе Conquest Reforged (их clay_jug, amphora_2): сосуд — тело вращения, которое
// просвечивается лучами под небольшим углом сверху. Свет сверху-слева, мягкая объёмная светотень с зерном,
// без чёрного контура — край просто темнее. Внутренность открытых сосудов видна через горло.
public static class Vessels {
    // ---------------------------------------------------------------- материалы

    public class Surface {
        public double[][] Ramp;   // 5 ступеней: глубокая тень, тень, основа, свет, блик
        public double Gloss;      // сила блика
        public double Sheen = 0.14; // широкий мягкий отсвет (у матовой глины)
        public double Grain;      // зерно
        public bool Streaks;      // следы лощила
        public Surface(string deep, string dark, string bas, string light, string hi, double gloss, double grain) {
            Ramp = new[] { H(deep), H(dark), H(bas), H(light), H(hi) }; Gloss = gloss; Grain = grain;
        }
    }

    static double[] H(string h) {
        h = h.TrimStart('#');
        return new double[] { Convert.ToInt32(h.Substring(0, 2), 16) / 255.0, Convert.ToInt32(h.Substring(2, 2), 16) / 255.0, Convert.ToInt32(h.Substring(4, 2), 16) / 255.0 };
    }

    // Сырец
    static readonly Surface Wet = new Surface("#221a14", "#463a30", "#74665a", "#9a8c7a", "#e8e0d4", 0.32, 0.06);
    static readonly Surface Dry = new Surface("#5e5244", "#968a76", "#cdc2ac", "#e8e0cc", "#f8f4ea", 0.0, 0.06);
    static readonly Surface DryBurnished = new Surface("#5e5244", "#968a76", "#cdc2ac", "#e8e0cc", "#ffffff", 0.6, 0.03) { Streaks = true };
    // Черепок
    static readonly Surface Terracotta = new Surface("#2a1009", "#6c3224", "#ab6248", "#d48d68", "#f0c09a", 0.04, 0.07);
    static readonly Surface Under = new Surface("#6a4636", "#a87862", "#d6ac8e", "#eccaae", "#fae6d4", 0.0, 0.06);
    static readonly Surface Over = new Surface("#220c08", "#4c2016", "#7a3a28", "#9e5638", "#c47e5a", 0.05, 0.08);
    static readonly Surface Burnished = new Surface("#2a1009", "#6c3224", "#ab6248", "#d48d68", "#ffe2c8", 0.4, 0.04) { Streaks = true };
    static readonly Surface Black = new Surface("#0a0808", "#1e1a18", "#342e2c", "#544c48", "#b8b0a8", 0.55, 0.03) { Streaks = true };

    // ---------------------------------------------------------------- сцена: поле расстояний

    delegate double Field(double x, double y, double z);

    /** Профиль по точкам (высота, радиус), сглаженный Катмулл-Ромом. */
    class Profile {
        readonly double[] h, r;
        public Profile(params double[] hr) { int n = hr.Length / 2; h = new double[n]; r = new double[n]; for (int i = 0; i < n; i++) { h[i] = hr[2 * i]; r[i] = hr[2 * i + 1]; } }
        public double Top { get { return h[h.Length - 1]; } }
        public double At(double y) {
            if (y <= h[0]) return r[0];
            if (y >= h[h.Length - 1]) return r[r.Length - 1];
            int i = 0; while (h[i + 1] < y) i++;
            double t = (y - h[i]) / (h[i + 1] - h[i]);
            double p0 = r[Math.Max(0, i - 1)], p1 = r[i], p2 = r[i + 1], p3 = r[Math.Min(r.Length - 1, i + 2)];
            double t2 = t * t, t3 = t2 * t;
            return 0.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
        }
    }

    class Form {
        public string Id;
        public Profile Outer;
        public double Wall = 0.9, Floor = 1.0;   // толщина стенки и дна
        public double HoleR = -1;                 // закрытый верх с отверстием (светильник)
        public double Lip = 0.7;                  // толщина венчика
        public List<double[]> Tubes = new List<double[]>(); // отрезки трубок: x1,y1,z1,x2,y2,z2,r1,r2
        public List<double[]> Holes = new List<double[]>(); // вычесть шары: x,y,z,r
        public double Yaw = 0;                    // поворот вокруг оси, градусы
        public double Elev = 24;                  // насколько сверху смотрим, градусы
        public double Inside = 0.32;              // насколько светла внутренность
        public double Size = 28;                  // во сколько пикселей вписать
        public double[] Flame;                    // где огонёк (светильник)
    }

    static void Handle(Form f, double side, params double[] xy) {
        for (int i = 0; i + 3 < xy.Length; i += 2)
            f.Tubes.Add(new[] { side * xy[i], xy[i + 1], 0, side * xy[i + 2], xy[i + 3], 0, 1.0, 1.0 });
    }

    static Form[] Forms() {
        var cup = new Form { Id = "cup", Outer = new Profile(0, 4.6, 0.6, 5.3, 4, 5.9, 8, 6.1, 11, 6.0, 12.5, 6.2), Inside = 0.45, Size = 25 };
        Handle(cup, 1, 5.6, 10.6, 8.2, 10.8, 10.0, 9.4, 10.4, 6.8, 9.4, 4.2, 7.4, 3.2, 5.4, 3.2);
        var bowl = new Form { Id = "bowl", Outer = new Profile(0, 3.6, 0.9, 3.8, 1.1, 4.6, 2.6, 7.6, 4.4, 9.8, 6.0, 11.0, 7.0, 11.4), Wall = 0.9, Floor = 1.2, Elev = 30, Inside = 0.62, Size = 29 };
        var pot = new Form { Id = "pot", Outer = new Profile(0, 5.4, 0.8, 6.4, 3.5, 9.2, 6.8, 10.4, 10, 9.6, 12.6, 7.6, 14, 6.8, 15.4, 7.4, 16.2, 7.9), Lip = 0.85, Inside = 0.45, Size = 28 };
        var jug = new Form { Id = "jug", Outer = new Profile(0, 4.6, 0.8, 5.6, 3.6, 7.8, 7, 8.6, 10, 7.8, 12.6, 5.6, 14.4, 3.4, 16.5, 2.6, 19.2, 2.8, 20.4, 3.4), Lip = 0.65, Size = 30 };
        Handle(jug, 1, 2.4, 18.6, 5.0, 19.0, 7.0, 17.4, 7.9, 14.6, 7.8, 11.6, 7.2, 9.8);
        jug.Tubes.Add(new[] { -2.6, 20.3, 0, -4.4, 20.9, 0, 0.75, 0.55 }); // носик-слив
        var amphora = new Form { Id = "amphora", Outer = new Profile(0, 0.9, 0.8, 1.3, 1.8, 1.6, 3.6, 3.2, 7.2, 6.0, 11, 7.6, 14, 7.8, 16.2, 6.8, 18, 4.4, 19.2, 2.7, 22.6, 2.4, 23.8, 2.9), Lip = 0.7, Size = 30 };
        Handle(amphora, 1, 2.0, 21.6, 4.6, 22.0, 6.2, 20.8, 6.9, 18.8, 6.6, 16.6);
        Handle(amphora, -1, 2.0, 21.6, 4.6, 22.0, 6.2, 20.8, 6.9, 18.8, 6.6, 16.6);
        var lamp = new Form { Id = "lamp", Outer = new Profile(0, 3.4, 0.5, 4.8, 1.5, 6.2, 2.5, 6.4, 3.1, 5.7, 3.6, 4.2, 3.8, 3.2), HoleR = 1.2, Lip = 0.5, Yaw = -24, Elev = 38, Size = 28 };
        lamp.Tubes.Add(new[] { 4.6, 1.8, 0, 9.0, 2.3, 0, 1.8, 1.3 });    // рожок
        lamp.Holes.Add(new[] { 9.0, 3.4, 0, 0.85 });                      // отверстие для фитиля
        Handle(lamp, -1, 4.6, 2.6, 5.9, 3.8, 6.3, 5.6, 5.4, 6.8, 4.3, 6.0, 3.9, 3.6); // ручка-кольцо сзади
        lamp.Flame = new[] { 9.0, 3.6, 0 };
        // тигель: толстостенный, с толстым дном
        var crucible = new Form { Id = "crucible", Outer = new Profile(0, 3.4, 0.6, 4.0, 3.0, 4.6, 6.0, 4.9, 7.6, 5.1), Wall = 1.5, Floor = 1.8, Lip = 0.9, Elev = 30, Inside = 0.38, Size = 24 };
        return new[] { cup, bowl, jug, pot, amphora, lamp, crucible };
    }

    // Что задело: 0 — снаружи, 1 — внутри полости, 2 — трубки
    static double Body(Form f, double x, double y, double z, out int part) {
        double rho = Math.Sqrt(x * x + z * z);
        double top = f.Outer.Top;
        double ro = f.Outer.At(y);
        double outer = Math.Max(Math.Max(rho - ro, y - top), -y);
        double d = outer; part = 0;
        // полость (у светильника — узкое отверстие сверху): < 0 внутри
        double ri = f.HoleR > 0 ? f.HoleR : ro - f.Wall;
        double cavity = Math.Max(rho - ri, f.Floor - y);
        if (-cavity > d) { d = -cavity; part = 1; }
        // венчик — тор по краю
        double lip = Math.Sqrt(Math.Pow(rho - (ro - f.Wall / 2 + (f.HoleR > 0 ? 0 : 0.15)), 2) + Math.Pow(y - top, 2)) - f.Lip;
        if (f.HoleR <= 0 && lip < d) { d = lip; part = 0; }
        foreach (var t in f.Tubes) {
            double c = Capsule(x, y, z, t);
            if (c < d) { d = c; part = 2; }
        }
        foreach (var hl in f.Holes) {
            double s = hl[3] - Math.Sqrt(Sq(x - hl[0]) + Sq(y - hl[1]) + Sq(z - hl[2]));
            if (s > d) { d = s; part = 1; }
        }
        return d;
    }

    static double Sq(double v) { return v * v; }

    static double Capsule(double x, double y, double z, double[] t) {
        double bx = t[3] - t[0], by = t[4] - t[1], bz = t[5] - t[2];
        double px = x - t[0], py = y - t[1], pz = z - t[2];
        double h = Math.Max(0, Math.Min(1, (px * bx + py * by + pz * bz) / (bx * bx + by * by + bz * bz)));
        double dx = px - bx * h, dy = py - by * h, dz = pz - bz * h;
        return Math.Sqrt(dx * dx + dy * dy + dz * dz) - (t[6] + (t[7] - t[6]) * h);
    }

    // ---------------------------------------------------------------- камера

    // Луч: вглубь и вниз под углом Elev формы; Up — вверх по экрану
    static double[] Dir, Up;
    static void Camera(double elevDeg) {
        double e = elevDeg * Math.PI / 180;
        Dir = new[] { 0, -Math.Sin(e), -Math.Cos(e) };
        Up = new[] { 0, Math.Cos(e), -Math.Sin(e) };
    }
    static readonly double[] Light = Norm(-0.62, 0.62, 0.48);

    static double[] Norm(double x, double y, double z) { double l = Math.Sqrt(x * x + y * y + z * z); return new[] { x / l, y / l, z / l }; }

    class Hit { public double X, Y, Z; public int Part; public double[] N; }

    /** Луч из точки экрана (u вправо, v вверх, в единицах сосуда). */
    static Hit Trace(Form f, double u, double v) {
        double cy = Math.Cos(f.Yaw * Math.PI / 180), sy = Math.Sin(f.Yaw * Math.PI / 180);
        double ox = u - Dir[0] * 60 + Up[0] * v, oy = Up[1] * v - Dir[1] * 60, oz = Up[2] * v - Dir[2] * 60;
        double tt = 0; int part;
        for (int i = 0; i < 600 && tt < 140; i++) {
            double x = ox + Dir[0] * tt, y = oy + Dir[1] * tt, z = oz + Dir[2] * tt;
            double lx = x * cy - z * sy, lz = x * sy + z * cy;
            double d = Body(f, lx, y, lz, out part);
            if (d < 0.002) {
                const double e = 0.03;
                int p;
                double nx = Body(f, lx + e, y, lz, out p) - Body(f, lx - e, y, lz, out p);
                double ny = Body(f, lx, y + e, lz, out p) - Body(f, lx, y - e, lz, out p);
                double nz = Body(f, lx, y, lz + e, out p) - Body(f, lx, y, lz - e, out p);
                // обратно в мир
                double wx = nx * cy + nz * sy, wz = -nx * sy + nz * cy;
                return new Hit { X = lx, Y = y, Z = lz, Part = part, N = Norm(wx, ny, wz) };
            }
            tt += Math.Max(0.02, d * 0.6);
        }
        return null;
    }

    /** Точка сосуда — на экран (u, v). */
    static double[] Project(Form f, double x, double y, double z) {
        double cy = Math.Cos(f.Yaw * Math.PI / 180), sy = Math.Sin(f.Yaw * Math.PI / 180);
        double wx = x * cy + z * sy, wz = -x * sy + z * cy;
        return new[] { wx, wx * Up[0] + y * Up[1] + wz * Up[2] };
    }

    // ---------------------------------------------------------------- шум

    static double Hash(int x, int y, int s) {
        unchecked { int h = x * 374761393 + y * 668265263 + s * 982451653; h = (h ^ (h >> 13)) * 1274126177; h ^= h >> 16; return (h & 0x7fffffff) / (double)0x7fffffff; }
    }
    static double Noise3(double x, double y, double z, int s) {
        return (Noise2(x + z * 0.7, y, s) + Noise2(x, y + z * 0.9, s + 3)) * 0.5;
    }
    static double Noise2(double x, double y, int s) {
        int xi = (int)Math.Floor(x), yi = (int)Math.Floor(y); double fx = x - xi, fy = y - yi;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        double a = Hash(xi, yi, s), b = Hash(xi + 1, yi, s), c = Hash(xi, yi + 1, s), d = Hash(xi + 1, yi + 1, s);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    // ---------------------------------------------------------------- рисование

    const int N = 32;
    const int SS = 3; // подвыборок на пиксель по стороне

    class Px { public double Cover; public double[] C = new double[3]; }

    static double[] Ramp(double[][] r, double v) {
        v = Math.Max(0, Math.Min(1, v));
        double[] st = { 0, 0.28, 0.56, 0.82, 1 };
        int i = 0; while (i < 3 && v > st[i + 1]) i++;
        double t = (v - st[i]) / (st[i + 1] - st[i]);
        return new[] { r[i][0] + (r[i + 1][0] - r[i][0]) * t, r[i][1] + (r[i + 1][1] - r[i][1]) * t, r[i][2] + (r[i + 1][2] - r[i][2]) * t };
    }

    /** Нарисовать сосуд: scale и смещение подбираются, чтобы вписать в Size пикселей по центру. */
    static void Fit(Form f, out double scale, out double offU, out double offV) {
        // Габарит на экране — по грубой выборке
        double minU = 1e9, maxU = -1e9, minV = 1e9, maxV = -1e9;
        for (double u = -20; u <= 20; u += 0.3) for (double v = -8; v <= 32; v += 0.3) {
            if (Trace(f, u, v) == null) continue;
            minU = Math.Min(minU, u); maxU = Math.Max(maxU, u); minV = Math.Min(minV, v); maxV = Math.Max(maxV, v);
        }
        scale = f.Size / Math.Max(maxU - minU + 0.3, maxV - minV + 0.3);
        offU = (minU + maxU) / 2; offV = (minV + maxV) / 2;
    }

    static Px[,] Render(Form f, Surface g, int seed, double scale, double offU, double offV) {
        var img = new Px[N, N];
        for (int px = 0; px < N; px++) for (int py = 0; py < N; py++) {
            var p = new Px(); int hits = 0;
            for (int a = 0; a < SS; a++) for (int b = 0; b < SS; b++) {
                double sx = px + (a + 0.5) / SS, sy = py + (b + 0.5) / SS;
                double u = (sx - N / 2.0) / scale + offU, v = (N / 2.0 - sy) / scale + offV;
                var hit = Trace(f, u, v);
                if (hit == null) continue;
                hits++;
                double[] c = Shade(f, g, hit, seed);
                p.C[0] += c[0]; p.C[1] += c[1]; p.C[2] += c[2];
            }
            p.Cover = hits / (double)(SS * SS);
            if (hits > 0) { p.C[0] /= hits; p.C[1] /= hits; p.C[2] /= hits; }
            img[px, py] = p;
        }
        return img;
    }

    static double[] Shade(Form f, Surface g, Hit h, int seed) {
        double[] n = h.N;
        double diff = Math.Max(0, n[0] * Light[0] + n[1] * Light[1] + n[2] * Light[2]);
        double wrap = (n[0] * Light[0] + n[1] * Light[1] + n[2] * Light[2] + 0.35) / 1.35; // мягкий спад в тень
        double v = 0.14 + 0.62 * Math.Max(0, wrap) + 0.22 * diff;
        // низ темнее, как у Conquest: свет сверху
        double top = f.Outer.Top;
        v -= 0.36 * Math.Pow(1 - Math.Min(1, h.Y / Math.Max(1, top)), 2);
        // полость — в тени, глубже — темнее
        if (h.Part == 1) v = v * f.Inside * (0.55 + 0.45 * Math.Min(1, Math.Max(0, (h.Y - f.Floor) / Math.Max(1, top - f.Floor))));
        // зерно глины
        v += g.Grain * (Noise3(h.X * 1.7, h.Y * 1.7, h.Z * 1.7, seed) - 0.5) * 2;
        if (g.Streaks) v += 0.05 * Math.Sin(h.Y * 3.1 + Math.Atan2(h.Z, h.X) * 2) * (h.Part == 0 ? 1 : 0);
        double[][] ramp = g.Ramp;
        double[] c = Ramp(ramp, v);
        // блик: полуплоскость между светом и взглядом
        if (h.Part != 1) {
            double[] hv = Norm(Light[0] - Dir[0], Light[1] - Dir[1], Light[2] - Dir[2]);
            double nh = Math.Max(0, n[0] * hv[0] + n[1] * hv[1] + n[2] * hv[2]);
            double s = Math.Pow(nh, 28) * g.Gloss + Math.Pow(nh, 5) * g.Sheen;
            double[] hi = ramp[4];
            c = new[] { c[0] + (hi[0] - c[0]) * s, c[1] + (hi[1] - c[1]) * s, c[2] + (hi[2] - c[2]) * s };
        }
        return c;
    }

    /** Ступени, зерно по пикселям, мягкий тёмный край. */
    static Bitmap Finish(Px[,] img, Surface g, int seed) {
        var bmp = new Bitmap(N, N, PixelFormat.Format32bppArgb);
        bool[,] ins = new bool[N, N];
        for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) ins[x, y] = img[x, y].Cover >= 0.5;
        for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) {
            if (!ins[x, y]) continue;
            double[] c = (double[])img[x, y].C.Clone();
            double k = 1 + (Hash(x, y, seed + 91) - 0.5) * 0.16;              // пиксельное зерно
            if (Hash(x, y, seed + 17) < 0.07) k *= 0.84;                        // тёмные крапины
            bool edgeLow = (y + 1 >= N || !ins[x, y + 1]) || (x + 1 >= N || !ins[x + 1, y]);
            bool edgeHigh = (y == 0 || !ins[x, y - 1]) || (x == 0 || !ins[x - 1, y]);
            if (edgeLow) k *= 0.74; else if (edgeHigh) k *= 0.9;
            int r = Q(c[0] * k), gg = Q(c[1] * k), b = Q(c[2] * k);
            bmp.SetPixel(x, y, Color.FromArgb(255, r, gg, b));
        }
        return bmp;
    }

    // Цвет на 6 бит на канал — палитра чуть грубее, как у рисованных вручную
    static int Q(double v) { int i = (int)Math.Max(0, Math.Min(255, Math.Round(v * 255))); return Math.Min(255, (i / 4) * 4 + 2); }

    // ---------------------------------------------------------------- приметы поверх

    /** Трещины на треснувшем сырце и посуде: ломаные тёмные линии от края вниз. */
    static void Cracks(Bitmap bmp, int seed) {
        var rnd = new Random(seed);
        int top = N, bottom = -1;
        for (int yy = 0; yy < N; yy++) for (int xx = 0; xx < N; xx++) if (bmp.GetPixel(xx, yy).A != 0) { top = Math.Min(top, yy); bottom = yy; }
        for (int k = 0; k < 2; k++) {
            int x, y;
            if (k == 0) {
                // от верхнего края в середине
                x = 13 + rnd.Next(5); y = 0;
                while (y < N && bmp.GetPixel(x, y).A == 0) y++;
                y += 1;
            } else {
                // через тулово: от левого края на трети высоты
                y = top + (bottom - top) * 2 / 5 + rnd.Next(3); x = 0;
                while (x < N && bmp.GetPixel(x, y).A == 0) x++;
                x += 1;
            }
            for (int s = 0; s < 9 + rnd.Next(5) && y < N; s++) {
                Color c = bmp.GetPixel(x, y);
                if (c.A == 0) break;
                bool dark = c.R + c.G + c.B < 200; // на чернолощёной трещина светлее, иначе не видно
                bmp.SetPixel(x, y, dark ? Color.FromArgb(255, Math.Min(255, c.R + 70), Math.Min(255, c.G + 64), Math.Min(255, c.B + 58))
                        : Color.FromArgb(255, c.R * 45 / 100, c.G * 42 / 100, c.B * 40 / 100));
                // светлый скол рядом
                if (x + 1 < N && bmp.GetPixel(x + 1, y).A != 0 && rnd.NextDouble() < 0.4) {
                    Color d = bmp.GetPixel(x + 1, y);
                    bmp.SetPixel(x + 1, y, Color.FromArgb(255, Math.Min(255, d.R + 26), Math.Min(255, d.G + 24), Math.Min(255, d.B + 20)));
                }
                if (k == 0) { y++; x += rnd.Next(3) - 1; } else { x++; y += rnd.Next(3) == 0 ? 0 : 1; }
                if (x < 1 || x >= N - 1) break;
            }
        }
    }

    /** Огонёк фитиля. */
    static void Flame(Bitmap bmp, int fx, int fy) {
        string[] rows = {
            "..o..",
            "..o..",
            ".oyo.",
            ".oyo.",
            "oywyo",
            ".ywy.",
            "..y..",
        };
        for (int j = 0; j < rows.Length; j++) for (int i = 0; i < 5; i++) {
            char ch = rows[j][i]; if (ch == '.') continue;
            int x = fx - 2 + i, y = fy - rows.Length + 1 + j;
            if (x < 0 || y < 0 || x >= N || y >= N) continue;
            Color col = ch == 'w' ? Color.FromArgb(255, 255, 250, 214) : ch == 'y' ? Color.FromArgb(255, 255, 206, 74) : Color.FromArgb(255, 234, 112, 30);
            bmp.SetPixel(x, y, col);
        }
    }

    // ---------------------------------------------------------------- всё вместе

    public static string[] WareStates = { "terracotta", "underfired", "overfired", "burnished", "black" };
    public static string[] GreenStates = { "wet", "dry", "cracked", "burnished" };

    static Surface WareSurface(string state) {
        switch (state) {
            case "underfired": return Under;
            case "overfired": return Over;
            case "burnished": return Burnished;
            case "black": return Black;
            default: return Terracotta;
        }
    }

    static Surface GreenSurface(string state) {
        switch (state) {
            case "wet": return Wet;
            case "burnished": return DryBurnished;
            default: return Dry;
        }
    }

    /** root — textures/item. Пишет pottery/ware/<форма>_<состояние>.png и pottery/greenware/<форма>_<состояние>.png. */
    public static void Run(string root) {
        string ware = Path.Combine(root, "pottery", "ware"), green = Path.Combine(root, "pottery", "greenware");
        Directory.CreateDirectory(ware); Directory.CreateDirectory(green);
        int seed = 500;
        foreach (var f in Forms()) {
            seed += 10;
            Camera(f.Elev);
            double sc, ou, ov;
            Fit(f, out sc, out ou, out ov);
            foreach (var s in WareStates) {
                var bmp = Finish(Render(f, WareSurface(s), seed, sc, ou, ov), WareSurface(s), seed);
                bmp.Save(Path.Combine(ware, f.Id + "_" + s + ".png"), ImageFormat.Png);
                using (var cracked = (Bitmap)bmp.Clone()) {
                    Cracks(cracked, seed + 3);
                    cracked.Save(Path.Combine(ware, f.Id + "_" + s + "_cracked.png"), ImageFormat.Png);
                }
                if (f.Flame != null) {
                    double[] p = Project(f, f.Flame[0], f.Flame[1], f.Flame[2]);
                    int fx = (int)Math.Floor((p[0] - ou) * sc + N / 2.0), fy = (int)Math.Floor(N / 2.0 - (p[1] - ov) * sc);
                    Flame(bmp, fx, fy - 1);
                    bmp.Save(Path.Combine(ware, f.Id + "_" + s + "_lit.png"), ImageFormat.Png);
                }
                bmp.Dispose();
            }
            foreach (var s in GreenStates) {
                var bmp = Finish(Render(f, GreenSurface(s), seed, sc, ou, ov), GreenSurface(s), seed);
                if (s == "cracked") Cracks(bmp, seed);
                bmp.Save(Path.Combine(green, f.Id + "_" + s + ".png"), ImageFormat.Png);
                bmp.Dispose();
            }
        }
    }
}
