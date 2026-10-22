package com.nrdhvnstd.halgventmod.client.cooking;

import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.cooking.Cookery;
import com.nrdhvnstd.halgventmod.cooking.Dish;
import com.nrdhvnstd.halgventmod.cooking.FoodExtras;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Подсказки готовки: сытость и состав порции из горшка, начинка сырого пирога. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class CookingTooltip {
    private CookingTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        List<Component> lines = event.getToolTip();
        Dish dish = stack.get(ModComponents.DISH.get());
        if (dish != null) {
            if (dish.nutrition() > 0) {
                lines.add(Component.translatable("tooltip.halgventmod.dish_food", dish.nutrition(),
                        (dish.thirst() >= 0 ? "+" : "") + dish.thirst())
                        .withStyle(ChatFormatting.GRAY));
            }
            List<Item> items = Cookery.items(dish.ingredients());
            if (!items.isEmpty()) {
                MutableComponent names = Component.empty();
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) {
                        names.append(", ");
                    }
                    names.append(items.get(i).getDescription());
                }
                lines.add(Component.translatable("tooltip.halgventmod.dish_of", names).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        FoodExtras extras = stack.get(ModComponents.FOOD_EXTRAS.get());
        if (extras != null && !extras.items().isEmpty()) {
            MutableComponent names = Component.empty();
            List<Item> added = Cookery.items(extras.items());
            for (int i = 0; i < added.size(); i++) {
                if (i > 0) {
                    names.append(", ");
                }
                names.append(added.get(i).getDescription());
            }
            lines.add(Component.translatable("tooltip.halgventmod.food_extras", names, Math.round(extras.saturation()))
                    .withStyle(ChatFormatting.GRAY));
        }
        String filling = stack.get(ModComponents.PIE_FILLING.get());
        if (filling != null) {
            lines.add(Component.translatable("tooltip.halgventmod.pie_filling." + filling).withStyle(ChatFormatting.GRAY));
        }
    }
}
