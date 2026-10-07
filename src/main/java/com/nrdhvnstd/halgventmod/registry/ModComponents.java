package com.nrdhvnstd.halgventmod.registry;

import java.util.List;

import com.mojang.serialization.Codec;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Компоненты данных предметов (замена NBT). */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, HalgventMod.MODID);

    // Признаки предмета: [Сырая] [Оленья]. Лежат в самом предмете,
    // поэтому процессы (чан, колода, сушилка) смогут их менять.
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> TRAITS =
            COMPONENTS.register("traits", () -> DataComponentType.<List<String>>builder()
                    .persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()))
                    .build());

    // Игровые сутки, в которые предмет появился в мире — от них считается срок годности
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CREATED_DAY =
            COMPONENTS.register("created_day", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Бородка ключа = секрет замка. Есть и у снятого навесного замка
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> LOCK_SECRET =
            COMPONENTS.register("lock_secret", () -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build());

    // Название ключа: «Амбар у мельницы»
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> KEY_LABEL =
            COMPONENTS.register("key_label", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    // Ключи на связке
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ItemStack>>> KEY_RING =
            COMPONENTS.register("key_ring", () -> DataComponentType.<List<ItemStack>>builder()
                    .persistent(ItemStack.CODEC.listOf())
                    .networkSynchronized(ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());

    private ModComponents() {}

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}