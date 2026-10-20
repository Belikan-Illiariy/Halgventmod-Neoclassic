using System;
using System.IO;
using F = System.Func<double, double, double>;

public static class Icons {
    // Палитры: тень, основа, свет, сила шума, масштаб шума, ступеней
    static Mat RawRed = new Mat("#3e1610", "#8a3426", "#c86a50", 0.16, 0.55, 6);
    static Mat SaltRed = new Mat("#3a1a16", "#7e3a30", "#b8705c", 0.14, 0.55, 6);
    static Mat Lime = new Mat("#4e4b44", "#a29e90", "#dcd8c8", 0.14, 0.5, 6);
    static Mat Pelt = new Mat("#6e5a44", "#c8b090", "#f0e0c2", 0.08, 0.4, 6);
    static Mat Wet = new Mat("#22140a", "#583418", "#9a6436", 0.10, 0.45, 7);
    static Mat Leather = new Mat("#341e0e", "#744424", "#ae7444", 0.10, 0.5, 6);
    static Mat Tan = new Mat("#4e3016", "#a06c38", "#d8a666", 0.08, 0.45, 6);
    static Mat Rawhide = new Mat("#5e3e14", "#b88a40", "#ecc880", 0.12, 0.35, 6);
    static Mat Fur = new Mat("#2e2014", "#6e5236", "#b8946a", 0.36, 1.1, 6);
    static Mat Boiled = new Mat("#1e120a", "#4e3018", "#80552e", 0.10, 0.5, 6);
    static Mat Flesh = new Mat("#6a3430", "#c47e70", "#eebcaa", 0.16, 0.6, 6);
    static Mat Wood = new Mat("#30200e", "#70482a", "#a87848", 0.14, 0.9, 6);
    static Mat DarkWood = new Mat("#24160a", "#5a381c", "#8a5c34", 0.12, 0.9, 6);
    static Mat Bark = new Mat("#24180e", "#5e4028", "#8e6a48", 0.30, 1.3, 6);
    static Mat Steel = new Mat("#262a30", "#7c8692", "#dce4ec", 0.04, 0.5, 6);
    static Mat Iron = new Mat("#1e2024", "#565c64", "#9aa2aa", 0.08, 0.6, 6);
    static Mat Salt = new Mat("#7a8a98", "#d6dee6", "#ffffff", 0.10, 1.2, 5);
    static Mat Ash = new Mat("#2e2c2a", "#6e6a66", "#aaa6a0", 0.22, 1.2, 6);
    static Mat Alum = new Mat("#7a6c8c", "#d0c4e0", "#fbf6ff", 0.04, 0.5, 5);
    static Mat Wax = new Mat("#6e4a0c", "#d29a2c", "#f6d676", 0.08, 0.5, 6);
    static Mat Amber = new Mat("#5a3006", "#b8701c", "#f0b24c", 0.06, 0.5, 6);
    static Mat Porridge = new Mat("#8e7448", "#dcc48a", "#f8ecc6", 0.12, 0.9, 5);
    static Mat Broth = new Mat("#6e5424", "#b89650", "#e8cc88", 0.08, 0.6, 5);
    static Mat Crust = new Mat("#5e3210", "#b07030", "#e6b268", 0.10, 0.7, 6);
    static Mat MushFill = new Mat("#30200f", "#6a4c34", "#a07c5a", 0.20, 1.0, 5);
    static Mat Char = new Mat("#0c0a08", "#26211e", "#4a4036", 0.20, 1.0, 5);
    static Mat Dough = new Mat("#857454", "#dccca2", "#faf0d4", 0.06, 0.5, 6);
    static Mat Bun = new Mat("#5e2c0c", "#bc722a", "#eeb25c", 0.06, 0.5, 6);
    static Mat Glaze = new Mat("#b8ae9a", "#f0e8d6", "#ffffff", 0.0, 0.5, 4);
    static Mat Butter = new Mat("#a07a14", "#ecc23c", "#fff08a", 0.0, 0.5, 4);

    static F Rot(F f, double cx, double cy, double deg) {
        double a = deg * Math.PI / 180, c = Math.Cos(a), s = Math.Sin(a);
        return (x, y) => { double dx = x - cx, dy = y - cy; return f(cx + dx * c + dy * s, cy - dx * s + dy * c); };
    }

