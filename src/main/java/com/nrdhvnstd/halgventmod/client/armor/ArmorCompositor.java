package com.nrdhvnstd.halgventmod.client.armor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.Blood;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ModArmor;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Склейка надетых вещей в одну текстуру на плоскость — только на клиенте.
 * Рецепт образа (вещи плоскости по порядку отрисовки) — ключ кэша: одинаково одетые игроки
 * делят одну текстуру. Нижние слои перекрываются непрозрачными пикселями верхних.
 */
public final class ArmorCompositor {
    // ===== Настройки =====
    public static final int WIDTH = 128;               // размер развёртки плоскости
    public static final int HEIGHT = 64;
    private static final int ALPHA_CUTOFF = 128;        // пиксель с меньшей прозрачностью считается пустым
    private static final long RELEASE_AFTER_MS = 3 * 60 * 1000; // неиспользуемая склейка живёт 3 минуты
    private static final String OVERLAY_SUFFIX = "_overlay";
    private static final int PIECE_FALLBACK_SIZE = 16;   // холст детали, если её текстуры нет
    private static final String PNG = ".png";

    // Участки развёртки 128×64 (двойная ванильная 64×32) — по ним решается, какая часть тела закрыта
    //                                                 x1   y1   x2   y2
    private static final int[] HEAD_AREA = {0, 0, 64, 32};
    private static final int[] BODY_AREA = {32, 32, 80, 64};
    private static final int[] ARM_AREA = {80, 32, 112, 64};
    private static final int[] LEG_AREA = {0, 32, 32, 64};

    /** Участки тела, на которых есть хоть один непрозрачный пиксель. */
    public static final int COVERS_HEAD = 1;
    public static final int COVERS_BODY = 2;
    public static final int COVERS_ARMS = 4;
    public static final int COVERS_LEGS = 8;

    /**
     * Одна вещь в рецепте: основа, её цвет (ARGB, NO_COLOR — не окрашена), способ окраски, слой покрытия
     * (эмаль или роспись, только у COATED) и неокрашиваемые детали поверх (null — их нет).
     * Позже сюда добавятся украшения (этап 6) — между основой (с покрытием) и overlay.
     */
    public record Layer(ResourceLocation texture, int color, ModArmor.Dye dye,
                        @Nullable ResourceLocation coating, @Nullable ResourceLocation overlay) {
        public static final int NO_COLOR = -1;
    }

    /** Ключ кэша склеек. */
    sealed interface Key permits Recipe, PieceRecipe {}

    /** Рецепт образа одной плоскости; blood — сколько на ней крови, 0…3 (combat/Blood). */
    public record Recipe(ArmorSlot.Plane plane, List<Layer> layers, int blood) implements Key {}

    /** Текстура окрашенной детали или плаща: основа → покрытие (если покрашена) → overlay. */
    public record PieceRecipe(Layer layer) implements Key {}

    /** Готовая склейка: текстура и закрытые участки тела. */
    public static final class Composite {
        private final ResourceLocation texture;
        private final int covers;
        private long lastUsed;
        private boolean released; // текстура освобождена, склейку надо взять из кэша заново

        private Composite(ResourceLocation texture, int covers) {
            this.texture = texture;
            this.covers = covers;
        }

        boolean released() {
            return released;
        }

        void touch() {
            lastUsed = Util.getMillis();
        }

        public ResourceLocation texture() {
            return texture;
        }

        public boolean covers(int part) {
            return (covers & part) != 0;
        }
    }

    private static final Map<Key, Composite> CACHE = new HashMap<>();
    // Исходные текстуры вещей; Optional.empty() — файла нет (предупреждение уже было)
    private static final Map<ResourceLocation, Optional<NativeImage>> SOURCES = new HashMap<>();
    private static int nextId;

    private ArmorCompositor() {}

    /** Склейка плоскости для надетой брони или null, если на плоскости ничего нет. */
    @Nullable
    public static Composite get(ArmorInventory armor, ArmorSlot.Plane plane) {
        return ArmorLook.of(armor).composite(plane);
    }

