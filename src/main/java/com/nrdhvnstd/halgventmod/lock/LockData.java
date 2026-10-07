package com.nrdhvnstd.halgventmod.lock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Замок на двери или сундуке.
 * secret  — секрет: к нему подходит ключ с такой же бородкой;
 * mortise — врезной (ставит только администрация, игроки снять не могут) или навесной;
 * locked  — заперт или отперт.
 * Поле group из старых сохранений просто игнорируется.
 */
public record LockData(long secret, boolean mortise, boolean locked) {
    public static final Codec<LockData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("secret").forGetter(LockData::secret),
            Codec.BOOL.optionalFieldOf("mortise", false).forGetter(LockData::mortise),
            Codec.BOOL.optionalFieldOf("locked", false).forGetter(LockData::locked)
    ).apply(instance, LockData::new));

    public static final StreamCodec<ByteBuf, LockData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LockData::secret,
            ByteBufCodecs.BOOL, LockData::mortise,
            ByteBufCodecs.BOOL, LockData::locked,
            LockData::new);

    public LockData withLocked(boolean value) {
        return new LockData(secret, mortise, value);
    }

    public LockData withSecret(long value) {
        return new LockData(value, mortise, locked);
    }
}
