using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;

// Иконки кузнечества (руды, металлы, кузнечные части, инструменты, литьё) — сцены для Sculpt.cs, в том же духе,
// что SculptIcons. И текстуры рудных жил: камень Conquest Reforged с вкраплениями руды по ступеням богатства
// (VeinTextures, нужен распакованный Conquest — путь в render.ps1).
public static class SmithIcons {
    static string root;

    static Mtl M(string dark, string bas, string light, double gloss = 0, double grain = 0.06) { return new Mtl(dark, bas, light, gloss, grain); }
    static Mtl Spots(Mtl a, Mtl spot, double freq, double thr, int seed) {
        var m = a.With();
        m.Swap = (x, y, z) => Scene.Noise3(x * freq, y * freq, z * freq, seed) > thr ? spot : null;
        return m;
    }

    static void Save(Scene s, string folder, string name) {
        string dir = Path.Combine(root, folder); Directory.CreateDirectory(dir);
        s.Render(Path.Combine(dir, name + ".png"));
    }

    // ---------------- материалы
    static readonly Mtl Wood = M("#2e1c0e", "#6e4626", "#a87444", 0, 0.07).With(grainScale: 2.2);
    static readonly Mtl Iron = M("#1a1e22", "#4e565e", "#959fa8", 0.55, 0.05).With(spec: 30);
    static readonly Mtl RawIron = M("#141618", "#3c4248", "#7a828a", 0.3, 0.1);
    static readonly Mtl Steel = M("#262c32", "#77838e", "#e2e9ef", 0.9, 0.02).With(spec: 40, sheen: 0.15);
    static readonly Mtl Copper = M("#3a1208", "#a84e2a", "#f0a070", 0.75, 0.03).With(spec: 34);
    static readonly Mtl Tin = M("#4a5058", "#b8bec6", "#f4f6f8", 0.6, 0.03);
    static readonly Mtl Lead = M("#24272c", "#5e646e", "#9aa0aa", 0.25, 0.05);
    static readonly Mtl Silver = M("#50565e", "#c8ccd4", "#ffffff", 1.0, 0.02).With(spec: 44, sheen: 0.2);
    static readonly Mtl Gold = M("#5a3a06", "#d8a832", "#fff0a0", 1.0, 0.02).With(spec: 40, sheen: 0.22);
    static readonly Mtl Bronze = M("#3a2408", "#a8722e", "#e8c080", 0.7, 0.04).With(spec: 30);
    static readonly Mtl Brass = M("#4a3608", "#c89e34", "#f8e08a", 0.8, 0.03).With(spec: 34);
    static readonly Mtl Pewter = M("#3c4248", "#959ca4", "#d4d9de", 0.35, 0.05);
    static readonly Mtl Glass = M("#3e5a62", "#a8c8d0", "#eefaff", 0.9, 0).With(glass: 0.7, spec: 40);
    static readonly Mtl Leather = M("#2a160a", "#6a3c1e", "#a26a3c", 0.15, 0.07);
    static readonly Mtl Cord = M("#3a2a14", "#8a6c3e", "#c8a878", 0, 0.06);

    static Sdf Mound(double rx, double ry, double rz, double rough, int seed) {
        return S.Inter(S.Rough(S.Ell(0, 0, 0, rx, ry, rz), rough, 0.7, seed), S.Box(0, ry, 0, rx + 3, ry, rz + 3));
    }

    /** Кусок породы: неровный ком. */
    static Sdf Lump(double cx, double cy, double cz, double r, int seed) {
        return S.Rough(S.Ell(cx, cy, cz, r * 1.15, r * 0.85, r), r * 0.22, 0.9, seed);
    }

