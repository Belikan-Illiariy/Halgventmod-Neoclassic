package com.nrdhvnstd.halgventmod.cooking;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Необязательные добавки в пирог или булочку с разделочного стола: что положили (id предметов — для подсказки)
 * и сколько насыщения они прибавляют. Компонент halgventmod:food_extras; из сырого переходит в испечённое,
 * Hunger прибавляет насыщение при еде.
 */
public record FoodExtras(List<String> items, float saturation) {
    public static final Codec<FoodExtras> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.listOf().fieldOf("items").forGetter(FoodExtras::items),
            Codec.FLOAT.fieldOf("saturation").forGetter(FoodExtras::saturation)
    ).apply(instance, FoodExtras::new));

    public static final StreamCodec<ByteBuf, FoodExtras> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), FoodExtras::items,
            ByteBufCodecs.FLOAT, FoodExtras::saturation,
            FoodExtras::new);
}
