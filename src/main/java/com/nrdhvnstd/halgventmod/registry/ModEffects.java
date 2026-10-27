package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.FoodEffect;
import com.nrdhvnstd.halgventmod.needs.NeedEffect;
import com.nrdhvnstd.halgventmod.needs.Starvation;
import com.nrdhvnstd.halgventmod.social.RestraintEffect;
import com.nrdhvnstd.halgventmod.weight.OverweightEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Эффекты. Значок: assets/halgventmod/textures/mob_effect/<id>.png, 18×18. */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, HalgventMod.MODID);

    private static final double SHACKLES_SLOW = -0.8;   // ⚠️
    private static final double CUFFS_WEAKNESS = -0.8;  // ⚠️

    public static final DeferredHolder<MobEffect, MobEffect> BURDEN =
            EFFECTS.register("burden", () -> new OverweightEffect(0x8B7355));      // Обуза
    public static final DeferredHolder<MobEffect, MobEffect> ENCUMBERED =
            EFFECTS.register("encumbered", () -> new OverweightEffect(0x6B4F2F));  // Скованность
    public static final DeferredHolder<MobEffect, MobEffect> OVERLOADED =
            EFFECTS.register("overloaded", () -> new OverweightEffect(0x4A2A1A));  // Тяжесть

    // Еда (food/FoodEffects): жареное и каши — «Сытость», горячие сложные блюда — «Улучшенное насыщение»,
    // сырое мясо и рыба, испорченное — «Отравление»
    public static final DeferredHolder<MobEffect, MobEffect> SATIETY =
            EFFECTS.register("satiety", () -> new FoodEffect(MobEffectCategory.BENEFICIAL, 0xD8A050));
    public static final DeferredHolder<MobEffect, MobEffect> WELL_FED =
            EFFECTS.register("well_fed", () -> new FoodEffect(MobEffectCategory.BENEFICIAL, 0xE8C060));
    public static final DeferredHolder<MobEffect, MobEffect> POISONING =
            EFFECTS.register("poisoning", () -> new FoodEffect(MobEffectCategory.HARMFUL, 0x6E8A2E));

    // Травы (herbs/Herb): крапивный отвар — «Бодрость», выносливость восстанавливается быстрее (needs/Stamina)
    public static final DeferredHolder<MobEffect, MobEffect> VIGOR =
            EFFECTS.register("vigor", () -> new FoodEffect(MobEffectCategory.BENEFICIAL, 0x5A8A3A));

    // Социальные взаимодействия (social/Social, docs/TZ_social_interactions.md, 5): без таймера, снимает другой игрок
    public static final DeferredHolder<MobEffect, MobEffect> BAGGED =
            EFFECTS.register("bagged", () -> new RestraintEffect(0x7A6040));       // Мешок на голове
    public static final DeferredHolder<MobEffect, MobEffect> SHACKLED =
            EFFECTS.register("shackled", () -> new RestraintEffect(0x5A5A60)       // В кандалах: −80 % скорости ⚠️
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, id("shackled"), SHACKLES_SLOW,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect, MobEffect> CUFFED =
            EFFECTS.register("cuffed", () -> new RestraintEffect(0x8A8A90)         // В наручниках: −80 % урона ⚠️
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, id("cuffed"), CUFFS_WEAKNESS,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    // Голод и жажда на нуле (needs/Starvation, docs/TZ_downed_and_hp.md, 2): −40 % каждый, вместе −80 %
    public static final DeferredHolder<MobEffect, MobEffect> STARVATION =
            EFFECTS.register("starvation", () -> need(0x8A6A3A, "starvation"));    // Голодание
    public static final DeferredHolder<MobEffect, MobEffect> DEHYDRATION =
            EFFECTS.register("dehydration", () -> need(0x6A8AAA, "dehydration"));  // Жажда

    private ModEffects() {}

    // ADD_MULTIPLIED_BASE складывается: два эффекта — −80 %, а не 0,6 × 0,6
    private static MobEffect need(int color, String path) {
        return new NeedEffect(color)
                .addAttributeModifier(Attributes.MOVEMENT_SPEED, id(path), -Starvation.PENALTY,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
                .addAttributeModifier(Attributes.ATTACK_SPEED, id(path), -Starvation.PENALTY,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
                .addAttributeModifier(Attributes.ATTACK_DAMAGE, id(path), -Starvation.PENALTY,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, path);
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}