    /** Две-три глыбы руды. */
    static void Ore(string name, Mtl rock, Mtl spot, double freq, double thr, int seed) {
        var s = new Scene { Elev = 26, Yaw = 20, Size = 28, Seed = seed };
        var m = Spots(rock, spot, freq, thr, seed);
        s.Add(Lump(-2, 0, 0, 6.5, seed), m);
        s.Add(Lump(5.5, -1.5, 3, 4.2, seed + 1), m);
        s.Add(Lump(-6, -2, 4.5, 3.2, seed + 2), m);
        Save(s, "minerals", name);
    }

    /** Слиток: усечённая призма, лежит наискосок. */
    static void Ingot(string name, Mtl m, int seed, double len = 1) {
        var s = new Scene { Elev = 30, Yaw = 32, Size = 28, Seed = seed };
        double l = 7 * len;
        s.Add(S.Plate(0, 3.0, 0.45, -l, -1.8, l, -1.8, l - 1.3, 1.8, -l + 1.3, 1.8), m);
        Save(s, "metals", name);
    }

    public static void Run(string outRoot) {
        root = outRoot;
        Ores_();
        Metals_();
        Parts_();
        Ware_();
        Tools_();
    }

    static void Ores_() {
        var brownRock = M("#2a1a10", "#6a4630", "#9c7454", 0.05, 0.1);
        var grayRock = M("#24262a", "#5e6268", "#9aa0a6", 0.05, 0.1);
        var quartz = M("#7a7468", "#d8d2c4", "#fbf8f0", 0.3, 0.06);
        Ore("iron_ore", brownRock, M("#2a0e04", "#8a3e1c", "#c46a3a", 0.3, 0.05), 1.3, 0.62, 601);
        var bog = M("#2a1606", "#7a4a1e", "#b07a3e", 0.05, 0.14).With(grainScale: 2.4);
        Ore("bog_iron_ore", bog, M("#140a04", "#3a2412", "#5e3e20", 0.1, 0.05), 1.6, 0.6, 602);
        var malachite = M("#08301e", "#2a8a5a", "#70d0a0", 0.4, 0.04);
        var pyrite = M("#4a3808", "#b89a3a", "#f0dc8a", 0.9, 0.02).With(spec: 36);
        var copper = Spots(Spots(grayRock, malachite, 1.2, 0.6, 603), pyrite, 2.2, 0.74, 604);
        var s = new Scene { Elev = 26, Yaw = 20, Size = 28, Seed = 603 };
        s.Add(Lump(-2, 0, 0, 6.5, 603), copper).Add(Lump(5.5, -1.5, 3, 4.2, 604), copper).Add(Lump(-6, -2, 4.5, 3.2, 605), copper);
        Save(s, "minerals", "copper_ore");
        Ore("tin_ore", grayRock, M("#0c0a08", "#2a2420", "#5a4e44", 0.7, 0.03), 1.8, 0.6, 606);
        Ore("lead_ore", M("#30343a", "#70767e", "#a8aeb6", 0.4, 0.04), M("#50565e", "#b0b6c0", "#f0f4f8", 0.95, 0.02).With(spec: 40), 1.0, 0.55, 607);
        Ore("silver_ore", quartz, M("#3a3e46", "#9ea4ae", "#ffffff", 1.0, 0.02), 2.0, 0.64, 608);
        Ore("gold_ore", quartz, Gold, 2.0, 0.64, 609);
        Ore("zinc_ore", M("#6a6250", "#c4bca4", "#ece6d4", 0.1, 0.1), M("#7a6a3a", "#d8c890", "#f6ecc4", 0.2, 0.04), 1.4, 0.64, 610);
        Ore("cinnabar", M("#2a0806", "#8a1a12", "#d04a34", 0.4, 0.06), M("#4a0404", "#c01810", "#ff6a4a", 0.6, 0.03), 1.8, 0.6, 611);

        // толчёная руда — серый песок с крапинами золота; золотой песок — горка крупинок
        var crushed = Spots(M("#4a4640", "#a8a298", "#e2ddd2", 0.1, 0.14).With(grainScale: 3), Gold, 2.8, 0.78, 612);
        s = new Scene { Elev = 26, Size = 28, Seed = 612 };
        s.Add(Mound(10, 6.5, 8.5, 0.7, 612), crushed); Save(s, "minerals", "crushed_gold_ore");
        var dust = Spots(M("#5a3a06", "#c89a2c", "#f6dc7a", 0.6, 0.12).With(grainScale: 3.4), M("#fff0a0", "#ffffff", "#ffffff", 1, 0), 3.4, 0.8, 613);
        s = new Scene { Elev = 28, Size = 24, Seed = 613 };
        s.Add(Mound(8, 4.5, 7, 0.5, 613), dust); Save(s, "minerals", "gold_dust");
        var flux = Spots(M("#7a766c", "#d2cec2", "#f6f4ee", 0.05, 0.14).With(grainScale: 3), M("#8a8478", "#b8b2a4", "#dad4c8", 0, 0.05), 2.4, 0.7, 614);
        s = new Scene { Elev = 26, Size = 28, Seed = 614 };
        s.Add(Mound(10, 7, 8.5, 0.9, 614), flux); Save(s, "minerals", "flux");
    }

