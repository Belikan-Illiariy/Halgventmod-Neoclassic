using System;
using System.Collections.Generic;
using System.IO;

// Сцены иконок для Sculpt.cs: из каких тел и материалов каждая вещь. Run пишет в textures/item/<папка>/ и
// textures/mob_effect/ — те же имена, что были у плоских иконок Icons.cs.
public static class SculptIcons {
    static string root;

    static Mtl M(string dark, string bas, string light, double gloss = 0, double grain = 0.06) { return new Mtl(dark, bas, light, gloss, grain); }

    /** Крапины другого материала там, где шум выше порога. */
    static Mtl Spots(Mtl a, Mtl spot, double freq, double thr, int seed) {
        var m = a.With();
        m.Swap = (x, y, z) => Scene.Noise3(x * freq, y * freq, z * freq, seed) > thr ? spot : null;
        return m;
    }
    /** Тонкие прожилки другого материала (жир в мясе). */
    static Mtl Veins(Mtl a, Mtl vein, double freq, double width, int seed) {
        var m = a.With();
        m.Swap = (x, y, z) => Math.Abs(Scene.Noise3(x * freq, y * freq, z * freq, seed) - 0.5) < width ? vein : null;
        return m;
    }
    static Mtl Bumped(Mtl a, Func<double, double, double, double> bump) { var m = a.With(); m.Bump = bump; return m; }

    // ---------------- материалы
    static readonly Mtl Wood = Bumped(M("#2e1c0e", "#6e4626", "#a87444", 0, 0.07).With(grainScale: 2.2),
        (x, y, z) => 0.06 * Math.Sin(x * 1.3 + Scene.Noise3(x * 0.3, y * 2, z * 0.3, 3) * 6));
    static readonly Mtl BowlWood = Bumped(M("#24150a", "#5e3a1e", "#946238", 0.1, 0.06),
        (x, y, z) => 0.07 * Math.Sin(y * 3.2 + Scene.Noise3(x * 0.4, y * 0.4, z * 0.4, 5) * 3));
    static readonly Mtl Steel = M("#262c32", "#77838e", "#e2e9ef", 0.9, 0.02).With(spec: 40, sheen: 0.15);
    static readonly Mtl Iron = M("#1a1e22", "#4e565e", "#959fa8", 0.55, 0.05).With(spec: 30);
    static readonly Mtl Rust = M("#2a1408", "#6a3a1e", "#a0603a", 0.2, 0.12);
    static readonly Mtl Fat = M("#9c8874", "#e6d8c4", "#fffaf0", 0.25, 0.04);
    static readonly Mtl Bone = M("#80766a", "#ddd4c4", "#fffcf4", 0.15, 0.05);
    static readonly Mtl Leaf = M("#102c0c", "#2f7024", "#78b850", 0.2, 0.05);
    static readonly Mtl Stem = M("#2a1a0a", "#5a3a1a", "#8a6234", 0, 0.04);
    static readonly Mtl Leather = M("#2a160a", "#6a3c1e", "#a26a3c", 0.15, 0.07);
    static readonly Mtl Tan = M("#46280e", "#9a6430", "#d29c5e", 0.1, 0.07);
    static readonly Mtl Terracotta = M("#3a160a", "#9a4e2e", "#d48a5e", 0.05, 0.08);
    static readonly Mtl Ceramic = M("#6a5e50", "#cfc2ae", "#f6efe2", 0.5, 0.03).With(spec: 30);
    static readonly Mtl Burlap = Bumped(M("#3e2c16", "#8c7048", "#c4a878", 0, 0.08),
        (x, y, z) => 0.08 * Math.Sin(x * 4) * Math.Sin(y * 4 + z * 4));
    static readonly Mtl Twine = M("#4a3418", "#a08050", "#d8bc88", 0, 0.05);
    static readonly Mtl Cork = M("#4a2c12", "#9a6a3a", "#d0a070", 0, 0.12).With(grainScale: 3);
    static readonly Mtl White = M("#8e9aa4", "#e4e8ec", "#ffffff", 0.3, 0.05);
    static readonly Mtl Wax = M("#5a3a08", "#c88c22", "#f4d070", 0.4, 0.05);
    static readonly Mtl Honey = M("#4a2204", "#b8680e", "#f6b040", 0.9, 0.02).With(spec: 34, sheen: 0.2);
    static readonly Mtl Eye = M("#000000", "#0e0c0c", "#3a3836", 1.0, 0).With(spec: 40);

    static void Save(Scene s, string folder, string name) {
        string dir = Path.Combine(root, folder); Directory.CreateDirectory(dir);
        s.Render(Path.Combine(dir, name + ".png"));
    }

    // ======================================================================== общие заготовки

    /**
     * Распластанная шкура: тело, лапы, шея, хвост — лежит плашмя (голова к -Z, хвост к +Z), по ней волна.
     * tail — длина хвоста, tailR — его толщина, legs — длина лап (у выделанного меха лапы обрезаны).
     */
    static Sdf HideShape(int seed, double scale = 1, double tail = 1, double tailR = 1, double legs = 1, double rough = 0.6) {
        double k = scale, l = legs;
        Sdf body = S.Blend(2.0,
            S.Ell(0, 0, 0, 9 * k, 1, 7.5 * k),
            S.Cap(-5 * k, 0, -4 * k, (-5 - 5.5 * l) * k, 0, (-4 - 4.5 * l) * k, 2.6 * k, 1.5 * k), S.Cap(5 * k, 0, -4 * k, (5 + 5.5 * l) * k, 0, (-4 - 4.5 * l) * k, 2.6 * k, 1.5 * k),
            S.Cap(-5 * k, 0, 4 * k, (-5 - 5 * l) * k, 0, (4 + 5 * l) * k, 2.6 * k, 1.5 * k), S.Cap(5 * k, 0, 4 * k, (5 + 5 * l) * k, 0, (4 + 5 * l) * k, 2.6 * k, 1.5 * k),
            S.Cap(0, 0, -6 * k, 0, 0, -11 * k, 2.6 * k, 1.9 * k),
            S.Cap(0, 0, 6 * k, 1 * k, 0, (6 + 4.5 * tail) * k, 1.6 * k * tailR, Math.Max(0.5, 0.8 * tailR) * k));
        return S.Wave(S.Squash(S.Rough(body, rough, 0.35, seed), 0.35), 1.2, 0.16, seed + 1);
    }

    /** Зверь для шкуры: размер, хвост, мех, окрас (цвет в точке шкуры: x — поперёк, z — от головы к хвосту). */
    class Beast {
        public string Id; public double K = 1, Tail = 1, TailR = 1, Hair = 0.1, Wool; public bool Fur;
        public Mtl M; public Func<double, double, double, Mtl> Pattern;
    }

    static Beast[] Beasts() {
        var list = new List<Beast>();
        // кролик: серо-бурый, маленький, хвостик-помпон
        list.Add(new Beast { Id = "rabbit", K = 0.6, Tail = 0.15, TailR = 1.6, Fur = true, Hair = 0.18, M = M("#2e261e", "#857360", "#c8b8a0", 0.05, 0.08) });
        // лиса: рыжая, чёрные лапы, пушистый хвост с белым кончиком
        var foxBlack = M("#0e0806", "#2a1a12", "#4a3426", 0.05); var foxWhite = M("#8a8070", "#ece4d6", "#ffffff", 0.05);
        list.Add(new Beast { Id = "fox", K = 0.8, Tail = 1.7, TailR = 1.9, Fur = true, Hair = 0.2, M = M("#4a1a04", "#c4601c", "#f0a050", 0.05, 0.07),
            Pattern = (x, y, z) => z > 9.0 ? foxWhite : (Math.Abs(x) > 5.6 && Math.Abs(z) > 3.6 && z < 8) ? foxBlack : null });
        // волк: серый, по хребту темнее, хвост пушистый
        var wolfDark = M("#1e1d1c", "#555250", "#8c8884", 0.05);
        list.Add(new Beast { Id = "wolf", K = 0.95, Tail = 1.4, TailR = 1.6, Fur = true, Hair = 0.22, M = M("#2a2826", "#7a7670", "#c4c0b8", 0.05, 0.07),
            Pattern = (x, y, z) => Math.Abs(x) < 1.2 + 2.2 * Scene.Noise3(x * 0.6, 0, z * 0.6, 3) ? wolfDark : null });
        // коза: бело-серая короткая шерсть
        list.Add(new Beast { Id = "goat", K = 0.85, Tail = 0.3, TailR = 0.8, Hair = 0.14, M = M("#6e6a62", "#cec6b8", "#f4f0e8", 0.05, 0.07) });
        // овца: густая кремовая шерсть
        list.Add(new Beast { Id = "sheep", K = 0.95, Tail = 0.4, TailR = 1.2, Fur = true, Wool = 0.7, Hair = 0.12, M = M("#8a8070", "#e6dcc6", "#fffaf0", 0, 0.08).With(grainScale: 3) });
        // свинья: розовая голая кожа
        list.Add(new Beast { Id = "pig", K = 0.95, Tail = 0.35, TailR = 0.6, Hair = 0.04, M = M("#7e4a42", "#dc9c8c", "#fcd0c0", 0.25, 0.05) });
        // кабан: тёмно-бурая щетина
        list.Add(new Beast { Id = "hoglin", K = 1.05, Tail = 0.5, TailR = 0.6, Hair = 0.26, Wool = 0.3, M = M("#160e08", "#4e3626", "#806248", 0.05, 0.1) });
        // вол: бурый с белыми пятнами, хвост длинный и тонкий
        var cowWhite = M("#8a847a", "#ece6dc", "#ffffff", 0.1, 0.05);
        list.Add(new Beast { Id = "cow", K = 1.15, Tail = 1.6, TailR = 0.6, Hair = 0.1, M = M("#24140a", "#5e3a20", "#9a6e4a", 0.1, 0.06),
            Pattern = (x, y, z) => Scene.Noise3(x * 0.32, 0, z * 0.32, 77) > 0.62 ? cowWhite : null });
        // конь: гнедой, тёмный ремень по хребту, хвост густой тёмный
        var horseDark = M("#180c06", "#4a2412", "#7a4426", 0.25);
        list.Add(new Beast { Id = "horse", K = 1.15, Tail = 1.6, TailR = 1.1, Hair = 0.08, M = M("#24100a", "#7a3e1e", "#b8743e", 0.3, 0.05),
            Pattern = (x, y, z) => Math.Abs(x) < 0.7 + Scene.Noise3(x, 0, z * 0.5, 4) * 0.8 || z > 10 ? horseDark : null });
        // лама: кремовая шерсть
        list.Add(new Beast { Id = "llama", K = 1.0, Tail = 0.4, TailR = 1.0, Fur = true, Wool = 0.5, Hair = 0.12, M = M("#6e5e44", "#d4c2a0", "#f6ecd8", 0, 0.08).With(grainScale: 3) });
        // медведь (белый): густой белый мех, большой
        list.Add(new Beast { Id = "bear", K = 1.3, Tail = 0.15, TailR = 1.2, Fur = true, Wool = 0.45, Hair = 0.2, M = M("#8a8a84", "#e6e4dc", "#ffffff", 0.05, 0.06) });
        // верблюд: песочный
        list.Add(new Beast { Id = "camel", K = 1.2, Tail = 0.9, TailR = 0.6, Hair = 0.12, M = M("#4e3a20", "#b08c58", "#e4c894", 0.05, 0.07) });
        // оцелот: жёлтый с тёмными пятнами
        var spot = M("#140c04", "#3a2410", "#5e3e1e", 0.05);
        list.Add(new Beast { Id = "ocelot", K = 0.62, Tail = 1.3, TailR = 0.8, Fur = true, Hair = 0.12, M = M("#5a3e10", "#d4a448", "#f4d888", 0.1, 0.06),
            Pattern = (x, y, z) => Scene.Noise3(x * 1.1, 0, z * 1.1, 91) > 0.68 ? spot : null });
        return list.ToArray();
    }

