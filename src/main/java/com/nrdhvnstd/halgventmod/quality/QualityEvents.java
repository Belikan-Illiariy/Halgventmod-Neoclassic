package com.nrdhvnstd.halgventmod.quality;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Как качество снаряжения работает в игре (таблица — GearQuality.Grade):
 * <ul>
 *   <li>урон оружия ближнего боя — множитель к атрибуту «Урон атаки» вещи (видно и в подсказке), вместе с затуплением
 *       (Dullness); стрелы — по качеству лука или арбалета, из которого выпущены;</li>
 *   <li>пробитие — удар оружием гасится бронёй цели слабее на долю пробития;</li>
 *   <li>защита — множитель к броне и твёрдости вещи (броня мода — в ModArmor, ванильная — к её атрибутам);</li>
 *   <li>стойкость — надетая броня дополнительно гасит урон своего типа: сумма стойкости вещей
 *       ({@link DamageKind#resistance} × качество) / {@link #RESISTANCE_DIVISOR}, не больше {@link #MAX_RESISTANCE}.</li>
 * </ul>
 * Крафт — бросок качества; вещь без качества в инвентаре — Посредственная. /quality &lt;качество&gt; — администратору,
 * вещи в руке.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class QualityEvents {
    // Меняйте здесь ⚠️
    public static final float RESISTANCE_DIVISOR = 4.0F; // примерно столько вещей закрывают одно место удара
    public static final float MAX_RESISTANCE = 0.6F;
    private static final int STAMP_INTERVAL = 40;

    private QualityEvents() {}

    // ---------------------------------------------------------------- атрибуты вещи

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        boolean quality = GearQuality.has(stack);
        float dull = Dullness.damageFactor(stack);
        if (!quality && dull == 1.0F) {
            return;
        }
        GearQuality.Grade grade = GearQuality.get(stack);
        float damage = (quality ? grade.damage() : 1.0F) * dull;
        float protection = quality ? grade.protection() : 1.0F;
        boolean weapon = GearQuality.isWeapon(stack);
        boolean vanillaArmor = stack.getItem() instanceof ArmorItem;
        if ((!weapon || damage == 1.0F) && (!vanillaArmor || protection == 1.0F)) {
            return;
        }
        List<ItemAttributeModifiers.Entry> scaled = new ArrayList<>();
        for (ItemAttributeModifiers.Entry entry : event.getModifiers()) {
            AttributeModifier modifier = entry.modifier();
            double amount = modifier.amount();
            if (weapon && entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                    && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                // У основного урона вещи учитываем и единицу кулака: весь удар ×множитель
                amount = modifier.id().equals(Item.BASE_ATTACK_DAMAGE_ID)
                        ? (1.0 + amount) * damage - 1.0 : amount * damage;
            } else if (vanillaArmor && (entry.attribute().equals(Attributes.ARMOR)
                    || entry.attribute().equals(Attributes.ARMOR_TOUGHNESS))) {
                amount *= protection;
            } else {
                continue;
            }
            scaled.add(new ItemAttributeModifiers.Entry(entry.attribute(),
                    new AttributeModifier(modifier.id(), amount, modifier.operation()), entry.slot()));
        }
        for (ItemAttributeModifiers.Entry entry : scaled) {
            event.replaceModifier(entry.attribute(), entry.modifier(), entry.slot());
        }
    }

    // ---------------------------------------------------------------- откуда берётся

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        if (GearQuality.applies(crafted) && !GearQuality.has(crafted) && !event.getEntity().level().isClientSide) {
            GearQuality.set(crafted, GearQuality.roll(event.getEntity().getRandom()));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % STAMP_INTERVAL != 0) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            stamp(inventory.getItem(i));
        }
        stamp(player.containerMenu.getCarried());
    }

    private static void stamp(ItemStack stack) {
        if (GearQuality.applies(stack) && !GearQuality.has(stack)) {
            GearQuality.set(stack, GearQuality.Grade.MEDIOCRE);
        }
    }

    // ---------------------------------------------------------------- бой

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        DamageKind kind = DamageKind.of(source);
        if (kind == null) {
            return;
        }
        ItemStack weapon = ItemStack.EMPTY;
        if (source.getDirectEntity() instanceof AbstractArrow arrow) {
            ItemStack bow = arrow.getWeaponItem();
            if (bow != null && GearQuality.has(bow)) {
                weapon = bow;
                event.setAmount(event.getAmount() * GearQuality.get(bow).damage());
            }
        } else if (source.getEntity() instanceof LivingEntity attacker) {
            weapon = attacker.getMainHandItem();
        }
        if (GearQuality.isWeapon(weapon)) {
            float penetration = GearQuality.get(weapon).penetration();
            if (penetration > 0) {
                event.addReductionModifier(DamageContainer.Reduction.ARMOR,
                        (container, reduction) -> reduction * (1.0F - penetration));
            }
        }
        if (event.getEntity() instanceof Player target) {
            float resistance = resistance(target, kind);
            if (resistance > 0) {
                event.setAmount(event.getAmount() * (1.0F - resistance));
            }
        }
    }

    /** Стойкость надетой брони игрока к типу урона. */
    public static float resistance(Player player, DamageKind kind) {
        ArmorInventory armor = player.getExistingDataOrNull(ModAttachments.ARMOR);
        if (armor == null) {
            return 0;
        }
        float sum = 0;
        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack piece = armor.get(slot);
            ModArmor.ArmorInfo info = ModArmor.get(piece);
            if (info != null) {
                sum += kind.resistance(info.kind()) * GearQuality.get(piece).resistance();
            }
        }
        return Math.min(MAX_RESISTANCE, sum / RESISTANCE_DIVISOR);
    }

    // ---------------------------------------------------------------- команда

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("quality")
                .requires(source -> source.hasPermission(Permissions.ADMIN_LEVEL))
                .then(Commands.argument("grade", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(GearQuality.Grade.values()).map(GearQuality.Grade::id), builder))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            GearQuality.Grade grade = GearQuality.Grade.byId(StringArgumentType.getString(context, "grade"));
                            ItemStack held = player.getMainHandItem();
                            if (grade == null || !GearQuality.applies(held)) {
                                context.getSource().sendFailure(Component.translatable("command.halgventmod.quality.not_gear"));
                                return 0;
                            }
                            GearQuality.set(held, grade);
                            context.getSource().sendSuccess(() -> Component.translatable("command.halgventmod.quality.set",
                                    held.getHoverName()), false);
                            return 1;
                        })));
    }
}
