package com.nrdhvnstd.halgventmod.quality;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.armor.ArmorKind;
import com.nrdhvnstd.halgventmod.combat.CombatDamageTypes;
import com.nrdhvnstd.halgventmod.sound.SoundTags;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Тип урона. Режущий (CUTTING) приходит только из профилей оружия (combat/WeaponProfile). Временная разметка до механики боя по таблице ГДД (этап 7 ТЗ брони). Тип определяется так же,
 * как в ТЗ эффектов персонажа: клинок (мечи, топоры — тег sound/blade) — рубящий, остриё (sound/point) — колющий,
 * стрела — проникающий, остальное в руке и кулак — дробящий. Урон не от удара и не от стрелы (падение, огонь,
 * взрыв) — без типа: от него стойкость брони не помогает.
 */
public enum DamageKind {
    //          урон телу  боль   кровотечение  отдача при непробитии (ТЗ боя, 7.2) ⚠️
    CHOPPING   (1.0F,      1.0F,  0.10F,        0.50F),
    CUTTING    (0.8F,      0.9F,  0.15F,        0.20F),
    PIERCING   (1.1F,      0.8F,  0.08F,        0.30F),
    BLUNT      (0.7F,      1.5F,  0.00F,        0.00F),
    PENETRATING(1.0F,      1.0F,  0.10F,        0.25F);

    private final float body;
    private final float pain;
    private final float bleed;
    private final float backlash;

    DamageKind(float body, float pain, float bleed, float backlash) {
        this.body = body;
        this.pain = pain;
        this.bleed = bleed;
        this.backlash = backlash;
    }

    /** Множитель урона по телу, дошедшего этим типом. */
    public float body() {
        return body;
    }

    /** Множитель боли от урона по телу. */
    public float pain() {
        return pain;
    }

    /** Шанс кровотечения при уроне по телу от 3. */
    public float bleed() {
        return bleed;
    }

    /** Доля непробившего урона, которая уходит дальше дробящей отдачей. */
    public float backlash() {
        return backlash;
    }

    /** Ключ перевода: damage_kind.halgventmod.chopping */
    public String langKey() {
        return "damage_kind.halgventmod." + id();
    }

    /** Имя в JSON профилей оружия: chopping, cutting... */
    public String id() {
        return name().toLowerCase();
    }

    @Nullable
    public static DamageKind byId(String id) {
        for (DamageKind kind : values()) {
            if (kind.id().equals(id)) {
                return kind;
            }
        }
        return null;
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
        DamageKind combat = CombatDamageTypes.kindOf(source);
        if (combat != null) {
            return combat;
        }
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
                case CHOPPING, CUTTING, PENETRATING -> 0.15F;
                case PIERCING -> 0.10F;
                case BLUNT -> 0.0F;
            };
            case MAIL -> switch (this) {
                case CHOPPING, CUTTING -> 0.15F;
                case PENETRATING -> 0.05F;
                case PIERCING, BLUNT -> 0.0F;
            };
            case PADDED -> this == BLUNT ? 0.10F : 0.05F;
            case LEATHER -> 0.05F;
        };
    }
}
