package com.nrdhvnstd.halgventmod.smithing;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Клеймо мастера ({@link Hallmark}): в инвентаре ПКМ клеймом по металлической вещи — поставить свой знак.
 * Клеймо без знака (из творческого, /give) выбивает знак того, кто им клеймит впервые.
 */
public class SmithStampItem extends Item {
    public SmithStampItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stamp, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        ItemStack target = slot.getItem();
        if (!Hallmark.markable(target)) {
            return false;
        }
        boolean server = !player.level().isClientSide;
        String already = Hallmark.mark(target);
        if (already != null) {
            if (server) {
                Smithing.say(player, "hallmark_already", already);
            }
            return true;
        }
        if (server) {
            Hallmark.bind(stamp, player);
            String mark = Hallmark.mark(stamp);
            target.set(com.nrdhvnstd.halgventmod.registry.ModComponents.HALLMARK.get(), mark);
            Smithing.say(player, "hallmark_set", target.getHoverName(), mark);
            if (!player.getAbilities().instabuild && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                stamp.hurtAndBreak(1, serverPlayer.serverLevel(), serverPlayer, item -> {});
            }
        }
        player.playSound(SoundEvents.ANVIL_PLACE, 0.25F, 1.8F);
        return true;
    }
}
