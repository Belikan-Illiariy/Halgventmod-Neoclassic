package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ArmorStats;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.banner.BannerEntry;
import com.nrdhvnstd.halgventmod.banner.BannerRegistry;
import com.nrdhvnstd.halgventmod.item.Gender;
import com.nrdhvnstd.halgventmod.item.ModLore;
import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.quality.QualityDisplay;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Подсказка предметов:
 *   Название
 *   [Признак] [Признак] [Признак]   — у каждого признака свой цвет
 *   Описание обычным шрифтом        — у любого предмета, для которого оно задано;
 *                                     **жирный** и *курсив* внутри текста
 *   Слот: Шлем                      — у брони
 *   (позже для оружия и брони: типы урона, урон или защита)
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class ItemTooltipHandler {
    private static final int LORE_WIDTH = 200; // ширина описания в пикселях, дальше перенос строки

    private ItemTooltipHandler() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        List<String> traits = stack.get(ModComponents.TRAITS.get());
        // Качество снаряжения — не признак, а отдельная плашка рядом (QualityBadge); у старых вещей оно могло лечь в признаки
        if (traits != null) {
            traits = traits.stream().filter(id -> GearQuality.Grade.byId(id) == null).toList();
        }
        boolean quality = QualityDisplay.of(stack) != null; // снаряжение, еда, кожа — плашкой (QualityBadge)
        if (traits != null && (stack.has(ModComponents.FOOD_QUALITY.get()) || stack.has(ModComponents.LEATHER_QUALITY.get()))) {
            traits = QualityDisplay.ownRarity(stack, traits); // у старых вещей качество было записано в редкость
        }
        ModLore.Entry lore = ModLore.get(item);
        ArmorSlot armorSlot = ModArmor.slotOf(stack);

        // Баннер из реестра знамён — только пока рисунок совпадает с записью (станок копирует компоненты)
        BannerEntry banner = BannerRegistry.client(stack.get(ModComponents.REGISTRY_BANNER.get()));
        if (banner != null && banner.sameDesign(stack)) {
            event.getToolTip().add(Math.min(1, event.getToolTip().size()), Component.translatable(
                    "tooltip.halgventmod.registry_banner", banner.name()).withStyle(ChatFormatting.GOLD));
        }

        boolean hasTraits = traits != null && !traits.isEmpty();
        if (!hasTraits && !quality && lore == null && armorSlot == null) {
            return;
        }

        // Строка 0 — название, всё наше идёт сразу под ним
        List<Component> tooltip = event.getToolTip();
        int index = Math.min(1, tooltip.size());

        if (hasTraits || quality) {
            ModItems.Definition def = ModItems.definition(item);
            Gender gender = def != null ? def.gender() : Gender.M;
            Component line = hasTraits ? traitsLine(traits, gender) : Component.empty();
            if (quality) {
                QualityBadge.host(line, stack);
            }
            tooltip.add(index++, line);
        }
        if (lore != null) {
            for (Component line : loreLines(ModLore.langKey(item), lore.color())) {
                tooltip.add(index++, line);
            }
        }
        if (armorSlot != null) {
            tooltip.add(index++, Component.translatable("tooltip.halgventmod.armor_slot",
                    Component.translatable(armorSlot.langKey())).withStyle(ChatFormatting.GRAY));
            ModArmor.ArmorInfo info = ModArmor.get(stack);
            // В бою (combat/DamagePipeline): твёрдость против пробивания, покрытие, что держит — с качеством и износом
            if (info != null) {
                ArmorStats.Stats stats = ArmorStats.of(stack); // разбита — null, об этом пишет строка износа
                if (stats != null) {
                    tooltip.add(index++, Component.translatable("tooltip.halgventmod.armor.combat",
                            WeightText.decimal(stats.hardness()), Math.round(stats.coverage())).withStyle(ChatFormatting.GRAY));
                    tooltip.add(index++, Component.translatable("tooltip.halgventmod.armor.resist",
                            percent(stats, DamageKind.CHOPPING), percent(stats, DamageKind.CUTTING),
                            percent(stats, DamageKind.PIERCING), percent(stats, DamageKind.BLUNT),
                            percent(stats, DamageKind.PENETRATING)).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            // Вне боя (падение, взрыв, моб без боя): ванильные броня и стойкость; у забрала — в нынешнем положении
            ModArmor.Protection protection = info != null ? info.protection(stack) : ModArmor.Protection.NONE;
            if (!protection.isNone()) {
                tooltip.add(index++, Component.translatable("tooltip.halgventmod.protection",
                        WeightText.decimal(protection.armor()), WeightText.decimal(protection.toughness())).withStyle(ChatFormatting.GRAY));
            }
            if (info != null && info.visor()) {
                tooltip.add(index++, Component.translatable(ModArmor.visorRaised(stack)
                        ? "tooltip.halgventmod.visor_raised" : "tooltip.halgventmod.visor_lowered")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static int percent(ArmorStats.Stats stats, DamageKind kind) {
        return Math.round(stats.resist()[kind.ordinal()] * 100);
    }

    private static Component traitsLine(List<String> traits, Gender gender) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < traits.size(); i++) {
            if (i > 0) {
                line.append(" ");
            }
            String id = traits.get(i);
            ModTraits.Trait trait = ModTraits.byId(id);
            int color = trait != null ? trait.color() : ModTraits.DEFAULT_COLOR;
            line.append(Component.literal("[")
                    .append(Component.translatable(ModTraits.langKey(id, gender)))
                    .append("]")
                    .withStyle(Style.EMPTY.withColor(color)));
        }
        return line;
    }

    // Описание переносится по ширине автоматически; \n в тексте — принудительный перенос.
    // Жирный и курсив внутри текста сохраняются и при переносе строки.
    private static List<Component> loreLines(String key, int color) {
        Style style = Style.EMPTY.withColor(color);
        List<Component> lines = new ArrayList<>();
        var splitter = Minecraft.getInstance().font.getSplitter();
        for (FormattedText part : splitter.splitLines(Component.translatable(key), LORE_WIDTH, style)) {
            MutableComponent line = Component.empty();
            part.<Void>visit((partStyle, text) -> {
                line.append(Component.literal(text).withStyle(partStyle));
                return Optional.empty();
            }, style);
            lines.add(line);
        }
        return lines;
    }
}