package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/** Число стрел на иконке колчана — там, где у стопки число штук. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class QuiverDecorator {
    private static final int COUNT_COLOR = 0xFFFFFF;
    private static final int COUNT_Z = 200; // над иконкой, как ванильное число

    private QuiverDecorator() {}

    /** Сколько стрел в колчане. */
    public static int arrows(ItemStack quiver) {
        ItemContainerContents contents = quiver.get(ModComponents.BAG_CONTENTS.get());
        return contents == null ? 0 : contents.nonEmptyStream().mapToInt(ItemStack::getCount).sum();
    }

    @SubscribeEvent
    public static void register(RegisterItemDecorationsEvent event) {
        event.register(ModItems.QUIVER.get(), (GuiGraphics graphics, Font font, ItemStack stack, int x, int y) -> {
            int arrows = arrows(stack);
            if (arrows <= 0) {
                return false;
            }
            String text = String.valueOf(arrows);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, COUNT_Z);
            graphics.drawString(font, text, x + 19 - 2 - font.width(text), y + 6 + 3, COUNT_COLOR, true);
            graphics.pose().popPose();
            return true;
        });
    }
}
