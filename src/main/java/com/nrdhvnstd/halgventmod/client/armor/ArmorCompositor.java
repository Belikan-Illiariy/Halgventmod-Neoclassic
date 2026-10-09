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
     * Одна вещь в рецепте: основа, её цвет (ARGB, NO_COLOR — не красится) и неокрашиваемые детали поверх
     * (null — их нет). Позже сюда добавятся украшения (этап 6) — между основой и overlay.
     */
    public record Layer(ResourceLocation texture, int color, @Nullable ResourceLocation overlay) {
        public static final int NO_COLOR = -1;
    }

    /** Рецепт образа одной плоскости — ключ кэша. */
    public record Recipe(ArmorSlot.Plane plane, List<Layer> layers) {}

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

    private static final Map<Recipe, Composite> CACHE = new HashMap<>();
    // Исходные текстуры вещей; Optional.empty() — файла нет (предупреждение уже было)
    private static final Map<ResourceLocation, Optional<NativeImage>> SOURCES = new HashMap<>();
    private static int nextId;

    private ArmorCompositor() {}

    /** Склейка плоскости для надетой брони или null, если на плоскости ничего нет. */
    @Nullable
    public static Composite get(ArmorInventory armor, ArmorSlot.Plane plane) {
        return ArmorLook.of(armor).composite(plane);
    }

    static Composite get(Recipe recipe) {
        Composite composite = CACHE.get(recipe);
        if (composite == null) {
            composite = build(recipe);
            CACHE.put(recipe, composite);
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
        for (ArmorSlot slot : worn) {
            layers.add(layer(armor.get(slot), slot));
        }
        return new Recipe(plane, List.copyOf(layers));
    }

    // Окрашиваемая вещь: цвет из компонента dyed_color (без него — как нарисована) и файл _overlay
    private static Layer layer(ItemStack stack, ArmorSlot slot) {
        ResourceLocation texture = texture(stack, slot);
        ModArmor.ArmorInfo info = ModArmor.get(stack);
        if (info == null || !info.dyeable()) {
            return new Layer(texture, Layer.NO_COLOR, null);
        }
        return new Layer(texture, color(stack), withSuffix(texture, OVERLAY_SUFFIX));
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
            // Внутри вещи: основа (окрашенная) → украшения (позже) → _overlay
            source(layer.texture(), true).ifPresent(image -> paint(canvas, image, layer.color()));
            if (layer.overlay() != null) {
                source(layer.overlay(), false).ifPresent(image -> paint(canvas, image, Layer.NO_COLOR));
            }
        }
        int covers = 0;
        if (recipe.plane() == ArmorSlot.Plane.OUTER) {
            covers |= opaque(canvas, HEAD_AREA) ? COVERS_HEAD : 0;
            covers |= opaque(canvas, BODY_AREA) ? COVERS_BODY : 0;
            covers |= opaque(canvas, ARM_AREA) ? COVERS_ARMS : 0;
        } else {
            covers |= opaque(canvas, LEG_AREA) ? COVERS_LEGS : 0;
        }

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "armor_composite/" + nextId++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(canvas));
        return new Composite(id, covers);
    }

    // Непрозрачные пиксели вещи перекрывают холст; размер, отличный от 128×64, масштабируется без сглаживания.
    // Окраска — умножение: серый пиксель становится оттенком цвета, складки и стёжка сохраняются.
    private static void paint(NativeImage canvas, NativeImage image, int color) {
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int pixel = image.getPixelRGBA(x * width / WIDTH, y * height / HEIGHT);
                if ((pixel >>> 24) >= ALPHA_CUTOFF) {
                    canvas.setPixelRGBA(x, y, (color == Layer.NO_COLOR ? pixel : tint(pixel, color)) | 0xFF000000);
                }
            }
        }
    }

    // Пиксель NativeImage — ABGR (красный в младшем байте), цвет — ARGB
    private static int tint(int abgr, int argb) {
        int r = (abgr & 0xFF) * ((argb >> 16) & 0xFF) / 255;
        int g = ((abgr >> 8) & 0xFF) * ((argb >> 8) & 0xFF) / 255;
        int b = ((abgr >> 16) & 0xFF) * (argb & 0xFF) / 255;
        return (abgr & 0xFF000000) | (b << 16) | (g << 8) | r;
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