    static void Metals_() {
        // крица: губчатый ком со шлаком в порах
        var bloom = Spots(M("#141210", "#4a4038", "#8a7c6c", 0.25, 0.14), M("#2a1206", "#6a3a18", "#a06030", 0.1, 0.05), 1.6, 0.6, 621);
        var s = new Scene { Elev = 26, Yaw = 15, Size = 28, Seed = 621 };
        s.Add(S.Rough(S.Ell(0, 0, 0, 9, 6, 8), 1.4, 0.55, 621), bloom);
        Save(s, "metals", "iron_bloom");

        Ingot("iron_ingot", Iron, 622);
        Ingot("steel_ingot", Steel, 623);
        Ingot("copper_ingot", Copper, 624);
        Ingot("tin_ingot", Tin, 625, 0.85);
        Ingot("lead_ingot", Lead, 626);
        Ingot("argentiferous_lead", Spots(Lead, Silver, 2.2, 0.76, 627), 627);
        Ingot("silver_ingot", Silver, 628, 0.75);
        Ingot("gold_ingot", Gold, 629, 0.75);
        Ingot("bronze_ingot", Bronze, 630);
        Ingot("brass_ingot", Brass, 631);
        Ingot("pewter_ingot", Pewter, 632, 0.85);

        // ртуть: запаянная склянка с серебристым
        s = new Scene { Elev = 22, Size = 27, Seed = 633 };
        s.Add(S.Lathe(0, 0, 0, 0.7, 0.8, 0, 3.6, 1.2, 4.4, 6.5, 4.6, 8.8, 3.2, 10.5, 1.6, 12.5, 1.4, 13.6, 0.9), Glass);
        s.Add(S.Lathe(0, 0.8, 0, 0, 0, 0, 3.4, 4.5, 3.8, 5.2, 3.6), Silver);
        Save(s, "metals", "mercury");
    }

