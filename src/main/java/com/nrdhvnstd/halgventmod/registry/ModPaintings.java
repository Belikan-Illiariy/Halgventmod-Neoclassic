package com.nrdhvnstd.halgventmod.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.item.ModPaintingItem;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModPaintings {
    // Отдельный реестр, чтобы картины шли во вкладку «Искусство», а не в основную
    public static final DeferredRegister.Items PAINTINGS = DeferredRegister.createItems(HalgventMod.MODID);

    // Эти два поля должны стоять ВЫШЕ списка картин
    private static final List<Definition> DEFINITIONS = new ArrayList<>();
    private static final Map<ResourceKey<PaintingVariant>, DeferredItem<ModPaintingItem>> BY_VARIANT = new HashMap<>();

    // ===== Картины =====
    // painting(имя, ширина, высота, название по-русски, название по-английски)
    // Ширина и высота — в блоках, от 1 до 16.
    // Полотно: assets/halgventmod/textures/painting/<имя>.png
    // Иконка:  assets/halgventmod/textures/item/<имя>_painting.png
    // Всё остальное (JSON картины, модель предмета, тег, переводы) делает runData.
    public static final DeferredItem<ModPaintingItem> WIZARD =
            painting("wizard", 2, 3, "Колдун", "Wizard");
    public static final DeferredItem<ModPaintingItem> KNIGHT_1 =
            painting("knight_1", 2, 3, "Рыцарь (1)", "Knight (1)");
    public static final DeferredItem<ModPaintingItem> KNIGHT_2 =
            painting("knight_2", 2, 3, "Рыцарь (2)", "Knight (2)");
    public static final DeferredItem<ModPaintingItem> KING =
            painting("king", 2, 3, "Король", "King");
    public static final DeferredItem<ModPaintingItem> COUPLE =
            painting("couple", 2, 3, "Пара", "Couple");

    private ModPaintings() {}

    public static void register(IEventBus modEventBus) {
        PAINTINGS.register(modEventBus);
    }

    /** Описание картины для генерации данных. */
    public record Definition(ResourceKey<PaintingVariant> variant, int width, int height,
                             String titleRu, String titleEn) {}

    public static List<Definition> definitions() {
        return Collections.unmodifiableList(DEFINITIONS);
    }

    /** Предмет картины из мода или null, если картина ванильная или чужая. */
    @Nullable
    public static Item itemFor(Holder<PaintingVariant> variant) {
        return variant.unwrapKey().map(BY_VARIANT::get).map(DeferredItem::get).orElse(null);
    }

    /** Для генерации данных: превращает список картин в JSON-файлы painting_variant. */
    public static void bootstrap(BootstrapContext<PaintingVariant> context) {
        for (Definition def : DEFINITIONS) {
            context.register(def.variant(),
                    new PaintingVariant(def.width(), def.height(), def.variant().location()));
        }
    }

    private static DeferredItem<ModPaintingItem> painting(String name, int width, int height,
                                                          String titleRu, String titleEn) {
        if (width < 1 || width > 16 || height < 1 || height > 16) {
            throw new IllegalArgumentException("Картина " + name + ": размер должен быть от 1 до 16 блоков");
        }
        ResourceKey<PaintingVariant> variant = ResourceKey.create(Registries.PAINTING_VARIANT,
                ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, name));
        DeferredItem<ModPaintingItem> item = PAINTINGS.register(name + "_painting",
                () -> new ModPaintingItem(variant, new Item.Properties()));
        DEFINITIONS.add(new Definition(variant, width, height, titleRu, titleEn));
        BY_VARIANT.put(variant, item);
        return item;
    }
}