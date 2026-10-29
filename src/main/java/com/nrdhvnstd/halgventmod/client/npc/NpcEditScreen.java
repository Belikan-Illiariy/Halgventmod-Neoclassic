package com.nrdhvnstd.halgventmod.client.npc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.nrdhvnstd.halgventmod.character.Appearance;
import com.nrdhvnstd.halgventmod.client.ConquestUi;
import com.nrdhvnstd.halgventmod.network.NpcEditOpenPayload;
import com.nrdhvnstd.halgventmod.network.NpcEditPayload;
import com.nrdhvnstd.halgventmod.npc.NpcRole;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Настройка НПС для администрации (Shift+E по НПС). Три вкладки: «Личность» (имя, роль, скин, тонкие руки,
 * новая личность), «Облик» (11 частей облика) и «Привязки» (место работы и дом порознь, маршрут, который ставится
 * жезлом НПС). «Сохранить» отправляет личность и облик со всех вкладок; привязки и «Новая личность» действуют сразу.
 * Мир за окном идёт, НПС виден рядом — изменения видно после «Сохранить».
 */
public class NpcEditScreen extends Screen {
    private enum Tab {
        IDENTITY("gui.halgventmod.npc.edit.tab.identity"),
        APPEARANCE("gui.halgventmod.npc.edit.tab.appearance"),
        BINDINGS("gui.halgventmod.npc.edit.tab.bindings"),
        EQUIPMENT("gui.halgventmod.npc.edit.tab.equipment");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private static final int WIDTH = 340;
    private static final int HEIGHT = 240;
    private static final int PADDING = 10;
    private static final int TAB_HEIGHT = 18;
    private static final int ROW = 20;
    private static final int STEP = 24;
    private static final int FIELD_WIDTH = 200;
    private static final int COLUMN_WIDTH = 155;
    private static final int PART_STEP = 22;
    private static final int NAME_MAX = 32;
    private static final int BUTTON_HERE = 64;
    private static final int MERCHANT_BOX = 90;
    private static final int BUTTON_CLEAR = 72;
    private static final int COLOR_TITLE = 0xFFFFE8A0;
    private static final int COLOR_DIM = 0xFFB8B0A0;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private final int entityId;
    private final Map<String, String> appearance = new LinkedHashMap<>();
    private Tab tab = Tab.IDENTITY;
    private String name;
    private String skin;
    private NpcRole role;
    private boolean slim;
    private BlockPos post;
    private BlockPos home;
    private int routePoints;
    private String template;
    private String merchant = "";      // ник игрока-торговца, к которому привязан НПС
    private String merchantNick = "";  // что набрано в поле ника

    private NpcEditScreen(NpcEditOpenPayload data) {
        super(Component.translatable("gui.halgventmod.npc.edit.title"));
        entityId = data.entityId();
        takeIdentity(data);
        takeBindings(data);
    }

    /** NpcEditOpenPayload: новое окно или обновление уже открытого для того же НПС. */
    public static void open(NpcEditOpenPayload data) {
        if (Minecraft.getInstance().screen instanceof NpcEditScreen screen && screen.entityId == data.entityId()) {
            screen.refresh(data);
        } else {
            Minecraft.getInstance().setScreen(new NpcEditScreen(data));
        }
    }

    private void takeIdentity(NpcEditOpenPayload data) {
        name = data.name();
        skin = data.skin();
        role = NpcRole.byOrdinal(data.role());
        slim = data.slim();
        appearance.clear();
        for (Appearance.Part part : Appearance.Part.values()) {
            appearance.put(part.id(), Appearance.get(data.appearance(), part));
        }
    }

    private void takeBindings(NpcEditOpenPayload data) {
        post = data.post();
        home = data.home();
        routePoints = data.routePoints();
        template = data.template();
        merchant = data.merchant();
        if (merchantNick.isEmpty()) { // по умолчанию — свой ник: администрация чаще всего и есть торговец
            merchantNick = merchant.isEmpty() && Minecraft.getInstance().player != null
                    ? Minecraft.getInstance().player.getGameProfile().getName() : merchant;
        }
    }

