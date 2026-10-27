package com.nrdhvnstd.halgventmod.downed;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;

/**
 * Состояние «Повержен» на игроке: сколько тиков осталось (0 — не повержен) и когда падал последний раз.
 * Сохраняется: выход из игры таймер не сбрасывает. Всем, кто видит игрока, рассылается только остаток —
 * для надписи над перекрестием и полоски над головой; между рассылками клиент считает сам.
 */
public class DownedData {
    public static final long NEVER = Long.MIN_VALUE / 2;

    public static final Codec<DownedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("ticks", 0).forGetter(data -> data.ticks),
            Codec.LONG.optionalFieldOf("last", NEVER).forGetter(data -> data.lastDownedAt)
    ).apply(instance, DownedData::new));

    public static final AttachmentSyncHandler<DownedData> SYNC = new AttachmentSyncHandler<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, DownedData data, boolean initialSync) {
            buf.writeVarInt(data.ticks);
        }

        @Override
        public DownedData read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, DownedData previousValue) {
            return new DownedData(buf.readVarInt(), NEVER);
        }
    };

    int ticks;
    long lastDownedAt = NEVER;

    public DownedData() {}

    private DownedData(int ticks, long lastDownedAt) {
        this.ticks = ticks;
        this.lastDownedAt = lastDownedAt;
    }

    public int ticks() {
        return ticks;
    }
}
