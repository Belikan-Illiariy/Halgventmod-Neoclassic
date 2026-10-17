package com.nrdhvnstd.halgventmod.armor;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Защита надетой брони — временно ванильными атрибутами «Броня» и «Твёрдость брони» (до этапа 7 ТЗ брони).
 * Сумма .protection(...) всех надетых вещей (у шлема с опущенным забралом — с прибавкой) ложится на игрока
 * одним модификатором на атрибут. Модификаторы не сохраняются: после входа, возрождения и любой смены брони
 * их пересчитывает ArmorEvents (по ArmorInventory.version).
 */
public final class ArmorProtection {
    private static final ResourceLocation ARMOR_ID = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "armor_protection");
    private static final ResourceLocation TOUGHNESS_ID = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "armor_toughness");

    private ArmorProtection() {}

    /** Сумма защиты надетых вещей. */
    public static ModArmor.Protection total(ArmorInventory armor) {
        ModArmor.Protection total = ModArmor.Protection.NONE;
        for (ArmorSlot slot : ArmorSlot.values()) {
            ModArmor.ArmorInfo info = ModArmor.get(armor.get(slot));
            if (info != null) {
                total = total.plus(info.protection(armor.get(slot)));
            }
        }
        return total;
    }

    /** Переложить защиту на атрибуты игрока. */
    public static void apply(Player player, ArmorInventory armor) {
        ModArmor.Protection total = total(armor);
        set(player, Attributes.ARMOR, ARMOR_ID, total.armor());
        set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, total.toughness());
    }

    private static void set(Player player, Holder<Attribute> attribute, ResourceLocation id, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (value == 0) {
            instance.removeModifier(id);
        } else {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}