    static void Parts_() {
        // гвозди: несколько вразбежку
        var s = new Scene { Elev = 34, Yaw = 10, Size = 29, Seed = 641 };
        double[][] nails = { new[] { -8.0, 0, -4, 8, 0, 3 }, new[] { -6.0, 0, 5, 9, 0, -1 }, new[] { -9.0, 0, 1, 5, 0, -6 } };
        foreach (var n in nails) {
            s.Add(S.Cap(n[0], n[1], n[2], n[3], n[4], n[5], 0.8, 0.25), Iron);
            double dx = n[3] - n[0], dz = n[5] - n[2], yaw = Math.Atan2(dz, dx) * 180 / Math.PI;
            s.Add(S.Move(S.Rot(S.Rot(S.Cyl(0, 0, 0, 1.9, 0.45, 0.2), 'z', 90), 'y', -yaw), n[0], n[1], n[2]), Iron);
        }
        Save(s, "metals", "nails");

        // заклёпки: горстка с полукруглыми шляпками
        s = new Scene { Elev = 40, Size = 26, Seed = 642 };
        var rnd = new Random(642);
        for (int i = 0; i < 7; i++) {
            double a = rnd.NextDouble() * Math.PI * 2, r = rnd.NextDouble() * 6;
            double x = Math.Cos(a) * r, z = Math.Sin(a) * r;
            s.Add(S.Inter(S.Sphere(x, 0, z, 2.2), S.Box(x, 1.1, z, 2.4, 1.1, 2.4)), Iron);
            s.Add(S.Cap(x, 0, z, x, -2.6, z, 0.8), Iron);
        }
        Save(s, "metals", "rivets");

        // проволока: моток
        s = new Scene { Elev = 40, Yaw = 10, Size = 28, Seed = 643 };
        for (int i = 0; i < 5; i++) s.Add(S.Torus(0, i * 0.9 - 1.8, 0, 7.5 - i * 0.15, 0.5), Iron);
        s.Add(S.Cap(7.4, 1.8, 0, 11, 4, 2, 0.45), Iron);
        Save(s, "metals", "iron_wire");

        // цепь: звенья, повёрнутые через одно
        s = new Scene { Elev = 30, Yaw = -20, Roll = -35, Size = 30, Seed = 644 };
        for (int i = 0; i < 5; i++) {
            double x = -11 + i * 5.4;
            Sdf link = S.Inter(S.Torus(0, 0, 0, 2.8, 0.75), S.Box(0, 0, 0, 3.6, 3, 3.6));
            Sdf stretched = (px, py, pz) => link(px / 1.4, py, pz) * 1.0;
            s.Add(i % 2 == 0 ? S.Move(S.Rot(stretched, 'x', 90), x, 0, 0) : S.Move(stretched, x, 0, 0), Iron);
        }
        Save(s, "metals", "chain");

        // бронзовая застёжка: кольцо с иглой
        s = new Scene { Elev = 60, Size = 26, Seed = 645 };
        s.Add(S.Torus(0, 0, 0, 7, 1.4), Bronze);
        s.Add(S.Cap(-7, 0.6, 0, 6, 1.2, -2.5, 0.8, 0.4), Bronze);
        Save(s, "metals", "bronze_clasp");

        // латунная пряжка: рамка и язычок
        s = new Scene { Elev = 60, Size = 26, Seed = 646 };
        s.Add(S.Sub(S.Box(0, 0, 0, 8, 0.9, 6, 1.2), S.Box(0, 0, 0, 6, 2, 4, 0.8)), Brass);
        s.Add(S.Cap(-6.5, 0.6, 0, 7.5, 1.0, 0, 0.8), Brass);
        Save(s, "metals", "brass_buckle");

        // латунные оковки: накладки с заклёпками
        s = new Scene { Elev = 50, Yaw = 20, Size = 28, Seed = 647 };
        s.Add(S.Box(-3, 0, -2, 7, 0.6, 2.6, 0.5), Brass).Add(S.Box(3, -1, 4, 6, 0.6, 2.2, 0.5), Brass);
        for (int i = 0; i < 3; i++) s.Add(S.Sphere(-8 + i * 5, 0.6, -2, 0.9), Brass);
        Save(s, "metals", "brass_fittings");

        // свинцовая печать: диск на шнуре
        s = new Scene { Elev = 50, Size = 26, Seed = 648 };
        s.Add(S.Cyl(0, 0, 0, 6.5, 1.2, 0.8), Lead);
        s.Add(S.Tube(0.6, 0.6, -11, -0.5, -3, -6, 0, -1, 6, 0, 1, 11, -0.5, 4), Cord);
        Save(s, "metals", "lead_seal");

        // золотая нить: катушка
        s = new Scene { Elev = 24, Yaw = 20, Size = 28, Seed = 649 };
        s.Add(S.Cyl(0, 0, 0, 5.6, 0.9, 0.3), Wood).Add(S.Cyl(0, 9, 0, 5.6, 0.9, 0.3), Wood);
        s.Add(S.Cyl(0, 4.5, 0, 4.2, 3.6, 0.6), Gold);
        Save(s, "textiles", "gold_thread");
    }

