using System;
using System.IO;
using F = System.Func<double, double, double>;

// Плоские текстуры блоков (Painter.cs): стадии грядок лука и чеснока. Иконки предметов — SculptIcons.cs,
// посуда — Vessels.cs.
public static class Icons {
    public static void Run(string root) {
        CropStages(root);
    }

    /** Стадия грядки лука или чеснока: перья из земли, на последней — луковица. */
    static void Stage(Canvas c, int stage, Mat bulb, Mat blade, string path) {
        double[] heights = { 7, 13, 21, 22 };
        double h = heights[stage];
        double[] xs = { 9, 13, 17, 21, 24 };
        for (int i = 0; i < xs.Length; i++) {
            double lean = (i - 2) * (1.0 + stage * 0.6);
            c.Fill(Canvas.Capsule(xs[i], 31, xs[i] + lean, 31 - h + (i % 2) * 3, 0.9), blade, 1);
        }
        if (stage == 3) {
            c.Fill(Canvas.Ellipse(16, 27, 5.5, 4.5), bulb, 2);
        }
        c.Save(path);
    }

    public static void CropStages(string root) {
        string block = Path.Combine(root, "..", "block"); Directory.CreateDirectory(block);
        Mat onionBlade = new Mat("#1e3a10", "#4e8a2e", "#8cc25c", 0.08, 0.8, 5);
        Mat garlicBlade = new Mat("#2a3a1a", "#6a8a4a", "#a8c488", 0.08, 0.8, 5);
        Mat onionBulb = new Mat("#5a3410", "#c88a3a", "#f0c878", 0.06, 0.6, 5);
        Mat garlicBulb = new Mat("#7a6e62", "#ece4d8", "#ffffff", 0.05, 0.6, 5);
        for (int s = 0; s < 4; s++) {
            Stage(new Canvas(300 + s), s, onionBulb, onionBlade, Path.Combine(block, "onion_crop_stage" + s + ".png"));
            Stage(new Canvas(310 + s), s, garlicBulb, garlicBlade, Path.Combine(block, "garlic_crop_stage" + s + ".png"));
        }
    }
}