    /** Мех зверя: окрас, ворс полосками вдоль шкуры. */
    static Mtl BeastMtl(Beast b, double gloss) {
        var m = b.M.With(sheen: gloss);
        m.Swap = b.Pattern;
        double hair = b.Hair;
        int seed = b.Id.Length * 31;
        m.Bump = (x, y, z) => hair * (Scene.Noise3(x * 3.2, y * 3.2, z * 0.9, seed) - 0.5) * 2;
        return m;
    }

    /** Шкура каждого зверя: парная и солёная — ворсом вверх; у пушных — ещё выделанный мех (лапы обрезаны, мех лоснится). */
    static void AnimalHides_() {
        int seed = 400;
        foreach (var b in Beasts()) {
            seed += 3;
            double size = 22 + 8 * Math.Min(1, Math.Max(0, (b.K - 0.6) / 0.55));
            double rough = 0.6 + b.Wool * 0.6;
            Sdf hide = HideShape(seed, b.K, b.Tail, b.TailR, 1, rough);
            if (b.Wool > 0) hide = S.Rough(hide, b.Wool * 0.6, 1.3, seed + 1);
            var raw = new Scene { Elev = 62, Yaw = 12, Size = size, Seed = seed };
            raw.Add(hide, BeastMtl(b, 0.08)); Save(raw, "materials/hide", "raw_hide_" + b.Id);

            var salted = new Scene { Elev = 62, Yaw = -14, Size = size, Seed = seed + 1 };
            var sm = BeastMtl(b, 0.04);
            var pattern = b.Pattern;
            sm.Swap = (x, y, z) => Scene.Noise3(x * 2.6, y * 2.6, z * 2.6, seed + 5) > 0.66 ? White : pattern == null ? null : pattern(x, y, z);
            salted.Add(hide, sm); Save(salted, "materials/hide", "salted_hide_" + b.Id);

            if (b.Fur) {
                var fur = new Scene { Elev = 58, Yaw = -8, Size = size, Seed = seed + 2 };
                Sdf trimmed = HideShape(seed + 2, b.K, b.Tail, b.TailR, 0.45, 0.35);
                if (b.Wool > 0) trimmed = S.Rough(trimmed, b.Wool * 0.5, 1.3, seed + 3);
                fur.Add(trimmed, BeastMtl(b, 0.28)); Save(fur, "materials/hide", "dressed_fur_" + b.Id);
            }
        }
    }

    /** Миска (дерево или глина) — тело; содержимое добавляют сверху. Высота 7, радиус края 11,6. */
    static void Bowl(Scene s, Mtl m) {
        s.Add(S.Lathe(0, 0, 0, 1.2, 1.4, 0, 4.6, 1, 6, 3.5, 9.4, 6, 11.1, 7, 11.6), m);
    }
    /** Содержимое миски: гладкая поверхность (похлёбка) или горка (каша). */
    static void BowlFill(Scene s, Mtl m, double dome) {
        if (dome <= 0) s.Add(S.Cyl(0, 5.3, 0, 10.4, 0.4), m);
        else s.Add(S.Inter(S.Ell(0, 5.2, 0, 10.4, dome, 10.4), S.Box(0, 9, 0, 12, 4, 12)), m);
    }
    /** Куски в похлёбке: эллипсоиды чуть над поверхностью в заданных местах. */
    static void Chunks(Scene s, Mtl m, double size, params double[] xz) {
        for (int i = 0; i + 1 < xz.Length; i += 2) {
            double a = i * 47;
            s.Add(S.Rot(S.Ell(xz[i], 5.6, xz[i + 1], size, size * 0.55, size * 0.75), 'y', a, xz[i], 5.6, xz[i + 1]), m);
        }
    }

    /** Склянка: круглая или высокая, внутри жидкость до fill (0..1), пробка. */
    static void Bottle(string folder, string name, Mtl glass, Mtl liquid, bool tall, double fill, int seed) {
        var s = new Scene { Elev = 16, Size = tall ? 30 : 28, Seed = seed };
        double[] prof = tall
            ? new double[] { 0, 4.2, 0.5, 4.6, 9, 4.6, 10.6, 4.0, 12.4, 2.0, 16, 1.6, 17, 1.9 }
            : new double[] { 0, 2.6, 0.5, 4.2, 2, 5.6, 4, 6.1, 6, 5.7, 7.6, 4.4, 8.8, 2.4, 9.4, 1.5, 12.6, 1.4, 13.3, 1.8 };
        s.Add(S.Lathe(0, 0, 0, 0.5, 0.6, prof), glass);
        double top = prof[prof.Length - 2];
        // жидкость: тот же профиль, на стенку уже, до уровня
        double level = tall ? 0.6 + fill * 9.4 : 0.6 + fill * 7.6;
        var inner = new List<double> { 0.6, prof[3] - 0.55 };
        for (int i = 0; i < prof.Length; i += 2) {
            double h = prof[i];
            if (h <= 0.6) continue;
            if (h > level) { inner.Add(level); inner.Add(Math.Max(0.5, prof[i + 1] - 0.55)); break; }
            inner.Add(h); inner.Add(Math.Max(0.5, prof[i + 1] - 0.55));
        }
        s.Add(S.Lathe(0, 0, 0, 0, 0, inner.ToArray()), liquid);
        s.Add(S.Cyl(0, top + 0.6, 0, prof[prof.Length - 1] - 0.3, 1.2, 0.3), Cork);
        Save(s, folder, name);
    }
    static Mtl Glass(string dark, string bas, string light) { return M(dark, bas, light, 1.0, 0).With(spec: 50, sheen: 0.25, glass: 0.72); }

    /** Горка: насыпь с неровностями, срезанная снизу. */
    static Sdf Mound(double rx, double ry, double rz, double rough, int seed) {
        return S.Inter(S.Rough(S.Ell(0, 0, 0, rx, ry, rz), rough, 0.7, seed), S.Box(0, ry, 0, rx + 3, ry, rz + 3));
    }

    /** Горка зерна: насыпь и отдельные зёрна сверху (у солода-зелёнки — с ростками). */
    static void GrainPile(string folder, string name, Mtl grain, bool sprouts, int seed) {
        var s = new Scene { Elev = 34, Size = 28, Seed = seed };
        s.Add(Mound(8, 3.5, 6.5, 0.4, seed), Bumped(grain, (x, y, z) => 0.18 * (Scene.Noise3(x * 2.2, y * 2.2, z * 2.2, seed) - 0.5)));
        var rnd = new Random(seed);
        var sprout = M("#2a4a14", "#7ab048", "#c4ec8c", 0.2, 0.03);
        for (int i = 0; i < 30; i++) {
            double a = rnd.NextDouble() * Math.PI * 2, r = Math.Sqrt(rnd.NextDouble()) * 0.95;
            double x = Math.Cos(a) * r * 9, z = Math.Sin(a) * r * 7.5;
            double y = 3.5 * Math.Sqrt(Math.Max(0, 1 - r * r)) + 0.5;
            Sdf g = S.Ell(x, y, z, 1.7, 0.8, 0.85);
            g = S.Rot(S.Rot(g, 'y', rnd.Next(180), x, y, z), 'z', rnd.Next(-30, 30), x, y, z);
            s.Add(g, grain);
            if (sprouts && i % 4 == 0) s.Add(S.Tube(0.3, 0.2, x, y, z, x + 0.5, y + 2.0, z + 0.4, x + 1.6, y + 2.6, z + 0.6), sprout);
        }
        Save(s, folder, name);
    }

    /** Рыба боком, головой влево: тело, хвост, плавники, глаз; брюхо светлее. */
    static void Fish(Scene s, Mtl back, Mtl belly, Mtl fin, double len, double height, double thick) {
        var body = back.With();
        body.Swap = (x, y, z) => y < -height * 0.25 + 0.6 * Math.Sin(x * 0.3) ? belly : null;
        Sdf b = S.Blend(1.4, S.Ell(0, 0, 0, len, height, thick), S.Ell(len * 0.55, 0, 0, len * 0.5, height * 0.5, thick * 0.6));
        s.Add(b, body);
        s.Add(S.Plate(0, 0.3, 0.15, len * 0.85, 0, len * 1.4, height * 1.15, len * 1.22, 0, len * 1.4, -height * 1.05), fin);
        s.Add(S.Plate(0, 0.25, 0.15, -len * 0.25, height * 0.75, len * 0.05, height * 1.45, len * 0.4, height * 0.75), fin);
        s.Add(S.Plate(thick * 0.75, 0.2, 0.1, -len * 0.45, -height * 0.25, -len * 0.15, -height * 0.9, -len * 0.05, -height * 0.3), fin);
        s.Add(S.Sphere(-len * 0.72, height * 0.22, thick * 0.62, 0.8), Eye);
    }

    /** Окорочок: мясо и кость с мослами. */
    static void Drumstick(Scene s, Mtl meat) {
        s.Add(S.Rough(S.Blend(2, S.Ell(-1, 0, 0, 6.5, 5, 5), S.Ell(-5, 0.5, 0, 4, 3.6, 3.6), S.Ell(3.5, 0.6, 0, 3, 2.6, 2.6)), 0.25, 0.5, s.Seed), meat);
        s.Add(S.Cap(4.5, 0.7, 0, 10, 1.2, 0, 1.1), Bone);
        s.Add(S.Sphere(10.6, 2.0, 0.6, 1.25), Bone).Add(S.Sphere(10.8, 0.5, -0.5, 1.25), Bone);
    }

    /** Пирог: корж, начинка и решётка из теста. */
    static void Pie(string name, Mtl crust, Mtl filling, int seed) {
        var s = new Scene { Elev = 42, Size = 29, Seed = seed };
        s.Add(S.Sub(S.Cyl(0, 0, 0, 11, 2.2, 1.2), S.Cyl(0, 2.2, 0, 9.4, 1.4)), crust);
        s.Add(S.Rough(S.Cyl(0, 1.3, 0, 9.6, 0.9), 0.2, 0.8, seed), filling);
        for (int i = -1; i <= 1; i++) {
            s.Add(S.Inter(S.Box(i * 5, 2.0, 0, 0.75, 0.5, 10, 0.4), S.Cyl(0, 0, 0, 10, 4)), crust);
            s.Add(S.Inter(S.Box(0, 2.3, i * 5, 10, 0.5, 0.75, 0.4), S.Cyl(0, 0, 0, 10, 4)), crust);
        }
        Save(s, "food", name);
    }

    /** Блюдо на тарелке: тарелка, на ней что-то и зелень. */
    static void PlateUnder(Scene s) {
        s.Add(S.Lathe(0, 0, 0, 0.7, 0.5, 0, 6, 0.4, 8.5, 0.9, 11.5, 1.3, 12.5), Ceramic);
    }
    static void Sprigs(Scene s, int seed, params double[] xz) {
        var rnd = new Random(seed);
        for (int i = 0; i + 1 < xz.Length; i += 2) {
            double x = xz[i], z = xz[i + 1];
            for (int k = 0; k < 3; k++) {
                double a = rnd.Next(360);
                s.Add(S.Rot(S.Ell(x + Math.Cos(a) * 0.8, 3.3 + k * 0.3, z + Math.Sin(a) * 0.8, 1.4, 0.3, 0.7), 'y', a, x, 3.3, z), Leaf);
            }
        }
    }

    /** Ягоды горкой, с листиком; crown — тёмная звёздочка-чашечка на ягоде (черника). */
    static void Berries(string name, Mtl berry, double r, bool crown, int seed) {
        var s = new Scene { Elev = 40, Size = 28, Seed = seed };
        var rnd = new Random(seed);
        var pts = new List<double[]>();
        for (int i = 0; i < 40 && pts.Count < 11; i++) {
            double x = (rnd.NextDouble() * 2 - 1) * 8, z = (rnd.NextDouble() * 2 - 1) * 6;
            double y = r + Math.Max(0, 3.2 - (x * x / 30 + z * z / 18)) * 0.9;
            bool ok = true;
            foreach (var p in pts) if ((p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y) + (p[2] - z) * (p[2] - z) < r * r * 3.2) ok = false;
            if (ok) pts.Add(new[] { x, y, z, r * (0.85 + rnd.NextDouble() * 0.3) });
        }
        var dark = M("#0a0610", "#1a1418", "#3a3038", 0, 0);
        foreach (var p in pts) {
            s.Add(S.Sphere(p[0], p[1], p[2], p[3]), berry);
            if (crown) s.Add(S.Cyl(p[0], p[1] + p[3] - 0.1, p[2], p[3] * 0.35, 0.25, 0.1), dark);
        }
        s.Add(S.Rot(S.Squash(S.Ell(6, 6, -5, 5.5, 1, 2.6), 0.4), 'z', -20, 6, 6, -5), Leaf);
        s.Add(S.Rot(S.Squash(S.Ell(-6, 4, -6, 4.5, 1, 2.2), 0.4), 'y', 40, -6, 4, -6), Leaf);
        Save(s, "food", name);
    }

