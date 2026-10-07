package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Клиент → сервер: новое название ключа или связки в основной руке. */
public record RenameKeyPayload(String name) implements CustomPacketPayload {
    public static final int MAX_LENGTH = 40;

    public static final Type<RenameKeyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "rename_key"));

    public static final StreamCodec<ByteBuf, RenameKeyPayload> STREAM_CODEC =
            ByteBufCodecs.stringUtf8(MAX_LENGTH).map(RenameKeyPayload::new, RenameKeyPayload::name);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RenameKeyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = player.getMainHandItem();
            if (!stack.is(ModItems.KEY.get()) && !stack.is(ModItems.KEY_RING.get())) {
                return;
            }
            String name = StringUtil.filterText(payload.name()).trim();
            if (name.isEmpty()) {
                stack.remove(ModComponents.KEY_LABEL.get());
            } else {
                stack.set(ModComponents.KEY_LABEL.get(), name);
            }
        });
    }
}