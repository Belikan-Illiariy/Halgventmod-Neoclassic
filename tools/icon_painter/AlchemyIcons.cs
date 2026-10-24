using System;
using System.Collections.Generic;
using System.IO;

// Иконки алхимии (textures/item/alchemy): вещества — комья, кристаллы и горки порошка, снадобья — склянки
// с пробкой. Сцены для Sculpt.cs, в том же духе, что SculptIcons и SmithIcons.
public static class AlchemyIcons {
    static string root;

    static Mtl M(string dark, string bas, string light, double gloss = 0, double grain = 0.06) { return new Mtl(dark, bas, light, gloss, grain); }
    static Mtl Spots(Mtl a, Mtl spot, double freq, double thr, int seed) {
        var m = a.With();
        m.Swap = (x, y, z) => Scene.Noise3(x * freq, y * freq, z * freq, seed) > thr ? spot : null;
        return m;
    }
    static Mtl Glass(string dark, string bas, string light) { return M(dark, bas, light, 1.0, 0).With(spec: 50, sheen: 0.25, glass: 0.72); }
    static readonly Mtl Cork = M("#4a2c12", "#9a6a3a", "#d0a070", 0, 0.12).With(grainScale: 3);
    static readonly Mtl Clear = Glass("#3c5a5a", "#94c0bc", "#e8fffc");

    static void Save(Scene s, string name) {
        string dir = Path.Combine(root, "alchemy"); Directory.CreateDirectory(dir);
        s.Render(Path.Combine(dir, name + ".png"));
    }

    static Sdf Mound(double rx, double ry, double rz, double rough, int seed) {
        return S.Inter(S.Rough(S.Ell(0, 0, 0, rx, ry, rz), rough, 0.7, seed), S.Box(0, ry, 0, rx + 3, ry, rz + 3));
    }

    static Sdf Lump(double cx, double cy, double cz, double r, int seed) {
        return S.Rough(S.Ell(cx, cy, cz, r * 1.15, r * 0.85, r), r * 0.22, 0.9, seed);
    }

    /** Горка порошка. */
    static void Powder(string name, Mtl m, int seed) {
        var s = new Scene { Elev = 28, Size = 25, Seed = seed };
        s.Add(Mound(9, 5, 7.5, 0.5, seed), m);
        Save(s, name);
    }

    /** Кристаллы: несколько вытянутых граней, торчащих из кучки. */
    static void Crystals(string name, Mtl m, int seed) {
        var s = new Scene { Elev = 26, Yaw = 15, Size = 27, Seed = seed };
        var rnd = new Random(seed);
        s.Add(Mound(8, 2.5, 6.5, 0.4, seed), m);
        for (int i = 0; i < 6; i++) {
            double x = rnd.NextDouble() * 13 - 6.5, z = rnd.NextDouble() * 8 - 4, tilt = rnd.NextDouble() * 60 - 30;
            double h = 3 + rnd.NextDouble() * 3;
            Sdf c = S.Rot(S.Box(0, h, 0, 1.1, h, 1.1, 0.1), 'y', 45);
            s.Add(S.Move(S.Rot(c, 'z', tilt), x, 0, z), m);
        }
        Save(s, name);
    }

    /** Склянка с пробкой и снадобьем. */
    static void Vial(string name, Mtl liquid, int seed) {
        var s = new Scene { Elev = 16, Size = 28, Seed = seed };
        double[] prof = { 0, 2.6, 0.5, 4.2, 2, 5.6, 4, 6.1, 6, 5.7, 7.6, 4.4, 8.8, 2.4, 9.4, 1.5, 12.6, 1.4, 13.3, 1.8 };
        s.Add(S.Lathe(0, 0, 0, 0.5, 0.6, prof), Clear);
        double top = prof[prof.Length - 2], level = 0.6 + 0.8 * 7.6;
        var inner = new List<double> { 0.6, prof[3] - 0.55 };
        for (int i = 0; i < prof.Length; i += 2) {
            double h = prof[i];
            if (h <= 0.6) continue;
            if (h > level) { inner.Add(level); inner.Add(Math.Max(0.5, prof[i + 1] - 0.55)); break; }
            inner.Add(h); inner.Add(Math.Max(0.5, prof[i + 1] - 0.55));
        }
        s.Add(S.Lathe(0, 0, 0, 0, 0, inner.ToArray()), liquid);
        s.Add(S.Cyl(0, top + 0.6, 0, prof[prof.Length - 1] - 0.3, 1.2, 0.3), Cork);
        // сургуч на пробке — снадобье, а не питьё
        s.Add(S.Cyl(0, top + 1.9, 0, prof[prof.Length - 1] + 0.2, 0.35, 0.2), M("#3a0606", "#8a1414", "#d04a3a", 0.5, 0.02));
        Save(s, name);
    }

