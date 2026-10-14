package com.nrdhvnstd.halgventmod.farming;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AdventureModePredicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Все переключатели фермерства в одном месте. Каждое правило — /gamerule, по умолчанию выключено.
 * Вернуть ванильное поведение: /gamerule <правило> true (или поменять значение по умолчанию здесь).
 *
 * farmlandTrampling   — пашню вытаптывают (игроки и мобы)
 * farmlandDriesToDirt — сухая пашня без воды и без растения превращается в землю
 * cropsNeedWater      — на сухой пашне растения растут втрое медленнее, чем на сырой
 */
public final class FarmingRules {
    public static final GameRules.Key<GameRules.BooleanValue> FARMLAND_TRAMPLING =
            GameRules.register("farmlandTrampling", GameRules.Category.UPDATES, GameRules.BooleanValue.create(false));
    public static final GameRules.Key<GameRules.BooleanValue> FARMLAND_DRIES_TO_DIRT =
            GameRules.register("farmlandDriesToDirt", GameRules.Category.UPDATES, GameRules.BooleanValue.create(false));
    public static final GameRules.Key<GameRules.BooleanValue> CROPS_NEED_WATER =
            GameRules.register("cropsNeedWater", GameRules.Category.UPDATES, GameRules.BooleanValue.create(false));

    /** Что срезают серп и коса (в режиме приключения — только это). */
    public static final TagKey<Block> HARVESTABLE = blockTag("sickle_harvestable");
    /** По чему можно кликнуть семенами, чтобы посадить их в режиме приключения. */
    public static final TagKey<Block> PLANTABLE_SOIL = blockTag("plantable_soil");

    /** Ванильные семена и корнеплоды, которые сажаются в пашню. Свои культуры — следующим этапом. */
    public static final List<Item> VANILLA_SEEDS = List.of(
            Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.CARROT, Items.POTATO,
            Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD);

    private FarmingRules() {}

    /** Вызывается из конструктора мода: правила должны появиться до загрузки мира. */
    public static void init() {}

    public static boolean trampling(Level level) {
        return level.getGameRules().getBoolean(FARMLAND_TRAMPLING);
    }

    public static boolean farmlandDriesToDirt(Level level) {
        return level.getGameRules().getBoolean(FARMLAND_DRIES_TO_DIRT);
    }

    /** Рост считается в BlockGetter; правило читается, только если это настоящий мир. */
    public static boolean cropsNeedWater(BlockGetter getter) {
        return getter instanceof Level level && level.getGameRules().getBoolean(CROPS_NEED_WATER);
    }

    /** Компонент can_break / can_place_on по тегу блоков, без ванильной строки в подсказке. */
    public static AdventureModePredicate adventurePredicate(TagKey<Block> tag) {
        return new AdventureModePredicate(List.of(BlockPredicate.Builder.block().of(tag).build()), false);
    }

    private static TagKey<Block> blockTag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, name));
    }
}
