package com.nrdhvnstd.halgventmod.client;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Ordering;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.Hunger;
import com.nrdhvnstd.halgventmod.food.Thirst;
import com.nrdhvnstd.halgventmod.needs.Stamina;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Окно «Персонаж». Посередине — фигурка игрока (как в инвентаре, вдвое крупнее).
 * Слева: «Тело и дух» (потребности, «Здоровье: 40/100») и под ним «Параметры»
 * (скорость, урон и скорость атаки — в процентах от обычных, без учёта оружия; броня).
 * Справа: «Особенности» (3 строки по 5 ячеек под будущие особенности) и под ним «Эффекты».
 * Оформление — по концептам docs/concept_gui: тёмные панели с каймой, названия на свитках, как у торговца;
 * общей подложки нет, как у инвентаря. Спрайты растягиваются по nine_slice (.mcmeta рядом с ними).
 * Ширина колонок считается по тексту; если окно не влезает в экран — оно целиком уменьшается
 * (фигурка рисуется отдельно, без уменьшения рамки: её обрезка работает в экранных координатах).
 * Интоксикации и боли в моде пока нет — показываются нулями.
 */
public class CharacterScreen extends Screen implements MenuTabScreen {
    // ===== Спрайты: textures/gui/sprites/character/, вырезаны из концепта docs/concept_gui =====
    private static final ResourceLocation PANEL = sprite("panel");               // social_interactions.png
    private static final ResourceLocation TITLE = sprite("title");               // свиток из villager.png
    private static final ResourceLocation FIGURE_FRAME = sprite("figure_frame"); // рамка фигурки из инвентаря
    private static final ResourceLocation CELL_SPRITE = sprite("cell");          // ячейка из инвентаря

    // ===== Разметка (в пикселях окна до уменьшения) =====
    private static final int HEIGHT = 210;
    private static final int PAD = 4;           // от края окна: сверху над панелями торчат свитки
    private static final int GAP = 6;           // между колонками
    private static final int V_GAP = 10;        // между панелями одна над другой (место под свиток)
    private static final int INNER = 8;         // внутри панели, за узорной каймой
    private static final int TITLE_H = 12;      // от верха панели до первой строки
    private static final int TITLE_RISE = 4;    // насколько свиток заголовка выступает над панелью
    private static final int TITLE_HEIGHT = 11;
    private static final int TITLE_PADDING = 12; // свиток шире надписи на столько с каждой стороны
    private static final int ROW = 11;          // строка показателя
    private static final int COLUMN_GAP = 12;   // между столбцами показателей
    private static final int FIGURE_WIDTH = 104;
    private static final int FIGURE_SCALE = 60; // в инвентаре 30
    private static final float FIGURE_Y_OFFSET = 0.0625F;
    private static final int TRAIT_SLOTS = 5;
    private static final int CELL = 18;
    private static final int CELL_GAP = 2;
    private static final int TRAIT_ROW = 10 + CELL + 3; // подпись + ячейки + просвет
    private static final int EFFECT_ROW = 20;
    private static final int EFFECTS_MIN_WIDTH = 120;
    private static final int TOP_MARGIN = MenuTabs.TAB_HEIGHT + 2; // под вкладками

    private static final int NEED_MAX = 100;

    // ===== Цвета текста (ARGB), как в календаре =====
    private static final int COLOR_TITLE = 0xFF3B2A1A; // тёмный на свитке
    private static final int COLOR_TEXT = 0xFFE8DCC0;
    private static final int COLOR_DIM = 0xFFB8A888;

    /** Показатель: подпись и значение для строки «Подпись: значение». */
    private record Stat(String key, Component value) {}

    // Ширины, посчитанные в init()
    private int bodyColumn1;
    private int statsColumn1;
    private int leftWidth;
    private int rightWidth;
    private int windowWidth;
    private float scale;
    private float left;
    private float top;

    public CharacterScreen() {
        super(MenuTab.CHARACTER.title());
    }

    @Override
    public MenuTab tab() {
        return MenuTab.CHARACTER;
    }

