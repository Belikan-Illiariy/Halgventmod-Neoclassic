package com.nrdhvnstd.halgventmod.armor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Сведения о вещах брони. Заполняется билдером ModItems через .armor(...). */
public final class ModArmor {
    private static final Map<ResourceLocation, ArmorInfo> BY_ID = new HashMap<>();

    private ModArmor() {}

    /**
     * Как красится вещь. Красит всегда «перекрытие» (Overlay): серый 128 = чистый цвет, темнее — тень, светлее — блик.
     * Неокрашиваемое — в _overlay.
     */
    public enum Dye {
        NONE,   // не красится
        CLOTH,  // красится сама основа: она рисуется в оттенках серого (ткань, крашеный металл)
        COATED  // основа в цвете не красится; красится серый слой покрытия поверх неё — эмаль или роспись
    }

    /** Покрытие: серый слой <id><суффикс>.png поверх основы. Сам не показывается, только окрашенным. */
    public enum Coating implements StringRepresentable {
        ENAMEL("_enamel"), // эмаль
        PAINT("_painted"); // роспись

        public static final Codec<Coating> CODEC = StringRepresentable.fromEnum(Coating::values);
        public static final StreamCodec<ByteBuf, Coating> STREAM_CODEC =
                ByteBufCodecs.idMapper(id -> values()[id], Coating::ordinal);

        private final String suffix;

        Coating(String suffix) {
            this.suffix = suffix;
        }

        /** Суффикс файла слоя: barbute → barbute_enamel.png */
        public String suffix() {
            return suffix;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Сведения о вещи брони.
     * @param slot           слот: ячейка, плоскость, порядок по умолчанию, папка текстур
     * @param layerOrder     свой порядок отрисовки вместо порядка слота; 0 — как у слота
     * @param dye            как красится: основа в оттенках серого + неокрашиваемый _overlay
     * @param coatings       покрытия, которые бывают у вещи (только у COATED), первое — по умолчанию
     * @param models         выступающие детали: models/armor/<папка слота>/<имя>
     * @param hoodCompatible шлем, поверх которого можно надеть капюшон
     * @param cloak          вид накидки (только для слота накидки)
     */
    public record ArmorInfo(ArmorSlot slot, int layerOrder, Dye dye, List<Coating> coatings, List<String> models,
                            boolean hoodCompatible, @Nullable CloakType cloak) {
        public static final int SLOT_ORDER = 0;

        /** Порядок отрисовки: больше — ближе к внешней стороне. */
        public int order() {
            return layerOrder != SLOT_ORDER ? layerOrder : slot.defaultOrder();
        }

        public boolean dyeable() {
            return dye != Dye.NONE;
        }

        /** Покрытие стака: из компонента halgventmod:coating, если вещь такое знает, иначе первое. */
        @Nullable
        public Coating coating(ItemStack stack) {
            if (coatings.isEmpty()) {
                return null;
            }
            Coating chosen = stack.get(ModComponents.COATING.get());
            return chosen != null && coatings.contains(chosen) ? chosen : coatings.get(0);
        }
    }

    public static void put(ResourceLocation itemId, ArmorInfo info) {
        BY_ID.put(itemId, info);
    }

    @Nullable
    public static ArmorInfo get(Item item) {
        return BY_ID.get(BuiltInRegistries.ITEM.getKey(item));
    }

    @Nullable
    public static ArmorInfo get(ItemStack stack) {
        return stack.isEmpty() ? null : get(stack.getItem());
    }

    /** Слот вещи или null, если это не броня. */
    @Nullable
    public static ArmorSlot slotOf(ItemStack stack) {
        ArmorInfo info = get(stack);
        return info == null ? null : info.slot();
    }
}
