package com.nrdhvnstd.halgventmod.client.squad;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.client.MenuTab;
import com.nrdhvnstd.halgventmod.client.MenuTabButton;
import com.nrdhvnstd.halgventmod.client.MenuTabScreen;
import com.nrdhvnstd.halgventmod.client.MenuTabs;
import com.nrdhvnstd.halgventmod.network.SquadActionPayload;
import com.nrdhvnstd.halgventmod.network.SquadActionPayload.Action;
import com.nrdhvnstd.halgventmod.network.SquadStatePayload;
import com.nrdhvnstd.halgventmod.squad.SquadClientState;
import com.nrdhvnstd.halgventmod.squad.SquadMapMode;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Вкладка «Отряд». Что видно — зависит от того, есть ли у игрока отряд:
 * - нет отряда — две кнопки: «Приглашения в отряд» (таблица приглашений: отряд, кто пригласил, принять, отклонить)
 *   и «Создать отряд» (редактор SquadEditScreen);
 * - есть отряд — слева знамя и название (лидеру — «Переименовать» и «Изменить знамя»), справа список участников
 *   со званиями; под ним три квадратные кнопки (урон по своим, отображение на карте, пригласить) и «Распустить».
 *   ПКМ лидера по участнику — меню: звание, выгнать, сделать лидером; ПКМ по значку карты — выбор званий.
 * Всё, что показывает окно, присылает сервер (SquadClientState); действия уходят ему же (SquadActionPayload).
 */
public class SquadScreen extends Screen implements MenuTabScreen, SquadClientState.Listener {
    // ===== Разметка =====
    private static final int HOME_BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 2;
    private static final int TOP_MARGIN = MenuTabs.TAB_HEIGHT + 10;

    private static final int INVITES_WIDTH = 320;
    private static final int INVITES_ROWS = 8;
    private static final int ROW = 14;
    private static final int ICON_COLUMN = 18;

    private static final int LEFT_WIDTH = 140;
    private static final int RIGHT_WIDTH = 240;
    private static final int COLUMN_GAP = 8;
    private static final int WINDOW_HEIGHT = 200;
    private static final int LIST_HEIGHT = 148;
    private static final int MEMBER_ROW = 12;
    private static final float BANNER_SCALE = 2.5F; // 50 × 100

    private static final int MENU_ITEM_HEIGHT = 14;
    private static final int MENU_PADDING = 4;

    private enum Mode { HOME, INVITES }

    private Mode mode = Mode.HOME;
    private int scroll;           // первая видимая строка списка (приглашения или участники)
    private int windowLeft;
    private int windowTop;
    @Nullable
    private ContextMenu contextMenu;
    @Nullable
    private Button mapButton;
    @Nullable
    private Component error;
    private long errorUntil;

    /** Меню правого клика по участнику: пункты и их место на экране. */
    private record ContextMenu(UUID member, String name, int x, int y, int width, List<MenuItem> items) {}

    private record MenuItem(Component label, Runnable action) {}

    public SquadScreen() {
        super(MenuTab.SQUAD.title());
        send(SquadActionPayload.of(Action.REFRESH)); // приглашения и «в игре» могли устареть
    }

    @Override
    public MenuTab tab() {
        return MenuTab.SQUAD;
    }

    @Override
    protected void init() {
        SquadClientState.listener = this;
        contextMenu = null;
        mapButton = null;
        SquadStatePayload.SquadView squad = SquadClientState.squad;
        if (squad != null) {
            initSquad(squad);
        } else if (mode == Mode.INVITES) {
            initInvites();
        } else {
            initHome();
        }
    }

    @Override
    public void removed() {
        if (SquadClientState.listener == this) {
            SquadClientState.listener = null;
        }
    }

    private void tabs(int left, int width) {
        for (MenuTabButton button : MenuTabs.createButtons(font, left, width, MenuTab.SQUAD)) {
            addRenderableWidget(button);
        }
    }

    // ===== Нет отряда: две кнопки =====

