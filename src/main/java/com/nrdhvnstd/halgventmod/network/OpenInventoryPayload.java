package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.menu.PlayerArmorMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: «открой мой инвентарь» (клавиша Tab, вкладка «Инвентарь» каталога).
 * carried — вещь на курсоре в каталоге творческого режима: она есть только у клиента,
 * сервер кладёт её на курсор открытого окна. Вне творческого режима не учитывается.
 */
public record OpenInventoryPayload(ItemStack carried) implements CustomPacketPayload {
    public static final OpenInventoryPayload INSTANCE = new OpenInventoryPayload(ItemStack.EMPTY);

    public static final Type<OpenInventoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "open_inventory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenInventoryPayload> STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.map(OpenInventoryPayload::new, OpenInventoryPayload::carried);

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
            if (player.containerMenu instanceof PlayerArmorMenu menu && creativeCarried(player, payload.carried())) {
                menu.setCarried(payload.carried().copy());
                menu.broadcastChanges();
            }
        });
    }

    // Те же проверки, что у ванильной выдачи вещи из каталога (ServerboundSetCreativeModeSlotPacket)
    private static boolean creativeCarried(Player player, ItemStack stack) {
        return player.isCreative() && !stack.isEmpty()
                && stack.isItemEnabled(player.level().enabledFeatures())
                && stack.getCount() <= stack.getMaxStackSize();
    }
}
