package com.nrdhvnstd.halgventmod.armor;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.menu.ArmorStandMenu;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Броня на стойке для брони: те же 12 ячеек, что у игрока (ModAttachments.ARMOR), и те же правила надевания.
 * ПКМ вещью брони — надеть в её ячейку (занятая меняется местами, снятое — в руку).
 * ПКМ пустой рукой — снять самую внешнюю вещь в руку. Shift+ПКМ — окно стойки со всеми 12 ячейками (ArmorStandMenu).
 * Ванильные предметы стойка принимает как раньше.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class ArmorStandEvents {
    private ArmorStandEvents() {}

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof ArmorStand stand) || stand.isMarker() || event.getEntity().isSpectator()) {
            return;
        }
        Player player = event.getEntity();
        if (player.isSecondaryUseActive()) {
            if (!player.level().isClientSide) {
                player.openMenu(new SimpleMenuProvider(
                                (id, inventory, opener) -> new ArmorStandMenu(id, inventory, stand), stand.getDisplayName()),
                        buf -> buf.writeVarInt(stand.getId()));
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
            event.setCanceled(true);
            return;
        }
        ItemStack held = event.getItemStack();
        InteractionResult result;
        if (held.isEmpty()) {
            result = event.getHand() == InteractionHand.MAIN_HAND ? takeOff(player, stand) : null;
        } else {
            result = ModArmor.slotOf(held) != null ? putOn(player, stand, held, event.getHand()) : null;
        }
        if (result != null) {
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }

    // Отказ — CONSUME, а не FAIL: иначе игра продолжит ПКМ как использование вещи и наденет её на самого игрока
    private static InteractionResult putOn(Player player, ArmorStand stand, ItemStack held, InteractionHand hand) {
        ArmorSlot slot = ModArmor.slotOf(held);
        ArmorInventory armor = stand.getData(ModAttachments.ARMOR);
        Component refusal = ArmorRules.refusal(armor, slot, held);
        if (refusal != null) {
            ArmorRules.tell(player, refusal);
            return InteractionResult.CONSUME;
        }
        if (!player.level().isClientSide) {
            ItemStack worn = armor.get(slot);
            armor.set(slot, held.copyWithCount(1));
            // В творческом режиме, как у ванильной стойки, на пустую ячейку кладётся копия
            if (!player.getAbilities().instabuild || !worn.isEmpty()) {
                player.setItemInHand(hand, worn);
            }
            changed(stand);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    // Снимается самая внешняя вещь, которую можно снять (подшлемник — только после шлема)
    @Nullable
    private static InteractionResult takeOff(Player player, ArmorStand stand) {
        ArmorInventory armor = stand.getExistingDataOrNull(ModAttachments.ARMOR);
        if (armor == null) {
            return null;
        }
        ArmorSlot outermost = null;
        int outermostOrder = Integer.MIN_VALUE;
        for (ArmorSlot slot : ArmorSlot.values()) {
            ModArmor.ArmorInfo info = ModArmor.get(armor.get(slot));
            if (info != null && info.order() > outermostOrder && ArmorRules.takeOffRefusal(armor, slot) == null) {
                outermost = slot;
                outermostOrder = info.order();
            }
        }
        if (outermost == null) {
            return null; // нашей брони нет — дальше как у ванильной стойки
        }
        if (!player.level().isClientSide) {
            player.setItemInHand(InteractionHand.MAIN_HAND, armor.get(outermost));
            armor.set(outermost, ItemStack.EMPTY);
            changed(stand);
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    private static void changed(ArmorStand stand) {
        stand.syncData(ModAttachments.ARMOR);
        stand.level().playSound(null, stand.getX(), stand.getY(), stand.getZ(),
                SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    // Стойку сломали — броня выпадает, как ванильная
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) {
            return;
        }
        ArmorInventory armor = stand.getExistingDataOrNull(ModAttachments.ARMOR);
        if (armor == null) {
            return;
        }
        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack stack = armor.get(slot);
            if (!stack.isEmpty()) {
                ItemEntity item = new ItemEntity(stand.level(), stand.getX(), stand.getY() + 1.0, stand.getZ(), stack);
                item.setDefaultPickUpDelay();
                event.getDrops().add(item);
                armor.set(slot, ItemStack.EMPTY);
            }
        }
    }
}
