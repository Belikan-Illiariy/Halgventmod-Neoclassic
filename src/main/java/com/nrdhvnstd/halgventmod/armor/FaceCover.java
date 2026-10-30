package com.nrdhvnstd.halgventmod.armor;

import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.world.entity.LivingEntity;

/**
 * Лицо не разглядеть: на голове капюшон или шлем с опущенным забралом (или глухой шлем с узким вырезом, .narrowView()).
 * Тогда ник над головой не виден (client/NameTagsRender).
 */
public final class FaceCover {
    private FaceCover() {}

    /** Лицо закрыто металлом: глухой шлем или опущенное забрало (капюшон не в счёт) — удар в лицо идёт в шлем. */
    public static boolean armored(LivingEntity entity) {
        ArmorInventory armor = entity.getExistingDataOrNull(ModAttachments.ARMOR);
        ModArmor.ArmorInfo helmet = armor == null ? null : ModArmor.get(armor.get(ArmorSlot.HELMET));
        return helmet != null && (helmet.narrowView() || (helmet.visor() && !ModArmor.visorRaised(armor.get(ArmorSlot.HELMET))));
    }

    public static boolean hidden(LivingEntity entity) {
        ArmorInventory armor = entity.getExistingDataOrNull(ModAttachments.ARMOR);
        if (armor == null) {
            return false;
        }
        ModArmor.ArmorInfo cloak = ModArmor.get(armor.get(ArmorSlot.CLOAK));
        if (cloak != null && cloak.cloak() != null && cloak.cloak().hasHood()) {
            return true;
        }
        ModArmor.ArmorInfo helmet = ModArmor.get(armor.get(ArmorSlot.HELMET));
        if (helmet == null) {
            return false;
        }
        return helmet.narrowView() || (helmet.visor() && !ModArmor.visorRaised(armor.get(ArmorSlot.HELMET)));
    }
}