    public static void Run(string outRoot) {
        root = outRoot;
        var sulfur = M("#6a5004", "#d8b81c", "#fff27a", 0.25, 0.08);
        var s = new Scene { Elev = 26, Yaw = 20, Size = 27, Seed = 701 };
        s.Add(Lump(-2, 0, 0, 6, 701), sulfur).Add(Lump(5, -1.5, 3, 4, 702), sulfur).Add(Lump(-5.5, -2, 4, 3, 703), sulfur);
        Save(s, "sulfur");

        Powder("sulfur_flowers", Spots(M("#8a6e0a", "#f0d43a", "#fffaa8", 0.1, 0.14).With(grainScale: 3.4),
            M("#fffbd0", "#ffffff", "#ffffff", 0.3, 0), 3.2, 0.8, 704), 704);
        Powder("lead_ash", M("#6a3c0c", "#d0882c", "#f4c070", 0.05, 0.14).With(grainScale: 3.2), 705);
        Powder("bone_ash", Spots(M("#7a7468", "#d8d2c6", "#f8f6f0", 0.05, 0.14).With(grainScale: 3.2),
            M("#4a4640", "#8a847a", "#b0aaa0", 0, 0.05), 3, 0.78, 706), 706);
        Crystals("sal_ammoniac", M("#8a9090", "#e0e6e6", "#ffffff", 0.6, 0.03), 707);
        Powder("verdigris", Spots(M("#0c3a2a", "#2a9a74", "#80e0bc", 0.1, 0.12).With(grainScale: 3),
            M("#0a2a1a", "#1c6a4a", "#3a9a70", 0, 0.05), 2.6, 0.72, 708), 708);
        Crystals("vitriol", M("#06244a", "#1c64c4", "#7ac0ff", 0.8, 0.02).With(spec: 40), 709);
        Powder("sublimate", Spots(M("#8a8c90", "#e6e8ec", "#ffffff", 0.5, 0.08).With(grainScale: 3.6),
            M("#b8bcc4", "#ffffff", "#ffffff", 0.9, 0), 3.6, 0.82, 710), 710);
        Powder("herb_powder", Spots(M("#1e300c", "#5a7a2a", "#9ab860", 0.05, 0.14).With(grainScale: 3.2),
            M("#3a3010", "#8a7a3a", "#b8a868", 0, 0.05), 3, 0.76, 711), 711);

        Vial("wound_balm", M("#0a3034", "#24887e", "#7ad8cc", 0.4, 0.02), 712);
        Vial("sulfur_balm", M("#6a5004", "#d8b020", "#fff07a", 0.4, 0.02), 713);
        Vial("theriac", M("#2a0e04", "#6a2a0c", "#b0602a", 0.4, 0.02), 714);
        Vial("herb_essence", M("#1a3a10", "#5aa030", "#b0e070", 0.4, 0.02), 715);
    }
}

// Иконки стекольного дела (textures/item/glass и tools): поташ, бой стекла, бокал, бутыль, трубка.
public static class GlassIcons {
    static string root;

    static Mtl M(string dark, string bas, string light, double gloss = 0, double grain = 0.06) { return new Mtl(dark, bas, light, gloss, grain); }
    static Mtl Glass(string dark, string bas, string light) { return M(dark, bas, light, 1.0, 0).With(spec: 50, sheen: 0.25, glass: 0.6); }

