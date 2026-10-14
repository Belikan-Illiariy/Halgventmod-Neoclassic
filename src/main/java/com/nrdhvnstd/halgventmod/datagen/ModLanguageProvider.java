package com.nrdhvnstd.halgventmod.datagen;

import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.item.Category;
import com.nrdhvnstd.halgventmod.item.Gender;
import com.nrdhvnstd.halgventmod.item.ModLore;
import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.registry.ModEffects;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.registry.ModPaintings;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** Языковые файлы ru_ru и en_us. Все переводы мода живут здесь. */
public class ModLanguageProvider extends LanguageProvider {
    private static final String PAINTING_AUTHOR = "Halgvent";

    private final boolean russian;

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, HalgventMod.MODID, locale);
        this.russian = locale.equals("ru_ru");
    }

    @Override
    protected void addTranslations() {
        // Вкладки: категории и «Искусство»
        for (Category category : Category.values()) {
            add(category.langKey(), russian ? category.nameRu() : category.nameEn());
        }
        add("itemGroup.halgventmod.art", russian ? "Искусство" : "Art");

        // Названия предметов мода
        for (ModItems.Definition def : ModItems.definitions()) {
            add(def.item().get(), russian ? def.nameRu() : def.nameEn());
        }

        // Описания: и предметов мода, и ванильных, и чужих
        for (Map.Entry<ResourceLocation, ModLore.Entry> lore : ModLore.entries().entrySet()) {
            add(ModLore.langKey(lore.getKey()),
                    ModLore.markup(russian ? lore.getValue().ru() : lore.getValue().en()));
        }

        // Признаки во всех родах (в английском формы одинаковые)
        for (ModTraits.Trait trait : ModTraits.all()) {
            for (Gender gender : Gender.values()) {
                add(ModTraits.langKey(trait.id(), gender), russian ? trait.ru(gender) : trait.en());
            }
        }

        // Картины: название предмета берётся из названия картины
        for (ModPaintings.Definition def : ModPaintings.definitions()) {
            var id = def.variant().location();
            add(id.toLanguageKey("painting", "title"), russian ? def.titleRu() : def.titleEn());
            add(id.toLanguageKey("painting", "author"), PAINTING_AUTHOR);
        }

        // Вес
        add("weight.halgventmod.grams", russian ? "%s гр" : "%s g");
        add("weight.halgventmod.kilograms", russian ? "%s кг" : "%s kg");
        add("weight.halgventmod.decimal", russian ? "," : ".");
        add("tooltip.halgventmod.weight", russian ? "Вес: %s" : "Weight: %s");
        add("hud.halgventmod.carry", "%s / %s");
        add("attribute.name.halgventmod.carry_capacity", russian ? "Грузоподъёмность" : "Carry Capacity");

        // Броня: названия слотов и кнопка каталога в инвентаре
        for (ArmorSlot slot : ArmorSlot.values()) {
            add(slot.langKey(), russian ? slot.nameRu() : slot.nameEn());
        }
        add("gui.halgventmod.catalog", russian ? "Каталог" : "Catalog");
        add("gui.halgventmod.clear_inventory",
                russian ? "Shift+щелчок — очистить весь инвентарь" : "Shift+click to clear the whole inventory");
        add("tooltip.halgventmod.armor_slot", russian ? "Слот: %s" : "Slot: %s");
        add("message.halgventmod.hood_over_helmet",
                russian ? "Капюшон не надеть поверх этого шлема" : "The hood won't fit over this helmet");
        add("message.halgventmod.remove_hood_first", russian ? "Сначала снимите капюшон" : "Take off the hood first");
        add("message.halgventmod.arming_cap_first",
                russian ? "Шлем надевается только поверх подшлемника" : "A helmet goes on only over an arming cap");
        add("message.halgventmod.remove_helmet_first", russian ? "Сначала снимите шлем" : "Take off the helmet first");

        // Отладка: хитбоксы (F3+B) — только администраторам
        add("debug.halgventmod.hitboxes.no_permission",
                russian ? "Хитбоксы доступны только администраторам" : "Hitboxes are available to admins only");

        // Ники над головами игроков (/nametags)
        add("command.halgventmod.nametags.current", russian ? "Ники над головами: %s" : "Name tags: %s");
        add("command.halgventmod.nametags.set",
                russian ? "Ники над головами теперь: %s" : "Name tags are now: %s");
        add("command.halgventmod.nametags.mode.off", russian ? "отключены" : "off");
        add("command.halgventmod.nametags.mode.admins",
                russian ? "видны только администраторам" : "visible to admins only");
        add("command.halgventmod.nametags.mode.all", russian ? "видны всем" : "visible to everyone");

        // Каналы чата
        add("chat.halgventmod.global", russian ? "[Глобальный] %s: %s" : "[Global] %s: %s");
        add("chat.halgventmod.local", russian ? "[Локальный] %s: %s" : "[Local] %s: %s");
        add("chat.halgventmod.emote", russian ? "[Локальный] * %s %s" : "[Local] * %s %s");
        add("chat.halgventmod.global_admins_only",
                russian ? "В глобальный чат («!» в начале) пишут только администраторы" : "Only admins can write to the global chat (\"!\" prefix)");
        add("chat.halgventmod.nobody_heard", russian ? "Рядом никого — вас никто не услышал" : "Nobody is near enough to hear you");

        // Отряды (/squad)
        squad("none", "Вы не в отряде. Создать: /squad create <название>", "You are not in a squad. Create one: /squad create <name>");
        squad("info.header", "Отряд «%s»", "Squad \"%s\"");
        squad("info.leader", "Лидер: %s", "Leader: %s");
        squad("info.members", "Участники (%s): %s", "Members (%s): %s");
        squad("already_in", "Вы уже в отряде «%s»", "You are already in squad \"%s\"");
        squad("name_length", "Название отряда — от %s до %s знаков", "The squad name must be %s to %s characters long");
        squad("name_characters", "В названии можно использовать буквы (кириллица, латиница), цифры, пробел, дефис и апостроф",
                "Only letters (Cyrillic, Latin), digits, spaces, hyphens and apostrophes are allowed");
        squad("name_taken", "Отряд «%s» уже есть", "Squad \"%s\" already exists");
        squad("not_in", "Вы не в отряде", "You are not in a squad");
        squad("not_leader", "Это может только лидер отряда", "Only the squad leader can do this");
        squad("invite_self", "Нельзя пригласить самого себя", "You can't invite yourself");
        squad("target_in_squad", "%s уже в отряде", "%s is already in a squad");
        squad("invite_sent", "Приглашение отправлено: %s. Оно действует пять минут", "Invitation sent to %s. It lasts five minutes");
        squad("invited", "%s приглашает вас в отряд «%s».", "%s invites you to squad \"%s\".");
        squad("button.accept", "[Принять]", "[Accept]");
        squad("button.decline", "[Отклонить]", "[Decline]");
        squad("no_invite", "У вас нет приглашений в отряд", "You have no squad invitations");
        squad("joined", "%s вступает в отряд «%s»", "%s joins squad \"%s\"");
        squad("declined", "%s отклоняет приглашение в отряд", "%s declines the squad invitation");
        squad("leader_cant_leave", "Лидер не может просто уйти: передайте лидерство (/squad leader <игрок>) или распустите отряд (/squad disband)",
                "The leader can't just leave: pass leadership (/squad leader <player>) or disband the squad (/squad disband)");
        squad("you_left", "Вы покинули отряд «%s»", "You left squad \"%s\"");
        squad("left", "%s покидает отряд", "%s leaves the squad");
        squad("kick_self", "Себя исключить нельзя: /squad leave или /squad disband", "You can't kick yourself: /squad leave or /squad disband");
        squad("not_member", "В отряде нет игрока %s", "There is no %s in the squad");
        squad("kicked", "%s исключён из отряда", "%s was kicked from the squad");
        squad("you_kicked", "Вас исключили из отряда «%s»", "You were kicked from squad \"%s\"");
        squad("new_leader", "Новый лидер отряда: %s", "New squad leader: %s");
        squad("disbanded", "Отряд «%s» распущен", "Squad \"%s\" has been disbanded");
        squad("chat", "[Отряд] %s: %s", "[Squad] %s: %s");
        squad("chat_not_in", "Вы не в отряде — писать в чат отряда некому", "You are not in a squad — nobody to talk to");
        squad("created_title", "Создан отряд", "Squad founded");
        squad("banner_too_many", "На знамени не больше %s узоров", "A banner can have at most %s patterns");
        squad("player_offline", "Игрок уже не в сети", "The player is no longer online");
        squad("renamed", "Отряд теперь называется «%s»", "The squad is now called \"%s\"");
        squad("rank_leader", "Звание лидера — «Лидер»", "The leader's rank is always \"Leader\"");
        squad("rank_length", "Звание — не длиннее %s знаков", "A rank can be at most %s characters long");
        squad("friendly_fire_on", "Лидер разрешил урон по своим", "The leader allowed friendly fire");
        squad("friendly_fire_off", "Лидер запретил урон по своим", "The leader disabled friendly fire");
        squad("map_mode", "Отображение отряда на карте: %s", "Squad map display: %s");
        squad("which_invite", "Приглашений несколько — укажите отряд: /squad accept <отряд>", "You have several invitations — name the squad: /squad accept <squad>");

        // Окно «Отряд»
        squadGui("title", "Отряд", "Squad");
        squadGui("invites", "Приглашения в отряд", "Squad invitations");
        squadGui("invites_count", "Приглашения в отряд (%s)", "Squad invitations (%s)");
        squadGui("create", "Создать отряд", "Create squad");
        squadGui("column.squad", "Отряд", "Squad");
        squadGui("column.inviter", "Пригласил", "Invited by");
        squadGui("no_invites", "Приглашений нет", "No invitations");
        squadGui("rename", "Переименовать отряд", "Rename squad");
        squadGui("edit_banner", "Изменить знамя отряда", "Change squad banner");
        squadGui("members", "Список игроков %s/%s", "Players %s/%s");
        squadGui("rank.leader", "Лидер", "Leader");
        squadGui("rank.member", "Участник", "Member");
        squadGui("friendly_fire.on", "Урон по своим включен", "Friendly fire is on");
        squadGui("friendly_fire.off", "Урон по своим выключен", "Friendly fire is off");
        squadGui("invite_members", "Пригласить участников", "Invite players");
        squadGui("disband", "Распустить отряд", "Disband squad");
        squadGui("disband.confirm", "Вы уверены, что хотите распустить отряд?", "Are you sure you want to disband the squad?");
        squadGui("leave", "Покинуть отряд", "Leave squad");
        squadGui("leave.confirm", "Вы уверены, что хотите покинуть отряд?", "Are you sure you want to leave the squad?");
        squadGui("menu.rank", "Изменить звание", "Change rank");
        squadGui("menu.kick", "Выгнать из отряда", "Kick from squad");
        squadGui("menu.leader", "Сделать лидером", "Make leader");
        squadGui("kick.confirm", "Выгнать %s из отряда?", "Kick %s from the squad?");
        squadGui("leader.confirm", "Сделать %s лидером отряда? Вы станете обычным участником", "Make %s the squad leader? You will become a regular member");
        squadGui("save", "Сохранить", "Save");
        squadGui("name", "Название отряда", "Squad name");
        squadGui("banner", "Знамя отряда", "Squad banner");
        squadGui("banner.hint", "Выберите цвет, затем узор. Щелчок по слою — перекрасить его", "Pick a color, then a pattern. Click a layer to recolor it");
        squadGui("banner.layers", "Слои %s/%s", "Layers %s/%s");
        squadGui("banner.base", "Основа: %s", "Base: %s");
        squadGui("invited", "Приглашён", "Invited");
        squadGui("invited_mark", "приглашён", "invited");
        squadGui("invite_to_squad", "Пригласить в отряд", "Invite to squad");
        squadGui("map.tooltip", "Отображение на карте: %s", "Shown on map: %s");
        squadGui("map.off", "отключено", "off");
        squadGui("map.ranks", "включено только для избранных рангов", "only for chosen ranks");
        squadGui("map.all", "включено для всех", "for everyone");
        squadGui("map.ranks_list", "Ранги: %s", "Ranks: %s");
        squadGui("map.ranks.none", "не выбраны", "none chosen");
        squadGui("map.hint", "ЛКМ — сменить режим, ПКМ — выбрать ранги", "LMB — change mode, RMB — choose ranks");
        squadGui("map.ranks_title", "Кому видны товарищи на карте", "Who sees squad members on the map");
        squadGui("map.ranks_hint", "Лидеру видно всегда", "The leader always sees them");
        squadGui("nobody_to_invite", "Все игроки в сети уже в отрядах", "Everyone online is already in a squad");
        squadGui("rank_of", "Звание: %s", "Rank: %s");
        squadGui("rank.hint", "Пустое поле — звание «Участник»", "Leave empty for \"Member\"");

        // RP-имя (/nick)
        add("command.halgventmod.nick.none",
                russian ? "У вас нет RP-имени. Задать: /nick <имя>" : "You have no RP name. Set one: /nick <name>");
        add("command.halgventmod.nick.current", russian ? "Ваше RP-имя: %s" : "Your RP name: %s");
        add("command.halgventmod.nick.set", russian ? "Теперь вас зовут: %s" : "Your name is now: %s");
        add("command.halgventmod.nick.reset", russian ? "RP-имя снято, виден настоящий ник" : "RP name removed, your real name is shown");
        add("command.halgventmod.nick.length",
                russian ? "Имя должно быть от %s до %s знаков" : "The name must be %s to %s characters long");
        add("command.halgventmod.nick.characters",
                russian ? "В имени можно использовать только буквы (кириллица, латиница), пробел, дефис и апостроф; начинаться и заканчиваться оно должно буквой"
                        : "Only letters (Cyrillic, Latin), spaces, hyphens and apostrophes are allowed; the name must start and end with a letter");

        // Срок годности
        add("tooltip.halgventmod.spoils_in", russian ? "Испортится через: %s сут." : "Spoils in: %s d.");
        add("tooltip.halgventmod.spoiled", russian ? "Испорчено" : "Spoiled");

        // Правила фермерства (/gamerule и экран «Правила игры»)
        add("gamerule.farmlandTrampling", russian ? "Вытаптывание пашни" : "Farmland trampling");
        add("gamerule.farmlandDriesToDirt", russian ? "Сухая пашня превращается в землю" : "Dry farmland turns to dirt");
        add("gamerule.cropsNeedWater", russian ? "Растениям нужна сырая пашня" : "Crops need wet farmland");

        // Календарь: дни недели, месяцы, сезоны
        for (int i = 0; i < Calendar.DAYS_PER_WEEK; i++) {
            add(Calendar.weekdayKey(i + 1), russian ? Calendar.WEEKDAYS_RU[i] : Calendar.WEEKDAYS_EN[i]);
        }
        for (int i = 0; i < Calendar.MONTHS_PER_YEAR; i++) {
            add(Calendar.monthKey(i + 1), russian ? Calendar.MONTHS_RU[i] : Calendar.MONTHS_EN[i]);
            add(Calendar.monthKey(i + 1) + ".of", russian ? Calendar.MONTHS_RU_OF[i] : Calendar.MONTHS_EN[i]);
        }
        for (Calendar.Season season : Calendar.Season.values()) {
            add(season.langKey(), russian ? season.nameRu : season.nameEn);
        }
        add("calendar.halgventmod.dawn.title", russian ? "%1$s, %2$s %3$s" : "%1$s, %2$s of %3$s");
        add("calendar.halgventmod.dawn.subtitle", russian ? "%1$s, %2$s г." : "%1$s, year %2$s");
        add("calendar.halgventmod.date", russian ? "%1$s %2$s %3$s г." : "%1$s of %2$s, year %3$s");
        add("calendar.halgventmod.command.date", russian
                ? "Сегодня: %1$s, %2$s %3$s %4$s г., %6$s. %5$s."
                : "Today: %1$s, %2$s of %3$s, year %4$s, %6$s. %5$s.");
        add("calendar.halgventmod.command.jumped", russian
                ? "Время промотано вперёд: %1$s, %2$s" : "Time moved forward: %1$s, %2$s");
        add("calendar.halgventmod.command.rewound", russian
                ? "Время переведено назад: %1$s, %2$s" : "Time moved back: %1$s, %2$s");
        add("calendar.halgventmod.command.time_set_blocked", russian
                ? "Команда /time set отключена: она сбила бы календарь. Используйте /calendar time, /calendar skip или /calendar goto"
                : "/time set is disabled: it would break the calendar. Use /calendar time, /calendar skip or /calendar goto");
        add("calendar.halgventmod.command.too_early", russian
                ? "Раньше начала мира (%s) нельзя" : "Cannot go before the start of the world (%s)");
        add("calendar.halgventmod.command.event_added", russian ? "Событие на %1$s: %2$s" : "Event on %1$s: %2$s");
        add("calendar.halgventmod.command.events_removed", russian ? "События на %s удалены" : "Events on %s removed");
        add("calendar.halgventmod.command.no_events", russian ? "На %s событий нет" : "No events on %s");
        add("calendar.halgventmod.command.daylight", russian
                ? "День: %1$s ч %2$s мин, ночь: %3$s ч %4$s мин"
                : "Day: %1$sh %2$sm, night: %3$sh %4$sm");

        // Окно календаря и клавиша
        add("key.categories.halgventmod", "Halgventmod: Neoclassic");
        add("key.halgventmod.calendar", russian ? "Календарь" : "Calendar");
        add("key.halgventmod.character", russian ? "Персонаж" : "Character");
        add("key.halgventmod.map", russian ? "Карта" : "Map");
        add("key.halgventmod.squad", russian ? "Отряд" : "Squad");
        add("key.halgventmod.codex", russian ? "Кодекс" : "Codex");

        // Вкладки над окнами игрока
        add("gui.halgventmod.tab.inventory", russian ? "Инвентарь" : "Inventory");
        add("gui.halgventmod.tab.character", russian ? "Персонаж" : "Character");
        add("gui.halgventmod.tab.map", russian ? "Карта" : "Map");
        add("gui.halgventmod.tab.squad", russian ? "Отряд" : "Squad");
        add("gui.halgventmod.tab.codex", russian ? "Кодекс" : "Codex");
        add("gui.halgventmod.tab.hint", "%s (%s)");
        add("gui.halgventmod.tab.wip", russian ? "Раздел в разработке" : "Coming soon");

        // Окно «Карта»
        add("gui.halgventmod.map.cursor", "X: %s   Z: %s");
        // Меню правого клика по карте
        add("gui.halgventmod.map.menu.add_waypoint", russian ? "Поставить метку" : "Add Waypoint");
        add("gui.halgventmod.map.menu.edit_waypoint", russian ? "Изменить метку" : "Edit Waypoint");
        add("gui.halgventmod.map.menu.add_text", russian ? "Добавить надпись" : "Add Inscription");
        add("gui.halgventmod.map.menu.edit_text", russian ? "Изменить надпись" : "Edit Inscription");
        add("gui.halgventmod.map.menu.delete_text", russian ? "Удалить надпись" : "Delete Inscription");
        add("gui.halgventmod.map.menu.teleport", russian ? "Телепортация" : "Teleport");
        // Окно надписи на карте
        add("gui.halgventmod.map.text.new", russian ? "Новая надпись" : "New Inscription");
        add("gui.halgventmod.map.text.edit", russian ? "Надпись" : "Inscription");
        add("gui.halgventmod.map.text.text", russian ? "Текст" : "Text");
        add("gui.halgventmod.map.text.size", russian ? "Размер: %s" : "Size: %s");
        add("gui.halgventmod.map.text.color", russian ? "Цвет" : "Color");
        add("gui.halgventmod.map.tool.move", russian ? "Рука" : "Hand");
        add("gui.halgventmod.map.tool.draw", russian ? "Карандаш" : "Pencil");
        add("gui.halgventmod.map.tool.style", russian ? "Стиль" : "Style");

        // Карта: рисование
        add("gui.halgventmod.map.draw.color", russian ? "Цвет" : "Color");
        add("gui.halgventmod.map.draw.brush", russian ? "Кисть" : "Brush");
        add("gui.halgventmod.map.draw.brush_size", russian ? "%s бл." : "%s blocks");
        add("gui.halgventmod.map.draw.hint.draw", russian ? "ЛКМ: рисовать" : "LMB: draw");
        add("gui.halgventmod.map.draw.hint.erase", russian ? "ПКМ: стирать" : "RMB: erase");
        add("gui.halgventmod.map.draw.hint.pan", russian ? "СКМ: двигать карту" : "MMB: move the map");
        add("gui.halgventmod.map.draw.hint.undo", russian ? "Ctrl+Z: отменить штрих" : "Ctrl+Z: undo a stroke");
        add("gui.halgventmod.map.draw.clear", russian ? "Удалить все пометки" : "Erase All Drawings");
        add("gui.halgventmod.map.draw.clear_confirm", russian
                ? "Все рисунки на карте этого мира и измерения пропадут. Отменить будет нельзя."
                : "All drawings on the map of this world and dimension will be lost. This cannot be undone.");

        // Карта: стиль
        add("gui.halgventmod.map.style.base", russian ? "Основа" : "Base");
        add("gui.halgventmod.map.style.base.vanilla", russian ? "Ваниль" : "Vanilla");
        add("gui.halgventmod.map.style.base.parchment", russian ? "Пергамент" : "Parchment");
        add("gui.halgventmod.map.style.base.engraving", russian ? "Гравюра" : "Engraving");
        add("gui.halgventmod.map.style.base.atlas", russian ? "Атлас" : "Atlas");
        add("gui.halgventmod.map.style.group.color", russian ? "Цвет" : "Color");
        add("gui.halgventmod.map.style.group.relief", russian ? "Рельеф" : "Relief");
        add("gui.halgventmod.map.style.group.paper", russian ? "Бумага" : "Paper");
        add("gui.halgventmod.map.style.saturation", russian ? "Насыщенность" : "Saturation");
        add("gui.halgventmod.map.style.contrast", russian ? "Контраст" : "Contrast");
        add("gui.halgventmod.map.style.brightness", russian ? "Яркость" : "Brightness");
        add("gui.halgventmod.map.style.sepia", russian ? "Сепия" : "Sepia");
        add("gui.halgventmod.map.style.hue", russian ? "Оттенок" : "Hue");
        add("gui.halgventmod.map.style.biome_color", russian ? "Цвет биомов" : "Biome Color");
        add("gui.halgventmod.map.style.green_mute", russian ? "Приглушение зелени" : "Mute Greens");
        add("gui.halgventmod.map.style.slopes", russian ? "Склоны" : "Slopes");
        add("gui.halgventmod.map.style.heights", russian ? "Светотень высот" : "Elevation Shading");
        add("gui.halgventmod.map.style.grain", russian ? "Зерно" : "Grain");
        add("gui.halgventmod.map.style.percent", "%s: %s%%");
        add("gui.halgventmod.map.style.degrees", "%s: %s°");
        add("gui.halgventmod.map.style.biome_labels", russian ? "Названия биомов" : "Biome Names");
        add("gui.halgventmod.map.style.reset", russian ? "Сброс к основе" : "Reset to Base");

        // Метки
        add("key.halgventmod.waypoint", russian ? "Поставить метку" : "Add Waypoint");
        add("gui.halgventmod.waypoint.new", russian ? "Новая метка" : "New Waypoint");
        add("gui.halgventmod.waypoint.edit", russian ? "Метка" : "Waypoint");
        add("gui.halgventmod.waypoint.name", russian ? "Название" : "Name");
        add("gui.halgventmod.waypoint.color", russian ? "Цвет" : "Color");
        add("gui.halgventmod.waypoint.save", russian ? "Сохранить" : "Save");
        add("gui.halgventmod.waypoint.delete", russian ? "Удалить" : "Delete");
        add("gui.halgventmod.waypoint.icon", russian ? "Значок: %s" : "Icon: %s");
        add("gui.halgventmod.waypoint.flag_color", russian ? "Цвет флага: %s" : "Flag color: %s");
        add("gui.halgventmod.map.icon.flag", russian ? "Флаг" : "Flag");
        add("gui.halgventmod.map.icon.castle", russian ? "Крепость" : "Fortress");
        add("gui.halgventmod.map.icon.tower", russian ? "Башня" : "Tower");
        add("gui.halgventmod.map.icon.house", russian ? "Дом" : "House");
        add("gui.halgventmod.map.icon.village", russian ? "Деревня" : "Village");
        add("gui.halgventmod.map.icon.ruins", russian ? "Руины" : "Ruins");
        add("gui.halgventmod.map.icon.temple", russian ? "Храм" : "Temple");
        add("gui.halgventmod.map.icon.mill", russian ? "Мельница" : "Mill");
        add("gui.halgventmod.map.icon.mine", russian ? "Шахта" : "Mine");
        add("gui.halgventmod.map.icon.cave", russian ? "Пещера" : "Cave");
        add("gui.halgventmod.map.icon.camp", russian ? "Лагерь" : "Camp");
        add("gui.halgventmod.map.icon.port", russian ? "Пристань" : "Harbor");
        add("gui.halgventmod.map.icon.treasure", russian ? "Клад" : "Treasure");
        add("gui.halgventmod.map.icon.grave", russian ? "Могила" : "Grave");
        add("gui.halgventmod.map.icon.portal", russian ? "Портал" : "Portal");
        add("gui.halgventmod.map.icon.knight", russian ? "Рыцарь" : "Knight");
        add("gui.halgventmod.map.icon.rider", russian ? "Всадник" : "Rider");
        add("gui.halgventmod.map.icon.caravan", russian ? "Обоз" : "Caravan");
        add("gui.halgventmod.map.icon.guard", russian ? "Стража" : "Guard");
        add("gui.halgventmod.map.icon.mark", russian ? "Отметка" : "Mark");
        add("gui.halgventmod.map.icon.gathering", russian ? "Место сбора" : "Gathering Point");
        add("gui.halgventmod.map.icon.goal", russian ? "Цель" : "Goal");
        add("gui.halgventmod.map.icon.battle", russian ? "Битва" : "Battle");
        add("gui.halgventmod.map.icon.dragon", russian ? "Дракон" : "Dragon");
        add("gui.halgventmod.map.icon.beast", russian ? "Чудовище" : "Beast");
        add("gui.halgventmod.waypoint.coords", "X: %s  Y: %s  Z: %s");
        add("gui.halgventmod.waypoint.distance", russian ? "%s м" : "%s m");
        add("gui.halgventmod.waypoint.in_world", russian ? "%s (%s м)" : "%s (%s m)");

        // Окно «Персонаж»
        String character = "gui.halgventmod.character.";
        add(character + "body", russian ? "Тело и дух" : "Body and Spirit");
        add(character + "stats", russian ? "Параметры" : "Attributes");
        add(character + "traits", russian ? "Особенности" : "Traits");
        add(character + "effects", russian ? "Эффекты" : "Effects");
        add(character + "stat", "%s: %s");
        add(character + "health", russian ? "Здоровье" : "Health");
        add(character + "hunger", russian ? "Голод" : "Hunger");
        add(character + "saturation", russian ? "Насыщение" : "Saturation");
        add(character + "stamina", russian ? "Выносливость" : "Stamina");
        add(character + "thirst", russian ? "Жажда" : "Thirst");
        add(character + "intoxication", russian ? "Интоксикация" : "Intoxication");
        add(character + "pain", russian ? "Боль" : "Pain");
        add(character + "speed", russian ? "Скорость" : "Speed");
        add(character + "attack_damage", russian ? "Урон" : "Attack Damage");
        add(character + "attack_speed", russian ? "Скорость атаки" : "Attack Speed");
        add(character + "armor", russian ? "Броня" : "Armor");
        add(character + "armor_toughness", russian ? "Твёрдость брони" : "Armor Toughness");
        add(character + "traits.body", russian ? "Телесные особенности" : "Physical Traits");
        add(character + "traits.mind", russian ? "Душевное состояние" : "State of Mind");
        add(character + "traits.experience", russian ? "Жизненный опыт" : "Life Experience");
        add(character + "effects.none", russian ? "Нет эффектов" : "No effects");
        add(character + "effects.more", russian ? "…и ещё %s" : "…and %s more");
        add("calendar.halgventmod.screen.title", russian ? "Календарь" : "Calendar");
        add("calendar.halgventmod.button", russian ? "Календарь (%s)" : "Calendar (%s)");
        add("calendar.halgventmod.screen.header", russian ? "%1$s %2$s г." : "%1$s, year %2$s");
        add("calendar.halgventmod.screen.arrows", russian
                ? "Shift — на год, Ctrl — на 10 лет, Home — к сегодня"
                : "Shift — by a year, Ctrl — by 10 years, Home — today");
        add("calendar.halgventmod.screen.today", russian ? "Сегодня: %1$s, %2$s %3$s" : "Today: %1$s, %2$s of %3$s");
        add("calendar.halgventmod.screen.daylight", russian ? "День %1$s, ночь %2$s" : "Day %1$s, night %2$s");
        add("calendar.halgventmod.screen.until_sunset", russian ? "До заката: %s" : "Until sunset: %s");
        add("calendar.halgventmod.screen.until_sunrise", russian ? "До рассвета: %s" : "Until sunrise: %s");
        add("calendar.halgventmod.duration.hours", russian ? "%1$s ч %2$s мин" : "%1$sh %2$sm");
        add("calendar.halgventmod.duration.minutes", russian ? "%s мин" : "%sm");

        // Замки и ключи
        add("lock.halgventmod.locked", russian ? "Заперто" : "Locked");
        add("lock.halgventmod.unlocked", russian ? "Отперто" : "Unlocked");
        add("lock.halgventmod.wrong_key", russian ? "Ключ не подходит" : "The key doesn't fit");
        add("lock.halgventmod.installed", russian ? "Замок повешен" : "Padlock hung");
        add("lock.halgventmod.mortise_installed", russian ? "Врезной замок установлен" : "Mortise lock fitted");
        add("lock.halgventmod.removed", russian ? "Замок снят" : "Lock removed");
        add("lock.halgventmod.cannot_remove", russian ? "Врезной замок не снять" : "A mortise lock can't be removed");
        add("lock.halgventmod.no_lock", russian ? "Здесь нет замка" : "There is no lock here");
        add("lock.halgventmod.no_permission", russian
                ? "Набор слесаря — только для администрации" : "The locksmith's kit is for administration only");
        add("lock.halgventmod.info", russian
                ? "Замок: секрет %1$s, %2$s, %3$s" : "Lock: secret %1$s, %2$s, %3$s");
        add("lock.halgventmod.type.mortise", russian ? "врезной" : "mortise");
        add("lock.halgventmod.type.padlock", russian ? "навесной" : "padlock");
        add("lock.halgventmod.state.locked", russian ? "заперт" : "locked");
        add("lock.halgventmod.state.unlocked", russian ? "отперт" : "unlocked");
        add("lock.halgventmod.blank_key", russian ? "Без бородки: не подходит ни к одному замку" : "Uncut: fits no lock");
        add("lock.halgventmod.advanced.secret", russian ? "Секрет: %s" : "Secret: %s");
        add("lock.halgventmod.command.no_target", russian ? "Посмотрите на дверь или сундук" : "Look at a door or a chest");
        add("lock.halgventmod.command.rekeyed", russian
                ? "Новый секрет замка: %s. Старые ключи больше не подходят" : "New lock secret: %s. Old keys no longer fit");
        add("lock.halgventmod.command.key_given", russian ? "Выдан ключ к секрету %s" : "Key for secret %s given");
        add("lock.halgventmod.command.named", russian ? "Ключ подписан: %s" : "Key named: %s");
        add("lock.halgventmod.command.not_key", russian
                ? "Возьмите ключ в основную руку" : "Hold a key in your main hand");
        add("lock.halgventmod.name_screen", russian ? "Подпись" : "Label");
        add("lock.halgventmod.ring_count", russian ? "Ключей: %1$s из %2$s" : "Keys: %1$s of %2$s");
        add("lock.halgventmod.ring_unnamed_key", russian ? "ключ без подписи" : "unlabelled key");

        // Эффекты перегруза
        add(ModEffects.BURDEN.get(), russian ? "Обуза" : "Burden");
        add(ModEffects.ENCUMBERED.get(), russian ? "Скованность" : "Encumbered");
        add(ModEffects.OVERLOADED.get(), russian ? "Тяжесть" : "Overloaded");
    }

    private void squad(String key, String ru, String en) {
        add("squad.halgventmod." + key, russian ? ru : en);
    }

    private void squadGui(String key, String ru, String en) {
        add("gui.halgventmod.squad." + key, russian ? ru : en);
    }
}