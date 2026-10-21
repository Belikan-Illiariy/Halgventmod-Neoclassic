package com.nrdhvnstd.halgventmod.quality;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.armor.ArmorKind;
import com.nrdhvnstd.halgventmod.sound.SoundTags;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Тип урона — временная разметка до механики боя по таблице ГДД (этап 7 ТЗ брони). Тип определяется так же,
 * как в ТЗ эффектов персонажа: клинок (мечи, топоры — тег sound/blade) — рубящий, остриё (sound/point) — колющий,
 * стрела — проникающий, остальное в руке и кулак — дробящий. Урон не от удара и не от стрелы (падение, огонь,
 * взрыв) — без типа: от него стойкость брони не помогает.
 */
public enum DamageKind {
    CHOPPING, PIERCING, BLUNT, PENETRATING;

    /** Ключ перевода: damage_kind.halgventmod.chopping */
    public String langKey() {
        return "damage_kind.halgventmod." + name().toLowerCase();
    }

    /** Каким уроном бьёт это оружие. */
    public static DamageKind of(ItemStack weapon) {
        if (weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem) {
            return PENETRATING;
        }
        if (weapon.is(SoundTags.BLADE)) {
            return CHOPPING;
        }
        return weapon.is(SoundTags.POINT) ? PIERCING : BLUNT;
    }

    /** Тип пришедшего урона или null — не удар и не стрела. */
    @Nullable
    public static DamageKind of(DamageSource source) {
        if (source.getDirectEntity() instanceof AbstractArrow) {
            return PENETRATING;
        }
        if (source.is(SoundTags.MELEE) && source.getEntity() instanceof LivingEntity attacker
                && source.getDirectEntity() == attacker) {
            return of(attacker.getMainHandItem());
        }
        return null;
    }

    /**
     * Стойкость материала брони к типу урона ⚠️: доля урона, которую вещь дополнительно гасит после брони
     * (у вещи Посредственного качества). Латы держат рубящий и стрелы, кольчуга — рубящий, ткань — дробящий.
     */
    public float resistance(ArmorKind armor) {
        return switch (armor) {
            case PLATE -> switch (this) {
                case CHOPPING, PENETRATING -> 0.15F;
                case PIERCING -> 0.10F;
                case BLUNT -> 0.0F;
            };
            case MAIL -> switch (this) {
                case CHOPPING -> 0.15F;
                case PENETRATING -> 0.05F;
                case PIERCING, BLUNT -> 0.0F;
            };
            case PADDED -> this == BLUNT ? 0.10F : 0.05F;
            case LEATHER -> 0.05F;
        };
    }
}
