package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: «открой мой инвентарь» (клавиша E). */
public record OpenInventoryPayload() implements CustomPacketPayload {
    public static final OpenInventoryPayload INSTANCE = new OpenInventoryPayload();

    public static final Type<OpenInventoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "open_inventory"));

    public static final StreamCodec<ByteBuf, OpenInventoryPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenInventoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            // Только если не открыто другое окно (сундук, печь...) и игрок не наблюдатель
            if (player.isSpectator() || player.containerMenu != player.inventoryMenu) {
                return;
            }
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new PlayerArmorMenu(id, inventory),
                    Component.translatable("container.inventory")));
        });
    }
}