    /** Мешочек: тело, горловина, завязка. */
    static void Sack(Scene s, Mtl cloth, double cx, double cz, double size) {
        double k = size;
        s.Add(S.Rough(S.Blend(1.5, S.Ell(cx, 5 * k, cz, 6.5 * k, 5.2 * k, 6 * k), S.Cyl(cx, 10.5 * k, cz, 2.6 * k, 1.8 * k), S.Ell(cx, 12.8 * k, cz, 3.4 * k, 1.4 * k, 3.4 * k)), 0.35, 0.6, s.Seed), cloth);
        s.Add(S.Torus(cx, 10.4 * k, cz, 2.8 * k, 0.6 * k), Twine);
    }

    // ======================================================================== всё

    /** Только шкуры зверей — для быстрой проверки. */
    public static void Hides(string outRoot) { root = outRoot; AnimalHides_(); }

    public static void Run(string outRoot) {
        root = outRoot;
        Leather_();
        AnimalHides_();
        Materials_();
        Minerals_();
        Tools_();
        Food_();
        Meat_();
        Fish_();
        Plants_();
        Fruits_();
        Dishes_();
        Drinks_();
        Grains_();
        Pottery_();
        Effects_();
    }

    // ------------------------------------------------------------------------ шкуры и кожа
    static void Leather_() {
        Scene s;
        var flesh = M("#5a1a14", "#a8463a", "#dc8670", 0.25, 0.07);
        s = new Scene { Elev = 62, Yaw = 15, Size = 30, Seed = 11 };
        s.Add(HideShape(11), Spots(flesh, Fat, 1.3, 0.8, 12)); Save(s, "materials", "raw_hide");

        var salted = M("#3a1612", "#84382e", "#bc6c58", 0.1, 0.07);
        s = new Scene { Elev = 62, Yaw = -12, Size = 30, Seed = 12 };
        s.Add(HideShape(12), Spots(salted, White, 2.6, 0.64, 13)); Save(s, "materials", "salted_hide");

        var lime = M("#46443c", "#9c988a", "#dcd8c8", 0.05, 0.08);
        s = new Scene { Elev = 62, Yaw = 8, Size = 30, Seed = 13 };
        s.Add(HideShape(13), Spots(lime, M("#a8a49a", "#f0eee4", "#ffffff", 0, 0.03), 2.2, 0.7, 14)); Save(s, "materials", "limed_hide");

        var pelt = M("#6a543c", "#c4aa88", "#f2e2c4", 0.15, 0.05);
        s = new Scene { Elev = 62, Yaw = 22, Size = 30, Seed = 14 };
        s.Add(HideShape(14, 0.95), pelt); Save(s, "materials", "pelt");

        var wet = M("#1a0e06", "#4e2c14", "#8e5a30", 0.7, 0.05).With(spec: 30);
        s = new Scene { Elev = 62, Yaw = -18, Size = 30, Seed = 15 };
        s.Add(HideShape(15, 0.95), wet); Save(s, "materials", "wet_leather");

        var rawhide = M("#5a3a10", "#b4863c", "#eec680", 0.45, 0.06);
        s = new Scene { Elev = 62, Yaw = 30, Size = 28, Seed = 16 };
        s.Add(HideShape(16, 0.85), rawhide); Save(s, "materials", "rawhide");

        var fur = Bumped(M("#24180c", "#6a4c30", "#b89064", 0, 0.1).With(grainScale: 4),
            (x, y, z) => 0.22 * (Scene.Noise3(x * 3.5, y * 3.5, z * 1.2, 17) - 0.5));
        s = new Scene { Elev = 58, Yaw = -10, Size = 30, Seed = 17 };
        s.Add(S.Rough(HideShape(17), 0.4, 1.4, 18), fur); Save(s, "materials", "dressed_fur");

        // выделанная кожа: лист с загнутым углом
        s = new Scene { Elev = 55, Yaw = -18, Size = 29, Seed = 18 };
        s.Add(S.Wave(S.Rough(S.Box(0, 0, 0, 10, 0.5, 7.5, 0.45), 0.3, 0.5, 18), 0.9, 0.18, 19), Leather);
        s.Add(S.Rot(S.Slab(0, 0.35, 0.2, 4, -7.5, 10, -7.5, 10, -1.5), 'x', 0, 0, 0, 0), Leather);
        s.Add(S.Move(S.Rot(S.Slab(0, 0.3, 0.2, 6, -7.5, 10, -7.5, 10, -3.5), 'z', 25, 6, 0, -7.5), 0, 1.2, 0), Tan);
        Save(s, "materials", "tanned_leather");

        // мягкая кожа: свёрток и край листа
        s = new Scene { Elev = 40, Yaw = 25, Size = 29, Seed = 19 };
        s.Add(S.Rot(S.Sub(S.Cyl(0, 0, 0, 3.6, 8, 0.5), S.Cyl(0, 0, 0, 1.0, 9)), 'z', 90), Tan);
        s.Add(S.Wave(S.Box(0, -3.2, 5, 8, 0.4, 5, 0.35), 0.6, 0.2, 20), Tan);
        s.Add(S.Rot(S.Cyl(0, 0, 0, 1.2, 8.1), 'z', 90), M("#2a160a", "#5a3418", "#8a5a30", 0, 0.05));
        Save(s, "materials", "soft_leather");

        // варёная кожа: жёсткая выгнутая пластина с заклёпками
        s = new Scene { Elev = 50, Yaw = -15, Size = 28, Seed = 20 };
        var boiled = M("#1a0e06", "#4a2c14", "#7e5028", 0.35, 0.06);
        s.Add(S.Inter(S.Sub(S.Ell(0, -1, 0, 9, 4, 11), S.Ell(0, -1.8, 0, 8.4, 3.6, 10.4)), S.Box(0, 2, 0, 9, 3, 11)), boiled);
        foreach (var p in new[] { new[] { -6.0, -7.5 }, new[] { 6.0, -7.5 }, new[] { -6.0, 7.5 }, new[] { 6.0, 7.5 } }) {
            double hy = -1 + 4 * Math.Sqrt(Math.Max(0, 1 - p[0] * p[0] / 81 - p[1] * p[1] / 121));
            s.Add(S.Sphere(p[0], hy + 0.1, p[1], 0.9), Iron);
        }
        Save(s, "materials", "boiled_leather");

        // обрезки: три лоскута
        s = new Scene { Elev = 58, Size = 28, Seed = 21 };
        s.Add(S.Wave(S.Slab(0, 0.45, 0.25, -11, -6, -3, -8, -1, -2, -9, 0), 0.6, 0.3, 21), Leather);
        s.Add(S.Wave(S.Slab(0.6, 0.45, 0.25, 1, -7, 9, -5, 10, 1, 3, 0), 0.6, 0.3, 22), Tan);
        s.Add(S.Wave(S.Slab(0.3, 0.45, 0.25, -6, 2, 3, 3, 4, 8, -5, 7), 0.6, 0.3, 23), M("#3a200c", "#80502a", "#b47e4a", 0.15, 0.07));
        Save(s, "materials", "leather_scraps");

        // ремень: свёрнутая кольцом полоса, хвост и пряжка
        s = new Scene { Elev = 48, Yaw = 10, Size = 29, Seed = 22 };
        s.Add(S.Inter(S.Sub(S.Cyl(0, 0, 0, 7.5, 1.2, 0.3), S.Cyl(0, 0, 0, 5.6, 2)), S.Box(0, 0, 0, 9, 1.2, 9)), Leather);
        s.Add(S.Inter(S.Sub(S.Cyl(0, 1.0, 0, 5.4, 1.2, 0.3), S.Cyl(0, 1.0, 0, 3.6, 2)), S.Box(0, 1.0, 0, 9, 1.2, 9)), Leather);
        s.Add(S.Box(9, 0, 3, 4.5, 0.35, 1.0, 0.25), Leather);
        s.Add(S.Rot(S.Torus(13.5, 0, 3, 1.6, 0.4), 'x', 90, 13.5, 0, 3), Iron);
        Save(s, "materials", "leather_strap");

        // мездра: розовые обрезки с жиром
        s = new Scene { Elev = 40, Size = 28, Seed = 23 };
        s.Add(S.Rough(S.Blend(2.2, S.Ell(-4, 2, 2, 6, 3, 4.5), S.Ell(4, 2.2, 1, 5.5, 3.2, 5), S.Ell(0, 4, -3, 5, 3, 4)), 0.6, 0.6, 23),
            Spots(flesh, Fat, 1.4, 0.68, 24));
        Save(s, "materials", "flesh");

        // мездровый клей: миска с янтарным клеем и потёк
        s = new Scene { Elev = 38, Size = 28, Seed = 24 };
        Bowl(s, BowlWood); BowlFill(s, Honey.With(), 0);
        s.Add(S.Tube(1.0, 0.7, -9.6, 6.6, 6, -10.5, 4.5, 6.4, -10.3, 2.8, 6.2), Honey);
        Save(s, "materials", "hide_glue");
    }

    // ------------------------------------------------------------------------ материалы
    static void Materials_() {
        Scene s;
        // кора: две полосы-желобка, снаружи грубая, изнутри светлая
        var barkOut = Bumped(M("#1e140a", "#4e3622", "#7e5e40", 0, 0.12).With(grainScale: 2.5), (x, y, z) => 0.16 * (Scene.Noise3(x * 0.4, y * 2.5, z * 2.5, 25) - 0.5));
        var barkIn = M("#5a3a1e", "#a87a4a", "#dcb07a", 0, 0.06);
        // желобок вдоль X: нижняя половина трубы, вогнутостью вверх; изнутри — светлая изнанка
        s = new Scene { Elev = 50, Yaw = 30, Size = 30, Seed = 25 };
        for (int i = 0; i < 2; i++) {
            double dz = i == 0 ? -3.2 : 3.2, dy = i * 1.4, len = i == 0 ? 10 : 8.5;
            Sdf tube = (x, y, z) => {
                double r = Math.Sqrt(y * y + z * z);
                double ring = Math.Max(Math.Max(r - 3.6, 2.7 - r), Math.Abs(x) - len);
                return Math.Max(ring, y - 0.6);
            };
            var m = barkOut.With();
            m.Swap = (x, y, z) => Math.Sqrt((y - dy) * (y - dy) + (z - dz) * (z - dz)) < 3.0 ? barkIn : null;
            s.Add(S.Move(S.Rough(tube, 0.2, 1.1, 25 + i), 0, dy, dz), m);
        }
        Save(s, "materials", "bark");

        // воск: брусок-лепёшка
        s = new Scene { Elev = 36, Yaw = 20, Size = 27, Seed = 26 };
        s.Add(S.Cyl(0, 0, 0, 8, 2.4, 1.2), Wax);
        s.Add(S.Cyl(1.5, 4.4, -1, 5, 1.8, 1.0), Wax);
        Save(s, "materials", "beeswax");

        // дрова: три полена, видны торцы с кольцами
        var log = Bumped(M("#1e140a", "#4e3622", "#7e5e40", 0, 0.12).With(grainScale: 2.5), (x, y, z) => 0.14 * (Scene.Noise3(x * 2, y * 0.4, z * 2, 27) - 0.5));
        s = new Scene { Elev = 28, Yaw = -35, Size = 30, Seed = 27 };
        double[][] logs = { new[] { -3.3, 0.0, 0 }, new[] { 3.3, 0.0, 1 }, new[] { 0.0, 5.6, 2 } };
        foreach (var l in logs) {
            double lx = l[0], ly = l[1];
            var cut = M("#6a4424", "#c4925c", "#eec890", 0, 0.05);
            var cutRings = Bumped(cut, (x, y, z) => 0.1 * Math.Sin(Math.Sqrt((x - lx) * (x - lx) + (y - ly) * (y - ly)) * 3.2));
            var m = log.With();
            m.Swap = (x, y, z) => Math.Abs(z) > 9.4 ? cutRings : null;
            s.Add(S.Rough(S.Rot(S.Cyl(lx, ly, 0, 3.1, 9.8, 0.4), 'x', 90, lx, ly, 0), 0.2, 1.0, 27 + (int)l[2]), m);
        }
        Save(s, "materials", "firewood");
    }

