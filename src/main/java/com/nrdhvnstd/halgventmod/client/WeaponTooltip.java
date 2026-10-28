package com.nrdhvnstd.halgventmod.client;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.AttackKind;
import com.nrdhvnstd.halgventmod.combat.Combat;
import com.nrdhvnstd.halgventmod.combat.WeaponProfile;
import com.nrdhvnstd.halgventmod.combat.WeaponProfiles;
import com.nrdhvnstd.halgventmod.quality.Dullness;
import com.nrdhvnstd.halgventmod.quality.GearQuality;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Подсказка оружия боя (ТЗ боя, 11.3): удары из профиля — урон (с качеством и затуплением) и тип каждого, пробивание
 * и длина клинка. Ванильные «Урон атаки» и «Скорость атаки» (у оружия Conquest Reforged) не показываются — бой их не
 * использует.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class WeaponTooltip {
    private static final List<AttackKind> SHOWN = List.of(AttackKind.SLASH, AttackKind.OVERHEAD, AttackKind.STAB, AttackKind.SPECIAL);

    private WeaponTooltip() {}

    /** Оружие боя (не пустая рука). */
    public static WeaponProfile profile(Player player, ItemStack stack) {
        return player == null || stack.isEmpty() ? null : WeaponProfiles.of(player, stack);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        WeaponProfile profile = profile(event.getEntity(), stack);
        if (profile == null) {
            return;
        }
        boolean alt = Combat.altGrip(stack);
        float factor = GearQuality.get(stack).damage() * Dullness.damageFactor(stack);
        List<Component> tooltip = event.getToolTip();
        for (AttackKind kind : SHOWN) {
            WeaponProfile.Attack attack = profile.attack(kind, alt);
            if (attack != null) {
                tooltip.add(Component.translatable("tooltip.halgventmod.weapon.attack", Component.translatable(kind.langKey()),
                        Math.round(attack.damage() * factor), Component.translatable(attack.type().langKey()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable("tooltip.halgventmod.weapon.stats",
                String.format("%.1f", profile.penetration() * (1.0F + GearQuality.get(stack).penetration())),
                String.format("%.1f", profile.bladeLength())).withStyle(ChatFormatting.DARK_GRAY));
        if (!profile.altGrip().isEmpty()) {
            tooltip.add(Component.translatable(alt ? "tooltip.halgventmod.weapon.grip_alt" : "tooltip.halgventmod.weapon.grip_main")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @SubscribeEvent
    public static void onAttributeTooltips(GatherSkippedAttributeTooltipsEvent event) {
        if (profile(event.getContext().player(), event.getStack()) != null) {
            event.skipId(Item.BASE_ATTACK_DAMAGE_ID);
            event.skipId(Item.BASE_ATTACK_SPEED_ID);
        }
    }
}
