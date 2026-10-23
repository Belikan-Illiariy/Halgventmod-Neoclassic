package com.nrdhvnstd.halgventmod.smithing;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Что в тигле ({@link Crucible}): доли чистых металлов по id ({@link Metal}), сумма «качество × доли», накрыт ли
 * глиняной крышкой (для латуни), сплавлено ли уже (molten) и до какого игрового тика расплав жидкий.
 * Шихта, которую ещё не плавили, — molten = false.
 */
public record Melt(Map<String, Integer> parts, int qualitySum, boolean lid, boolean molten, long liquidUntil) {
    public static final Codec<Melt> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("parts").forGetter(Melt::parts),
            Codec.INT.optionalFieldOf("quality_sum", 0).forGetter(Melt::qualitySum),
            Codec.BOOL.optionalFieldOf("lid", false).forGetter(Melt::lid),
            Codec.BOOL.optionalFieldOf("molten", false).forGetter(Melt::molten),
            Codec.LONG.optionalFieldOf("liquid_until", 0L).forGetter(Melt::liquidUntil)
    ).apply(instance, Melt::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Melt> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<io.netty.buffer.ByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new,
                    ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), Melt::parts,
            ByteBufCodecs.VAR_INT, Melt::qualitySum,
            ByteBufCodecs.BOOL, Melt::lid,
            ByteBufCodecs.BOOL, Melt::molten,
            ByteBufCodecs.VAR_LONG, Melt::liquidUntil,
            Melt::new);

    public Melt {
        parts = Map.copyOf(parts);
    }
}
