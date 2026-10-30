package com.nrdhvnstd.halgventmod.combat;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.quality.DamageKind;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Пять типов урона боя (ТЗ боя, 7): halgventmod:chopping, cutting, piercing, blunt, penetrating. Создаются datagen'ом
 * (data/halgventmod/damage_type), теги — ModDamageTypeTagsProvider: bypasses_armor (ванильная броня второй раз не
 * считает — её работу делает DamagePipeline), is_player_attack и ближний бой для звуков.
 */
public final class CombatDamageTypes {
    private static final Map<DamageKind, ResourceKey<DamageType>> KEYS = new EnumMap<>(DamageKind.class);

    static {
        for (DamageKind kind : DamageKind.values()) {
            KEYS.put(kind, ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, kind.id())));
        }
    }

    /** Кровотечение (combat/Bleeding): мимо брони, без виновника. */
    public static final ResourceKey<DamageType> BLEEDING = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "bleeding"));

    private CombatDamageTypes() {}

    public static ResourceKey<DamageType> key(DamageKind kind) {
        return KEYS.get(kind);
    }

    /** Для datagen: сами типы. Сообщение о смерти — death.attack.halgventmod.&lt;тип&gt;[.player]. */
    public static void bootstrap(BootstrapContext<DamageType> context) {
        for (DamageKind kind : DamageKind.values()) {
            context.register(key(kind), new DamageType(HalgventMod.MODID + "." + kind.id(), DamageScaling.NEVER, 0.1F));
        }
        context.register(BLEEDING, new DamageType(HalgventMod.MODID + ".bleeding", DamageScaling.NEVER, 0.0F));
    }

    /** Источник урона боя: тип по удару, атакующий — и причина, и прямой виновник. */
    public static DamageSource source(LivingEntity attacker, DamageKind kind) {
        return new DamageSource(attacker.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(key(kind)), attacker);
    }

    /** Источник урона кровотечения. */
    public static DamageSource bleeding(LivingEntity entity) {
        return new DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(BLEEDING));
    }

    /** Тип урона боя у источника или null — урон не из боя. */
    @Nullable
    public static DamageKind kindOf(DamageSource source) {
        for (Map.Entry<DamageKind, ResourceKey<DamageType>> entry : KEYS.entrySet()) {
            if (source.is(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
