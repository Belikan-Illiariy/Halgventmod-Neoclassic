package com.nrdhvnstd.halgventmod.armor;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;

/**
 * Защита надетой брони от урона не из боя (падение, мобы, взрывы) — ванильными атрибутами «Броня» и «Твёрдость брони».
 * Удары боя её не видят (тег bypasses_armor): их считают 4 слоя брони (combat/DamagePipeline).
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
                // Изношенная защищает слабее, разбитая — никак (ArmorStats)
                total = total.plus(info.protection(armor.get(slot)).times(ArmorStats.condition(armor.get(slot))));
            }
        }
        return total;
    }

    /** Переложить защиту на атрибуты игрока. */
    public static void apply(LivingEntity player, ArmorInventory armor) {
        ModArmor.Protection total = total(armor);
        set(player, Attributes.ARMOR, ARMOR_ID, total.armor());
        set(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, total.toughness());
    }

    private static void set(LivingEntity player, Holder<Attribute> attribute, ResourceLocation id, double value) {
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