    private void initHome() {
        tabs(width / 2 - HOME_BUTTON_WIDTH / 2, HOME_BUTTON_WIDTH);
        int x = (width - HOME_BUTTON_WIDTH) / 2;
        int y = height / 2 - BUTTON_HEIGHT - BUTTON_GAP * 2;
        Component invites = SquadClientState.invites.isEmpty()
                ? Component.translatable("gui.halgventmod.squad.invites")
                : Component.translatable("gui.halgventmod.squad.invites_count", SquadClientState.invites.size());
        addRenderableWidget(Button.builder(invites, button -> {
            mode = Mode.INVITES;
            scroll = 0;
            rebuildWidgets();
        }).bounds(x, y, HOME_BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.squad.create"),
                button -> minecraft.setScreen(SquadEditScreen.create(this)))
                .bounds(x, y + BUTTON_HEIGHT + BUTTON_GAP * 4, HOME_BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    // ===== Приглашения: таблица =====

    private int invitesPanelHeight() {
        return SquadUi.TITLE_H + ROW + INVITES_ROWS * ROW + SquadUi.INNER;
    }

    private void initInvites() {
        windowLeft = (width - INVITES_WIDTH) / 2;
        windowTop = Math.max(TOP_MARGIN, (height - invitesPanelHeight() - BUTTON_HEIGHT - 8) / 2);
        tabs(windowLeft, INVITES_WIDTH);
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> {
            mode = Mode.HOME;
            rebuildWidgets();
        }).bounds((width - HOME_BUTTON_WIDTH) / 2, windowTop + invitesPanelHeight() + 8, HOME_BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void renderInvites(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = windowLeft;
        int y = windowTop;
        SquadUi.panel(graphics, font, x, y, INVITES_WIDTH, invitesPanelHeight(),
                Component.translatable("gui.halgventmod.squad.invites"));
        int nameX = x + SquadUi.INNER;
        int inviterX = x + INVITES_WIDTH / 2;
        int acceptX = x + INVITES_WIDTH - SquadUi.INNER - 2 * ICON_COLUMN;
        int declineX = acceptX + ICON_COLUMN;
        int headerY = y + SquadUi.TITLE_H;
        graphics.drawString(font, Component.translatable("gui.halgventmod.squad.column.squad"), nameX, headerY,
                SquadUi.COLOR_DIM, false);
        graphics.drawString(font, Component.translatable("gui.halgventmod.squad.column.inviter"), inviterX, headerY,
                SquadUi.COLOR_DIM, false);

        List<SquadStatePayload.InviteView> invites = SquadClientState.invites;
        if (invites.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.halgventmod.squad.no_invites"),
                    x + INVITES_WIDTH / 2, headerY + ROW + 4, SquadUi.COLOR_DIM);
            return;
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, invites.size() - INVITES_ROWS));
        for (int i = 0; i < INVITES_ROWS && scroll + i < invites.size(); i++) {
            SquadStatePayload.InviteView invite = invites.get(scroll + i);
            int rowY = headerY + ROW + i * ROW;
            if (SquadUi.inside(mouseX, mouseY, x + 4, rowY - 3, INVITES_WIDTH - 8, ROW)) {
                graphics.fill(x + 4, rowY - 3, x + INVITES_WIDTH - 4, rowY - 3 + ROW, SquadUi.COLOR_HOVER_ROW);
            }
            graphics.drawString(font, SquadUi.fit(font, invite.squadName(), inviterX - nameX - 6), nameX, rowY,
                    SquadUi.COLOR_GOLD, false);
            graphics.drawString(font, SquadUi.fit(font, invite.inviter(), acceptX - inviterX - 6), inviterX, rowY,
                    SquadUi.COLOR_TEXT, false);
            boolean overAccept = SquadUi.inside(mouseX, mouseY, acceptX, rowY - 3, ICON_COLUMN, ROW);
            boolean overDecline = SquadUi.inside(mouseX, mouseY, declineX, rowY - 3, ICON_COLUMN, ROW);
            graphics.drawCenteredString(font, "✔", acceptX + ICON_COLUMN / 2, rowY,
                    overAccept ? SquadUi.COLOR_ACCEPT : SquadUi.COLOR_ICON);
            graphics.drawCenteredString(font, "✖", declineX + ICON_COLUMN / 2, rowY,
                    overDecline ? SquadUi.COLOR_DECLINE : SquadUi.COLOR_ICON);
        }
    }

    private boolean clickInvites(double mouseX, double mouseY) {
        int x = windowLeft;
        int headerY = windowTop + SquadUi.TITLE_H;
        int acceptX = x + INVITES_WIDTH - SquadUi.INNER - 2 * ICON_COLUMN;
        int declineX = acceptX + ICON_COLUMN;
        List<SquadStatePayload.InviteView> invites = SquadClientState.invites;
        for (int i = 0; i < INVITES_ROWS && scroll + i < invites.size(); i++) {
            int rowY = headerY + ROW + i * ROW;
            SquadStatePayload.InviteView invite = invites.get(scroll + i);
            if (SquadUi.inside(mouseX, mouseY, acceptX, rowY - 3, ICON_COLUMN, ROW)) {
                send(SquadActionPayload.target(Action.ACCEPT, invite.squad()));
                return true;
            }
            if (SquadUi.inside(mouseX, mouseY, declineX, rowY - 3, ICON_COLUMN, ROW)) {
                send(SquadActionPayload.target(Action.DECLINE, invite.squad()));
                return true;
            }
        }
        return false;
    }

    // ===== Свой отряд =====

    private boolean leader() {
        return minecraft.player != null && SquadClientState.isLeader(minecraft.player.getUUID());
    }

    private void initSquad(SquadStatePayload.SquadView squad) {
        int windowWidth = LEFT_WIDTH + COLUMN_GAP + RIGHT_WIDTH;
        windowLeft = (width - windowWidth) / 2;
        windowTop = Math.max(TOP_MARGIN, (height - WINDOW_HEIGHT) / 2);
        tabs(windowLeft, windowWidth);
        boolean leader = leader();

        // Слева под знаменем и названием — кнопки лидера
        int leftButtonX = windowLeft + SquadUi.INNER;
        int leftButtonWidth = LEFT_WIDTH - 2 * SquadUi.INNER;
        int leftButtonY = windowTop + WINDOW_HEIGHT - SquadUi.INNER - 2 * BUTTON_HEIGHT - BUTTON_GAP;
        if (leader) {
            addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.squad.rename"),
                    button -> minecraft.setScreen(SquadEditScreen.rename(this, squad.name())))
                    .bounds(leftButtonX, leftButtonY, leftButtonWidth, BUTTON_HEIGHT).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.squad.edit_banner"),
                    button -> minecraft.setScreen(SquadEditScreen.banner(this, squad.bannerBase(), squad.bannerPatterns())))
                    .bounds(leftButtonX, leftButtonY + BUTTON_HEIGHT + BUTTON_GAP, leftButtonWidth, BUTTON_HEIGHT).build());
        }