    /** Распластанная шкура: тело, четыре лапы, шея и хвост. */
    static F Hide(Canvas c, double cx, double cy, double sc, double rot, double rough, int s) {
        F shape = Canvas.Union(
            Canvas.Ellipse(cx, cy, 7.5 * sc, 6.0 * sc),
            Canvas.Capsule(cx - 4 * sc, cy - 3 * sc, cx - 10.5 * sc, cy - 8 * sc, 2.6 * sc, 1.3 * sc),
            Canvas.Capsule(cx + 4 * sc, cy - 3 * sc, cx + 10.5 * sc, cy - 8 * sc, 2.6 * sc, 1.3 * sc),
            Canvas.Capsule(cx - 4 * sc, cy + 3 * sc, cx - 10 * sc, cy + 9 * sc, 2.6 * sc, 1.3 * sc),
            Canvas.Capsule(cx + 4 * sc, cy + 3 * sc, cx + 10 * sc, cy + 9 * sc, 2.6 * sc, 1.3 * sc),
            Canvas.Capsule(cx, cy - 5 * sc, cx, cy - 10 * sc, 2.2 * sc, 1.6 * sc),
            Canvas.Capsule(cx, cy + 5 * sc, cx + 1 * sc, cy + 11 * sc, 1.4 * sc, 0.8 * sc));
        return c.Rough(Rot(shape, cx, cy, rot), rough, 0.45, s);
    }

    /** Миска сбоку-сверху: тело и обод; возвращает форму верха содержимого. */
    static F Bowl(Canvas c, Mat bowl, double cx, double cy, double rx, double depth) {
        F rim = Canvas.Ellipse(cx, cy, rx, rx * 0.38);
        F body = Canvas.Union(rim, Canvas.Cut(Canvas.Ellipse(cx, cy, rx, depth), Canvas.Box(cx, cy - depth, rx + 1, depth)));
        c.Fill(body, bowl, 2.5);
        return Canvas.Ellipse(cx, cy, rx - 1.6, rx * 0.38 - 1.0);
    }

    static void Pie(Canvas c, Mat crust, Mat filling, string lattice, string path) {
        c.Fill(Canvas.Ellipse(16, 17, 13, 9.5), crust, 3);
        c.Fill(Canvas.Ellipse(16, 16.5, 10, 6.8), filling, 2);
        for (int i = -2; i <= 2; i++) { c.Line(11 + i * 3, 10.5, 15 + i * 3, 22.5, lattice); }
        c.Line(7, 14, 25, 14, lattice); c.Line(7, 19, 25, 19, lattice);
        c.Save(path);
    }

    static void Fish(Canvas c, Mat body, string speck, double specks, string path) {
        F fish = Canvas.Union(Canvas.Ellipse(14, 17, 10, 5, -20), Canvas.Poly(23, 12, 29, 7, 28, 15, 30, 19, 24, 15));
        c.Fill(fish, body, 2.5);
        c.Fill(Canvas.Poly(11, 12, 15, 9, 17, 13), body, 1);
        if (specks > 0) { c.Scatter(fish, speck, specks, 2); } else { c.Line(8, 19, 18, 15, speck); }
        c.Dot(7, 18, "#101010"); c.Save(path);
    }

    static void Herbs(Canvas c, Mat leaf, string path) {
        Mat stem = new Mat("#24300e", "#56702c", "#88a050", 0.1, 1.0, 5);
        for (int i = 0; i < 5; i++) { double tx = 9 + i * 3.5, ty = 6 + (i % 2) * 2; c.Fill(Canvas.Capsule(16, 26, tx, ty, 0.8), stem, 1); }
        for (int i = 0; i < 7; i++) { double lx = 8 + i * 2.6, ly = 9 + (i % 3) * 3; c.Fill(c.Rough(Canvas.Ellipse(lx, ly, 3, 1.7, 40 - i * 15), 0.4, 1.0, i), leaf, 1.5); }
        c.Fill(Canvas.Box(16, 21, 3.2, 1.4, 0, 0.5), new Mat("#5a3a1a", "#a8743e", "#d8a666", 0.05, 0.6, 4), 1);
        c.Save(path);
    }

    static void Soup(Canvas c, Mat broth, string chunkA, string chunkB, string path) {
        F top = Bowl(c, Wood, 16, 16, 12, 10); c.Fill(top, broth, 2);
        c.Dot(12, 15, chunkA); c.Dot(13, 15, chunkA); c.Dot(11, 15, chunkA); c.Dot(18, 17, chunkA); c.Dot(19, 17, chunkA);
        c.Dot(16, 13, chunkB); c.Dot(21, 15, chunkB); c.Dot(10, 17, chunkB); c.Dot(15, 18, chunkB);
        c.Save(path);
    }

