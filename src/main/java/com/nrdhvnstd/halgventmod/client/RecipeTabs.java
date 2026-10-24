package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Вкладки тем у списка рецептов — как у наковальни: столбик значков снаружи слева от окна (или книги), выбранная —
 * в рамке, при наведении — название. Окно держит список рецептов целиком, а показывает только выбранную тему.
 * Одна тема — вкладок нет.
 */
public final class RecipeTabs {
    public static final int TAB = 20;
    private static final int SELECTED = 0xFFFFD27F;

    /** Вкладка: значок и ключ названия. */
    public record Tab(ItemStack icon, String langKey) {}

    private final List<Tab> tabs = new ArrayList<>();
    private int selected;

    public RecipeTabs(List<Tab> tabs) {
        this.tabs.addAll(tabs);
    }

    /** Есть ли что показывать: вкладки нужны, только когда тем больше одной. */
    public boolean shown() {
        return tabs.size() > 1;
    }

    /** Сколько места занимают слева (0 — вкладок нет). */
    public int width() {
        return shown() ? TAB + 4 : 0;
    }

    public int selected() {
        return selected;
    }

    public void select(int index) {
        selected = Math.clamp(index, 0, Math.max(0, tabs.size() - 1));
    }

    public Tab current() {
        return tabs.get(selected);
    }

    /** Вкладка под мышью или −1; right — правый край столбика (левый край окна или книги), top — его верх. */
    public int at(double mouseX, double mouseY, int right, int top) {
        if (!shown()) {
            return -1;
        }
        double rx = mouseX - (right - TAB - 2);
        double ry = mouseY - (top + 4);
        if (rx < 0 || rx >= TAB || ry < 0) {
            return -1;
        }
        int i = (int) ry / (TAB + 2);
        return i < tabs.size() && ry % (TAB + 2) < TAB ? i : -1;
    }

    /** Щелчок: попали во вкладку — выбрать её. true — щелчок забран. */
    public boolean click(double mouseX, double mouseY, int button, int right, int top) {
        int i = at(mouseX, mouseY, right, top);
        if (i < 0 || button != 0) {
            return false;
        }
        selected = i;
        return true;
    }

    /** Нарисовать столбик (до окна, чтобы окно легло поверх края). */
    public void render(GuiGraphics g, int right, int top) {
        if (!shown()) {
            return;
        }
        for (int i = 0; i < tabs.size(); i++) {
            int tx = right - TAB - 2;
            int ty = top + 4 + i * (TAB + 2);
            ConquestUi.inset(g, tx, ty, tx + TAB + 3, ty + TAB);
            if (i == selected) {
                g.renderOutline(tx, ty, TAB + 3, TAB, SELECTED);
            }
            g.renderItem(tabs.get(i).icon(), tx + 2, ty + 2);
        }
    }

    /** Подсказка с названием вкладки под мышью. true — показана. */
    public boolean tooltip(GuiGraphics g, Font font, int mouseX, int mouseY, int right, int top) {
        int i = at(mouseX, mouseY, right, top);
        if (i < 0) {
            return false;
        }
        g.renderTooltip(font, Component.translatable(tabs.get(i).langKey()), mouseX, mouseY);
        return true;
    }
}