        // Справа под списком — три квадратные кнопки в ряд: урон по своим, карта, пригласить
        int rightX = windowLeft + LEFT_WIDTH + COLUMN_GAP;
        int y = windowTop + LIST_HEIGHT + 6;
        int iconStep = SquadIconButton.SIZE + 4;
        Button friendlyFire = addRenderableWidget(new SquadIconButton(rightX, y, new ItemStack(Items.IRON_SWORD),
                squad.friendlyFire() ? SquadIconButton.Badge.NONE : SquadIconButton.Badge.OFF,
                Component.translatable(squad.friendlyFire()
                        ? "gui.halgventmod.squad.friendly_fire.on" : "gui.halgventmod.squad.friendly_fire.off"),
                button -> send(SquadActionPayload.flag(Action.FRIENDLY_FIRE, !squad.friendlyFire()))));
        mapButton = addRenderableWidget(new SquadIconButton(rightX + iconStep, y, new ItemStack(Items.FILLED_MAP),
                switch (squad.mapMode()) {
                    case OFF -> SquadIconButton.Badge.OFF;
                    case RANKS -> SquadIconButton.Badge.PARTIAL;
                    case ALL -> SquadIconButton.Badge.NONE;
                },
                mapTooltip(squad, leader),
                button -> send(SquadActionPayload.text(Action.MAP_MODE, squad.mapMode().next().id()))));
        friendlyFire.active = leader;
        mapButton.active = leader;
        y += BUTTON_HEIGHT + BUTTON_GAP * 3;
        if (leader) {
            addRenderableWidget(new SquadIconButton(rightX + 2 * iconStep, windowTop + LIST_HEIGHT + 6,
                    new ItemStack(Items.PLAYER_HEAD), SquadIconButton.Badge.NONE,
                    Component.translatable("gui.halgventmod.squad.invite_members"),
                    button -> minecraft.setScreen(new SquadInviteScreen(this))));
            addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.squad.disband"),
                    button -> confirm(Component.translatable("gui.halgventmod.squad.disband.confirm"),
                            SquadActionPayload.of(Action.DISBAND)))
                    .bounds(rightX, y, RIGHT_WIDTH, BUTTON_HEIGHT).build());
        } else {
            addRenderableWidget(Button.builder(Component.translatable("gui.halgventmod.squad.leave"),
                    button -> confirm(Component.translatable("gui.halgventmod.squad.leave.confirm"),
                            SquadActionPayload.of(Action.LEAVE)))
                    .bounds(rightX, y, RIGHT_WIDTH, BUTTON_HEIGHT).build());
        }
    }

    // «Отображение на карте: …»; в режиме званий — какие отмечены; лидеру — как менять
    private static Component mapTooltip(SquadStatePayload.SquadView squad, boolean leader) {
        MutableComponent text = Component.translatable("gui.halgventmod.squad.map.tooltip",
                Component.translatable("gui.halgventmod.squad.map." + squad.mapMode().id()));
        if (squad.mapMode() == SquadMapMode.RANKS) {
            List<String> names = new ArrayList<>();
            for (String rank : squad.mapRanks()) {
                names.add(rank.isEmpty() ? Component.translatable("gui.halgventmod.squad.rank.member").getString() : rank);
            }
            text.append("\n").append(Component.translatable("gui.halgventmod.squad.map.ranks_list",
                    names.isEmpty() ? Component.translatable("gui.halgventmod.squad.map.ranks.none")
                            : Component.literal(String.join(", ", names))).withStyle(ChatFormatting.GRAY));
        }
        if (leader) {
            text.append("\n").append(Component.translatable("gui.halgventmod.squad.map.hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return text;
    }

    private void renderSquad(GuiGraphics graphics, SquadStatePayload.SquadView squad, int mouseX, int mouseY) {
        // Слева: знамя и название
        int leftX = windowLeft;
        SquadUi.panel(graphics, font, leftX, windowTop, LEFT_WIDTH, WINDOW_HEIGHT,
                Component.translatable("gui.halgventmod.squad.title"));
        float bannerWidth = SquadUi.BANNER_W * BANNER_SCALE;
        float bannerTop = windowTop + SquadUi.TITLE_H + 2;
        SquadUi.banner(graphics, leftX + (LEFT_WIDTH - bannerWidth) / 2.0F, bannerTop, BANNER_SCALE,
                squad.bannerBase(), squad.bannerPatterns());
        int nameY = Math.round(bannerTop + SquadUi.BANNER_H * BANNER_SCALE) + 6;
        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(Component.literal(squad.name()), LEFT_WIDTH - 2 * SquadUi.INNER);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawCenteredString(font, lines.get(i), leftX + LEFT_WIDTH / 2, nameY + i * 10, SquadUi.COLOR_GOLD);
        }

        // Справа: список участников
        int rightX = windowLeft + LEFT_WIDTH + COLUMN_GAP;
        int online = 0;
        for (SquadStatePayload.MemberView member : squad.members()) {
            if (member.online()) {
                online++;
            }
        }
        SquadUi.panel(graphics, font, rightX, windowTop, RIGHT_WIDTH, LIST_HEIGHT,
                Component.translatable("gui.halgventmod.squad.members", online, squad.members().size()));
        int rows = visibleMemberRows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, squad.members().size() - rows));
        int rowY = windowTop + SquadUi.TITLE_H;
        int rankRight = rightX + RIGHT_WIDTH - SquadUi.INNER;
        for (int i = 0; i < rows && scroll + i < squad.members().size(); i++) {
            SquadStatePayload.MemberView member = squad.members().get(scroll + i);
            int y = rowY + i * MEMBER_ROW;
            if (SquadUi.inside(mouseX, mouseY, rightX + 4, y - 2, RIGHT_WIDTH - 8, MEMBER_ROW) && contextMenu == null) {
                graphics.fill(rightX + 4, y - 2, rightX + RIGHT_WIDTH - 4, y - 2 + MEMBER_ROW, SquadUi.COLOR_HOVER_ROW);
            }
            boolean isLeader = member.id().equals(squad.leader());
            Component rank = isLeader ? Component.translatable("gui.halgventmod.squad.rank.leader")
                    : member.rank().isEmpty() ? Component.translatable("gui.halgventmod.squad.rank.member")
                    : Component.literal(member.rank());
            int rankWidth = font.width(rank);
            graphics.drawString(font, rank, rankRight - rankWidth, y, isLeader ? SquadUi.COLOR_GOLD : SquadUi.COLOR_DIM, false);
            graphics.drawString(font, SquadUi.fit(font, member.name(), RIGHT_WIDTH - 2 * SquadUi.INNER - rankWidth - 8),
                    rightX + SquadUi.INNER, y, member.online() ? SquadUi.COLOR_TEXT : SquadUi.COLOR_OFFLINE, false);
        }
    }

    private int visibleMemberRows() {
        return (LIST_HEIGHT - SquadUi.TITLE_H - SquadUi.INNER) / MEMBER_ROW;
    }

    @Nullable
    private SquadStatePayload.MemberView memberAt(double mouseX, double mouseY) {
        SquadStatePayload.SquadView squad = SquadClientState.squad;
        if (squad == null) {
            return null;
        }
        int rightX = windowLeft + LEFT_WIDTH + COLUMN_GAP;
        int rowY = windowTop + SquadUi.TITLE_H;
        for (int i = 0; i < visibleMemberRows() && scroll + i < squad.members().size(); i++) {
            if (SquadUi.inside(mouseX, mouseY, rightX + 4, rowY + i * MEMBER_ROW - 2, RIGHT_WIDTH - 8, MEMBER_ROW)) {
                return squad.members().get(scroll + i);
            }
        }
        return null;
    }

    // ПКМ лидера по участнику (не по себе)
    private void openContextMenu(SquadStatePayload.MemberView member, int mouseX, int mouseY) {
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(Component.translatable("gui.halgventmod.squad.menu.rank"),
                () -> minecraft.setScreen(new SquadRankScreen(this, member.id(), member.name(), member.rank()))));
        items.add(new MenuItem(Component.translatable("gui.halgventmod.squad.menu.kick"),
                () -> confirm(Component.translatable("gui.halgventmod.squad.kick.confirm", member.name()),
                        SquadActionPayload.target(Action.KICK, member.id()))));
        items.add(new MenuItem(Component.translatable("gui.halgventmod.squad.menu.leader"),
                () -> confirm(Component.translatable("gui.halgventmod.squad.leader.confirm", member.name()),
                        SquadActionPayload.target(Action.MAKE_LEADER, member.id()))));
        int menuWidth = 0;
        for (MenuItem item : items) {
            menuWidth = Math.max(menuWidth, font.width(item.label()));
        }
        menuWidth += 2 * MENU_PADDING;
        int menuHeight = items.size() * MENU_ITEM_HEIGHT + 2;
        int x = Math.min(mouseX, width - menuWidth - 2);
        int y = Math.min(mouseY, height - menuHeight - 2);
        contextMenu = new ContextMenu(member.id(), member.name(), x, y, menuWidth, items);
    }

    private void renderContextMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        ContextMenu menu = contextMenu;
        int height = menu.items().size() * MENU_ITEM_HEIGHT + 2;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(menu.x() - 1, menu.y() - 1, menu.x() + menu.width() + 1, menu.y() + height + 1, 0xFF6B5A3E);
        graphics.fill(menu.x(), menu.y(), menu.x() + menu.width(), menu.y() + height, 0xF0201810);
        for (int i = 0; i < menu.items().size(); i++) {
            int itemY = menu.y() + 1 + i * MENU_ITEM_HEIGHT;
            boolean hovered = SquadUi.inside(mouseX, mouseY, menu.x(), itemY, menu.width(), MENU_ITEM_HEIGHT);
            if (hovered) {
                graphics.fill(menu.x(), itemY, menu.x() + menu.width(), itemY + MENU_ITEM_HEIGHT, 0x40FFFFFF);
            }
            graphics.drawString(font, menu.items().get(i).label(), menu.x() + MENU_PADDING, itemY + 3,
                    hovered ? 0xFFFFFFFF : SquadUi.COLOR_TEXT, false);
        }
        graphics.pose().popPose();
    }

    // ===== Общее =====

    private void confirm(Component question, SquadActionPayload action) {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                send(action);
            }
            minecraft.setScreen(this);
        }, question, Component.empty(), CommonComponents.GUI_YES, CommonComponents.GUI_NO));
    }

    static void send(SquadActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    @Override
    public void squadStateChanged() {
        if (SquadClientState.squad != null) {
            mode = Mode.HOME; // после выхода из отряда — снова две кнопки
        }
        rebuildWidgets();
    }

    @Override
    public void squadResult(Action action, @Nullable Component error) {
        if (error != null) {
            this.error = error;
            errorUntil = System.currentTimeMillis() + 5000;
        }
    }

    // Панели — под кнопками
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        SquadStatePayload.SquadView squad = SquadClientState.squad;
        if (squad != null) {
            renderSquad(graphics, squad, mouseX, mouseY);
        } else if (mode == Mode.INVITES) {
            renderInvites(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (error != null && System.currentTimeMillis() < errorUntil) {
            graphics.drawCenteredString(font, error, width / 2, height - 14, SquadUi.COLOR_ERROR);
        }
        if (contextMenu != null) {
            renderContextMenu(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenu != null) {
            ContextMenu menu = contextMenu;
            contextMenu = null;
            for (int i = 0; i < menu.items().size(); i++) {
                int itemY = menu.y() + 1 + i * MENU_ITEM_HEIGHT;
                if (SquadUi.inside(mouseX, mouseY, menu.x(), itemY, menu.width(), MENU_ITEM_HEIGHT)) {
                    menu.items().get(i).action().run();
                    return true;
                }
            }
            return true; // щелчок мимо меню — просто закрыть его
        }
        if (SquadClientState.squad == null && mode == Mode.INVITES && button == 0 && clickInvites(mouseX, mouseY)) {
            return true;
        }
        if (SquadClientState.squad != null && button == 1 && leader()) {
            if (mapButton != null && mapButton.isMouseOver(mouseX, mouseY)) {
                minecraft.setScreen(new SquadMapRanksScreen(this));
                return true;
            }
            SquadStatePayload.MemberView member = memberAt(mouseX, mouseY);
            if (member != null && !member.id().equals(minecraft.player.getUUID())) {
                openContextMenu(member, (int) mouseX, (int) mouseY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        scroll = Math.max(0, scroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (contextMenu != null && keyCode == 256) { // Esc — закрыть меню, а не окно
            contextMenu = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
