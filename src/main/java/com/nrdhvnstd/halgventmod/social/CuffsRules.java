package com.nrdhvnstd.halgventmod.social;

import java.util.Map;
import java.util.WeakHashMap;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorRules;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.storage.Storage;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Наручники (ТЗ 5.4): урон −80 % — модификатор атрибута эффекта (ModEffects.CUFFED); здесь остальное.
 * Оружие и щит в руку не берутся: выбрал карман с оружием — рука возвращается на другой карман, оружие или щит
 * во второй руке уходит в сумки. Что было в основной руке в момент заковывания, там и остаётся,
 * но ни ударить им, ни поднять щит, ни натянуть лук нельзя. Кулаками бить можно.
 * Удар и использование отменяют и клиент, и сервер (маска ограничений синхронизирована); перекладывание — сервер.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class CuffsRules {
    // Что осталось в руках с момента заковывания. Только в памяти сервера: после перезахода оружие уберут из рук
    private record Kept(ItemStack main, ItemStack off) {}
    private static final Map<Player, Kept> KEPT = new WeakHashMap<>();

    private CuffsRules() {}

    public static boolean forbidden(ItemStack stack) {
        return !stack.isEmpty() && (GearQuality.isWeapon(stack) || stack.getItem() instanceof ShieldItem);
    }

    public static boolean isCuffed(Player player) {
        return Restraint.CUFFS.on(player);
    }

    /** Только что заковали (Social.complete): что в руке — остаётся в руке. */
    static void cuffed(ServerPlayer player) {
        KEPT.put(player, new Kept(player.getMainHandItem(), player.getOffhandItem()));
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (isCuffed(event.getEntity()) && forbidden(event.getEntity().getMainHandItem())) {
            event.setCanceled(true);
        }
    }

    // Щит, лук, арбалет, трезубец — не поднять
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isCuffed(event.getEntity()) && forbidden(event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isCuffed(player)) {
            KEPT.remove(player);
            return;
        }
        Inventory inventory = player.getInventory();
        Kept kept = KEPT.getOrDefault(player, new Kept(ItemStack.EMPTY, ItemStack.EMPTY));
        ItemStack main = player.getMainHandItem();
        ItemStack off = inventory.offhand.get(0);
        // Рука сменилась — то, что было, обратно уже не взять
        KEPT.put(player, new Kept(main == kept.main() ? main : ItemStack.EMPTY, off == kept.off() ? off : ItemStack.EMPTY));
        if (main != kept.main() && forbidden(main) && switchAway(player, inventory)) {
            ArmorRules.tell(player, Component.translatable("message.halgventmod.cuffed_weapon"));
        }
        if (off != kept.off() && forbidden(off)) {
            inventory.offhand.set(0, ItemStack.EMPTY);
            Storage.addOrDrop(inventory, off);
            ArmorRules.tell(player, Component.translatable("message.halgventmod.cuffed_weapon"));
        }
    }

    // Рука — на другой карман: пустой, а нет — на карман без оружия. Все карманы с оружием — оставить как есть
    private static boolean switchAway(ServerPlayer player, Inventory inventory) {
        int pick = -1;
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            if (stack.isEmpty()) {
                pick = slot;
                break;
            }
            if (pick < 0 && !forbidden(stack)) {
                pick = slot;
            }
        }
        if (pick < 0) {
            return false;
        }
        inventory.selected = pick;
        player.connection.send(new ClientboundSetCarriedItemPacket(pick));
        return true;
    }
}