    // ------------------------------------------------------------------------ минералы
    static void Minerals_() {
        Scene s;
        var salt = Spots(M("#56646e", "#b4c0ca", "#f4f8fc", 0.4, 0.08).With(grainScale: 3), M("#c8d4dc", "#ffffff", "#ffffff", 0.8, 0), 3.2, 0.72, 31);
        s = new Scene { Elev = 24, Size = 28, Seed = 31 };
        s.Add(Mound(10, 8, 8.5, 0.6, 31), salt); Save(s, "minerals", "salt");

        var ash = Spots(M("#262422", "#66625e", "#a8a49e", 0, 0.12).With(grainScale: 2.6), M("#0e0c0a", "#1e1c1a", "#34302c", 0, 0), 2.4, 0.74, 32);
        s = new Scene { Elev = 24, Size = 28, Seed = 32 };
        s.Add(Mound(10, 7.5, 8.5, 0.9, 32), ash); Save(s, "minerals", "ash");

        // квасцы: кристаллы-призмы с гранёными верхушками
        var alum = M("#6a5c80", "#c8bcdc", "#fbf6ff", 0.7, 0.02).With(spec: 34);
        s = new Scene { Elev = 22, Yaw = 30, Size = 28, Seed = 33 };
        double[][] cr = { new[] { 0.0, 0, -1, 2.4, 9, 0 }, new[] { -5.5, 0, 1, 1.9, 6, -24 }, new[] { 5.5, 0, 0, 2.1, 7, 22 }, new[] { 1.0, 0, 4.5, 1.5, 4, 10 } };
        foreach (var c in cr) s.Add(Crystal(c[0], c[1], c[2], c[3], c[4], c[5]), alum);
        Save(s, "minerals", "alum");
    }

    /** Кристалл: четырёхгранная призма с пирамидкой наверху, наклонён на tilt. */
    static Sdf Crystal(double cx, double cy, double cz, double r, double h, double tilt) {
        Sdf c = (x, y, z) => {
            double ax = Math.Abs(x), az = Math.Abs(z);
            double prism = (ax + az - r) * 0.707;
            double tip = ((ax + az) + (y - h) * 1.2 - r) * 0.6;
            return Math.Max(Math.Max(prism, -y), tip);
        };
        return S.Move(S.Rot(S.Rot(c, 'y', 45 + tilt * 2), 'z', tilt), cx, cy, cz);
    }

    // ------------------------------------------------------------------------ инструменты: вдоль X, рукоять слева
    static void Tools_() {
        Scene s;
        s = new Scene { Elev = 12, Roll = -45, Size = 31, Seed = 41 };
        s.Add(S.Cap(-12, 0, 0, -3.6, 0, 0, 1.5, 1.75), Wood);
        s.Add(S.Box(-3, 0, 0, 0.6, 2.0, 1.25, 0.3), Iron);
        s.Add(S.Plate(0, 0.38, 0.15, -2.6, 1.6, 9, 1.8, 13.5, 0.4, 9, -1.4, -2.6, -1.7), Steel);
        Save(s, "tools", "knife");

        // мездряк: длинный дугой изогнутый клинок, по рукояти на концах
        s = new Scene { Elev = 14, Roll = 10, Size = 31, Seed = 42 };
        var arc = new List<double>();
        for (int i = 0; i <= 10; i++) { double t = -1 + i * 0.2; arc.Add(t * 10); arc.Add(-Math.Abs(t) * Math.Abs(t) * 4 + 2.4); }
        for (int i = 10; i >= 0; i--) { double t = -1 + i * 0.2; arc.Add(t * 10); arc.Add(-Math.Abs(t) * Math.Abs(t) * 4); }
        s.Add(S.Plate(0, 0.35, 0.12, arc.ToArray()), Steel);
        s.Add(S.Cap(-10.5, -3.6, 0, -11.5, -9.5, 0, 1.4, 1.6), Wood).Add(S.Cap(10.5, -3.6, 0, 11.5, -9.5, 0, 1.4, 1.6), Wood);
        Save(s, "tools", "fleshing_knife");

        // струг: прямое широкое лезвие, рукояти вверх
        s = new Scene { Elev = 18, Roll = 8, Size = 31, Seed = 43 };
        s.Add(S.Plate(0, 0.45, 0.2, -10, 1.6, 10, 1.6, 10, -1.6, -10, -0.8), Iron);
        s.Add(S.Plate(0.2, 0.3, 0.1, -10, -0.8, 10, -1.6, 10, -2.4, -10, -1.4), Steel);
        s.Add(S.Cap(-10.5, 1.0, 0, -12, 9, 0, 1.4, 1.6), Wood).Add(S.Cap(10.5, 1.0, 0, 12, 9, 0, 1.4, 1.6), Wood);
        Save(s, "tools", "scraper");

        // киянка: рукоять и сыромятная головка поперёк
        var rawhideHead = Bumped(M("#5a3a10", "#b4863c", "#eec680", 0.3, 0.06), (x, y, z) => Math.Abs(Math.Sin(y * 1.6)) < 0.25 ? -0.15 : 0);
        s = new Scene { Elev = 18, Yaw = 25, Roll = -45, Size = 30, Seed = 44 };
        s.Add(S.Cap(-12, 0, 0, 7, 0, 0, 1.2, 1.35), Wood);
        s.Add(S.Cyl(9, 0, 0, 3.4, 5.2, 0.8), rawhideHead);
        Save(s, "tools", "leather_mallet");

        // половник: длинная ручка и черпак
        s = new Scene { Elev = 40, Yaw = -20, Roll = 30, Size = 30, Seed = 45 };
        s.Add(S.Tube(1.0, 1.25, 14, 4.0, 0, 6, 2.5, 0, 3.6, 1.6, 0), Wood);
        s.Add(S.Lathe(-2, -3.2, 0, 0.8, 0.8, 0, 2.4, 1, 3.6, 2.6, 4.8, 4.2, 5.4), Wood);
        Save(s, "tools", "ladle");

        // дымарь: жестяная банка с носиком, сбоку меха
        s = new Scene { Elev = 20, Yaw = 30, Size = 30, Seed = 46 };
        s.Add(S.Cyl(-2, 0, 0, 4.6, 6.5, 0.6), Iron);
        s.Add(S.Lathe(-2, 6.5, 0, 0, 0, 0, 4.6, 2.6, 2.2, 4.0, 1.0, 5.2, 0.9), Steel);
        s.Add(S.Torus(-2, 2, 0, 4.7, 0.3), Rust).Add(S.Torus(-2, -3, 0, 4.7, 0.3), Rust);
        s.Add(S.Box(6.0, -1, 0, 0.5, 5.5, 3.2, 0.3), Wood).Add(S.Box(10.5, -2, 0, 0.5, 5, 3.0, 0.3), Wood);
        s.Add(S.Rough(S.Ell(8.2, -1.5, 0, 2.6, 4.8, 3.0), 0.3, 0.8, 46), Leather);
        s.Add(S.Cap(10.8, -6.5, 0, 12, -10, 0, 0.9), Wood);
        Save(s, "tools", "bee_smoker");

        // серп: короткая рукоять и полумесяц клинка
        s = new Scene { Elev = 12, Roll = -45, Size = 31, Seed = 47 };
        s.Add(S.Cap(-12, 0, 0, -4, 0, 0, 1.4, 1.6), Wood);
        s.Add(S.Box(-3.2, 0, 0, 0.8, 1.6, 1.1, 0.3), Iron);
        var cres = new List<double>();
        for (int i = 0; i <= 12; i++) { double a = Math.PI * (1.05 - i * 0.085); cres.Add(4 + Math.Cos(a) * 7.5); cres.Add(Math.Sin(a) * 9 - 0.2); }
        for (int i = 12; i >= 0; i--) { double a = Math.PI * (1.05 - i * 0.085); cres.Add(4.6 + Math.Cos(a) * 5.6); cres.Add(Math.Sin(a) * 6.6 + 0.6); }
        s.Add(S.Plate(0, 0.32, 0.12, cres.ToArray()), Steel);
        Save(s, "tools", "sickle");

        // коса: длинное косовище, ручка и длинное лезвие сверху
        s = new Scene { Elev = 12, Size = 31, Seed = 48 };
        s.Add(S.Cap(-9, -13, 0, 6, 11, 0, 1.15, 1.3), Wood);
        s.Add(S.Cap(-3, -2.5, 0, 0.5, 0.5, 1.2, 0.9), Wood);
        s.Add(S.Box(6, 11, 0, 1.4, 1.4, 1.4, 0.3), Iron);
        var blade = new List<double>();
        for (int i = 0; i <= 12; i++) { double t = i / 12.0; blade.Add(6 - t * 18); blade.Add(12 + Math.Sin(t * Math.PI * 0.9) * 2.0 - t * t * 6); }
        for (int i = 12; i >= 0; i--) { double t = i / 12.0; blade.Add(6 - t * 18); blade.Add(12 + Math.Sin(t * Math.PI * 0.9) * 2.0 - t * t * 6 - 3.2 * (1 - t) - 0.4); }
        s.Add(S.Plate(0, 0.35, 0.12, blade.ToArray()), Steel);
        Save(s, "tools", "scythe");

        // набор ключника: кожаный свёрток, из него — отмычки
        s = new Scene { Elev = 38, Yaw = -15, Size = 29, Seed = 49 };
        s.Add(S.Rough(S.Box(0, 0, 0, 9, 1.4, 5.5, 1.2), 0.2, 0.6, 49), Leather);
        s.Add(S.Box(0, 0, 0, 9.2, 1.5, 0.7, 0.3), Tan);
        for (int i = 0; i < 4; i++) {
            double x = -5 + i * 3.4;
            s.Add(S.Tube(0.4, 0.35, x, 1.0, -4, x + 0.5, 2.0, -9.5, x + 1.6, 2.0, -10.5), Steel);
        }
        Save(s, "tools", "locksmith_kit");
    }

