package com.nrdhvnstd.halgventmod.banner;

import java.util.List;
import java.util.regex.Pattern;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Знамя из реестра знамён: настоящий баннер (цвет основы и слои узоров) под своим id, с названием и описанием.
 * Слои хранят ссылки на реестр узоров, поэтому узоры, которые мод добавит позже, работают без доработок.
 */
public record BannerEntry(String id, String name, String description, DyeColor base, BannerPatternLayers patterns,
                          String author) {
    public static final int ID_MAX = 32;
    public static final int NAME_MAX = 32;
    public static final int DESCRIPTION_MAX = 512; // ⚠️
    public static final int AUTHOR_MAX = 16;
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_]{1," + ID_MAX + "}");

    public static final Codec<BannerEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(BannerEntry::id),
            Codec.STRING.fieldOf("name").forGetter(BannerEntry::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(BannerEntry::description),
            DyeColor.CODEC.fieldOf("base").forGetter(BannerEntry::base),
            BannerPatternLayers.CODEC.optionalFieldOf("patterns", BannerPatternLayers.EMPTY).forGetter(BannerEntry::patterns),
            Codec.STRING.optionalFieldOf("author", "").forGetter(BannerEntry::author)
    ).apply(instance, BannerEntry::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BannerEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(ID_MAX), BannerEntry::id,
            ByteBufCodecs.stringUtf8(NAME_MAX * 4), BannerEntry::name,
            ByteBufCodecs.stringUtf8(DESCRIPTION_MAX * 4), BannerEntry::description,
            DyeColor.STREAM_CODEC, BannerEntry::base,
            BannerPatternLayers.STREAM_CODEC, BannerEntry::patterns,
            ByteBufCodecs.stringUtf8(AUTHOR_MAX * 4), BannerEntry::author,
            BannerEntry::new);

    public static boolean validId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    public BannerEntry withName(String newName) {
        return new BannerEntry(id, newName, description, base, patterns, author);
    }

    public BannerEntry withDescription(String newDescription) {
        return new BannerEntry(id, name, newDescription, base, patterns, author);
    }

    public BannerEntry withDesign(DyeColor newBase, BannerPatternLayers newPatterns) {
        return new BannerEntry(id, name, description, newBase, newPatterns, author);
    }

    /** Копия знамени предметом: ванильный баннер с узорами и меткой реестра. */
    public ItemStack makeStack(int count) {
        ItemStack stack = new ItemStack(BannerBlock.byColor(base).asItem(), count);
        stack.set(DataComponents.BANNER_PATTERNS, patterns);
        stack.set(ModComponents.REGISTRY_BANNER.get(), id);
        return stack;
    }

    /** Тот же ли рисунок у баннера: цвет основы и узоры по порядку. Перекрашенный на станке — уже не тот. */
    public boolean sameDesign(ItemStack stack) {
        if (!(stack.getItem() instanceof BannerItem banner) || banner.getColor() != base) {
            return false;
        }
        List<BannerPatternLayers.Layer> layers = stack.getOrDefault(DataComponents.BANNER_PATTERNS,
                BannerPatternLayers.EMPTY).layers();
        List<BannerPatternLayers.Layer> own = patterns.layers();
        if (layers.size() != own.size()) {
            return false;
        }
        for (int i = 0; i < own.size(); i++) {
            BannerPatternLayers.Layer a = own.get(i);
            BannerPatternLayers.Layer b = layers.get(i);
            // Узор сравниваем по ключу реестра: объекты узора на сервере и в предмете могут быть разными
            if (a.color() != b.color() || !a.pattern().unwrapKey().equals(b.pattern().unwrapKey())) {
                return false;
            }
        }
        return true;
    }
}
