package com.nrdhvnstd.halgventmod.network;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModSounds;
import com.nrdhvnstd.halgventmod.sound.SoundPlay;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Клиент → сервер: «подними или опусти забрало» (клавиша V).
 * Сервер переключает halgventmod:visor_raised у надетого шлема и кладёт шлем обратно —
 * броня уходит всем по обычной синхронизации. Нет шлема или шлем без забрала — ничего не происходит.
 */
public record ToggleVisorPayload() implements CustomPacketPayload {
    public static final ToggleVisorPayload INSTANCE = new ToggleVisorPayload();

    private static final float VISOR_VOLUME = 0.8F;        // ⚠️
    private static final float VISOR_PITCH_SPREAD = 0.05F; // ⚠️

    public static final Type<ToggleVisorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "toggle_visor"));

    public static final StreamCodec<ByteBuf, ToggleVisorPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleVisorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (player.isSpectator()) {
                return;
            }
            ArmorInventory armor = player.getData(ModAttachments.ARMOR);
            ItemStack helmet = armor.get(ArmorSlot.HELMET);
            ModArmor.ArmorInfo info = ModArmor.get(helmet);
            if (info == null || !info.visor()) {
                return;
            }
            // На копии и обратно через set: срабатывает onContentsChanged, и броня уходит всем
            ItemStack toggled = helmet.copy();
            boolean lowering = ModArmor.visorRaised(helmet);
            if (lowering) {
                toggled.remove(ModComponents.VISOR_RAISED.get());
            } else {
                toggled.set(ModComponents.VISOR_RAISED.get(), true);
            }
            armor.set(ArmorSlot.HELMET, toggled);
            SoundPlay.at(player, lowering ? ModSounds.ARMOR_VISOR_CLOSE.get() : ModSounds.ARMOR_VISOR_OPEN.get(),
                    SoundSource.PLAYERS, VISOR_VOLUME, VISOR_PITCH_SPREAD);
        });
    }
}