    // ------------------------------------------------------------------------ еда: тесто, выпечка, каши, похлёбки
    static void Food_() {
        Scene s;
        // мёд: глиняный горшок с медовой шапкой и потёком
        s = new Scene { Elev = 28, Size = 29, Seed = 57 };
        s.Add(S.Lathe(0, 0, 0, 0.9, 1.0, 0, 5.4, 1, 6.8, 4, 8.6, 7, 8.4, 9.5, 6.4, 10.5, 6.0, 11.4, 6.6), Terracotta);
        s.Add(S.Inter(S.Ell(0, 10.3, 0, 5.8, 1.6, 5.8), S.Box(0, 12, 0, 8, 2, 8)), Honey);
        s.Add(S.Tube(1.1, 0.8, -5.5, 11.2, 3, -7.4, 8.5, 3.6, -8.2, 5.5, 3.6), Honey);
        Save(s, "food", "honey");

        var flour = M("#a49a88", "#f0ece2", "#ffffff", 0, 0.03);
        var dough = Spots(M("#7a6a4c", "#d8c69a", "#f8eed2", 0.1, 0.04), flour, 2.2, 0.73, 58);
        s = new Scene { Elev = 32, Size = 27, Seed = 58 };
        s.Add(S.Rough(S.Blend(3, S.Ell(0, 2.6, 0, 9, 4, 7.5), S.Ell(-2, 5.2, -1, 5.5, 3.2, 4.5)), 0.35, 0.4, 58), dough);
        Save(s, "food", "dough");
        var ryeDough = Spots(M("#4a3624", "#8c6c4a", "#bc9c76", 0.1, 0.05), flour, 2.2, 0.75, 59);
        s = new Scene { Elev = 32, Size = 27, Seed = 59 };
        s.Add(S.Rough(S.Blend(3, S.Ell(0, 2.6, 0, 9, 4, 7.5), S.Ell(2, 5.2, 0, 5.5, 3.2, 4.5)), 0.35, 0.4, 59), ryeDough);
        Save(s, "food", "rye_dough");

        // каши: деревянная миска, горка, крупа
        var butter = M("#8a6408", "#e8be34", "#fff09a", 0.6, 0.02);
        s = new Scene { Elev = 38, Size = 29, Seed = 51 };
        Bowl(s, BowlWood); BowlFill(s, Spots(M("#8a6e40", "#dcc286", "#f8ecc6", 0.15, 0.06), M("#a08a5c", "#fff4d0", "#ffffff", 0, 0), 2.6, 0.74, 51), 2.2);
        s.Add(S.Box(1, 7.6, 0, 1.6, 0.9, 1.6, 0.3), butter);
        Save(s, "food", "porridge");
        Porridge("wheat_porridge", M("#8a6a2a", "#dcbc6c", "#f8e4a6", 0.15, 0.06), "#fff0c0", 220);
        Porridge("rye_porridge", M("#4a3622", "#8c6c4a", "#bc9c76", 0.15, 0.06), "#d8c0a0", 221);
        Porridge("barley_porridge", M("#6a5428", "#b8965a", "#e2c88e", 0.15, 0.06), "#f4e2b8", 222);
        Porridge("oat_porridge", M("#86785c", "#ded2b4", "#fffaec", 0.15, 0.06), "#b4a07a", 223);

        // похлёбки
        var carrot = M("#7a2a06", "#e0701c", "#ffb05a", 0.3);
        var greens = M("#1a3a10", "#4a8a2c", "#90c860", 0.2);
        var fishMeat = M("#9a8c7c", "#ece2d4", "#ffffff", 0.2, 0.04);
        s = new Scene { Elev = 38, Size = 29, Seed = 52 };
        Bowl(s, BowlWood); BowlFill(s, M("#6a4e1e", "#b8944c", "#e8cc88", 0.7, 0.03).With(spec: 30), 0);
        Chunks(s, fishMeat, 2.0, -4, 2, 3.5, -2); Chunks(s, carrot, 1.2, 0, 4, -2, -4.5); Chunks(s, greens, 1.0, 5, 2.5, -6, -1);
        Save(s, "food", "fish_soup");

        s = new Scene { Elev = 38, Size = 29, Seed = 73 };
        Bowl(s, BowlWood); BowlFill(s, M("#5a1410", "#a43828", "#dc6c54", 0.7, 0.03).With(spec: 30), 0);
        Chunks(s, carrot, 1.6, -3, 2, 4, -1, 1, 4.5); Chunks(s, greens, 1.3, -5, -2.5, 3, 3.8); Chunks(s, M("#7a6a40", "#e6d8a0", "#fff6d0", 0.2), 1.6, -0.5, -3.5);
        Save(s, "food", "vegetable_soup");

        var mushroom = Bumped(M("#6a5034", "#d8c4a0", "#f8ecd4", 0.1), (x, y, z) => 0.1 * Math.Sin(x * 3));
        s = new Scene { Elev = 38, Size = 29, Seed = 74 };
        Bowl(s, BowlWood); BowlFill(s, M("#4a3018", "#8a6038", "#c09060", 0.6, 0.03).With(spec: 30), 0);
        Chunks(s, mushroom, 2.0, -3.5, 1.5, 3.5, -1.5, 0.5, 4.5); Chunks(s, M("#2a1a0c", "#5a3a20", "#8a6040", 0.1), 1.4, -4, -4, 5, 3);
        Save(s, "food", "mushroom_soup");

        var stewMeat = M("#2a0e06", "#6a2a16", "#a85a36", 0.2, 0.08);
        s = new Scene { Elev = 38, Size = 29, Seed = 75 };
        Bowl(s, BowlWood); BowlFill(s, M("#3e2410", "#7a4a24", "#b07a44", 0.6, 0.03).With(spec: 30), 0);
        Chunks(s, stewMeat, 2.2, -3.5, 1.5, 3.5, -1, 0, -4.5); Chunks(s, carrot, 1.3, 1, 4.5, -5, -2.5); Chunks(s, greens, 1.0, 5, 3);
        Save(s, "food", "meat_stew");

        var potato = M("#8a7030", "#e8d488", "#fff4c8", 0.15, 0.05);
        s = new Scene { Elev = 38, Size = 29, Seed = 219 };
        Bowl(s, BowlWood); BowlFill(s, M("#7a6428", "#d0b462", "#f2dc98", 0.6, 0.03).With(spec: 30), 0);
        Chunks(s, potato, 2.0, -3.5, 1.5, 3.5, -1.5, 0.5, 4.5, -1, -4); Chunks(s, greens, 1.0, 5, 3, -5.5, -1.5);
        Save(s, "food", "potato_stew");

        // пироги
        var crust = Bumped(M("#5a2e0c", "#b0702e", "#e8b468", 0.15, 0.05), (x, y, z) => 0.05 * Math.Sin(x * 2 + z * 1.4));
        var rawCrust = M("#7e6e50", "#d8c69a", "#f6ead0", 0.05, 0.04);
        var meatFill = M("#3a120a", "#7a2e1c", "#b0583a", 0.3, 0.1);
        var berryFill = M("#2a0818", "#6a1834", "#a83858", 0.5, 0.06);
        var mushFill = M("#2a1a0c", "#664a32", "#9e7a58", 0.2, 0.1);
        Pie("raw_pie", rawCrust, meatFill, 61);
        Pie("meat_pie", crust, meatFill, 62);
        Pie("berry_pie", crust, berryFill, 63);
        Pie("mushroom_pie", crust, mushFill, 53);

        // булочки
        s = new Scene { Elev = 30, Size = 26, Seed = 55 };
        s.Add(S.Inter(S.Ell(0, 0, 0, 9, 6.5, 8.5), S.Box(0, 4, 0, 10, 4, 10)), M("#7a6a4c", "#dccaa0", "#f8eed2", 0.1, 0.04));
        Save(s, "food", "raw_bun");
        var bun = M("#5a280a", "#b86a26", "#eeae5a", 0.35, 0.04);
        s = new Scene { Elev = 32, Size = 26, Seed = 56 };
        s.Add(S.Inter(S.Ell(0, 0, 0, 9, 6.5, 8.5), S.Box(0, 4, 0, 10, 4, 10)), bun);
        s.Add(S.Rough(S.Inter(S.Ell(0, 5.6, 0, 5.5, 1.6, 5), S.Box(0, 7, 0, 7, 2, 7)), 0.4, 0.8, 56), M("#b4aa9a", "#f2ece0", "#ffffff", 0.6, 0.02));
        s.Add(S.Sphere(-1.5, 7.2, 0.5, 1.0), M("#4a0610", "#b01c30", "#f06070", 0.8, 0.02)).Add(S.Sphere(1.6, 7.0, -0.8, 1.0), M("#4a0610", "#b01c30", "#f06070", 0.8, 0.02));
        Save(s, "food", "sweet_bun");

        // горелое: обугленный ком с тлеющими искрами
        var ember = M("#6a1a02", "#e0601a", "#ffc060", 0.5, 0);
        var charred = Spots(M("#060504", "#1e1a18", "#463c34", 0.2, 0.12), ember, 1.8, 0.8, 54);
        s = new Scene { Elev = 32, Size = 27, Seed = 54 };
        s.Add(S.Rough(S.Blend(2, S.Ell(-1, 2.5, 0, 8, 3.5, 6.5), S.Ell(3, 4.5, -1, 4.5, 3, 4)), 0.9, 0.6, 54), charred);
        Save(s, "food", "burnt_food");

        // мука: открытый мешок с подвёрнутым краем, полный муки; рядом просыпано
        s = new Scene { Elev = 34, Yaw = 15, Size = 30, Seed = 64 };
        var flourSoft = Bumped(M("#9c9282", "#ece6da", "#ffffff", 0, 0.05).With(grainScale: 2.5),
            (x, y, z) => 0.08 * (Scene.Noise3(x * 1.4, y * 1.4, z * 1.4, 64) - 0.5));
        var folds = Bumped(Burlap, (x, y, z) => 0.12 * Math.Sin(Math.Atan2(z, x + 2) * 7 + y * 0.15) + 0.06 * Math.Sin(x * 4) * Math.Sin(y * 4));
        s.Add(S.Rough(S.Lathe(-2, 0, 0, 0.9, 1.2, 0, 5.6, 1, 7.0, 4, 7.8, 8, 7.4, 11, 6.6, 12, 6.8), 0.35, 0.6, 64), folds);
        s.Add(S.Rough(S.Torus(-2, 12.2, 0, 7.0, 1.2), 0.3, 0.9, 65), folds);
        s.Add(S.Rough(S.Inter(S.Ell(-2, 10.6, 0, 6.4, 4.2, 6.4), S.Box(-2, 13, 0, 7, 2.4, 7)), 0.25, 0.9, 66), flourSoft);
        s.Add(S.Move(Mound(6, 3.2, 5, 0.35, 67), 6.5, 0, 6.5), flourSoft);
        Save(s, "food", "wheat_flour");

        // медовые соты: пластина воска, ячейки с мёдом
        var comb = M("#8a6010", "#e8b440", "#fff0a0", 0.3, 0.04);
        comb.Swap = (x, y, z) => CellDist(x, z, 3.4) < 0.34 && y > 0.6 ? M("#3a1a02", "#9a560c", "#e09a30", 0.9, 0.02) : null;
        comb.Bump = (x, y, z) => CellDist(x, z, 3.4) < 0.34 && y > 0.6 ? -0.15 : 0.08;
        s = new Scene { Elev = 52, Yaw = -14, Size = 29, Seed = 76 };
        s.Add(S.Rough(S.Box(0, 0, 0, 10, 1.4, 7.5, 1.0), 0.3, 0.6, 76), comb);
        Save(s, "food", "honeycomb_full");
    }

    /** Расстояние до центра ближайшей ячейки сот (ряды со сдвигом), в долях шага: 0 — центр, ~0,58 — стенка. */
    static double CellDist(double x, double z, double step) {
        double rowH = step * 0.866, best = 1e9;
        int row = (int)Math.Floor(z / rowH);
        for (int r = row - 1; r <= row + 1; r++) {
            double off = (r & 1) == 0 ? 0 : step / 2;
            int col = (int)Math.Floor((x - off) / step);
            for (int c = col - 1; c <= col + 1; c++) {
                double px = c * step + off, pz = r * rowH;
                best = Math.Min(best, Math.Sqrt((x - px) * (x - px) + (z - pz) * (z - pz)));
            }
        }
        return best / step;
    }

    static void Porridge(string name, Mtl grain, string speck, int seed) {
        var s = new Scene { Elev = 38, Size = 29, Seed = seed };
        double[] c = Mtl.H(speck);
        Bowl(s, BowlWood);
        BowlFill(s, Spots(grain, M(Hex(Mtl.Mul(c, 0.6)), speck, "#ffffff", 0.2, 0), 2.6, 0.7, seed), 2.2);
        Save(s, "food", name);
    }
    static string Hex(double[] c) { return "#" + ((int)(c[0] * 255)).ToString("x2") + ((int)(c[1] * 255)).ToString("x2") + ((int)(c[2] * 255)).ToString("x2"); }