    // Привязки обновляются всегда; личность — только по reset, чтобы не стереть несохранённое
    private void refresh(NpcEditOpenPayload data) {
        if (data.reset()) {
            takeIdentity(data);
        }
        takeBindings(data);
        rebuildWidgets();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    @Override
    protected void init() {
        int x = left() + PADDING;
        int y = top() + PADDING + font.lineHeight + 4;

        // Вкладки
        int tabWidth = (WIDTH - 2 * PADDING - 2 * 4) / Tab.values().length;
        int tabX = x;
        for (Tab each : Tab.values()) {
            Button button = Button.builder(Component.translatable(each.key), pressed -> {
                tab = each;
                rebuildWidgets();
            }).bounds(tabX, y, tabWidth, TAB_HEIGHT).build();
            button.active = each != tab;
            addRenderableWidget(button);
            tabX += tabWidth + 4;
        }
        y += TAB_HEIGHT + 8;

        switch (tab) {
            case IDENTITY -> initIdentity(x, y);
            case APPEARANCE -> initAppearance(x, y);
            case BINDINGS -> initBindings(x, y);
            case EQUIPMENT -> initEquipment(x, y);
        }

        // Общий низ: сохранить и убрать
        int bottom = top() + HEIGHT - PADDING - ROW;
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.npc.edit.save"),
                button -> save()).bounds(x, bottom, FIELD_WIDTH / 2, ROW).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.npc.edit.remove"), button -> {
            send(NpcEditPayload.Action.REMOVE);
            onClose();
        }).bounds(left() + WIDTH - PADDING - FIELD_WIDTH / 2, bottom, FIELD_WIDTH / 2, ROW).build());
    }

    private void initIdentity(int x, int y) {
        EditBox nameBox = box(x, y, "gui.halgventmod.npc.edit.name", name, NAME_MAX);
        nameBox.setResponder(text -> name = text);
        y += STEP;
        addRenderableWidget(CycleButton.<NpcRole>builder(NpcRole::title)
                .withValues(NpcRole.values()).withInitialValue(role)
                .create(x, y, FIELD_WIDTH, ROW, Component.translatable("gui.halgventmod.npc.edit.role"),
                        (button, value) -> role = value));
        y += STEP;
        EditBox skinBox = box(x, y, "gui.halgventmod.npc.edit.skin", skin, 64);
        skinBox.setResponder(text -> skin = text);
        y += STEP;
        addRenderableWidget(CycleButton.onOffBuilder(slim)
                .create(x, y, FIELD_WIDTH, ROW, Component.translatable("gui.halgventmod.npc.edit.slim"),
                        (button, value) -> slim = value));
        y += STEP;
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.npc.edit.reroll"),
                button -> send(NpcEditPayload.Action.REROLL)).bounds(x, y, FIELD_WIDTH, ROW).build());
    }

    // Облик: каждая часть — кнопка, по клику следующий вариант; две колонки
    private void initAppearance(int x, int y) {
        int perColumn = (Appearance.Part.values().length + 1) / 2;
        int index = 0;
        for (Appearance.Part part : Appearance.Part.values()) {
            int column = index / perColumn;
            int row = index % perColumn;
            Button button = Button.builder(partLabel(part), pressed -> {
                List<String> options = part.options();
                int next = (options.indexOf(appearance.get(part.id())) + 1) % options.size();
                appearance.put(part.id(), options.get(next));
                pressed.setMessage(partLabel(part));
            }).bounds(x + column * (COLUMN_WIDTH + 10), y + row * PART_STEP, COLUMN_WIDTH, ROW).build();
            addRenderableWidget(button);
            index++;
        }
    }

    // Снаряжение: настоящий инвентарь НПС — броня, руки и ваш инвентарь рядом, вещи тянутся (NpcGearMenu)
    private void initEquipment(int x, int y) {
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.npc.edit.gear_open"),
                button -> send(NpcEditPayload.Action.OPEN_GEAR)).bounds(x, y, FIELD_WIDTH, ROW).build());
    }

    // Привязки: у каждой строки подпись слева (рисуется в render), «Здесь» и «Сбросить» справа
    private void initBindings(int x, int y) {
        int clearX = left() + WIDTH - PADDING - BUTTON_CLEAR;
        int hereX = clearX - 4 - BUTTON_HERE;
        bindingButtons(hereX, clearX, y, NpcEditPayload.Action.POST_HERE, NpcEditPayload.Action.CLEAR_POST);
        bindingButtons(hereX, clearX, y + STEP, NpcEditPayload.Action.HOME_HERE, NpcEditPayload.Action.CLEAR_HOME);
        Button clearRoute = Button.builder(Component.translatable("gui.halgventmod.npc.edit.route_clear"),
                button -> send(NpcEditPayload.Action.CLEAR_ROUTE)).bounds(clearX, y + 2 * STEP, BUTTON_CLEAR, ROW).build();
        clearRoute.active = routePoints > 0;
        addRenderableWidget(clearRoute);
        // Хозяин лавки (НПС-торговец): ник игрока-торговца, «Здесь» — назначить, «Сбросить» — отвязать
        EditBox nick = new EditBox(font, hereX - 4 - MERCHANT_BOX, y + 3 * STEP, MERCHANT_BOX, ROW,
                Component.translatable("gui.halgventmod.npc.edit.merchant_nick"));
        nick.setMaxLength(32);
        nick.setHint(Component.translatable("gui.halgventmod.npc.edit.merchant_nick").withStyle(style -> style.withColor(COLOR_DIM)));
        nick.setValue(merchantNick);
        nick.setResponder(text -> merchantNick = text);
        addRenderableWidget(nick);
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.npc.edit.merchant_set"), button ->
                PacketDistributor.sendToServer(new NpcEditPayload(entityId, NpcEditPayload.Action.SET_MERCHANT, merchantNick,
                        0, "", false, Map.of()))).bounds(hereX, y + 3 * STEP, BUTTON_HERE, ROW).build());
        Button clearMerchant = Button.builder(Component.translatable("gui.halgventmod.npc.edit.clear"),
                button -> send(NpcEditPayload.Action.CLEAR_MERCHANT)).bounds(clearX, y + 3 * STEP, BUTTON_CLEAR, ROW).build();
        clearMerchant.active = !merchant.isEmpty();
        addRenderableWidget(clearMerchant);
    }

    private void bindingButtons(int hereX, int clearX, int y, NpcEditPayload.Action here, NpcEditPayload.Action clear) {
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.npc.edit.here"),
                button -> send(here)).bounds(hereX, y, BUTTON_HERE, ROW).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.npc.edit.clear"),
                button -> send(clear)).bounds(clearX, y, BUTTON_CLEAR, ROW).build());
    }

    private EditBox box(int x, int y, String hintKey, String value, int maxLength) {
        Component hint = Component.translatable(hintKey);
        EditBox box = new EditBox(font, x, y, FIELD_WIDTH, ROW, hint);
        box.setMaxLength(maxLength);
        box.setHint(hint.copy().withStyle(style -> style.withColor(COLOR_DIM)));
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private Component partLabel(Appearance.Part part) {
        return Component.translatable(part.langKey()).append(": ")
                .append(Component.translatable(part.optionKey(appearance.get(part.id()))));
    }

    private void save() {
        PacketDistributor.sendToServer(new NpcEditPayload(entityId, NpcEditPayload.Action.APPLY,
                name, role.ordinal(), skin, slim, Map.copyOf(appearance)));
    }

    private void send(NpcEditPayload.Action action) {
        PacketDistributor.sendToServer(NpcEditPayload.of(entityId, action));
    }

    private Component place(String labelKey, BlockPos pos) {
        Component value = pos.equals(BlockPos.ZERO) ? Component.translatable("gui.halgventmod.npc.edit.unset")
                : Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
        return Component.translatable(labelKey, value);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        ConquestUi.window(graphics, left(), top(), left() + WIDTH, top() + HEIGHT);
        graphics.drawString(font, title, left() + PADDING, top() + PADDING, COLOR_TITLE, true);
        if (!template.isEmpty()) {
            graphics.drawString(font, template, left() + WIDTH - PADDING - font.width(template),
                    top() + PADDING, COLOR_DIM, false);
        }
        if (tab == Tab.EQUIPMENT) {
            graphics.drawString(font, Component.translatable("gui.halgventmod.npc.edit.gear_hint"), left() + PADDING,
                    top() + PADDING + font.lineHeight + 4 + TAB_HEIGHT + 8 + STEP + 4, COLOR_DIM, false);
        }
        if (tab == Tab.BINDINGS) {
            int x = left() + PADDING;
            int y = top() + PADDING + font.lineHeight + 4 + TAB_HEIGHT + 8;
            int textY = (ROW - font.lineHeight) / 2;
            graphics.drawString(font, place("gui.halgventmod.npc.edit.post_at", post), x, y + textY, COLOR_TEXT, false);
            graphics.drawString(font, place("gui.halgventmod.npc.edit.home_at", home), x, y + STEP + textY, COLOR_TEXT, false);
            graphics.drawString(font, Component.translatable("gui.halgventmod.npc.edit.route_points", routePoints),
                    x, y + 2 * STEP + textY, COLOR_TEXT, false);
            Component merchantName = merchant.isEmpty() ? Component.translatable("gui.halgventmod.npc.edit.unset") : Component.literal(merchant);
            graphics.drawString(font, Component.translatable("gui.halgventmod.npc.edit.merchant", merchantName), x,
                    y + 3 * STEP + textY, COLOR_TEXT, false);
            graphics.drawString(font, Component.translatable("gui.halgventmod.npc.edit.wand_hint"), x,
                    y + 4 * STEP + textY, COLOR_DIM, false);
        }
    }
}