    @Override
    protected void init() {
        // Ширина столбцов — по самым длинным строкам с запасом на большие значения
        Component needSample = Component.literal(NEED_MAX + "/" + NEED_MAX);
        Component number = Component.literal("100,0");
        Component percent = Component.literal("1000%");
        List<Stat> bodySample = List.of(
                new Stat("health", needSample), new Stat("hunger", needSample),
                new Stat("saturation", needSample), new Stat("stamina", needSample),
                new Stat("thirst", needSample), new Stat("intoxication", needSample), new Stat("pain", needSample));
        List<Stat> statsSample = List.of(
                new Stat("speed", percent), new Stat("attack_damage", percent),
                new Stat("attack_speed", percent), new Stat("armor", number), new Stat("armor_toughness", number));
        int[] body = columnWidths(bodySample);
        int[] stats = columnWidths(statsSample);
        bodyColumn1 = body[0];
        statsColumn1 = stats[0];
        leftWidth = Math.max(body[0] + body[1], stats[0] + stats[1]) + COLUMN_GAP + 2 * INNER;

        int traits = TRAIT_SLOTS * CELL + (TRAIT_SLOTS - 1) * CELL_GAP;
        for (String key : new String[] {"body", "mind", "experience"}) {
            traits = Math.max(traits, font.width(Component.translatable(traitKey(key))));
        }
        rightWidth = Math.max(traits, EFFECTS_MIN_WIDTH) + 2 * INNER;
        windowWidth = PAD + leftWidth + GAP + FIGURE_WIDTH + GAP + rightWidth + PAD;

        // Не влезает — уменьшаем окно целиком
        scale = Math.min(1.0F, Math.min((width - 8.0F) / windowWidth, (height - TOP_MARGIN - 4.0F) / HEIGHT));
        left = (width - windowWidth * scale) / 2.0F;
        top = Math.max(TOP_MARGIN, (height - HEIGHT * scale) / 2.0F);

        for (MenuTabButton button : MenuTabs.createButtons(font, Math.round(left),
                Math.round(windowWidth * scale), MenuTab.CHARACTER)) {
            addRenderableWidget(button);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);

        // Общей подложки нет — панели висят над миром, как ячейки инвентаря
        int columnHeight = HEIGHT - 2 * PAD;
        int figureX = PAD + leftWidth + GAP;
        int rightX = figureX + FIGURE_WIDTH + GAP;

        // Слева: «Тело и дух» сверху, «Параметры» до низа
        int bodyHeight = TITLE_H + 4 * ROW + 5;
        panel(graphics, PAD, PAD, leftWidth, bodyHeight, "body");
        stats(graphics, PAD, PAD, bodyColumn1, bodyStats(player));
        int statsY = PAD + bodyHeight + V_GAP;
        panel(graphics, PAD, statsY, leftWidth, columnHeight - bodyHeight - V_GAP, "stats");
        stats(graphics, PAD, statsY, statsColumn1, attributeStats(player));

        // Посередине: рамка фигурки
        graphics.blitSprite(FIGURE_FRAME, figureX, PAD, FIGURE_WIDTH, columnHeight);

        // Справа: «Особенности» сверху, «Эффекты» до низа
        int traitsHeight = TITLE_H + 3 * TRAIT_ROW + 4;
        panel(graphics, rightX, PAD, rightWidth, traitsHeight, "traits");
        traits(graphics, rightX, PAD);
        int effectsY = PAD + traitsHeight + V_GAP;
        int effectsHeight = columnHeight - traitsHeight - V_GAP;
        panel(graphics, rightX, effectsY, rightWidth, effectsHeight, "effects");
        effects(graphics, player, rightX, effectsY, effectsHeight);
        graphics.pose().popPose();

        // Фигурка — в экранных координатах (см. описание класса)
        int x1 = Math.round(left + (figureX + 1) * scale);
        int y1 = Math.round(top + (PAD + 1) * scale);
        int x2 = Math.round(left + (figureX + FIGURE_WIDTH - 1) * scale);
        int y2 = Math.round(top + (PAD + columnHeight - 1) * scale);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2,
                Math.round(FIGURE_SCALE * scale), FIGURE_Y_OFFSET, mouseX, mouseY, player);
    }

    // ===== Показатели =====

    private static List<Stat> bodyStats(LocalPlayer player) {
        return List.of(
                new Stat("health", need(player.getHealth(), player.getMaxHealth())),
                new Stat("hunger", need(Hunger.get(player), Hunger.MAX)),
                new Stat("saturation", need(Hunger.saturation(player), Hunger.MAX)),
                new Stat("stamina", need(Stamina.get(player), Stamina.MAX)),
                new Stat("thirst", need(Thirst.get(player), Thirst.MAX)),
                new Stat("intoxication", need(0, NEED_MAX)), // пока нет в моде
                new Stat("pain", need(0, NEED_MAX)));        // пока нет в моде
    }

    private static List<Stat> attributeStats(LocalPlayer player) {
        return List.of(
                new Stat("speed", percent(player, Attributes.MOVEMENT_SPEED)),
                new Stat("attack_damage", percent(player, Attributes.ATTACK_DAMAGE)),
                new Stat("attack_speed", percent(player, Attributes.ATTACK_SPEED)),
                new Stat("armor", decimal(player.getAttributeValue(Attributes.ARMOR))),
                new Stat("armor_toughness", decimal(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS))));
    }

    /**
     * Атрибут в процентах от стандартного значения игрока, без вклада оружия в руке:
     * показывает только усиления и ослабления (эффекты, в будущем — особенности и навыки).
     * Урон на клиент не приходит, поэтому модификаторы эффектов добавляем сами, по действующим эффектам.
     */
    private static Component percent(LocalPlayer player, Holder<Attribute> attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return Component.literal("100%");
        }
        Map<ResourceLocation, AttributeModifier> modifiers = new HashMap<>();
        for (AttributeModifier modifier : instance.getModifiers()) {
            modifiers.put(modifier.id(), modifier);
        }
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effect.getEffect().value().createModifiers(effect.getAmplifier(), (holder, modifier) -> {
                if (holder.equals(attribute)) {
                    modifiers.put(modifier.id(), modifier);
                }
            });
        }
        player.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (holder, modifier) -> {
            if (holder.equals(attribute)) {
                modifiers.remove(modifier.id());
            }
        });
        double base = instance.getBaseValue();
        double value = apply(base, modifiers.values());
        return Component.literal(base == 0 ? "100%" : Math.round(value / base * 100) + "%");
    }

    /** Ванильная формула: сначала прибавки, потом доли от базы, потом множители. */
    private static double apply(double base, Collection<AttributeModifier> modifiers) {
        double value = base;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                value += modifier.amount();
            }
        }
        double added = value;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                value += added * modifier.amount();
            }
        }
        for (AttributeModifier modifier : modifiers) {
            if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                value *= 1.0 + modifier.amount();
            }
        }
        return Math.max(0, value);
    }

    /** «40/100». Округляем вверх: пока есть хоть капля — не ноль (как на шкалах). */
    private static Component need(float value, float max) {
        return Component.literal(Mth.ceil(value) + "/" + Mth.ceil(max));
    }

    private static Component decimal(double value) {
        return Component.literal(WeightText.decimal(value));
    }

    private Component statLine(Stat stat) {
        return Component.translatable("gui.halgventmod.character.stat",
                Component.translatable("gui.halgventmod.character." + stat.key()), stat.value());
    }

    /** Ширина двух столбцов: первая половина показателей (с округлением вверх) — в левом. */
    private int[] columnWidths(List<Stat> stats) {
        int rows = (stats.size() + 1) / 2;
        int[] widths = new int[2];
        for (int i = 0; i < stats.size(); i++) {
            int column = i < rows ? 0 : 1;
            widths[column] = Math.max(widths[column], font.width(statLine(stats.get(i))));
        }
        return widths;
    }

    // ===== Рисование =====

    /** Тёмная панель с каймой и свиток с названием посередине её верхнего края. */
    private void panel(GuiGraphics graphics, int x, int y, int width, int height, String key) {
        graphics.blitSprite(PANEL, x, y, width, height);
        Component title = Component.translatable("gui.halgventmod.character." + key);
        int titleWidth = font.width(title) + 2 * TITLE_PADDING;
        int titleX = x + (width - titleWidth) / 2;
        int titleY = y - TITLE_RISE;
        graphics.blitSprite(TITLE, titleX, titleY, titleWidth, TITLE_HEIGHT);
        graphics.drawString(font, title, titleX + TITLE_PADDING, titleY + 2, COLOR_TITLE, false);
    }

    /** Показатели в два столбца: сначала сверху вниз левый, потом правый. */
    private void stats(GuiGraphics graphics, int panelX, int panelY, int column1Width, List<Stat> stats) {
        int rows = (stats.size() + 1) / 2;
        for (int i = 0; i < stats.size(); i++) {
            int x = panelX + INNER + (i < rows ? 0 : column1Width + COLUMN_GAP);
            int y = panelY + TITLE_H + (i % rows) * ROW;
            graphics.drawString(font, statLine(stats.get(i)), x, y, COLOR_TEXT, false);
        }
    }

    /** Три строки по 5 пустых ячеек — места под будущие особенности. */
    private void traits(GuiGraphics graphics, int panelX, int panelY) {
        String[] rows = {"body", "mind", "experience"};
        for (int row = 0; row < rows.length; row++) {
            int y = panelY + TITLE_H + row * TRAIT_ROW;
            graphics.drawString(font, Component.translatable(traitKey(rows[row])),
                    panelX + INNER, y, COLOR_DIM, false);
            for (int slot = 0; slot < TRAIT_SLOTS; slot++) {
                int x = panelX + INNER + slot * (CELL + CELL_GAP);
                graphics.blitSprite(CELL_SPRITE, x, y + 10, CELL, CELL);
            }
        }
    }

    /** Действующие эффекты: значок, название и оставшееся время. Что не влезло — «…и ещё N». */
    private void effects(GuiGraphics graphics, LocalPlayer player, int panelX, int panelY, int panelHeight) {
        List<MobEffectInstance> effects = Ordering.natural().sortedCopy(player.getActiveEffects());
        int x = panelX + INNER;
        int y = panelY + TITLE_H;
        if (effects.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.halgventmod.character.effects.none"),
                    x, y, COLOR_DIM, false);
            return;
        }
        int fits = Math.max(1, (panelHeight - TITLE_H - INNER) / EFFECT_ROW);
        int shown = effects.size() <= fits ? effects.size() : fits - 1;
        int textX = x + CELL + 4;
        int textWidth = rightWidth - INNER - (textX - panelX);
        float tickRate = minecraft.level.tickRateManager().tickrate();
        for (int i = 0; i < shown; i++) {
            MobEffectInstance effect = effects.get(i);
            TextureAtlasSprite icon = minecraft.getMobEffectTextures().get(effect.getEffect());
            graphics.blit(x, y, 0, CELL, CELL, icon);
            graphics.drawString(font, fit(effectName(effect).getString(), textWidth), textX, y, COLOR_TEXT, false);
            graphics.drawString(font, MobEffectUtil.formatDuration(effect, 1.0F, tickRate), textX, y + 9, COLOR_DIM, false);
            y += EFFECT_ROW;
        }
        if (shown < effects.size()) {
            graphics.drawString(font, Component.translatable("gui.halgventmod.character.effects.more",
                    effects.size() - shown), x, y + 4, COLOR_DIM, false);
        }
    }

    // Как в ванильном инвентаре: «Сила II»
    private static Component effectName(MobEffectInstance effect) {
        MutableComponent name = effect.getEffect().value().getDisplayName().copy();
        if (effect.getAmplifier() >= 1 && effect.getAmplifier() <= 9) {
            name.append(CommonComponents.SPACE)
                    .append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
        }
        return name;
    }

    // Длинное название обрезается с многоточием
    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    private static String traitKey(String row) {
        return "gui.halgventmod.character.traits." + row;
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "character/" + name);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