    // ------------------------------------------------------------------------ мясо
    static void Meat_() {
        Scene s;
        var venison = Veins(M("#3a0a08", "#86221a", "#c45444", 0.3, 0.07), Fat, 0.7, 0.03, 201);
        s = new Scene { Elev = 42, Yaw = -20, Size = 28, Seed = 201 };
        s.Add(S.Rough(S.Squash(S.Blend(2, S.Ell(0, 0, 0, 10, 3, 7), S.Ell(5, 0, 2, 5, 3, 5)), 0.9), 0.4, 0.5, 201), venison);
        s.Add(S.Rough(S.Squash(S.Cap(-9, 0.4, -5, 6, 0.4, -6.8, 2.0, 1.6), 0.9), 0.3, 0.6, 202), Fat);
        Save(s, "food", "venison");

        var seared = Bumped(M("#1e0804", "#5e2810", "#a0582c", 0.3, 0.1), (x, y, z) => Math.Abs(Math.Sin((x + z) * 0.9)) < 0.2 ? -0.3 : 0);
        s = new Scene { Elev = 42, Yaw = -20, Size = 28, Seed = 203 };
        s.Add(S.Rough(S.Squash(S.Blend(2, S.Ell(0, 0, 0, 10, 3, 7), S.Ell(5, 0, 2, 5, 3, 5)), 0.9), 0.4, 0.5, 203), seared);
        Save(s, "food", "cooked_venison");

        var salted = Spots(Veins(M("#3a0a08", "#8e2a20", "#c86050", 0.2, 0.07), Fat, 0.7, 0.03, 65), White, 2.8, 0.7, 66);
        s = new Scene { Elev = 40, Yaw = 18, Size = 28, Seed = 65 };
        s.Add(S.Rough(S.Box(0, 0, 0, 10, 2.6, 6.5, 1.6), 0.4, 0.5, 65), salted);
        s.Add(S.Rough(S.Box(0, 0, -6.6, 10, 2.4, 1.0, 0.8), 0.3, 0.6, 66), Spots(Fat, White, 2.8, 0.7, 67));
        Save(s, "food", "salted_meat");

        var smoked = M("#1e0a04", "#5a2410", "#9a5a2e", 0.45, 0.08).With(spec: 20);
        s = new Scene { Elev = 25, Size = 29, Seed = 66 };
        s.Add(S.Rough(S.Blend(2.5, S.Ell(-2, 0, 0, 7.5, 6.5, 6), S.Ell(3, 1, 0, 4, 3.6, 3.6)), 0.3, 0.5, 66), smoked);
        s.Add(S.Cap(5, 1.5, 0, 11, 3, 0, 1.3), Bone);
        s.Add(S.Sphere(11.6, 4.0, 0.6, 1.4), Bone).Add(S.Sphere(11.8, 2.4, -0.6, 1.4), Bone);
        s.Add(S.Torus(-2, 0, 0, 6.2, 0.35), Twine);
        Save(s, "food", "smoked_meat");

        var rawGoose = M("#6a3a2e", "#d49a86", "#f6d4c2", 0.3, 0.05);
        s = new Scene { Elev = 25, Roll = 25, Size = 29, Seed = 202 };
        Drumstick(s, Spots(rawGoose, Fat, 1.5, 0.78, 202)); Save(s, "food", "goose");
        var roastBird = M("#3e1806", "#a0561a", "#e8a24c", 0.5, 0.06).With(spec: 22);
        s = new Scene { Elev = 25, Roll = 25, Size = 29, Seed = 204 };
        Drumstick(s, roastBird); Save(s, "food", "cooked_goose");
    }

    // ------------------------------------------------------------------------ рыба
    static void Fish_() {
        Scene s;
        var silverBack = M("#1a2430", "#4a6276", "#a4bccc", 0.7, 0.04).With(spec: 30);
        var silverBelly = M("#5a6670", "#c8d4dc", "#ffffff", 0.7, 0.03).With(spec: 30);
        var greyFin = M("#2a3036", "#6a7680", "#b0bcc4", 0.2, 0.03);
        s = new Scene { Elev = 10, Roll = 20, Size = 31, Seed = 205 };
        Fish(s, silverBack, silverBelly, greyFin, 11, 3.6, 2.2); Save(s, "food", "herring");

        var bronze = M("#2e200a", "#7a5e28", "#c8a65c", 0.6, 0.05).With(spec: 26);
        var bronzeBelly = M("#5a4a28", "#c4ac70", "#f0e0a8", 0.5, 0.04);
        s = new Scene { Elev = 10, Roll = 15, Size = 30, Seed = 206 };
        Fish(s, bronze, bronzeBelly, M("#2a2010", "#6a5630", "#a89060", 0.2), 10, 6, 2.0); Save(s, "food", "bream");

        var perch = Bumped(M("#1a2808", "#5a7a24", "#a8c060", 0.5, 0.04), (x, y, z) => Math.Sin(x * 1.1) > 0.55 && y > -1.5 ? -0.3 : 0);
        s = new Scene { Elev = 10, Roll = 20, Size = 31, Seed = 207 };
        Fish(s, perch, M("#6a6a40", "#d8d8a0", "#fffff0", 0.4), M("#4a1006", "#c03a1c", "#f08050", 0.2), 11, 4.4, 2.2); Save(s, "food", "perch");

        var fried = M("#3a1a04", "#9a5a1c", "#e0a04c", 0.4, 0.08).With(spec: 22);
        s = new Scene { Elev = 10, Roll = 20, Size = 31, Seed = 208 };
        Fish(s, fried, M("#5a3410", "#c8883c", "#f4c47a", 0.4), M("#2a1004", "#7a4012", "#b8783a", 0.2), 11, 3.6, 2.2); Save(s, "food", "cooked_herring");

        var crisp = Bumped(fried, (x, y, z) => 0.18 * (Scene.Noise3(x * 1.8, y * 1.8, z * 1.8, 209) - 0.5));
        s = new Scene { Elev = 10, Roll = 15, Size = 30, Seed = 209 };
        Fish(s, crisp, crisp, M("#2a1004", "#7a4012", "#b8783a", 0.2), 10, 5, 2.2); Save(s, "food", "fried_fish");

        var dried = Spots(M("#24140a", "#6e4624", "#a87c4c", 0.1, 0.1), White, 3, 0.76, 210);
        s = new Scene { Elev = 10, Roll = 25, Size = 31, Seed = 210 };
        Fish(s, dried, dried, M("#1e1008", "#56381c", "#8e6a44", 0), 11, 3.0, 1.2); Save(s, "food", "dried_fish");

        var salt = Spots(silverBack, White, 2.6, 0.66, 67);
        s = new Scene { Elev = 10, Roll = 20, Size = 31, Seed = 67 };
        Fish(s, salt, Spots(silverBelly, White, 2.6, 0.66, 68), greyFin, 11, 3.8, 2.2); Save(s, "food", "salted_fish");

        var smokedFish = M("#2a1606", "#8a5a20", "#d8a252", 0.5, 0.06);
        s = new Scene { Elev = 10, Roll = 20, Size = 31, Seed = 68 };
        Fish(s, smokedFish, M("#5a3a14", "#c89a52", "#f4d090", 0.4), M("#2a1606", "#6a4418", "#a87a40", 0.2), 11, 3.8, 2.2); Save(s, "food", "smoked_fish");
    }

    // ------------------------------------------------------------------------ овощи, травы, зерно в колосьях
    static void Plants_() {
        Scene s;
        // лук: луковица с прожилками, сухое пёрышко, корешки
        var onion = Bumped(M("#4a2808", "#b4742c", "#eebc70", 0.35, 0.05), (x, y, z) => 0.1 * Math.Sin(Math.Atan2(z, x) * 9));
        s = new Scene { Elev = 18, Size = 28, Seed = 211 };
        s.Add(S.Lathe(0, 0, 0, 0, 0, 0, 1.2, 0.8, 3.5, 2.5, 6.4, 5, 7.0, 7.5, 5.4, 9.5, 2.2, 11.2, 0.7, 12.5, 0.3), onion);
        s.Add(S.Tube(0.5, 0.3, 0, 12, 0, 0.6, 14, 0.2, 1.8, 15.5, 0.4), M("#4a3418", "#9a7a44", "#d0b07a", 0));
        for (int i = 0; i < 5; i++) s.Add(S.Cap(0, 0.6, 0, -1.5 + i * 0.75, -1.2, (i % 2) * 0.6, 0.18), M("#6a5a40", "#c8b48a", "#ecdcb4", 0));
        Save(s, "food", "onion");

        // репа: белая снизу, фиолетовая сверху, ботва
        var white = M("#b0a6a8", "#ece6e6", "#ffffff", 0.3, 0.04);
        var turnip = M("#4a1438", "#a8388a", "#e078c0", 0.3, 0.04);
        var t = white.With(); t.Swap = (x, y, z) => y > 6.4 + Scene.Noise3(x, 0, z, 212) * 1.2 ? turnip : null;
        s = new Scene { Elev = 18, Size = 29, Seed = 212 };
        s.Add(S.Lathe(0, 0, 0, 0, 0, 0, 0.3, 1.5, 2, 3.5, 5.2, 6.0, 6.6, 8.2, 5.8, 9.4, 3.6, 9.8, 1.2), t);
        s.Add(S.Cap(0, 0.4, 0, 0.3, -3.5, 0.2, 0.5, 0.15), white);
        s.Add(S.Rot(S.Squash(S.Ell(-3, 13.5, 0, 2.2, 5, 1.2), 0.9), 'z', 25, -1, 10, 0), Leaf);
        s.Add(S.Rot(S.Squash(S.Ell(3, 14, 0.5, 2.4, 5.5, 1.2), 0.9), 'z', -20, 1, 10, 0), Leaf);
        s.Add(S.Rot(S.Ell(0, 14.5, -1, 2, 5, 1), 'x', 25, 0, 10, 0), Leaf);
        Save(s, "food", "turnip");

        // фасоль: горка бобов, красные и белые
        var bean = M("#3a1006", "#8a3418", "#c8744c", 0.6, 0.03);
        var beanW = M("#7a7064", "#e6dccc", "#ffffff", 0.6, 0.03);
        s = new Scene { Elev = 40, Size = 28, Seed = 213 };
        var rnd = new Random(213);
        for (int i = 0; i < 24; i++) {
            double a = rnd.NextDouble() * Math.PI * 2, r = Math.Sqrt(rnd.NextDouble()) * 7;
            double x = Math.Cos(a) * r, z = Math.Sin(a) * r * 0.8, y = 0.9 + Math.Max(0, 3.4 - r * 0.5);
            Sdf b = S.Blend(0.5, S.Ell(x - 0.45, y, z, 1.15, 0.75, 0.8), S.Ell(x + 0.45, y, z, 1.15, 0.75, 0.8));
            b = S.Rot(S.Rot(b, 'y', rnd.Next(180), x, y, z), 'z', rnd.Next(-25, 25), x, y, z);
            s.Add(b, i % 4 == 0 ? beanW : bean);
        }
        Save(s, "food", "beans");

        // капуста: кочан и внешние листья с прожилками
        var cab = Bumped(M("#14300e", "#4e8e34", "#a8d880", 0.25, 0.04), (x, y, z) => 0.12 * Math.Abs(Math.Sin(Math.Atan2(z, x) * 5 + y * 0.4)) - 0.06);
        var cabIn = M("#3a5a20", "#9cc870", "#e0f4c0", 0.25, 0.04);
        s = new Scene { Elev = 25, Size = 28, Seed = 214 };
        s.Add(S.Sphere(0, 6, 0, 7), cabIn);
        for (int i = 0; i < 6; i++) {
            double a = i * 60 + 15;
            Sdf leaf = S.Inter(S.Sub(S.Sphere(0, 6, 0, 8.6), S.Sphere(0, 6, 0, 8.0)), S.Box(0, 4, 0, 6, 9, 13));
            leaf = S.Inter(leaf, S.Rot(S.Box(0, 4, 8, 5.5, 7, 6), 'y', a, 0, 0, 0));
            s.Add(S.Rough(leaf, 0.25, 0.7, 214 + i), cab);
        }
        Save(s, "food", "cabbage");

        // чеснок: головка с дольками, верхушка и корешки
        var garlic = Bumped(M("#b4a698", "#f0e8de", "#ffffff", 0.3, 0.04), (x, y, z) => 0.12 * Math.Abs(Math.Sin(Math.Atan2(z, x) * 3.5)) - 0.06);
        s = new Scene { Elev = 18, Size = 28, Seed = 215 };
        s.Add(S.Lathe(0, 0, 0, 0, 0, 0, 0.8, 1.2, 3.0, 3.0, 5.2, 5.2, 6.6, 7.4, 5.0, 9.2, 2.2, 10.6, 0.8, 12, 0.4), garlic);
        s.Add(S.Tube(0.4, 0.25, 0, 11.5, 0, 0.2, 13.5, 0, 0.8, 14.8, 0.2), M("#5a4c3a", "#b4a07e", "#e0d0b0", 0));
        for (int i = 0; i < 5; i++) s.Add(S.Cap(0, 0.8, 0, -1.2 + i * 0.6, -0.8, (i % 2) * 0.5, 0.16), M("#6a5a40", "#c8b48a", "#ecdcb4", 0));
        Save(s, "food", "garlic");

        // пряности: холщовый мешочек и горка красного порошка
        var spice = Spots(M("#5a0e06", "#c03a12", "#f08a3a", 0.1, 0.12).With(grainScale: 3), M("#8a6008", "#f0c040", "#fff0a0", 0, 0), 3, 0.7, 216);
        s = new Scene { Elev = 30, Size = 29, Seed = 216 };
        Sack(s, Burlap, -3, -2, 0.8);
        s.Add(S.Move(Mound(5.5, 3, 4.5, 0.4, 216), 5, 0, 4), spice);
        Save(s, "food", "spices");

        // травы: пучок стеблей, перевязанный бечёвкой, сверху листья
        Herbs("herbs", Leaf, M("#24300e", "#56702c", "#88a050", 0), 70);
        Herbs("dried_herbs", M("#2e2c0e", "#6e6a34", "#a8a060", 0.05), M("#3a2e14", "#6e5a2c", "#9c8650", 0), 71);

        // сушёные грибы: три шляпки на нитке
        var cap = M("#1e120a", "#583822", "#906848", 0.1, 0.08);
        var stalk = M("#5a4a34", "#b8a07a", "#e2d0aa", 0, 0.05);
        s = new Scene { Elev = 20, Roll = -12, Size = 30, Seed = 69 };
        s.Add(S.Cap(-10, 0.8, 0, 10, -0.8, 0, 0.2), Twine);
        for (int i = 0; i < 3; i++) {
            double x = -6.5 + i * 6.5, y = 0.5 - i * 0.6;
            s.Add(S.Rough(S.Inter(S.Ell(x, y, 0, 3.6, 2.6, 3.6), S.Box(x, y + 2, 0, 5, 2, 5)), 0.35, 0.9, 69 + i), cap);
            s.Add(S.Cap(x, y, 0, x + 0.2, y - 3, 0, 1.0, 0.8), stalk);
        }
        Save(s, "food", "dried_mushrooms");

        // ячмень и овёс — снопики колосьев
        Sheaf("barley", M("#5a4418", "#c09a4c", "#f0d488", 0.15, 0.05), true, 217);
        Sheaf("oats", M("#6a5c3a", "#c8b688", "#f0e4c0", 0.15, 0.05), false, 218);
    }

