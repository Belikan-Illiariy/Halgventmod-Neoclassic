package com.nrdhvnstd.halgventmod.registry;

import java.util.List;

import com.mojang.serialization.Codec;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.brewing.Brew;
import com.nrdhvnstd.halgventmod.cooking.Dish;
import com.nrdhvnstd.halgventmod.cooking.FoodExtras;
import com.nrdhvnstd.halgventmod.leather.Hide;
import com.nrdhvnstd.halgventmod.pottery.Stored;
import com.nrdhvnstd.halgventmod.pottery.Vessel;

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
    // Второй хват оружия (бой, X): у профиля оружия — второй набор ударов (combat/WeaponProfile.altGrip)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ALT_GRIP =
            COMPONENTS.register("alt_grip", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

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

    // Порция из горшка: сытость, жажда и состав (Dish)
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

    // Гончарное дело (pottery/*): качество глины, сырца и посуды 0–5 (Ужасное…Шедевр)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> POTTERY_QUALITY =
            COMPONENTS.register("pottery_quality", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 5))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Травы (herbs/*): вид сушёной травы и отвара — ромашка, мята… (Herb.id)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> HERB =
            COMPONENTS.register("herb", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    // Плотничество (carpentry/*): качество деревянной вещи с верстака 0–5 (Ужасное…Шедевр), у инструмента — прочность
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WOOD_QUALITY =
            COMPONENTS.register("wood_quality", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 5))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Кузнечество (smithing/*): качество металла и кованой вещи 0–5 — у крицы, слитков, инструментов (прочность)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> METAL_QUALITY =
            COMPONENTS.register("metal_quality", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 5))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Руда обожжена в гончарном горне (болотная, медная): плавится чище
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ROASTED =
            COMPONENTS.register("roasted", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    // Что в тигле: шихта или расплав (smithing/Crucible)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.nrdhvnstd.halgventmod.smithing.Melt>> CRUCIBLE_MELT =
            COMPONENTS.register("crucible_melt", () -> DataComponentType.<com.nrdhvnstd.halgventmod.smithing.Melt>builder()
                    .persistent(com.nrdhvnstd.halgventmod.smithing.Melt.CODEC)
                    .networkSynchronized(com.nrdhvnstd.halgventmod.smithing.Melt.STREAM_CODEC)
                    .build());

    // Склонность глины трескаться при сушке и обжиге, в процентах: жирная — больше, с шамотом — меньше
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CLAY_CRACK =
            COMPONENTS.register("clay_crack", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Форма сырца или посудины по вокселям: так её и видно
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Vessel>> VESSEL =
            COMPONENTS.register("vessel", () -> DataComponentType.<Vessel>builder()
                    .persistent(Vessel.CODEC)
                    .networkSynchronized(Vessel.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    // Признаки посуды битами (Pottery.DRIED, CRACKED, BURNISHED, BLACK, UNDERFIRED, OVERFIRED)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WARE_FLAGS =
            COMPONENTS.register("ware_flags", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Сколько мер воды в кружке, кувшине или корчаге
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WATER =
            COMPONENTS.register("water", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Что лежит в горшке или корчаге
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Stored>> STORED =
            COMPONENTS.register("stored", () -> DataComponentType.<Stored>builder()
                    .persistent(Stored.CODEC)
                    .networkSynchronized(Stored.STREAM_CODEC)
                    .build());

    // Сколько раз в горшке варили на очаге — износ
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> POT_USES =
            COMPONENTS.register("pot_uses", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Оружие: сколько ударов осталось до затупления (Dullness); нет — ещё не считали или только что заточено
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> HITS_TO_DULL =
            COMPONENTS.register("hits_to_dull", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Светильник: запас масла в тиках и горит ли
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LAMP_FUEL =
            COMPONENTS.register("lamp_fuel", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> LAMP_LIT =
            COMPONENTS.register("lamp_lit", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    // В какую миску налита порция из горшка: её и вернуть, когда съедят
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemStack>> SERVED_IN =
            COMPONENTS.register("served_in", () -> DataComponentType.<ItemStack>builder()
                    .persistent(ItemStack.CODEC)
                    .networkSynchronized(ItemStack.STREAM_CODEC)
                    .build());

    // Сулема на клинке: сколько ударов ещё отравят (alchemy/Alchemy)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> POISON_COAT =
            COMPONENTS.register("poison_coat", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    // Клеймо мастера (smithing/Hallmark): у клейма — чей знак выбит, у вещи — чьим клеймом помечена
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> HALLMARK =
            COMPONENTS.register("hallmark", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    // Цвет выдувного стекла (glass/Glassmaking.Tint): у бокала, бутыли
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> GLASS_TINT =
            COMPONENTS.register("glass_tint", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    private ModComponents() {}

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}