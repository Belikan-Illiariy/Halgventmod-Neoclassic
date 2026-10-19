package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.gear.GearInventory;
import com.nrdhvnstd.halgventmod.gear.GearSlot;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: кнопка с крестиком в инвентаре. Только в творческом режиме.
 * all = false — удалить вещь на курсоре (её перетащили на крестик);
 * all = true — очистить всё: карманы, вторую руку, всю броню, снаряжение с сумками и вещь на курсоре.
 */
public record ClearInventoryPayload(boolean all) implements CustomPacketPayload {
    public static final ClearInventoryPayload ALL = new ClearInventoryPayload(true);
    public static final ClearInventoryPayload CARRIED = new ClearInventoryPayload(false);

    public static final Type<ClearInventoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "clear_inventory"));

    public static final StreamCodec<ByteBuf, ClearInventoryPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(all -> all ? ALL : CARRIED, ClearInventoryPayload::all);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClearInventoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!player.isCreative()) {
                return;
            }
            if (payload.all()) {
                player.getInventory().clearContent();
                ArmorInventory armor = player.getData(ModAttachments.ARMOR);
                for (ArmorSlot slot : ArmorSlot.values()) {
                    armor.set(slot, ItemStack.EMPTY);
                }
                GearInventory gear = player.getData(ModAttachments.GEAR); // сумки — вместе с содержимым
                for (GearSlot slot : GearSlot.values()) {
                    gear.set(slot, ItemStack.EMPTY);
                }
            }
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
        });
    }
}