    static void Ware_() {
        // пьютеровая кружка: цилиндр с крышкой и ручкой
        var s = new Scene { Elev = 22, Yaw = -25, Size = 28, Seed = 651 };
        s.Add(S.Lathe(0, 0, 0, 0.8, 0.8, 0, 4.6, 0.6, 5.0, 9, 4.6, 11, 4.4), Pewter);
        s.Add(S.Cyl(0, 11.3, 0, 4.9, 0.5, 0.3), Pewter);
        s.Add(S.Tube(0.9, 0.9, 4.4, 10, 0, 7.6, 9.6, 0, 8.2, 5.4, 0, 4.6, 2.2, 0), Pewter);
        Save(s, "pottery", "pewter_tankard");
        s = new Scene { Elev = 50, Size = 28, Seed = 652 };
        s.Add(S.Lathe(0, 0, 0, 0.7, 0.6, 0, 4.0, 0.6, 5.4, 1.2, 9.0, 1.8, 11.2), Pewter);
        Save(s, "pottery", "pewter_plate");
        s = new Scene { Elev = 40, Yaw = 10, Roll = -40, Size = 29, Seed = 653 };
        s.Add(S.Sub(S.Ell(6, 0, 0, 4.2, 1.6, 3.0), S.Ell(6, 1.0, 0, 3.6, 1.4, 2.4)), Pewter);
        s.Add(S.Tube(0.6, 0.8, 2, 0.4, 0, -6, 1.0, 0, -12, 1.6, 0), Pewter);
        Save(s, "pottery", "pewter_spoon");
    }