    static void Herbs(string name, Mtl leaf, Mtl stem, int seed) {
        var s = new Scene { Elev = 18, Roll = 15, Size = 30, Seed = seed };
        var rnd = new Random(seed);
        for (int i = 0; i < 6; i++) {
            double tx = -5 + i * 2 + rnd.NextDouble(), tz = rnd.NextDouble() * 2 - 1;
            s.Add(S.Tube(0.35, 0.3, 0, -10, 0, tx * 0.3, 0, tz * 0.3, tx, 7 + rnd.NextDouble() * 2, tz), stem);
            for (int k = 0; k < 3; k++) {
                double ly = 3 + k * 2.5 + rnd.NextDouble(), lx = tx * (0.3 + 0.7 * (ly / 9));
                double side = k % 2 == 0 ? 1 : -1;
                s.Add(S.Rot(S.Squash(S.Ell(lx + side * 1.6, ly, tz, 2.0, 1, 0.9), 0.6), 'z', side * 30, lx, ly, tz), leaf);
            }
        }
        s.Add(S.Torus(0, -3, 0, 1.4, 0.55), Twine);
        Save(s, "food", name);
    }

    static void Sheaf(string name, Mtl grain, bool awns, int seed) {
        var s = new Scene { Elev = 12, Roll = 18, Size = 31, Seed = seed };
        var stalk = M("#5a4a20", "#b49a58", "#e4cc8a", 0, 0.03);
        var rnd = new Random(seed);
        for (int i = 0; i < 7; i++) {
            double ang = (i - 3) * 8 + rnd.Next(-3, 3), rad = ang * Math.PI / 180;
            double tx = Math.Sin(rad) * 12, ty = Math.Cos(rad) * 12 - 4, tz = (i % 2) * 1.2 - 0.6;
            s.Add(S.Cap(0, -12, 0, tx, ty, tz, 0.5), stalk);
            for (int k = 0; k < 6; k++) {
                double f = 1 + k * 0.09, ex = tx * f + Math.Sin(rad) * 0, ey = ty + k * 1.1, ez = tz;
                double side = k % 2 == 0 ? 0.6 : -0.6;
                if (awns) {
                    s.Add(S.Rot(S.Ell(ex + side * 1.2 * Math.Cos(rad), ey, ez, 0.95, 1.4, 0.75), 'z', -ang + side * 40, ex, ey, ez), grain);
                } else {
                    // овёс: колоски свисают
                    double hx = ex + side * 1.8, hy = ey - 0.4;
                    s.Add(S.Cap(ex, ey, ez, hx, hy + 0.6, ez, 0.12), stalk);
                    s.Add(S.Rot(S.Ell(hx, hy - 0.5, ez, 0.75, 1.5, 0.65), 'z', side * 25, hx, hy, ez), grain);
                }
            }
            if (awns) s.Add(S.Cap(tx * 1.5, ty + 6.4, tz, tx * 1.75, ty + 11, tz, 0.15), stalk);
        }
        s.Add(S.Rot(S.Torus(0, -5, 0, 1.6, 0.6), 'x', 0), Twine);
        Save(s, "food", name);
    }

    // ------------------------------------------------------------------------ ягоды и фрукты
    static void Fruits_() {
        Berries("blueberry", M("#0c1230", "#2c3c84", "#6a80c8", 0.35, 0.03).With(sheen: 0.25), 2.5, true, 228);
        Berries("lingonberry", M("#4a0606", "#b01e18", "#ec5c48", 0.7, 0.03), 2.0, false, 229);
        Berries("cranberry", M("#320410", "#8a1028", "#d04050", 0.7, 0.03), 2.6, false, 230);

        Scene s;
        var cherry = M("#3a0408", "#a0101c", "#e8505a", 0.9, 0.02).With(spec: 30);
        s = new Scene { Elev = 14, Size = 29, Seed = 231 };
        s.Add(S.Sphere(-4, 0, 0, 4.2), cherry).Add(S.Sphere(4.5, -0.5, 1, 4.2), cherry);
        s.Add(S.Tube(0.35, 0.3, -3.8, 3.8, 0, -2, 9, 0, 0.5, 13, 0), Stem).Add(S.Tube(0.35, 0.3, 4.3, 3.3, 1, 3, 9, 0.5, 0.5, 13, 0), Stem);
        s.Add(S.Rot(S.Squash(S.Ell(4.5, 13.5, -0.5, 4.5, 1, 2.2), 0.4), 'z', -15, 2, 13, 0), Leaf);
        Save(s, "food", "cherry");

        var plum = M("#1a0828", "#542470", "#9a64b8", 0.25, 0.03).With(sheen: 0.3);
        s = new Scene { Elev = 16, Size = 27, Seed = 232 };
        var p = plum.With(); p.Bump = (x, y, z) => Math.Abs(x + z * 0.3) < 0.35 && z > 0 ? -0.25 : 0;
        s.Add(S.Ell(0, 0, 0, 7, 8.5, 7), p);
        s.Add(S.Tube(0.4, 0.3, 0, 8, 0, 0.6, 11, 0), Stem);
        s.Add(S.Rot(S.Squash(S.Ell(4, 11, -0.5, 4.2, 1, 2), 0.4), 'z', -20, 1, 11, 0), Leaf);
        Save(s, "food", "plum");

        // груша: чуть наклонена, жёлто-зелёная с румянцем на солнечном боку и рыжими крапинками, изогнутый черенок и лист
        var pearSkin = M("#3e440c", "#a6aa34", "#eae67e", 0.35, 0.04);
        var blush = M("#5a3a0e", "#c08a36", "#f0c070", 0.35, 0.04);
        var speck = M("#4a3008", "#7a5418", "#a07a34", 0, 0);
        var pear = pearSkin.With();
        pear.Swap = (x, y, z) => {
            if (Scene.Noise3(x * 2.6, y * 2.6, z * 2.6, 233) > 0.8) return speck;
            double b = -x * 0.5 + z * 0.4 + (Scene.Noise3(x * 0.5, y * 0.5, z * 0.5, 234) - 0.5) * 3;
            return b + (Scene.Hash((int)(x * 4), (int)(z * 4 + y * 4), 235) - 0.5) * 1.6 > 2.8 && y < 10 ? blush : null;
        };
        s = new Scene { Elev = 12, Size = 30, Seed = 233 };
        Sdf body = S.Lathe(0, 0, 0, 0, 0, 0, 0.8, 0.6, 3.6, 2.6, 6.4, 5.4, 7.0, 7.6, 5.2, 9.8, 3.4, 12, 2.6, 14, 1.9, 15.2, 0.6);
        s.Add(S.Rot(body, 'z', -10, 0, 0, 0), pear);
        s.Add(S.Tube(0.45, 0.32, 2.4, 14.6, 0, 2.9, 16.4, 0, 3.9, 17.6, 0.2), Stem);
        s.Add(S.Rot(S.Ell(7.6, 17.6, 0.8, 3.9, 1.8, 0.5), 'z', -24, 3.9, 17.6, 0), Leaf);
        Save(s, "food", "pear");

        var grape = M("#1e0826", "#562866", "#9a6aa8", 0.5, 0.03).With(sheen: 0.3);
        s = new Scene { Elev = 18, Size = 29, Seed = 234 };
        var rnd = new Random(234);
        for (int row = 0; row < 5; row++) {
            int n = 5 - row; double y = 8 - row * 3.0;
            for (int i = 0; i < n; i++) {
                double x = (i - (n - 1) / 2.0) * 3.0 + rnd.NextDouble() * 0.6, z = (rnd.NextDouble() - 0.5) * 2 + ((i + row) % 2) * 1.2;
                s.Add(S.Sphere(x, y, z, 1.75), grape);
            }
        }
        s.Add(S.Tube(0.45, 0.35, 0, 9, 0, 0.5, 12.5, 0), Stem);
        s.Add(S.Rot(S.Squash(S.Ell(4.5, 12, -1, 5, 1, 3), 0.4), 'z', -15, 1, 12, 0), Leaf);
        Save(s, "food", "grape");

        var orange = Bumped(M("#6a2604", "#e07414", "#ffbe5c", 0.4, 0.04), (x, y, z) => 0.12 * (Scene.Noise3(x * 2.5, y * 2.5, z * 2.5, 235) - 0.5));
        s = new Scene { Elev = 18, Size = 27, Seed = 235 };
        s.Add(S.Sphere(0, 0, 0, 8), orange);
        s.Add(S.Cyl(0, 7.9, 0, 0.6, 0.3), Stem);
        s.Add(S.Rot(S.Squash(S.Ell(3.5, 8.5, -1, 4.2, 1, 2), 0.4), 'z', -20, 0.5, 8, 0), Leaf);
        Save(s, "food", "orange");

        // оливки в глиняной мисочке
        s = new Scene { Elev = 40, Size = 28, Seed = 236 };
        s.Add(S.Lathe(0, 0, 0, 1.0, 1.2, 0, 4.5, 1, 5.8, 3.5, 8.4, 5.2, 9.6), Terracotta);
        var green = M("#1a2408", "#4e6a1e", "#94b058", 0.8, 0.02).With(spec: 30);
        var black = M("#060406", "#241c24", "#5a4a5a", 0.8, 0.02).With(spec: 30);
        double[][] ol = { new[] { -3.5, 1.5 }, new[] { 0.0, 3 }, new[] { 3.5, 1 }, new[] { -1.5, -2.5 }, new[] { 2.5, -2.5 }, new[] { -4.5, -1.5 }, new[] { 5.0, -1 } };
        for (int i = 0; i < ol.Length; i++) s.Add(S.Rot(S.Ell(ol[i][0], 5.0, ol[i][1], 1.9, 1.3, 1.3), 'y', i * 50, ol[i][0], 5, ol[i][1]), i % 2 == 0 ? green : black);
        Save(s, "food", "olives");
    }