    static Composite get(Key key) {
        Composite composite = CACHE.get(key);
        if (composite == null) {
            composite = key instanceof Recipe recipe ? build(recipe) : build((PieceRecipe) key);
            CACHE.put(key, composite);
        }
        composite.touch();
        return composite;
    }

    // Надетые вещи плоскости, от внутренней к внешней. Считается только при смене брони (ArmorLook)
    @Nullable
    static Recipe recipe(ArmorInventory armor, ArmorSlot.Plane plane) {
        List<ArmorSlot> worn = new ArrayList<>(ArmorSlot.COUNT);
        for (ArmorSlot slot : ArmorSlot.values()) {
            if (slot.plane() == plane && !armor.get(slot).isEmpty()) {
                worn.add(slot);
            }
        }
        if (worn.isEmpty()) {
            return null;
        }
        worn.sort(Comparator.comparingInt(slot -> order(armor.get(slot), slot)));
        List<Layer> layers = new ArrayList<>(worn.size());
        float blood = 0;
        for (ArmorSlot slot : worn) {
            layers.add(layer(armor.get(slot), slot));
            blood = Math.max(blood, Blood.level(armor.get(slot)));
        }
        int stains = blood < Blood.VISIBLE ? 0 : Math.min(3, 1 + (int) ((blood - Blood.VISIBLE) / ((1 - Blood.VISIBLE) / 3)));
        return new Recipe(plane, List.copyOf(layers), stains);
    }

    private static Layer layer(ItemStack stack, ArmorSlot slot) {
        return layer(stack, texture(stack, slot));
    }

    /**
     * Слой вещи по её текстуре (плоской или выступающей детали). Окрашиваемая вещь: цвет из компонента
     * dyed_color (без него — не окрашена), файл _overlay, у вещи с покрытием ещё _enamel или _painted.
     */
    static Layer layer(ItemStack stack, ResourceLocation texture) {
        ModArmor.ArmorInfo info = ModArmor.get(stack);
        if (info == null || !info.dyeable()) {
            return new Layer(texture, Layer.NO_COLOR, ModArmor.Dye.NONE, null, null);
        }
        ModArmor.Coating coating = info.coating(stack);
        ResourceLocation coatingTexture = coating != null ? withSuffix(texture, coating.suffix()) : null;
        return new Layer(texture, color(stack), info.dye(), coatingTexture, withSuffix(texture, OVERLAY_SUFFIX));
    }

    /** Цвет окрашиваемой вещи (ARGB) или Layer.NO_COLOR, если она не окрашена. */
    public static int color(ItemStack stack) {
        return DyedItemColor.getOrDefault(stack, Layer.NO_COLOR);
    }

    // textures/armor/helmet/x.png → textures/armor/helmet/x_overlay.png
    private static ResourceLocation withSuffix(ResourceLocation texture, String suffix) {
        String path = texture.getPath();
        return texture.withPath(path.substring(0, path.length() - PNG.length()) + suffix + PNG);
    }

    private static int order(ItemStack stack, ArmorSlot slot) {
        ModArmor.ArmorInfo info = ModArmor.get(stack.getItem());
        return info != null ? info.order() : slot.defaultOrder();
    }