    static void Tools_() {
        // клещи: две губы, перекрещенные у шарнира, длинные ручки
        var s = new Scene { Elev = 14, Roll = -45, Size = 31, Seed = 661 };
        s.Add(S.Tube(0.75, 0.7, -14, 1.4, 0, 2, 0.4, 0, 8, -1.0, 0, 11, -0.4, 0), Iron);
        s.Add(S.Tube(0.75, 0.7, -14, -1.4, 0.6, 2, -0.4, 0.6, 8, 1.0, 0.6, 11, 0.4, 0.6), Iron);
        s.Add(S.Sphere(2, 0, 0.3, 1.1), Iron);
        Save(s, "tools", "tongs");

        // кузнечный молот: тяжёлая головка, боёк и клин
        s = new Scene { Elev = 18, Yaw = 25, Roll = -45, Size = 30, Seed = 662 };
        s.Add(S.Cap(-12, 0, 0, 7.5, 0, 0, 1.2, 1.4), Wood);
        s.Add(S.Box(9, 1.6, 0, 2.3, 4.6, 2.2, 0.5), Iron);
        s.Add(S.Cyl(9, -3.4, 0, 2.0, 1.4, 0.4), Steel);
        Save(s, "tools", "smith_hammer");

        // кувалда: длинная рукоять, большой боёк
        s = new Scene { Elev = 18, Yaw = 25, Roll = -45, Size = 31, Seed = 663 };
        s.Add(S.Cap(-15, 0, 0, 9, 0, 0, 1.1, 1.3), Wood);
        s.Add(S.Box(10.5, 0, 0, 3.2, 6.0, 3.2, 0.8), Iron);
        Save(s, "tools", "sledgehammer");

        // волочильная доска: стальная плитка с рядом отверстий
        s = new Scene { Elev = 70, Yaw = 10, Size = 28, Seed = 664 };
        Sdf plate = S.Box(0, 0, 0, 11, 1.2, 4, 0.6);
        for (int i = 0; i < 6; i++) plate = S.Sub(plate, S.Cyl(-8.5 + i * 3.4, 0, 0, 1.3 - i * 0.16, 3));
        s.Add(plate, Steel);
        Save(s, "tools", "draw_plate");

        // лоток: широкая неглубокая деревянная чаша
        s = new Scene { Elev = 45, Size = 29, Seed = 665 };
        s.Add(S.Lathe(0, 0, 0, 0.9, 0.8, 0, 3.0, 0.6, 5.0, 2.0, 9.0, 3.0, 11.0), Wood);
        Save(s, "tools", "gold_pan");

        // штемпель: стальной пуансон с монетным ликом
        s = new Scene { Elev = 24, Yaw = 20, Size = 28, Seed = 666 };
        s.Add(S.Cyl(0, 3, 0, 3.2, 8, 0.6), Steel);
        s.Add(S.Cyl(0, -5.8, 0, 4.4, 1.0, 0.4), Steel);
        s.Add(S.Torus(0, -4.8, 0, 3.2, 0.35), Gold);
        Save(s, "tools", "coin_die");

        // драгоценности
        s = new Scene { Elev = 50, Size = 22, Seed = 671 };
        s.Add(S.Torus(0, 0, 0, 5.5, 1.3), Silver); Save(s, "jewelry", "silver_ring");
        s = new Scene { Elev = 50, Size = 22, Seed = 672 };
        s.Add(S.Torus(0, 0, 0, 5.5, 1.3), Gold); Save(s, "jewelry", "gold_ring");
        s = new Scene { Elev = 22, Size = 28, Seed = 673 };
        s.Add(S.Lathe(0, 0, 0, 0.7, 0.8, 0, 4.4, 0.6, 3.6, 1.2, 1.0, 5.0, 0.9, 6.0, 2.6, 7.0, 4.6, 10, 5.2, 12, 5.4), Silver);
        Save(s, "jewelry", "silver_goblet");
        // паломнический значок: раковина-гребешок
        s = new Scene { Elev = 70, Size = 24, Seed = 674 };
        var shell = new List<double>();
        for (int i = 0; i <= 14; i++) { double a = Math.PI * (0.1 + i * 0.057); shell.Add(Math.Cos(a) * 8); shell.Add(Math.Sin(a) * 8 - 2); }
        shell.Add(2); shell.Add(-5); shell.Add(-2); shell.Add(-5);
        var ribbed = Pewter.With();
        ribbed.Bump = (x, y, z) => 0.12 * Math.Sin(Math.Atan2(z + 2, x) * 14);
        s.Add(S.Slab(0, 0.8, 0.3, shell.ToArray()), ribbed);
        Save(s, "jewelry", "pilgrim_badge");
    }
}

/** Текстуры рудных жил, ила и россыпи: камень Conquest + вкрапления по богатству (0 — пустая, 3 — богатая). */
public static class VeinTextures {
    static string src, dst;

    class Vein {
        public string Name, Base; public string[] Ore; public int Seed; public double Grain = 1; public bool Pebbles;
    }

