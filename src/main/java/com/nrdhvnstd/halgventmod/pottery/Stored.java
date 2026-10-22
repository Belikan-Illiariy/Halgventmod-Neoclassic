package com.nrdhvnstd.halgventmod.pottery;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Что лежит в горшке (еда) или корчаге (питьё): одна стопка одного и того же, с какими сутками её положили
 * и запечатан ли сосуд воском. Внутри портится медленнее — срок сдвигается, когда вынимают (Pottery.unpack).
 */
public record Stored(ItemStack stack, int since, boolean sealed) {
    public static final Codec<Stored> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("stack").forGetter(Stored::stack),
            Codec.INT.fieldOf("since").forGetter(Stored::since),
            Codec.BOOL.optionalFieldOf("sealed", false).forGetter(Stored::sealed)
    ).apply(instance, Stored::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Stored> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, Stored::stack,
            ByteBufCodecs.VAR_INT, Stored::since,
            ByteBufCodecs.BOOL, Stored::sealed,
            Stored::new);

    @Override
    public boolean equals(Object other) {
        return other instanceof Stored s && since == s.since && sealed == s.sealed && stack.getCount() == s.stack.getCount()
                && ItemStack.isSameItemSameComponents(stack, s.stack);
    }

    @Override
    public int hashCode() {
        return (ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount()) * 31 + since * 2 + (sealed ? 1 : 0);
    }
}
