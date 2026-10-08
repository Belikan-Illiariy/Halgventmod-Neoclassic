package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.food.HungerData;
import com.nrdhvnstd.halgventmod.food.ThirstData;
import com.nrdhvnstd.halgventmod.lock.ChunkLocks;
import com.nrdhvnstd.halgventmod.needs.NeedsSync;
import com.nrdhvnstd.halgventmod.needs.StaminaData;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Данные, прикреплённые к сущностям и чанкам. */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, HalgventMod.MODID);

    // Текущий штраф за перегруз (0.2 = 20%). Пересчитывается каждые полсекунды, поэтому не сохраняется.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Float>> OVERWEIGHT_PENALTY =
            ATTACHMENTS.register("overweight_penalty", () -> AttachmentType.builder(() -> 0.0F).build());

    // Голод 0–100 (ванильный отключён). Сохраняется; после смерти — снова полный.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<HungerData>> HUNGER =
            ATTACHMENTS.register("hunger", () -> AttachmentType.builder(HungerData::new)
                    .serialize(HungerData.CODEC)
                    .build());

    // Жажда. Сохраняется; после смерти — снова полная, как голод.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ThirstData>> THIRST =
            ATTACHMENTS.register("thirst", () -> AttachmentType.builder(ThirstData::new)
                    .serialize(ThirstData.CODEC)
                    .build());

    // Выносливость. Сохраняется; после смерти — снова полная.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<StaminaData>> STAMINA =
            ATTACHMENTS.register("stamina", () -> AttachmentType.builder(StaminaData::new)
                    .serialize(StaminaData.CODEC)
                    .build());

    // Что последним отправлено клиенту о голоде, жажде и выносливости. Не сохраняется.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<NeedsSync.Sent>> NEEDS_SENT =
            ATTACHMENTS.register("needs_sent", () -> AttachmentType.builder(NeedsSync.Sent::new).build());

    // Замки на дверях и сундуках чанка. Сохраняются вместе с чанком.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ChunkLocks>> CHUNK_LOCKS =
            ATTACHMENTS.register("chunk_locks", () -> AttachmentType.builder(ChunkLocks::new)
                    .serialize(ChunkLocks.CODEC)
                    .build());

    // Надетая броня, 12 ячеек. Сохраняется; видна самому игроку и всем, кто его видит.
    // При смерти выпадает (ArmorEvents), с keepInventory переносится в новое тело.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ArmorInventory>> ARMOR =
            ATTACHMENTS.register("armor", () -> AttachmentType.serializable(() -> new ArmorInventory())
                    .copyOnDeath()
                    .sync(ArmorInventory.SYNC)
                    .build());

    private ModAttachments() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}