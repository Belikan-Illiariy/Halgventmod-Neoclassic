package com.nrdhvnstd.halgventmod.registry;

import java.util.List;

import com.mojang.serialization.Codec;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.brewing.Brew;
import com.nrdhvnstd.halgventmod.cooking.Dish;
import com.nrdhvnstd.halgventmod.cooking.FoodExtras;
import com.nrdhvnstd.halgventmod.leather.Hide;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
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

    // Покрытие брони, у которой их несколько (эмаль или роспись); цвет — в ванильном dyed_color
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ModArmor.Coating>> COATING =
            COMPONENTS.register("coating", () -> DataComponentType.<ModArmor.Coating>builder()
                    .persistent(ModArmor.Coating.CODEC)
                    .networkSynchronized(ModArmor.Coating.STREAM_CODEC)
                    .build());

    // Забрало шлема поднято (у шлемов с .visor()). Нет компонента — опущено. Живёт на самом шлеме: снял и надел — осталось как было
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> VISOR_RAISED =
            COMPONENTS.register("visor_raised", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    // Вещи внутри рюкзака, сумки, подсумка или колчана. Лежат в самой вещи: сняли сумку — вещи остались в ней
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> BAG_CONTENTS =
            COMPONENTS.register("bag_contents", () -> DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    // id знамени из реестра знамён у выданного баннера (/banner give). Строка в подсказке — только пока рисунок совпадает
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> REGISTRY_BANNER =
            COMPONENTS.register("registry_banner", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    // id книги-гербовника; нет компонента — общая книга main. Содержимое книги — на сервере (ArmorialBooks)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ARMORIAL_BOOK =
            COMPONENTS.register("armorial_book", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    // Кожевенное дело (leather/*): чья шкура, сколько кусков, с мехом ли, снята ли мездра
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Hide>> HIDE =
            COMPONENTS.register("hide", () -> DataComponentType.<Hide>builder()
                    .persistent(Hide.CODEC)
                    .networkSynchronized(Hide.STREAM_CODEC)
                    .build());

    // Во что высохнет мокрая кожа на раме (Tannage)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TANNAGE =
            COMPONENTS.register("tannage", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    // Качество кожи и вещей из неё 0–4 (LeatherQuality), видно признаком редкости
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LEATHER_QUALITY =
            COMPONENTS.register("leather_quality", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 4))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Сутки, когда кожаную вещь последний раз смазали (LeatherCare)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> OILED_DAY =
            COMPONENTS.register("oiled_day", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Кожаная вещь пересохла — хуже защищает
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> DRIED_OUT =
            COMPONENTS.register("dried_out", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    // Готовка (cooking/*): качество еды 0–4 (FoodQuality), видно признаком редкости
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FOOD_QUALITY =
            COMPONENTS.register("food_quality", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 5))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Порция из котелка: сытость, жажда и состав (Dish)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Dish>> DISH =
            COMPONENTS.register("dish", () -> DataComponentType.<Dish>builder()
                    .persistent(Dish.CODEC)
                    .networkSynchronized(Dish.STREAM_CODEC)
                    .build());

    // Начинка сырого пирога: meat, mushroom, berry
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PIE_FILLING =
            COMPONENTS.register("pie_filling", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    // Качество снаряжения 0–5: Ужасный…Шедевр (quality/GearQuality), видно признаком
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GEAR_QUALITY =
            COMPONENTS.register("gear_quality", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 5))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Необязательные добавки в пирог или булочку: что и сколько насыщения (FoodExtras)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FoodExtras>> FOOD_EXTRAS =
            COMPONENTS.register("food_extras", () -> DataComponentType.<FoodExtras>builder()
                    .persistent(FoodExtras.CODEC)
                    .networkSynchronized(FoodExtras.STREAM_CODEC)
                    .build());

    // Состав напитка или сусла: основа и приметы (brewing/Brew)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Brew>> BREW =
            COMPONENTS.register("brew", () -> DataComponentType.<Brew>builder()
                    .persistent(Brew.CODEC)
                    .networkSynchronized(Brew.STREAM_CODEC)
                    .build());

    private ModComponents() {}

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}