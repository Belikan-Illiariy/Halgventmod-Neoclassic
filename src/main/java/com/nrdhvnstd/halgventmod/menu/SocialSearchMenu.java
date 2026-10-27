package com.nrdhvnstd.halgventmod.menu;

import com.nrdhvnstd.halgventmod.registry.ModMenus;
import com.nrdhvnstd.halgventmod.social.Social;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Обыск (docs/TZ_social_interactions.md, 5.1): инвентарь жертвы в раскладке окна инвентаря — броня, снаряжение,
 * сумки, карманы, вторая рука — только для просмотра. Ни один клик ничего не меняет ни у жертвы, ни у атакующего.
 * Закрывается Esc, сам — когда жертва ушла из радиуса, и при уроне атакующему (Social.onDamage).
 */
public class SocialSearchMenu extends PlayerArmorMenu {
    private final Player target;

    public static SocialSearchMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        Entity entity = inventory.player.level().getEntity(buf.readVarInt());
        if (!(entity instanceof Player target)) {
            throw new IllegalStateException("Searched player is not loaded on the client");
        }
        // Своя копия карманов: сервер заполнит её вещами жертвы, не трогая того, что клиент знает о её руках
        return new SocialSearchMenu(containerId, target, new Inventory(target));
    }

    /** inventory — вещи жертвы: на сервере её настоящий инвентарь, на клиенте — копия под вещи с сервера. */
    public SocialSearchMenu(int containerId, Player target, Inventory inventory) {
        super(ModMenus.SOCIAL_SEARCH.get(), containerId, inventory);
        this.target = target;
    }

    public Player target() {
        return target;
    }

    @Override
    public boolean stillValid(Player player) {
        return Social.canStillSearch(player, target);
    }

    // Только просмотр: ни взять, ни положить, ни выбросить, ни Shift+клик, ни перетаскивание
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }
}
