package com.nrdhvnstd.halgventmod.cooking;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Порция блюда из горшка: сытость и насыщение (шкала 0–100), жажда и из чего сварено (id предметов —
 * для подсказки). Лежит компонентом halgventmod:dish; Hunger и Freshness берут значения отсюда,
 * а не из описания предмета.
 */
public record Dish(int nutrition, float saturation, int thirst, List<String> ingredients) {
    public static final Codec<Dish> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 100).fieldOf("nutrition").forGetter(Dish::nutrition),
            Codec.FLOAT.fieldOf("saturation").forGetter(Dish::saturation),
            Codec.INT.optionalFieldOf("thirst", 0).forGetter(Dish::thirst),
            Codec.STRING.listOf().optionalFieldOf("ingredients", List.of()).forGetter(Dish::ingredients)
    ).apply(instance, Dish::new));

    public static final StreamCodec<ByteBuf, Dish> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Dish::nutrition,
            ByteBufCodecs.FLOAT, Dish::saturation,
            ByteBufCodecs.VAR_INT, Dish::thirst,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Dish::ingredients,
            Dish::new);
}