    public static void Run(string conquestTextures, string outBlock) {
        src = conquestTextures; dst = Path.Combine(outBlock, "smithing"); Directory.CreateDirectory(dst);
        var veins = new[] {
            new Vein { Name = "iron_vein", Base = "1_stone/dark_brown_granite", Ore = new[] { "#3a1608", "#8a3e1c", "#c46a3a" }, Seed = 1 },
            new Vein { Name = "copper_vein", Base = "1_stone/gray_quartzite", Ore = new[] { "#0e4028", "#2a9a62", "#c8a83a" }, Seed = 2 },
            new Vein { Name = "tin_vein", Base = "1_stone/dark_gray_granite", Ore = new[] { "#0c0a08", "#2e2620", "#6a5c4e" }, Seed = 3 },
            new Vein { Name = "lead_vein", Base = "1_stone/slate", Ore = new[] { "#5a606a", "#a8aeb8", "#e8ecf2" }, Seed = 4 },
            new Vein { Name = "silver_vein", Base = "1_stone/calcite", Ore = new[] { "#7a8088", "#c8ccd4", "#ffffff" }, Seed = 5, Grain = 0.6 },
            new Vein { Name = "gold_vein", Base = "1_stone/white_rough_marble", Ore = new[] { "#8a6010", "#e0b040", "#fff0a0" }, Seed = 6, Grain = 0.6 },
            new Vein { Name = "zinc_vein", Base = "1_stone/pale_limestone", Ore = new[] { "#8a7a4a", "#cec08a", "#f2ead0" }, Seed = 7 },
            new Vein { Name = "cinnabar_vein", Base = "1_stone/dark_gray_granite", Ore = new[] { "#5a0806", "#b01e14", "#f05a40" }, Seed = 8 },
            new Vein { Name = "limestone_vein", Base = "1_stone/gray_limestone", Ore = new[] { "#b8b4aa", "#e2ded4", "#f8f6f0" }, Seed = 9, Grain = 1.6 },
            new Vein { Name = "bog_silt", Base = "5_dirt/thick_mud", Ore = new[] { "#3a1a08", "#7a4018", "#a86a32" }, Seed = 10, Grain = 1.4 },
            new Vein { Name = "gold_placer", Base = "3_sand_clay/wet_river_sand", Ore = new[] { "#9a7010", "#e8bc40", "#fff4b0" }, Seed = 11, Grain = 0.4, Pebbles = true },
        };
        foreach (var v in veins) for (int r = 0; r <= 3; r++) Make(v, r);
    }

    static Color C(string h) { return ColorTranslator.FromHtml(h); }

    static void Make(Vein v, int richness) {
        using (var basebmp = new Bitmap(Path.Combine(src, v.Base + ".png"))) {
            int n = basebmp.Width;
            var bmp = new Bitmap(n, n, PixelFormat.Format32bppArgb);
            for (int x = 0; x < n; x++) for (int y = 0; y < n; y++) bmp.SetPixel(x, y, basebmp.GetPixel(x, y));
            var rnd = new Random(v.Seed * 100 + 7);
            if (richness == 0) {
                // Выработано: следы кирки — тёмные косые щербины
                for (int i = 0; i < 14; i++) {
                    int x = rnd.Next(n), y = rnd.Next(n), len = 2 + rnd.Next(3);
                    for (int k = 0; k < len; k++) Dark(bmp, (x + k) % n, (y + k) % n, 0.62);
                }
            } else {
                double[] density = { 0, 0.05, 0.1, 0.17 };
                int clusters = (int)(density[richness] * n * n / 3.0 * v.Grain + 2);
                Color dark = C(v.Ore[0]), mid = C(v.Ore[1]), light = C(v.Ore[2]);
                for (int i = 0; i < clusters; i++) {
                    int x = rnd.Next(n), y = rnd.Next(n);
                    int size = v.Pebbles ? 1 : 1 + rnd.Next(3);
                    for (int k = 0; k < size * 2; k++) {
                        int px = ((x + rnd.Next(size + 1) - size / 2) % n + n) % n, py = ((y + rnd.Next(size + 1) - size / 2) % n + n) % n;
                        bmp.SetPixel(px, py, mid);
                        bmp.SetPixel((px + 1) % n, (py + 1) % n, dark);
                    }
                    bmp.SetPixel(x, y, light);
                }
            }
            bmp.Save(Path.Combine(dst, v.Name + "_" + richness + ".png"), ImageFormat.Png);
            bmp.Dispose();
        }
    }

    static void Dark(Bitmap b, int x, int y, double k) {
        Color c = b.GetPixel(x, y);
        b.SetPixel(x, y, Color.FromArgb(255, (int)(c.R * k), (int)(c.G * k), (int)(c.B * k)));
    }
}
