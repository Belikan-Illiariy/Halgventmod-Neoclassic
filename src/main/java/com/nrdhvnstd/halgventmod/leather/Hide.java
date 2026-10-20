package com.nrdhvnstd.halgventmod.leather;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Шкура: с какого зверя (id из {@link Animals}), сколько кусков кожи из неё выйдет, с мехом ли и снята ли мездра.
 * Лежит компонентом в шкуре и идёт за ней через все стадии — от парной до мокрой кожи на раме.
 */
public record Hide(String animal, int pieces, boolean fur, boolean fleshed) {
    public static final Codec<Hide> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("animal").forGetter(Hide::animal),
            Codec.intRange(1, 16).fieldOf("pieces").forGetter(Hide::pieces),
            Codec.BOOL.optionalFieldOf("fur", false).forGetter(Hide::fur),
            Codec.BOOL.optionalFieldOf("fleshed", false).forGetter(Hide::fleshed)
    ).apply(instance, Hide::new));

    public static final StreamCodec<ByteBuf, Hide> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Hide::animal,
            ByteBufCodecs.VAR_INT, Hide::pieces,
            ByteBufCodecs.BOOL, Hide::fur,
            ByteBufCodecs.BOOL, Hide::fleshed,
            Hide::new);

    public Hide withFleshed() {
        return new Hide(animal, pieces, fur, true);
    }

    /** После золки волос сходит: мех больше не мех. */
    public Hide withoutFur() {
        return new Hide(animal, pieces, false, fleshed);
    }

    /** Из мелкой шкуры (козы, овцы, кролика) выходит пергамент. */
    public boolean parchmentGrade() {
        Animals.Kind kind = Animals.byId(animal);
        return kind != null && kind.parchment();
    }
}
