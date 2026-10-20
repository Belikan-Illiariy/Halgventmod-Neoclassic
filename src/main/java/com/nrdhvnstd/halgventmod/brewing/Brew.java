package com.nrdhvnstd.halgventmod.brewing;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Из чего напиток (компонент halgventmod:brew): основа и приметы. Основа — apple, berry, glowberry, honey,
 * wort_pale, wort_amber, wort_dark (у крепкого — bread, brandy, apple, mead; у настойки — её спирт).
 * Приметы: honey (сладкое, с мёдом), herbs (на травах), berry (с ягодами). По ним собирается название:
 * «Тёмное пиво на травах», «Ягодное вино с мёдом».
 */
public record Brew(String kind, List<String> flags) {
    public static final Codec<Brew> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("kind").forGetter(Brew::kind),
            Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(Brew::flags)
    ).apply(instance, Brew::new));

    public static final StreamCodec<ByteBuf, Brew> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Brew::kind,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Brew::flags,
            Brew::new);

    public static Brew of(String kind) {
        return new Brew(kind, List.of());
    }

    public Brew withFlag(String flag) {
        if (flags.contains(flag)) {
            return this;
        }
        List<String> more = new ArrayList<>(flags);
        more.add(flag);
        return new Brew(kind, List.copyOf(more));
    }

    public Brew withKind(String newKind) {
        return new Brew(newKind, flags);
    }
}
