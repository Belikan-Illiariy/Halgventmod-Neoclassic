package com.nrdhvnstd.halgventmod.sound;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;

/** Теги для звуков боя. Состав — в датагене (ModItemTagsProvider, ModDamageTypeTagsProvider), ⚠️. */
public final class SoundTags {
    // Как звучит удар оружием; всё прочее в руке — как дробящее
    public static final TagKey<Item> BLADE = item("sound/blade");               // мечи, топоры
    public static final TagKey<Item> POINT = item("sound/point");               // трезубец, позже копья и кинжалы
    public static final TagKey<Item> BLUNT = item("sound/blunt");               // булава, позже молоты и палицы
    public static final TagKey<Item> HEAVY_WEAPON = item("sound/heavy_weapon"); // тяжёлый замах: топоры, булава
    public static final TagKey<Item> SHEATHED = item("sound/sheathed");         // носится в ножнах: мечи

    /** Ближний бой: удар игрока или моба рукой или оружием (без шипов, взрывов, лучей). */
    public static final TagKey<DamageType> MELEE =
            TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "melee"));

    private SoundTags() {}

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, path));
    }
}
