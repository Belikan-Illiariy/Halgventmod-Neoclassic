package com.nrdhvnstd.halgventmod.registry;

import java.util.EnumMap;
import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.item.Category;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Вкладки: по одной на каждую категорию (в порядке Category).
 * Пустые вкладки игра скрывает сама. Иконка вкладки — первый предмет категории.
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HalgventMod.MODID);

    public static final Map<Category, DeferredHolder<CreativeModeTab, CreativeModeTab>> CATEGORY_TABS =
            new EnumMap<>(Category.class);

    static {
        // Первая вкладка встаёт после последней ванильной, каждая следующая — после предыдущей.
        // Не OP_BLOCKS: NeoForge не сортирует служебные вкладки, привязка к ним теряется,
        // и вкладка без привязки уходит по алфавиту ID в начало списка.
        ResourceKey<CreativeModeTab> previous = CreativeModeTabs.SPAWN_EGGS;
        for (Category category : Category.values()) {
            ResourceKey<CreativeModeTab> after = previous;
            DeferredHolder<CreativeModeTab, CreativeModeTab> tab = CREATIVE_MODE_TABS.register(category.folder(),
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable(category.langKey()))
                            .withTabsBefore(after)
                            .icon(() -> icon(category))
                            .displayItems((parameters, output) -> {
                                for (ModItems.Definition def : ModItems.definitions()) {
                                    if (def.category() == category) {
                                        output.accept(def.item().get());
                                    }
                                }
                            })
                            .build());
            CATEGORY_TABS.put(category, tab);
            previous = tab.getKey();
        }
    }

    private ModCreativeTabs() {}

    public static void register(IEventBus modEventBus) {
        CREATIVE_MODE_TABS.register(modEventBus);
    }

    private static ItemStack icon(Category category) {
        for (ModItems.Definition def : ModItems.definitions()) {
            if (def.category() == category) {
                return new ItemStack(def.item().get());
            }
        }
        return new ItemStack(Items.PAPER); // запасной вариант; пустая вкладка всё равно скрыта
    }
}