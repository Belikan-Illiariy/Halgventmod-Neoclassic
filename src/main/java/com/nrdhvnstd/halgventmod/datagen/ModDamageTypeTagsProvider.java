package com.nrdhvnstd.halgventmod.datagen;

import java.util.concurrent.CompletableFuture;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.CombatDamageTypes;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.sound.SoundTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Теги типов урона мода: data/halgventmod/tags/damage_type/*.json */
public class ModDamageTypeTagsProvider extends TagsProvider<DamageType> {
    public ModDamageTypeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                     ExistingFileHelper existingFileHelper) {
        super(output, Registries.DAMAGE_TYPE, lookupProvider, HalgventMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Ближний бой — по нему звук удара. Без тега сюда попали бы взрыв крипера, шипы, луч стража, звуковой удар вардена
        tag(SoundTags.MELEE).add(DamageTypes.PLAYER_ATTACK, DamageTypes.MOB_ATTACK, DamageTypes.MOB_ATTACK_NO_AGGRO);
        // Урон боя: ближний бой (звуки), ванильная броня его не считает — это работа 4 слоёв (combat/DamagePipeline)
        for (DamageKind kind : DamageKind.values()) {
            tag(SoundTags.MELEE).add(CombatDamageTypes.key(kind));
            tag(DamageTypeTags.BYPASSES_ARMOR).add(CombatDamageTypes.key(kind));
            tag(DamageTypeTags.IS_PLAYER_ATTACK).add(CombatDamageTypes.key(kind));
        }
        // Кровотечение — кровь течёт и под латами, щит не держит, не толкает
        tag(DamageTypeTags.BYPASSES_ARMOR).add(CombatDamageTypes.BLEEDING);
        tag(DamageTypeTags.BYPASSES_SHIELD).add(CombatDamageTypes.BLEEDING);
        tag(DamageTypeTags.NO_KNOCKBACK).add(CombatDamageTypes.BLEEDING);
    }
}
