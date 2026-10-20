package com.nrdhvnstd.halgventmod.armor;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModSounds;
import com.nrdhvnstd.halgventmod.sound.SoundPlay;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;

/** Звуки брони: надевание, звон на ходу, во что пришёлся удар (docs/sound_spec/TZ_sounds.md, 6.1–6.3). */
public final class ArmorSounds {
    public static final float EQUIP_VOLUME = 0.8F;        // ⚠️
    public static final float EQUIP_PITCH_SPREAD = 0.08F; // ⚠️

    /** Самый твёрдый слой на туловище — по нему выбирается звук удара. */
    public enum Layer { PLATE, MAIL, CLOTH, BODY }

    // Слои туловища; накидка не считается — латник в сюрко звучит как латы
    private static final ArmorSlot[] TORSO = {ArmorSlot.BREASTPLATE, ArmorSlot.MAIL, ArmorSlot.PADDING};
    // Что звенит на ходу: латы на теле и ногах, кольчуга на теле и ногах. Шлем и перчатки сами не звенят
    private static final ArmorSlot[] RATTLING_PLATE = {ArmorSlot.BREASTPLATE, ArmorSlot.GREAVES, ArmorSlot.BOOTS};
    private static final ArmorSlot[] RATTLING_MAIL = {ArmorSlot.MAIL, ArmorSlot.CHAUSSES};

    private ArmorSounds() {}

    /** Надел или снял вещь: звук по её материалу, у того, кто её носит. Только на сервере. */
    public static void equip(Entity wearer, ItemStack stack) {
        ModArmor.ArmorInfo info = ModArmor.get(stack);
        if (info == null || wearer.level().isClientSide) {
            return;
        }
        SoundPlay.at(wearer, info.kind().equipSound(), SoundSource.PLAYERS, EQUIP_VOLUME, EQUIP_PITCH_SPREAD);
    }

    /** Самый твёрдый слой на туловище. Мобы — по ванильной нагрудной броне ⚠️. */
    public static Layer bodyLayer(LivingEntity entity) {
        ArmorInventory armor = entity.getExistingDataOrNull(ModAttachments.ARMOR);
        Layer layer = armor != null ? torsoLayer(armor) : Layer.BODY;
        return layer != Layer.BODY ? layer : vanillaLayer(entity.getItemBySlot(EquipmentSlot.CHEST));
    }

    private static Layer torsoLayer(ArmorInventory armor) {
        Layer best = Layer.BODY;
        for (ArmorSlot slot : TORSO) {
            ModArmor.ArmorInfo info = ModArmor.get(armor.get(slot));
            if (info == null) {
                continue;
            }
            Layer layer = switch (info.kind()) {
                case PLATE -> Layer.PLATE;
                case MAIL -> Layer.MAIL;
                case PADDED, LEATHER -> Layer.CLOTH;
            };
            if (layer.ordinal() < best.ordinal()) {
                best = layer;
            }
        }
        return best;
    }

    private static Layer vanillaLayer(ItemStack chest) {
        if (!(chest.getItem() instanceof ArmorItem item)) {
            return Layer.BODY;
        }
        ArmorMaterial material = item.getMaterial().value();
        if (is(material, ArmorMaterials.CHAIN)) {
            return Layer.MAIL;
        }
        if (is(material, ArmorMaterials.LEATHER)) {
            return Layer.CLOTH;
        }
        if (is(material, ArmorMaterials.IRON) || is(material, ArmorMaterials.GOLD)
                || is(material, ArmorMaterials.DIAMOND) || is(material, ArmorMaterials.NETHERITE)) {
            return Layer.PLATE;
        }
        return Layer.BODY;
    }

    private static boolean is(ArmorMaterial material, Holder<ArmorMaterial> vanilla) {
        return material == vanilla.value();
    }

    /** Чем звенит броня на ходу: латы, иначе кольчуга, иначе null — тихо. */
    @Nullable
    public static SoundEvent moveSound(ArmorInventory armor) {
        if (has(armor, RATTLING_PLATE, ArmorKind.PLATE)) {
            return ModSounds.ARMOR_MOVE_PLATE.get();
        }
        if (has(armor, RATTLING_MAIL, ArmorKind.MAIL)) {
            return ModSounds.ARMOR_MOVE_MAIL.get();
        }
        return null;
    }

    private static boolean has(ArmorInventory armor, ArmorSlot[] slots, ArmorKind kind) {
        for (ArmorSlot slot : slots) {
            ModArmor.ArmorInfo info = ModArmor.get(armor.get(slot));
            if (info != null && info.kind() == kind) {
                return true;
            }
        }
        return false;
    }
}