    public static void Run(string root) {
        string mat = Path.Combine(root, "materials"), min = Path.Combine(root, "minerals"), tools = Path.Combine(root, "tools"), food = Path.Combine(root, "food");
        Canvas c;

        // ---------- шкуры и кожа
        c = new Canvas(11); { F h = Hide(c, 16, 16, 1.0, -12, 0.8, 1); c.Fill(h, RawRed, 3); c.Scatter(h, "#d88a76", 0.06, 2); } c.Save(Path.Combine(mat, "raw_hide.png"));
        c = new Canvas(12); { F h = Hide(c, 16, 16, 1.0, 14, 0.8, 1); c.Fill(h, SaltRed, 3); c.Scatter(h, "#f4f6f8", 0.16, 3); c.Scatter(h, "#c8d2da", 0.06, 4); } c.Save(Path.Combine(mat, "salted_hide.png"));
        c = new Canvas(13); { F h = Hide(c, 16, 16, 1.0, -8, 0.8, 1); c.Fill(h, Lime, 3); c.Scatter(h, "#f2f0e6", 0.10, 3); } c.Save(Path.Combine(mat, "limed_hide.png"));
        c = new Canvas(14); { F h = Hide(c, 16, 16, 0.95, 10, 0.5, 1); c.Fill(h, Pelt, 3.5); } c.Save(Path.Combine(mat, "pelt.png"));
        c = new Canvas(15); { F h = Hide(c, 16, 16, 0.95, -15, 0.6, 1); c.Fill(h, Wet, 3); c.Line(10, 12, 15, 9, "#b88050"); c.Line(17, 20, 21, 17, "#a87040"); } c.Save(Path.Combine(mat, "wet_leather.png"));
        c = new Canvas(16); { F piece = c.Rough(Canvas.Box(16, 17, 9, 7, -18, 2), 0.5, 0.5, 2); c.Fill(piece, Leather, 3);
            F fold = Canvas.Poly(20, 9, 25.5, 13, 21.5, 15.5); c.Fill(fold, Tan, 1.5); } c.Save(Path.Combine(mat, "tanned_leather.png"));
        c = new Canvas(17); { F piece = c.Rough(Canvas.Box(16, 17, 9, 7, 16, 3), 0.7, 0.45, 3); c.Fill(piece, Tan, 4);
            F fold = Canvas.Poly(8, 12, 13.5, 9, 12, 15); c.Fill(fold, Pelt, 1.5); } c.Save(Path.Combine(mat, "soft_leather.png"));
        c = new Canvas(18); { F h = Hide(c, 16, 16, 0.85, 20, 0.9, 1); c.Fill(h, Rawhide, 2); c.Scatter(h, "#f4dca0", 0.05, 2); } c.Save(Path.Combine(mat, "rawhide.png"));
        c = new Canvas(19); { F h = Hide(c, 16, 16, 1.0, -10, 1.4, 1); c.Fill(h, Fur, 3); c.Scatter(h, "#d4b48a", 0.07, 2); } c.Save(Path.Combine(mat, "dressed_fur.png"));
        c = new Canvas(20); { F plate = Canvas.Box(16, 16, 8.5, 10, -10, 4); c.Fill(plate, Boiled, 3.5);
            int[] rivets = { 11, 10, 21, 8, 12, 23, 22, 21 };
            for (int i = 0; i < rivets.Length; i += 2) { c.Dot(rivets[i], rivets[i + 1], "#c8ccd0"); c.Dot(rivets[i] + 1, rivets[i + 1] + 1, "#40444a"); } } c.Save(Path.Combine(mat, "boiled_leather.png"));
        c = new Canvas(21); { c.Fill(c.Rough(Canvas.Box(11, 12, 5, 3.5, -25, 1), 0.6, 0.6, 1), Leather, 2);
            c.Fill(c.Rough(Canvas.Box(21, 14, 4.5, 3, 30, 1), 0.6, 0.6, 2), Tan, 2);
            c.Fill(c.Rough(Canvas.Box(15, 22, 5.5, 3, 8, 1), 0.6, 0.6, 3), Leather, 2); } c.Save(Path.Combine(mat, "leather_scraps.png"));
        c = new Canvas(22); { F ring = Canvas.Cut(Canvas.Ellipse(15, 16, 10, 8), Canvas.Ellipse(15, 16, 6, 4.2));
            c.Fill(ring, Leather, 2); c.Fill(Canvas.Capsule(23, 19, 28, 25, 2.2, 1.8), Leather, 2);
            c.Line(8, 13, 11, 10, "#ae7444"); } c.Save(Path.Combine(mat, "leather_strap.png"));
        c = new Canvas(23); { F pile = c.Rough(Canvas.Union(Canvas.Ellipse(12, 19, 6.5, 4.5), Canvas.Ellipse(20, 18, 6, 5), Canvas.Ellipse(16, 13, 5, 4)), 0.8, 0.6, 1);
            c.Fill(pile, Flesh, 2.5); c.Scatter(pile, "#f8e0d4", 0.10, 2); } c.Save(Path.Combine(mat, "flesh.png"));
        c = new Canvas(24); { F top = Bowl(c, Wood, 16, 17, 10, 9); c.Fill(top, Amber, 2); c.Line(13, 16, 18, 15, "#fcd486"); } c.Save(Path.Combine(mat, "hide_glue.png"));
        c = new Canvas(25); { F a = c.Rough(Canvas.Capsule(7, 22, 24, 9, 3.3), 0.7, 0.8, 1); c.Fill(a, Bark, 2);
            F b = c.Rough(Canvas.Capsule(11, 26, 26, 17, 2.6), 0.6, 0.8, 2); c.Fill(b, Bark, 2);
            c.Line(9, 21, 22, 11, "#1e140a"); c.Line(13, 25, 24, 18, "#1e140a"); } c.Save(Path.Combine(mat, "bark.png"));
        c = new Canvas(26); { F body = Canvas.Union(Canvas.Box(16, 19, 9, 5), Canvas.Ellipse(16, 24, 9, 3.4)); c.Fill(body, Wax, 2);
            c.Fill(Canvas.Ellipse(16, 14, 9, 3.4), Wax, 2.5); c.Line(11, 13, 15, 12, "#fff0b0"); } c.Save(Path.Combine(mat, "beeswax.png"));

        c = new Canvas(27); { // дрова: три колотых полена — кора и светлый срез
            Mat cut = new Mat("#6e4a26", "#c49460", "#ecc890", 0.10, 0.9, 5);
            c.Fill(c.Rough(Canvas.Capsule(6, 22, 22, 22, 3.6), 0.4, 0.9, 1), Bark, 2.5); c.Fill(Canvas.Ellipse(23, 22, 2.6, 3.6), cut, 1.5);
            c.Fill(c.Rough(Canvas.Capsule(10, 15, 26, 15, 3.6), 0.4, 0.9, 2), Bark, 2.5); c.Fill(Canvas.Ellipse(27, 15, 2.6, 3.6), cut, 1.5);
            c.Fill(c.Rough(Canvas.Capsule(5, 9, 19, 11, 3.2), 0.4, 0.9, 3), Bark, 2.5); c.Fill(Canvas.Ellipse(20, 11, 2.3, 3.2), cut, 1.5);
            c.Dot(23, 22, "#8a5c30"); c.Dot(27, 15, "#8a5c30"); c.Dot(20, 11, "#8a5c30"); } c.Save(Path.Combine(mat, "firewood.png"));

        // ---------- минералы
        c = new Canvas(31); { F pile = c.Rough(Canvas.Cut(Canvas.Ellipse(16, 23, 12, 11), Canvas.Box(16, 30, 14, 5)), 0.8, 0.7, 1);
            c.Fill(pile, Salt, 3); c.Scatter(pile, "#9aaab6", 0.06, 2); c.Scatter(pile, "#ffffff", 0.10, 3); } c.Save(Path.Combine(min, "salt.png"));
        c = new Canvas(32); { F pile = c.Rough(Canvas.Cut(Canvas.Ellipse(16, 23, 12, 10), Canvas.Box(16, 30, 14, 5)), 1.0, 0.7, 1);
            c.Fill(pile, Ash, 3); c.Scatter(pile, "#1c1a18", 0.08, 2); c.Scatter(pile, "#c8c4be", 0.05, 3); } c.Save(Path.Combine(min, "ash.png"));
        c = new Canvas(33); {
            c.Fill(Canvas.Poly(9, 24, 12, 11, 16, 9, 17, 24), Alum, 1.5);
            c.Fill(Canvas.Poly(15, 25, 18, 7, 22, 5, 24, 25), Alum, 1.5);
            c.Fill(Canvas.Poly(21, 26, 24, 15, 27, 14, 27, 26), Alum, 1.5);
            c.Line(13, 12, 14, 22, "#ffffff"); c.Line(19, 9, 20, 22, "#ffffff"); } c.Save(Path.Combine(min, "alum.png"));

        // ---------- инструменты (по диагонали, рукоять внизу слева)
        c = new Canvas(41); {
            c.Fill(Canvas.Capsule(6, 26, 12, 20, 2.0), Wood, 2);
            c.Fill(Canvas.Capsule(11, 22, 14, 18, 1.4), Iron, 1.5);
            c.Fill(Canvas.Poly(13, 18, 15, 16, 27, 5, 25.5, 9.5), Steel, 1.5);
            c.Line(15, 17, 26, 6, "#f4f8fc"); } c.Save(Path.Combine(tools, "knife.png"));
        c = new Canvas(42); { // мездряк: дугой изогнутый клинок с двумя рукоятями
            F arc = Canvas.Cut(Canvas.Cut(Canvas.Ellipse(16, 22, 11, 10), Canvas.Ellipse(16, 23, 9, 8)), Canvas.Box(16, 28, 14, 6));
            c.Fill(arc, Steel, 1.2);
            c.Fill(Canvas.Capsule(5, 22, 4, 28, 1.8), Wood, 2); c.Fill(Canvas.Capsule(27, 22, 28, 28, 1.8), Wood, 2); } c.Save(Path.Combine(tools, "fleshing_knife.png"));
        c = new Canvas(43); { // струг: прямое широкое лезвие и рукояти вверх
            c.Fill(Canvas.Box(16, 18, 10, 2.2, -8), Iron, 1.5);
            c.Line(7, 20, 25, 17, "#c8d0d8");
            c.Fill(Canvas.Capsule(6, 20, 4, 11, 1.9), Wood, 2); c.Fill(Canvas.Capsule(26, 17, 28, 8, 1.9), Wood, 2); } c.Save(Path.Combine(tools, "scraper.png"));
        c = new Canvas(44); {
            c.Fill(Canvas.Capsule(7, 26, 18, 15, 1.6), Wood, 2);
            c.Fill(Canvas.Box(21, 11, 7, 4.2, -45, 1.5), Rawhide, 3); } c.Save(Path.Combine(tools, "leather_mallet.png"));
        c = new Canvas(45); {
            c.Fill(Canvas.Capsule(14, 18, 26, 5, 1.5), Wood, 2);
            c.Fill(Canvas.Ellipse(10, 22, 6.5, 5.5, -20), Wood, 3);
            c.Fill(Canvas.Ellipse(9.5, 21.5, 4.2, 3.2, -20), DarkWood, 2); } c.Save(Path.Combine(tools, "ladle.png"));
        c = new Canvas(46); { // дымарь: жестяная банка с носиком и кожаные меха сбоку
            c.Fill(Canvas.Box(13, 18, 5.5, 8, 0, 1.5), Iron, 2.5);
            c.Fill(Canvas.Poly(9, 10, 17, 10, 14.5, 5, 11.5, 5), Steel, 1.5);
            c.Fill(Canvas.Poly(19, 13, 28, 18, 28, 24, 19, 24), Leather, 2);
            c.Fill(Canvas.Box(24, 26, 4, 1.2), Wood, 1); c.Line(20, 15, 27, 19, "#ae7444"); } c.Save(Path.Combine(tools, "bee_smoker.png"));

        c = new Canvas(47); { // серп: короткая рукоять и серповидный клинок
            c.Fill(Canvas.Capsule(6, 27, 12, 21, 1.9), Wood, 2);
            F blade = Canvas.Cut(Canvas.Cut(Canvas.Ellipse(19, 14, 10, 9), Canvas.Ellipse(20.5, 16, 8.5, 7.5)), Canvas.Box(9, 22, 6, 5));
            c.Fill(blade, Steel, 1.2); c.Fill(Canvas.Capsule(11, 22, 13, 20, 1.3), Iron, 1); } c.Save(Path.Combine(tools, "sickle.png"));
        c = new Canvas(48); { // коса: длинное косовище с ручкой и лезвие сверху
            c.Fill(Canvas.Capsule(6, 29, 20, 5, 1.3), Wood, 1.5);
            c.Fill(Canvas.Capsule(10, 20, 14, 22, 1.2), Wood, 1);
            F blade = Canvas.Cut(Canvas.Cut(Canvas.Ellipse(16, 13, 13, 9), Canvas.Ellipse(15, 15.5, 12.5, 8)), Canvas.Box(16, 20, 16, 6));
            c.Fill(Canvas.Cut(blade, Canvas.Box(27, 8, 6, 8)), Steel, 1.2); } c.Save(Path.Combine(tools, "scythe.png"));
        c = new Canvas(49); { // набор ключника: кожаный свёрток с отмычками
            c.Fill(Canvas.Box(15, 19, 10, 6.5, -10, 2.5), Leather, 3);
            for (int i = 0; i < 4; i++) { double x = 10 + i * 3.5; c.Fill(Canvas.Capsule(x, 16, x + 2, 6 + (i % 2) * 2, 0.8), Steel, 1); c.Dot((int)(x + 2), 5 + (i % 2) * 2, "#9aa2aa"); }
            c.Fill(Canvas.Box(15, 18, 10.5, 1.2, -10), Tan, 1); } c.Save(Path.Combine(tools, "locksmith_kit.png"));

        // ---------- еда
        c = new Canvas(57); { // мёд: глиняный горшок, медовая шапка и потёк по боку
            Mat clay = new Mat("#4a2414", "#9a5a36", "#d08c5c", 0.10, 0.8, 6);
            F pot = Canvas.Union(Canvas.Ellipse(16, 20, 9.5, 8.5), Canvas.Box(16, 11.5, 6.5, 2.2, 0, 1));
            c.Fill(pot, clay, 3);
            c.Fill(Canvas.Ellipse(16, 10.5, 7, 2.6), Amber, 1.5);
            c.Fill(Canvas.Union(Canvas.Capsule(11, 11, 10.5, 17, 1.4, 1.1), Canvas.Ellipse(10.5, 17.5, 1.5, 1.8)), Amber, 1);
            c.Line(13, 10, 17, 9.5, "#fcd486"); c.Line(9, 22, 11, 26, "#e0a070"); } c.Save(Path.Combine(food, "honey.png"));
        c = new Canvas(58); { // тесто: мягкий ком, присыпанный мукой
            F lump = c.Rough(Canvas.Union(Canvas.Cut(Canvas.Ellipse(16, 20, 11, 8.5), Canvas.Box(16, 30, 14, 3)), Canvas.Ellipse(14, 15, 6, 4.5)), 0.7, 0.5, 1);
            c.Fill(lump, Dough, 4.5);
            c.Scatter(lump, "#ffffff", 0.07, 2); c.Line(18, 17, 22, 19, "#bfae86"); } c.Save(Path.Combine(food, "dough.png"));
        c = new Canvas(59); { // ржаное тесто: тёмный ком, присыпанный мукой
            Mat ryeDough = new Mat("#4e3a26", "#8e6e4c", "#bc9c76", 0.08, 0.5, 6);
            F lump = c.Rough(Canvas.Union(Canvas.Cut(Canvas.Ellipse(16, 20, 11, 8.5), Canvas.Box(16, 30, 14, 3)), Canvas.Ellipse(18, 15, 6, 4.5)), 0.7, 0.5, 4);
            c.Fill(lump, ryeDough, 4.5);
            c.Scatter(lump, "#e8dcc4", 0.07, 5); c.Line(11, 18, 15, 16, "#6a5036"); } c.Save(Path.Combine(food, "rye_dough.png"));
        c = new Canvas(51); { F top = Bowl(c, Wood, 16, 16, 12, 10); c.Fill(top, Porridge, 2); c.Fill(Canvas.Box(17, 14, 2.5, 1.5, 0, 0.5), Butter, 1); } c.Save(Path.Combine(food, "porridge.png"));
        c = new Canvas(52); { F top = Bowl(c, Wood, 16, 16, 12, 10); c.Fill(top, Broth, 2);
            c.Fill(Canvas.Ellipse(12, 15, 2.6, 1.4), Pelt, 1); c.Fill(Canvas.Ellipse(19, 16.5, 2.4, 1.3), Pelt, 1);
            c.Dot(15, 13, "#e06a24"); c.Dot(21, 14, "#e06a24"); c.Dot(10, 17, "#4e7a28"); c.Dot(17, 18, "#4e7a28"); } c.Save(Path.Combine(food, "fish_soup.png"));
        c = new Canvas(53); { F pie = Canvas.Ellipse(16, 17, 13, 9.5); c.Fill(pie, Crust, 3);
            F fill = Canvas.Ellipse(16, 16.5, 10, 6.8); c.Fill(fill, MushFill, 2);
            for (int i = -2; i <= 2; i++) { c.Line(11 + i * 3, 10.5, 15 + i * 3, 22.5, "#d8a058"); }
            c.Line(7, 14, 25, 14, "#d8a058"); c.Line(7, 19, 25, 19, "#d8a058"); } c.Save(Path.Combine(food, "mushroom_pie.png"));
        c = new Canvas(54); { F lump = c.Rough(Canvas.Union(Canvas.Ellipse(15, 18, 10, 7), Canvas.Ellipse(19, 14, 6, 5)), 1.2, 0.6, 1);
            c.Fill(lump, Char, 3); c.Scatter(lump, "#d0601c", 0.05, 2); c.Scatter(lump, "#f09030", 0.02, 3); } c.Save(Path.Combine(food, "burnt_food.png"));
        c = new Canvas(55); { F bun = Canvas.Cut(Canvas.Ellipse(16, 20, 11, 9), Canvas.Box(16, 30, 14, 5)); c.Fill(bun, Dough, 4);
            c.Line(11, 15, 14, 13, "#c8b688"); } c.Save(Path.Combine(food, "raw_bun.png"));
        c = new Canvas(56); { F bun = Canvas.Cut(Canvas.Ellipse(16, 20, 11, 9), Canvas.Box(16, 30, 14, 5)); c.Fill(bun, Bun, 4);
            F glaze = c.Rough(Canvas.Ellipse(16, 14.5, 6.5, 3.2), 0.6, 0.8, 2); c.Fill(glaze, Glaze, 1.5);
            c.Dot(14, 14, "#b02840"); c.Dot(18, 15, "#b02840"); } c.Save(Path.Combine(food, "sweet_bun.png"));
        // ---------- выпечка: общая форма пирога — круглый, сверху решётка из теста
        Mat rawCrust = new Mat("#857454", "#d8c69a", "#f6ead0", 0.06, 0.6, 6);
        Mat meatFill = new Mat("#3e140c", "#7e3020", "#b05a3c", 0.18, 1.0, 5);
        Mat berryFill = new Mat("#2a0a1a", "#6a1a36", "#a83a5a", 0.14, 1.0, 5);
        Pie(new Canvas(61), rawCrust, meatFill, "#efe0bc", Path.Combine(food, "raw_pie.png"));
        Pie(new Canvas(62), Crust, meatFill, "#d8a058", Path.Combine(food, "meat_pie.png"));
        Pie(new Canvas(63), Crust, berryFill, "#d8a058", Path.Combine(food, "berry_pie.png"));

        // ---------- мука: завязанный мешочек, сверху мука
        c = new Canvas(64); { Mat burlap = new Mat("#4e3a20", "#9a7e52", "#ccb284", 0.18, 1.2, 6);
            F sack = Canvas.Union(Canvas.Ellipse(16, 21, 9.5, 8), Canvas.Box(16, 12, 5, 4, 0, 2));
            c.Fill(sack, burlap, 3); c.Fill(Canvas.Box(16, 13.5, 6, 1.2), Wood, 1);
            c.Fill(Canvas.Ellipse(16, 9, 4.5, 2.2), Salt, 1.5); c.Scatter(sack, "#7a6240", 0.05, 2); } c.Save(Path.Combine(food, "wheat_flour.png"));

        // ---------- мясо и рыба
        Mat meat = new Mat("#4a160e", "#963828", "#d06c50", 0.14, 0.9, 6);
        Mat smoked = new Mat("#2a1208", "#6a3018", "#a85e30", 0.16, 0.9, 6);
        Mat fat = new Mat("#a89880", "#ece0cc", "#ffffff", 0.04, 0.6, 4);
        c = new Canvas(65); { F slab = c.Rough(Canvas.Box(16, 17, 10, 6.5, -14, 2.5), 0.5, 0.6, 1); c.Fill(slab, meat, 3);
            c.Fill(c.Rough(Canvas.Box(15, 11.5, 9.5, 1.5, -14, 1), 0.3, 0.8, 2), fat, 1);
            c.Scatter(slab, "#f6f8fa", 0.12, 3); } c.Save(Path.Combine(food, "salted_meat.png"));
        c = new Canvas(66); { F ham = c.Rough(Canvas.Ellipse(14, 19, 9, 7.5, -30), 0.5, 0.6, 1); c.Fill(ham, smoked, 3);
            c.Fill(Canvas.Capsule(20, 13, 26, 7, 1.6), fat, 1.5); c.Fill(Canvas.Ellipse(26.5, 6.5, 2.2, 2.0), fat, 1);
            c.Line(9, 16, 14, 13, "#c07a44"); } c.Save(Path.Combine(food, "smoked_meat.png"));
        Mat silver = new Mat("#363e46", "#8a96a2", "#e2eaf0", 0.06, 0.8, 6);
        Mat gold = new Mat("#4e2c0c", "#a8702a", "#e6b45a", 0.08, 0.8, 6);
        Fish(new Canvas(67), silver, "#f6f8fa", 0.10, Path.Combine(food, "salted_fish.png"));
        Fish(new Canvas(68), gold, "#f0c878", 0.0, Path.Combine(food, "smoked_fish.png"));

        // ---------- грибы и травы
        c = new Canvas(69); { Mat cap = new Mat("#24160c", "#5a3a22", "#8e6a46", 0.18, 1.0, 5);
            c.Line(5, 9, 27, 13, "#c8b088");
            c.Fill(c.Rough(Canvas.Ellipse(9, 14, 4.5, 3.6), 0.6, 0.9, 1), cap, 2);
            c.Fill(c.Rough(Canvas.Ellipse(17, 17, 4.8, 3.8), 0.6, 0.9, 2), cap, 2);
            c.Fill(c.Rough(Canvas.Ellipse(24, 20, 4.2, 3.4), 0.6, 0.9, 3), cap, 2); } c.Save(Path.Combine(food, "dried_mushrooms.png"));
        Mat green = new Mat("#1a3410", "#4a7e2c", "#8cc25c", 0.16, 1.2, 6);
        Mat olive = new Mat("#34320e", "#76723a", "#aca462", 0.16, 1.2, 6);
        Herbs(new Canvas(70), green, Path.Combine(food, "herbs.png"));
        Herbs(new Canvas(71), olive, Path.Combine(food, "dried_herbs.png"));

        // ---------- отвар: склянка с пробкой
        c = new Canvas(72); { Mat glass = new Mat("#3c5a5a", "#94c0bc", "#e8fffc", 0.0, 0.5, 5);
            Mat tea = new Mat("#3e3a0c", "#8a8228", "#c8be56", 0.04, 0.6, 5);
            c.Fill(Canvas.Union(Canvas.Ellipse(16, 21, 7.5, 7.5), Canvas.Box(16, 11, 2.6, 4)), glass, 2);
            c.Fill(Canvas.Cut(Canvas.Ellipse(16, 21, 6, 6), Canvas.Box(16, 15, 8, 2.5)), tea, 2);
            c.Fill(Canvas.Box(16, 6.5, 2.2, 1.8, 0, 0.6), Wood, 1); c.Line(12, 18, 13, 22, "#ffffff"); } c.Save(Path.Combine(food, "herbal_tea.png"));

        // ---------- похлёбки в той же миске, что каша и уха
        Soup(new Canvas(73), new Mat("#5a1410", "#a43828", "#dc6c54", 0.06, 0.6, 5), "#e88a30", "#5a8a30", Path.Combine(food, "vegetable_soup.png"));
        Soup(new Canvas(74), new Mat("#4a3018", "#8a6038", "#c09060", 0.06, 0.6, 5), "#d8c4a0", "#6a4a2a", Path.Combine(food, "mushroom_soup.png"));
        Soup(new Canvas(75), new Mat("#3e2410", "#7a4a24", "#b07a44", 0.06, 0.6, 5), "#8a3a28", "#e07a2a", Path.Combine(food, "meat_stew.png"));

        // ---------- медовые соты: шестигранные ячейки
        c = new Canvas(76); { F comb = c.Rough(Canvas.Box(16, 17, 10, 8, -10, 3), 0.5, 0.6, 1); c.Fill(comb, Wax, 3);
            for (int row = 0; row < 4; row++) for (int col = 0; col < 5; col++) {
                int x = 9 + col * 4 + (row % 2) * 2, y = 11 + row * 4;
                c.Dot(x, y, "#8a5a10"); c.Dot(x + 1, y, "#8a5a10"); c.Dot(x, y + 1, "#a8701a");
            } } c.Save(Path.Combine(food, "honeycomb_full.png"));
    }
}
