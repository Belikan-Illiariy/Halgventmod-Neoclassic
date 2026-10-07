package com.nrdhvnstd.halgventmod.armor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Сведения о вещах брони. Заполняется билдером ModItems через .armor(...). */
public final class ModArmor {
    private static final Map<ResourceLocation, ArmorInfo> BY_ID = new HashMap<>();

    private ModArmor() {}

    /**
     * Сведения о вещи брони.
     * @param slot           слот: ячейка, плоскость, порядок по умолчанию, папка текстур
     * @param layerOrder     свой порядок отрисовки вместо порядка слота; 0 — как у слота
     * @param dyeable        красится: текстура в оттенках серого + неокрашиваемый _overlay
     * @param models         выступающие детали: models/armor/<папка слота>/<имя>
     * @param hoodCompatible шлем, поверх которого можно надеть капюшон
     * @param cloak          вид накидки (только для слота накидки)
     */
    public record ArmorInfo(ArmorSlot slot, int layerOrder, boolean dyeable, List<String> models,
                            boolean hoodCompatible, @Nullable CloakType cloak) {
        public static final int SLOT_ORDER = 0;

        /** Порядок отрисовки: больше — ближе к внешней стороне. */
        public int order() {
            return layerOrder != SLOT_ORDER ? layerOrder : slot.defaultOrder();
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