    // ------------------------------------------------------------------------ блюда
    static void Dishes_() {
        Scene s;
        // заготовка на доске: сырая рыба с травами
        var salmon = Bumped(M("#6a1e14", "#d8705a", "#f8b8a0", 0.4, 0.04), (x, y, z) => Math.Abs(Math.Sin(x * 1.3 + z * 0.5)) < 0.2 ? 0.2 : 0);
        s = new Scene { Elev = 45, Yaw = -12, Size = 29, Seed = 224 };
        s.Add(S.Box(0, 0, 0, 12, 0.8, 7.5, 0.4), Wood);
        s.Add(S.Rough(S.Box(0, 2.0, 0, 7, 1.3, 4.0, 1.2), 0.25, 0.6, 224), salmon);
        Sprigs(s, 224, -3, -1, 2.5, 1.5, 5, -2); s.Parts.ForEach(q => { });
        Save(s, "food", "raw_dish");

        var baked = Bumped(M("#5a2410", "#d0703e", "#f4b07a", 0.4, 0.05), (x, y, z) => Math.Abs(Math.Sin(x * 1.3 + z * 0.5)) < 0.2 ? 0.2 : 0);
        s = new Scene { Elev = 45, Size = 29, Seed = 225 };
        PlateUnder(s);
        s.Add(S.Rough(S.Box(0, 2.3, 0, 7, 1.3, 4.2, 1.2), 0.25, 0.6, 225), baked);
        Sprigs(s, 225, -2.5, -1.5, 2, 2, 4, -2);
        Save(s, "food", "salmon_with_herbs");

        var roast = M("#2a0e04", "#7a3a14", "#c07a3a", 0.5, 0.06).With(spec: 22);
        s = new Scene { Elev = 45, Size = 29, Seed = 226 };
        PlateUnder(s);
        s.Add(S.Rough(S.Blend(2, S.Ell(-1.5, 2.6, 0, 6, 2.2, 4), S.Ell(3.5, 2.4, 0.5, 3, 1.8, 2.6)), 0.3, 0.6, 226), roast);
        s.Add(S.Cap(5.5, 2.5, 0.5, 9.5, 2.8, 1, 0.8), Bone).Add(S.Sphere(10, 3.0, 1.1, 1.0), Bone);
        Sprigs(s, 226, -3, 3, 1, -3);
        Save(s, "food", "hare_with_herbs");

        var mutton = Spots(M("#2a0e04", "#7e3414", "#c2703a", 0.5, 0.06).With(spec: 22), M("#5a0e04", "#d0401a", "#f4a050", 0, 0), 2.6, 0.78, 227);
        s = new Scene { Elev = 45, Size = 29, Seed = 227 };
        PlateUnder(s);
        s.Add(S.Rough(S.Box(0, 2.6, 0, 6.5, 1.8, 4.5, 1.6), 0.35, 0.5, 227), mutton);
        var clove = M("#7a6e62", "#ece4d8", "#ffffff", 0.3, 0.03);
        s.Add(S.Ell(8.2, 1.6, 2, 1.4, 1.0, 1.0), clove).Add(S.Ell(-8.4, 1.6, -1.5, 1.4, 1.0, 1.0), clove).Add(S.Ell(-7.6, 1.6, 2.5, 1.2, 0.9, 0.9), clove);
        Sprigs(s, 227, 7, -3);
        Save(s, "food", "exquisite_mutton");
    }

    // ------------------------------------------------------------------------ питьё в склянках
    static void Drinks_() {
        var clear = Glass("#3c5a5a", "#94c0bc", "#e8fffc");
        var brown = Glass("#2a1a0c", "#6a4420", "#b0844c");
        var green = Glass("#16301c", "#3c7048", "#9ad0a0");
        Bottle("food", "must", clear, M("#4e3c1e", "#a08450", "#d8bc88", 0.2, 0.08), false, 0.8, 81);
        Bottle("alcohol", "beer", brown, M("#5a3a0c", "#c08a2c", "#f0c060", 0.4, 0.02), false, 0.85, 82);
        Bottle("alcohol", "cider", clear, M("#6a4a10", "#d4a43c", "#f6d878", 0.4, 0.02), false, 0.85, 83);
        Bottle("alcohol", "wine", green, M("#2a0610", "#6a1426", "#b03448", 0.4, 0.02), true, 0.85, 84);
        Bottle("alcohol", "mead", clear, M("#6a400a", "#d89426", "#f8c858", 0.4, 0.02), false, 0.85, 85);
        Bottle("alcohol", "spirit", clear, M("#7a9094", "#c8dce0", "#ffffff", 0.6, 0.0), true, 0.8, 86);
        Bottle("alcohol", "tincture", clear, M("#4a0a10", "#a02030", "#e05a64", 0.4, 0.02), true, 0.8, 87);
        Bottle("food", "vinegar", clear, M("#6a6440", "#cfc89a", "#f2eccc", 0.4, 0.02), false, 0.8, 88);
        Bottle("food", "berry_juice", clear, M("#4a0814", "#b0283c", "#e86a7a", 0.4, 0.02), false, 0.85, 237);
        Bottle("food", "herbal_tea", clear, M("#3e3a0c", "#8a8228", "#c8be56", 0.4, 0.02), false, 0.8, 72);
    }

    // ------------------------------------------------------------------------ зерно и солод
    static void Grains_() {
        GrainPile("materials", "wet_grain", M("#4e3c1c", "#9a8048", "#c8b07a", 0.5, 0.05), false, 89);
        GrainPile("materials", "green_malt", M("#6a4e1c", "#c8a050", "#f0d48a", 0.15, 0.05), true, 90);
        GrainPile("materials", "pale_malt", M("#7a5e2a", "#d8b468", "#f8e2a0", 0.15, 0.05), false, 91);
        GrainPile("materials", "amber_malt", M("#5a3410", "#a8682a", "#d89a54", 0.2, 0.05), false, 92);
        GrainPile("materials", "dark_malt", M("#1a0e06", "#482a14", "#7a4e2c", 0.3, 0.05), false, 93);
    }

    // ------------------------------------------------------------------------ гончарное
    static void Pottery_() {
        Scene s;
        // гончарная глина: промятый ком со следами пальцев
        var clay = Bumped(M("#3a2c22", "#86705a", "#c0a88a", 0.25, 0.05), (x, y, z) => Math.Abs(Math.Sin(x * 0.9 + 0.3)) < 0.18 && z > 0 && y > 2 ? -0.25 : 0);
        s = new Scene { Elev = 30, Size = 27, Seed = 101 };
        s.Add(S.Rough(S.Blend(3, S.Inter(S.Ell(0, 0, 0, 9.5, 6, 8), S.Box(0, 4, 0, 11, 4, 10)), S.Ell(-1, 5, -1, 5.5, 3, 4.5)), 0.3, 0.5, 101), clay);
        Save(s, "pottery", "potter_clay");

        var grog = Spots(Spots(M("#3a1a0a", "#904e2c", "#c88a5c", 0.05, 0.14).With(grainScale: 3), M("#1a0a04", "#3a1a0c", "#5a2c16", 0, 0), 3, 0.74, 102),
            M("#8a5a3a", "#e4a878", "#ffd0a8", 0, 0), 3.4, 0.8, 103);
        s = new Scene { Elev = 24, Size = 28, Seed = 102 };
        s.Add(Mound(10, 7.5, 8.5, 0.9, 102), grog);
        Save(s, "pottery", "grog");

        // черепки: три выгнутых осколка с изломами
        var sherd = Terracotta.With();
        // осколки: выгнутые угловатые пластинки, излом — темнее (внутренность черепка)
        s = new Scene { Elev = 48, Size = 29, Seed = 103 };
        var core = M("#2a0e06", "#6a3018", "#9a5a36", 0, 0.1);
        sherd.Swap = (x, y, z) => y < 0.15 ? core : null;
        s.Add(Bend(S.Slab(0.5, 0.55, 0.1, -11, -2, -6, -7, -1, -4, -2, 1, -8, 3), 1), sherd);
        s.Add(Bend(S.Slab(0.5, 0.55, 0.1, 1, -9, 9, -7, 10, -1, 4, 0), 1), sherd);
        s.Add(Bend(S.Slab(0.5, 0.55, 0.1, -3, 3, 4, 2, 6, 9, -4, 8), 1), sherd);
        Save(s, "pottery", "sherds");

        // кирпич-сырец: брусок серой глины
        var rawBrick = M("#40342a", "#958068", "#cdb99e", 0.05, 0.1).With(grainScale: 2.4);
        s = new Scene { Elev = 32, Yaw = -30, Size = 28, Seed = 104 };
        s.Add(S.Rough(S.Box(0, 0, 0, 9, 3, 4.5, 0.6), 0.25, 0.8, 104), rawBrick);
        Save(s, "pottery", "raw_brick");

        // форма для кирпича: деревянная рамка без дна
        s = new Scene { Elev = 45, Yaw = -30, Size = 29, Seed = 105 };
        s.Add(S.Sub(S.Box(0, 0, 0, 11, 2.2, 6.5, 0.3), S.Box(0, 0, 0, 9.4, 3, 4.9)), Wood);
        s.Add(S.Cap(-11, 0, 0, -14, 0, 0, 1.0), Wood).Add(S.Cap(11, 0, 0, 14, 0, 0, 1.0), Wood);
        Save(s, "pottery", "brick_mould");
    }

    /** Выгнуть пластинку чашкой (осколок стенки сосуда). */
    static Sdf Bend(Sdf f, double k) { return (x, y, z) => f(x, y + k * (x * x + z * z) * 0.02, z); }

    // ------------------------------------------------------------------------ значки эффектов (поле 32×32, в игре — 18×18)
    static void Effects_() {
        string fx = Path.Combine(root, "..", "mob_effect");
        Scene s;
        s = new Scene { Elev = 25, Roll = 25, Size = 30, Seed = 240 };
        Drumstick(s, M("#3e1806", "#a0561a", "#e8a24c", 0.5, 0.06).With(spec: 22));
        Directory.CreateDirectory(fx); s.Render(Path.Combine(fx, "satiety.png"));

        s = new Scene { Elev = 32, Size = 26, Seed = 241 };
        Bowl(s, BowlWood); BowlFill(s, M("#6a4a18", "#c49040", "#f0cc7a", 0.6, 0.03).With(spec: 30), 0);
        Chunks(s, M("#2a0e06", "#6a2a16", "#a85a36", 0.2), 1.8, -3, 1.5, 3, -1);
        // ложка в миске
        s.Add(S.Tube(0.8, 0.9, 2, 6, -1, 9, 12, -3, 13, 15.5, -4), Wood);
        s.Add(S.Ell(1, 5.8, -0.6, 2.4, 0.6, 1.7), Wood);
        s.Render(Path.Combine(fx, "well_fed.png"));

        s = new Scene { Elev = 16, Size = 28, Seed = 242 };
        var glass = Glass("#3c5a5a", "#94c0bc", "#e8fffc");
        s.Add(S.Lathe(0, 0, 0, 0.5, 0.6, 0, 2.6, 0.5, 4.2, 2, 5.6, 4, 6.1, 6, 5.7, 7.6, 4.4, 8.8, 2.4, 9.4, 1.5, 12.6, 1.4, 13.3, 1.8), glass);
        s.Add(S.Lathe(0, 0, 0, 0, 0, 0.6, 3.6, 2, 5.0, 4, 5.5, 6, 5.1, 7.4, 3.9), M("#1a3006", "#5a9a1a", "#b8f060", 0.4, 0.02));
        s.Add(S.Sphere(-1.5, 8.6, 1, 0.9), M("#3a6a10", "#a0e040", "#eaffb0", 0.8)).Add(S.Sphere(1.2, 10.5, 0.4, 0.7), M("#3a6a10", "#a0e040", "#eaffb0", 0.8)).Add(S.Sphere(-0.3, 13.6, 0, 0.8), M("#3a6a10", "#a0e040", "#eaffb0", 0.8));
        s.Render(Path.Combine(fx, "poisoning.png"));
    }
}