    /** Плоская текстура вещи: textures/armor/<папка слота>/<id>.png */
    public static ResourceLocation texture(ItemStack stack, ArmorSlot slot) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                "textures/armor/" + slot.folder() + "/" + id.getPath() + ".png");
    }

    private static Composite build(Recipe recipe) {
        NativeImage canvas = new NativeImage(WIDTH, HEIGHT, true); // прозрачный холст
        for (Layer layer : recipe.layers()) {
            paintLayer(canvas, layer);
        }
        if (recipe.blood() > 0) {
            stain(canvas, recipe.blood(), recipe.plane().ordinal());
        }
        int covers = 0;
        if (recipe.plane() == ArmorSlot.Plane.OUTER) {
            covers |= opaque(canvas, HEAD_AREA) ? COVERS_HEAD : 0;
            covers |= opaque(canvas, BODY_AREA) ? COVERS_BODY : 0;
            covers |= opaque(canvas, ARM_AREA) ? COVERS_ARMS : 0;
        } else {
            covers |= opaque(canvas, LEG_AREA) ? COVERS_LEGS : 0;
        }

        return register(canvas, covers);
    }

    /**
     * Кровь на броне (по образцу KCC, BloodiedTextureCache): поверх склейки — бурые пятна и брызги по непрозрачным
     * пикселям. Пятна — крупный шум (кляксы) плюс мелкий (брызги); чем больше крови (1…3), тем ниже порог — пятен больше.
     * Одинаковые для одного уровня — склейка кэшируется.
     */
    private static void stain(NativeImage canvas, int level, int seed) {
        float threshold = switch (level) {
            case 1 -> 0.72F;
            case 2 -> 0.6F;
            default -> 0.48F;
        };
        for (int y = 0; y < canvas.getHeight(); y++) {
            for (int x = 0; x < canvas.getWidth(); x++) {
                int pixel = canvas.getPixelRGBA(x, y); // ABGR
                if ((pixel >>> 24) == 0) {
                    continue;
                }
                float n = 0.65F * smoothNoise(x / 5.0F, y / 5.0F, seed) + 0.35F * hash(x, y, seed + 7);
                if (n < threshold) {
                    continue;
                }
                float strength = Math.min(1, (n - threshold) / 0.12F) * BLOOD_ALPHA;
                // Сухая — темнее и буроватая, свежая — краснее; берём середину
                int r = (int) ((pixel & 0xFF) * (1 - strength) + BLOOD_R * strength);
                int g = (int) ((pixel >> 8 & 0xFF) * (1 - strength) + BLOOD_G * strength);
                int b = (int) ((pixel >> 16 & 0xFF) * (1 - strength) + BLOOD_B * strength);
                canvas.setPixelRGBA(x, y, (pixel & 0xFF000000) | b << 16 | g << 8 | r);
            }
        }
    }

    private static final int BLOOD_R = 0x6E, BLOOD_G = 0x0C, BLOOD_B = 0x0A;
    private static final float BLOOD_ALPHA = 0.85F;

    // Значение шума 0…1 в целой точке
    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 2147483647;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }

    // Гладкий шум (билинейный по сетке) — кляксы
    private static float smoothNoise(float x, float y, int seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        float a = hash(x0, y0, seed);
        float b = hash(x0 + 1, y0, seed);
        float c = hash(x0, y0 + 1, seed);
        float d = hash(x0 + 1, y0 + 1, seed);
        float top = a + (b - a) * fx;
        float bottom = c + (d - c) * fx;
        return top + (bottom - top) * fy;
    }

    // Холст детали — размера её текстуры (у деталей он любой)
    private static Composite build(PieceRecipe recipe) {
        NativeImage canvas = source(recipe.layer().texture(), true)
                .map(image -> new NativeImage(image.getWidth(), image.getHeight(), true))
                .orElseGet(() -> new NativeImage(PIECE_FALLBACK_SIZE, PIECE_FALLBACK_SIZE, true));
        paintLayer(canvas, recipe.layer());
        return register(canvas, 0);
    }

    // Внутри вещи: основа → покрытие (только если покрашена) → украшения (позже) → _overlay.
    // CLOTH красит саму основу; у COATED основа в цвете не красится, красится отдельный слой _enamel или _painted.
    private static void paintLayer(NativeImage canvas, Layer layer) {
        int baseColor = layer.dye() == ModArmor.Dye.CLOTH ? layer.color() : Layer.NO_COLOR;
        source(layer.texture(), true).ifPresent(image -> paint(canvas, image, baseColor));
        if (layer.coating() != null && layer.color() != Layer.NO_COLOR) {
            source(layer.coating(), true).ifPresent(image -> paint(canvas, image, layer.color()));
        }
        if (layer.overlay() != null) {
            source(layer.overlay(), false).ifPresent(image -> paint(canvas, image, Layer.NO_COLOR));
        }
    }

    private static Composite register(NativeImage canvas, int covers) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "armor_composite/" + nextId++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(canvas));
        return new Composite(id, covers);
    }

    // Непрозрачные пиксели вещи перекрывают холст; другой размер масштабируется до холста без сглаживания.
    // Окраска — «перекрытие» (Overlay), серый слой поверх сплошного цвета:
    // серый 128 = чистый цвет, темнее — тень, светлее — блик.
    private static void paint(NativeImage canvas, NativeImage image, int color) {
        int width = image.getWidth();
        int height = image.getHeight();
        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();
        for (int y = 0; y < canvasHeight; y++) {
            for (int x = 0; x < canvasWidth; x++) {
                int pixel = image.getPixelRGBA(x * width / canvasWidth, y * height / canvasHeight);
                if ((pixel >>> 24) >= ALPHA_CUTOFF) {
                    canvas.setPixelRGBA(x, y, (color == Layer.NO_COLOR ? pixel : tint(pixel, color)) | 0xFF000000);
                }
            }
        }
    }

    // Пиксель NativeImage — ABGR (красный в младшем байте), цвет — ARGB
    private static int tint(int abgr, int argb) {
        int r = blend(abgr & 0xFF, (argb >> 16) & 0xFF);
        int g = blend((abgr >> 8) & 0xFF, (argb >> 8) & 0xFF);
        int b = blend((abgr >> 16) & 0xFF, argb & 0xFF);
        return (abgr & 0xFF000000) | (b << 16) | (g << 8) | r;
    }

    // Один канал: base — серый из текстуры, dye — цвет краски; ветку Overlay выбирает цвет краски
    private static int blend(int base, int dye) {
        int result = dye < 128
                ? 2 * base * dye / 255
                : 255 - 2 * (255 - base) * (255 - dye) / 255;
        return Math.min(255, result);
    }

    private static boolean opaque(NativeImage canvas, int[] area) {
        for (int y = area[1]; y < area[3]; y++) {
            for (int x = area[0]; x < area[2]; x++) {
                if ((canvas.getPixelRGBA(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    // required = false — файла может не быть (у окрашиваемой вещи без неокрашиваемых деталей нет _overlay)
    private static Optional<NativeImage> source(ResourceLocation texture, boolean required) {
        return SOURCES.computeIfAbsent(texture, key -> load(key, required));
    }

    private static Optional<NativeImage> load(ResourceLocation texture, boolean required) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) {
            if (required) {
                HalgventMod.LOGGER.warn("Нет текстуры брони {}", texture);
            }
            return Optional.empty();
        }
        try (InputStream stream = resource.get().open()) {
            return Optional.of(NativeImage.read(stream));
        } catch (IOException e) {
            HalgventMod.LOGGER.warn("Не удалось прочитать текстуру брони {}", texture, e);
            return Optional.empty();
        }
    }

    /** Освободить склейки, которые давно не рисовались. */
    public static void releaseUnused() {
        long now = Util.getMillis();
        Iterator<Composite> it = CACHE.values().iterator();
        while (it.hasNext()) {
            Composite composite = it.next();
            if (now - composite.lastUsed > RELEASE_AFTER_MS) {
                Minecraft.getInstance().getTextureManager().release(composite.texture);
                composite.released = true;
                it.remove();
            }
        }
    }

    /** Очистить всё: перезагрузка ресурсов (F3+T) или выход из мира. */
    public static void clear() {
        for (Composite composite : CACHE.values()) {
            Minecraft.getInstance().getTextureManager().release(composite.texture);
            composite.released = true;
        }
        CACHE.clear();
        SOURCES.values().forEach(source -> source.ifPresent(NativeImage::close));
        SOURCES.clear();
    }
}