    static void Save(Scene s, string folder, string name) {
        string dir = Path.Combine(root, folder); Directory.CreateDirectory(dir);
        s.Render(Path.Combine(dir, name + ".png"));
    }

    public static void Run(string outRoot) {
        root = outRoot;
        // поташ: горка белой соли с сероватыми крупинками
        var potash = M("#7a7468", "#dcd6cc", "#fbf9f4", 0.15, 0.12).With(grainScale: 3.4);
        var s = new Scene { Elev = 28, Size = 25, Seed = 801 };
        s.Add(S.Inter(S.Rough(S.Ell(0, 0, 0, 9, 5, 7.5), 0.5, 0.7, 801), S.Box(0, 5, 0, 12, 5, 10.5)), potash);
        Save(s, "glass", "potash");

        // бой стекла: зеленоватые осколки
        var shard = Glass("#1e3a2a", "#6aa888", "#d8fff0");
        s = new Scene { Elev = 45, Size = 27, Seed = 802 };
        s.Add(S.Slab(0, 0.6, 0.1, -10, -3, -5, -8, 0, -2, -4, 4), shard);
        s.Add(S.Slab(1, 0.6, 0.1, 2, -8, 9, -4, 6, 3), shard);
        s.Add(S.Slab(0.5, 0.6, 0.1, -3, 3, 4, 2, 2, 9, -5, 8), shard);
        Save(s, "glass", "cullet");

        // бокал (чаша на ножке) и бутыль (пузатая, горло узкое) — в каждом цвете стекла; лесное — основная иконка
        string[][] tints = {
            new[] { "", "#1c3a26", "#7ab894", "#e8fff4" },            // лесное, зеленоватое
            new[] { "_green", "#0a3a14", "#3aa050", "#b0ffc0" },      // зелёное
            new[] { "_crystal", "#5a6a72", "#cfe2ea", "#ffffff" },    // хрусталь
            new[] { "_milk", "#8a8a88", "#e8e6e0", "#ffffff" },       // молочное
            new[] { "_amber", "#3a1e06", "#a8642a", "#f0c070" },      // бурое
            new[] { "_ruby", "#4a0610", "#c0283c", "#ff8a9a" } };     // рубиновое
        foreach (var t in tints) {
            var glass = Glass(t[1], t[2], t[3]);
            if (t[0] == "_milk") glass = M(t[1], t[2], t[3], 0.8, 0).With(spec: 40, sheen: 0.3); // непрозрачное
            s = new Scene { Elev = 20, Size = 28, Seed = 803 };
            s.Add(S.Lathe(0, 0, 0, 0.6, 0.7, 0, 4.2, 0.6, 3.4, 1.2, 0.8, 5.6, 0.8, 6.6, 3.2, 8.5, 4.8, 11, 5.2, 13, 5.4), glass);
            Save(s, "glass", "glass_goblet" + t[0]);
            s = new Scene { Elev = 16, Size = 29, Seed = 804 };
            s.Add(S.Lathe(0, 0, 0, 0.6, 0.7, 0, 3.6, 0.6, 5.4, 3, 6.8, 6, 6.6, 8.4, 4.4, 9.6, 2.0, 13.2, 1.6, 14, 2.0), glass);
            Save(s, "glass", "glass_flask" + t[0]);
        }

        // трубка: длинная железная, наискосок, на конце — капля стекла
        var iron = M("#1a1e22", "#4e565e", "#959fa8", 0.55, 0.05).With(spec: 30);
        var hot = M("#a03a0a", "#f08a2a", "#ffe0a0", 0.6, 0.02);
        s = new Scene { Elev = 16, Roll = -45, Size = 31, Seed = 805 };
        s.Add(S.Cap(-15, 0, 0, 12, 0, 0, 0.8), iron);
        s.Add(S.Cap(-15, 0, 0, -10, 0, 0, 1.2), M("#2e1c0e", "#6e4626", "#a87444", 0, 0.07));
        s.Add(S.Sphere(13.5, 0, 0, 2.4), hot);
        Save(s, "tools", "blowpipe");
    }
}
