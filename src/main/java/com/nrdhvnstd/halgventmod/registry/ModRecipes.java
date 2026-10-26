package com.nrdhvnstd.halgventmod.registry;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.quality.SharpenRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Свои виды рецептов. Сами рецепты — data/halgventmod/recipe/*.json. */
public final class ModRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, HalgventMod.MODID);

    /** Заточка оружия точильным камнем на верстаке (Dullness). */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<SharpenRecipe>> SHARPEN =
            SERIALIZERS.register("sharpen", () -> new SimpleCraftingRecipeSerializer<>(SharpenRecipe::new));

    private ModRecipes() {}

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
