package com.nrdhvnstd.halgventmod.client;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.item.Gender;
import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/**
 * Плашка качества снаряжения: отдельная рамка рядом со строкой признаков (редкостью), а не ещё один признак в ней.
 * ItemTooltipHandler кладёт строку признаков ({@link #host}); здесь она заменяется строкой «признаки + плашка».
 * Панель предмета в окне инвентаря рисует плашку сама ({@link #render}).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class QualityBadge {
    private static final int GAP = 4;
    private static final int PAD = 3;
    private static final int HEIGHT = 11;
    private static final int FILL = 0xF01A1612;

    /** Строка признаков последней подсказки вещи с качеством — сюда встанет плашка. */
    @Nullable
    private static Component host;
    @Nullable
    private static ItemStack hostStack;

    private QualityBadge() {}

    /** Отметить строку признаков, к которой приставить плашку (зовёт ItemTooltipHandler). */
    static void host(Component line, ItemStack stack) {
        host = line;
        hostStack = stack;
    }

    /** Эта ли строка — строка признаков вещи с качеством. */
    @Nullable
    static GearQuality.Grade gradeFor(Object line) {
        return line == host && hostStack != null ? GearQuality.get(hostStack) : null;
    }

    static Component label(ItemStack stack, GearQuality.Grade grade) {
        ModItems.Definition def = ModItems.definition(stack.getItem());
        Gender gender = def != null ? def.gender() : Gender.M;
        return Component.translatable(ModTraits.langKey(grade.id(), gender));
    }

    static int width(Font font, Component label) {
        return font.width(label) + 2 * PAD;
    }

    /** Нарисовать плашку; x, y — левый верхний угол строки текста. */
    static void render(GuiGraphics graphics, Font font, int x, int y, Component label, GearQuality.Grade grade) {
        int color = 0xFF000000 | grade.trait().color();
        int w = width(font, label);
        int top = y - 2;
        graphics.fill(x, top, x + w, top + HEIGHT, FILL);
        graphics.renderOutline(x, top, w, HEIGHT, color);
        graphics.drawString(font, label, x + PAD, y, color, false);
    }

    // ---------------------------------------------------------------- подсказка

    /** Строка признаков с плашкой справа. */
    public record Line(Component traits, Component label, GearQuality.Grade grade)
            implements TooltipComponent, ClientTooltipComponent {
        @Override
        public int getHeight() {
            return 11;
        }

        @Override
        public int getWidth(Font font) {
            int text = font.width(traits);
            return text + (text > 0 ? GAP : 0) + QualityBadge.width(font, label);
        }

        @Override
        public void renderText(Font font, int x, int y, org.joml.Matrix4f matrix,
                               net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers) {
            font.drawInBatch(traits, x, y + 1, -1, true, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
            int text = font.width(traits);
            QualityBadge.render(graphics, font, x + text + (text > 0 ? GAP : 0), y + 1, label, grade);
        }
    }

    @SubscribeEvent
    public static void registerFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(Line.class, line -> line);
    }

    @SubscribeEvent
    public static void onGather(RenderTooltipEvent.GatherComponents event) {
        var elements = event.getTooltipElements();
        for (int i = 0; i < elements.size(); i++) {
            var element = elements.get(i);
            if (element.left().isEmpty()) {
                continue;
            }
            FormattedText text = element.left().get();
            GearQuality.Grade grade = gradeFor(text);
            if (grade != null && text instanceof Component traits) {
                elements.set(i, com.mojang.datafixers.util.Either.right(
                        new Line(traits, label(event.getItemStack(), grade), grade)));
                return;
            }
        }
    }
}
