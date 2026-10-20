package com.nrdhvnstd.halgventmod.datagen;

import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.gear.GearSlot;
import com.nrdhvnstd.halgventmod.item.Category;
import com.nrdhvnstd.halgventmod.item.Gender;
import com.nrdhvnstd.halgventmod.item.ModLore;
import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.leather.Animals;
import com.nrdhvnstd.halgventmod.leather.Solution;
import com.nrdhvnstd.halgventmod.leather.Tannage;
import com.nrdhvnstd.halgventmod.registry.ModEffects;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.registry.ModSounds;
import com.nrdhvnstd.halgventmod.sound.Voices;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.data.LanguageProvider;

/** Языковые файлы ru_ru и en_us. Все переводы мода живут здесь. */
public class ModLanguageProvider extends LanguageProvider {
    private final boolean russian;

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, HalgventMod.MODID, locale);
        this.russian = locale.equals("ru_ru");
    }

    @Override
    protected void addTranslations() {
        // Вкладки: по одной на категорию
        for (Category category : Category.values()) {
            add(category.langKey(), russian ? category.nameRu() : category.nameEn());
        }

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

        // Вес
        add("weight.halgventmod.grams", russian ? "%s гр" : "%s g");
        add("weight.halgventmod.kilograms", russian ? "%s кг" : "%s kg");
        add("weight.halgventmod.decimal", russian ? "," : ".");
        add("tooltip.halgventmod.weight", russian ? "Вес: %s" : "Weight: %s");
        add("hud.halgventmod.carry", "%s / %s");

        // Компас вверху экрана: стороны света
        String[] compassRu = {"С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ"};
        String[] compassEn = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        String[] compassKeys = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        for (int i = 0; i < compassKeys.length; i++) {
            add("hud.halgventmod.compass." + compassKeys[i], russian ? compassRu[i] : compassEn[i]); // CompassHud.langKey
        }
        add("attribute.name.halgventmod.carry_capacity", russian ? "Грузоподъёмность" : "Carry Capacity");

        // Броня: названия слотов и кнопка каталога в инвентаре
        for (ArmorSlot slot : ArmorSlot.values()) {
            add(slot.langKey(), russian ? slot.nameRu() : slot.nameEn());
        }
        add("gui.halgventmod.catalog", russian ? "Каталог" : "Catalog");
        add("gui.halgventmod.clear_inventory",
                russian ? "Shift+щелчок — очистить весь инвентарь" : "Shift+click to clear the whole inventory");
        add("tooltip.halgventmod.armor_slot", russian ? "Слот: %s" : "Slot: %s");
        add("tooltip.halgventmod.protection", russian ? "Защита: %s, твёрдость %s" : "Protection: %s, toughness %s");
        add("tooltip.halgventmod.visor_raised", russian ? "Забрало поднято" : "Visor raised");
        add("tooltip.halgventmod.visor_lowered", russian ? "Забрало опущено" : "Visor lowered");
        add("message.halgventmod.hood_over_helmet",
                russian ? "Капюшон не надеть поверх этого шлема" : "The hood won't fit over this helmet");
        add("message.halgventmod.remove_hood_first", russian ? "Сначала снимите капюшон" : "Take off the hood first");
        add("message.halgventmod.arming_cap_first",
                russian ? "Шлем надевается только поверх подшлемника" : "A helmet goes on only over an arming cap");
        add("message.halgventmod.remove_helmet_first", russian ? "Сначала снимите шлем" : "Take off the helmet first");

        // Снаряжение и хранилище (концепт инвентаря)
        for (GearSlot slot : GearSlot.values()) {
            add(slot.langKey(), russian ? slot.nameRu() : slot.nameEn());
        }
        add("gear_slot.halgventmod.pouch", russian ? "Подсумок" : "Pouch"); // подсказка вещи (GearTooltip)
        add("message.halgventmod.belt_first", russian ? "Сначала наденьте пояс" : "Put on a belt first");
        add("message.halgventmod.remove_belt_items_first",
                russian ? "Сначала снимите то, что висит на поясе" : "First take off what hangs from the belt");
        add("message.halgventmod.no_room", russian ? "Некуда положить" : "No room for it");
        add("gui.halgventmod.storage.back", russian ? "Спина" : "Back");
        add("gui.halgventmod.storage.pouch_1", russian ? "Подсумок 1" : "Pouch 1");
        add("gui.halgventmod.storage.pouch_2", russian ? "Подсумок 2" : "Pouch 2");
        add("gui.halgventmod.storage.pockets", russian ? "Карманы" : "Pockets");
        add("gui.halgventmod.storage.locked.back", russian ? "Наденьте рюкзак или сумку" : "Put on a backpack or a bag");
        add("gui.halgventmod.storage.locked.pouch", russian ? "Наденьте пояс и подсумок" : "Put on a belt and a pouch");
        add("tooltip.halgventmod.capacity", russian ? "Вместимость: %s" : "Capacity: %s");
        add("tooltip.halgventmod.contents", russian ? "Внутри: %s из %s" : "Inside: %s of %s");
        add("tooltip.halgventmod.carry_bonus", russian ? "Грузоподъёмность: +%s, пока надето" : "Carry capacity: +%s while worn");
        add("tooltip.halgventmod.gear_slot", russian ? "Носится: %s" : "Worn: %s");
        add("gui.halgventmod.load.none", russian ? "ЛЕГКО" : "LIGHT");
        add("gui.halgventmod.load.burden", russian ? "ОБУЗА" : "BURDENED");
        add("gui.halgventmod.load.encumbered", russian ? "СКОВАННОСТЬ" : "ENCUMBERED");
        add("gui.halgventmod.load.overloaded", russian ? "ТЯЖЕСТЬ" : "OVERLOADED");

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
        add("key.halgventmod.pickup", russian ? "Подобрать / использовать" : "Pick Up / Use");
        add("key.halgventmod.visor", russian ? "Поднять/опустить забрало" : "Raise/Lower Visor");
        add("hud.halgventmod.pickup", russian ? "[%s] Подобрать" : "[%s] Pick up");
        add("hud.halgventmod.pickup_equip", russian ? "[Shift+%s] Надеть" : "[Shift+%s] Put on");
        // Музыка биомов и источники звука
        add("command.halgventmod.music.biome", russian ? "Биом: %s" : "Biome: %s");
        add("command.halgventmod.music.tags", russian ? "Теги: %s" : "Tags: %s");
        add("command.halgventmod.music.no_tags", russian ? "Тегов нет" : "No tags");
        add("command.halgventmod.music.set", russian ? "Набор: %s" : "Music set: %s");
        add("command.halgventmod.music.no_set", russian ? "Набора нет — тишина" : "No music set — silence");
        add("command.halgventmod.music.playing_set", russian
                ? "Пока играет набор %s (смена через несколько секунд)" : "Still playing %s (switching in a few seconds)");
        add("command.halgventmod.music.track", russian ? "Трек: %s (%s из %s)" : "Track: %s (%s of %s)");
        add("command.halgventmod.music.pause", russian ? "Трек: пауза между треками" : "Track: pause between tracks");
        add("command.halgventmod.music.muted", russian
                ? "Громкость от источников звука рядом: %s%%" : "Volume from sound sources nearby: %s%%");
        add("command.halgventmod.music.skipped", russian ? "Следующий трек" : "Next track");
        add("command.halgventmod.music.nothing", russian ? "Сейчас ничего не играет" : "Nothing is playing");
        add("gui.halgventmod.sound_emitter.title", russian ? "Источник звука" : "Sound Emitter");
        add("gui.halgventmod.sound_emitter.scene", russian ? "Сцена" : "Scene");
        add("gui.halgventmod.sound_emitter.no_scene", russian ? "— без сцены —" : "— no scene —");
        add("gui.halgventmod.sound_emitter.missing", russian
                ? "Сцены %s нет — источник молчит" : "No scene %s — the source is silent");
        add("gui.halgventmod.sound_emitter.radius", russian
                ? "Радиус 1–64, пусто — из сцены (%s)" : "Radius 1–64, empty — from the scene (%s)");
        add("hud.halgventmod.use.open", russian ? "Открыть: %s" : "Open: %s");
        add("hud.halgventmod.use.close", russian ? "Закрыть: %s" : "Close: %s");
        add("hud.halgventmod.use.use", russian ? "Использовать: %s" : "Use: %s");
        add("tooltip.halgventmod.arrows", russian ? "Стрел: %s из %s" : "Arrows: %s of %s");
        add("gui.halgventmod.waypoint.new", russian ? "Новая метка" : "New Waypoint");
        add("gui.halgventmod.waypoint.edit", russian ? "Метка" : "Waypoint");
        add("gui.halgventmod.waypoint.name", russian ? "Название" : "Name");
        add("gui.halgventmod.waypoint.color", russian ? "Цвет" : "Color");
        add("gui.halgventmod.waypoint.save", russian ? "Сохранить" : "Save");
        add("gui.halgventmod.waypoint.delete", russian ? "Удалить" : "Delete");
        add("gui.halgventmod.waypoint.icon", russian ? "Значок: %s" : "Icon: %s");
        add("gui.halgventmod.waypoint.flag_color", russian ? "Цвет флага: %s" : "Flag color: %s");
        add("gui.halgventmod.waypoint.compass", russian ? "Видимость на компасе" : "Show on compass");

        // Точки карты (ставит администрация, видны всем)
        add("gui.halgventmod.map.menu.add_point", russian ? "Поставить точку" : "Add Point");
        add("gui.halgventmod.map.menu.edit_point", russian ? "Изменить точку" : "Edit Point");
        add("gui.halgventmod.map.menu.edit_point_bounds", russian ? "Изменить границы точки" : "Edit Point Bounds");
        add("gui.halgventmod.point.new", russian ? "Новая точка" : "New Point");
        add("gui.halgventmod.point.edit", russian ? "Точка" : "Point");
        add("gui.halgventmod.point.shape.circle", russian ? "Круг" : "Circle");
        add("gui.halgventmod.point.shape.polygon", russian ? "Многоугольник" : "Polygon");
        add("gui.halgventmod.point.next", russian ? "Далее" : "Next");
        add("gui.halgventmod.point.hint.circle", russian
                ? "Колёсико или стрелки — радиус · ручку на краю можно тянуть · Shift+колёсико — масштаб"
                : "Scroll or arrows — radius · drag the handle on the edge · Shift+scroll — zoom");
        add("gui.halgventmod.point.hint.polygon", russian
                ? "Тяните углы · кружок на стороне — новый угол · ПКМ по углу — убрать · стрелки — сдвиг угла"
                : "Drag corners · dot on a side — new corner · right-click a corner — remove · arrows — nudge");
        add("gui.halgventmod.point.open", russian ? "Открыта" : "Discovered");
        add("gui.halgventmod.point.hidden", russian ? "Не открыта" : "Undiscovered");
        add("gui.halgventmod.point.unknown", russian ? "Неизвестное место" : "Unknown Place");
        add("gui.halgventmod.point.info", russian ? "Сведения" : "Details");
        add("gui.halgventmod.point.described", russian ? "С описанием" : "With description");
        add("gui.halgventmod.point.description", russian ? "Описание" : "Description");
        add("gui.halgventmod.point.hover_text", russian ? "Текст при наведении (пока не открыта)" : "Hover text (while undiscovered)");
        add("gui.halgventmod.point.icon_hidden", russian ? "Значок до открытия: %s" : "Icon before discovery: %s");
        add("gui.halgventmod.point.icon_open", russian ? "Значок после открытия: %s" : "Icon after discovery: %s");
        add("gui.halgventmod.point.icon_unknown", russian ? "Знак вопроса" : "Question mark");
        add("gui.halgventmod.point.banner", russian ? "Знамя из реестра: %s" : "Registry banner: %s");
        add("gui.halgventmod.point.no_banner", russian ? "нет" : "none");
        add("gui.halgventmod.point.missing_banner", russian ? "%s (нет в реестре)" : "%s (not in the registry)");
        add("gui.halgventmod.point.all_banners", russian ? "Весь реестр" : "All banners");
        add("gui.halgventmod.point.click_description", russian ? "ЛКМ — описание" : "Left-click: description");
        add("hud.halgventmod.point.discovered", russian ? "Открытая локация" : "Location discovered");
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
                ? "Набор ключника — только для администрации и творческого режима"
                : "The locksmith's kit is for administration and creative mode only");
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
        // Реестр знамён (BannerCommand)
        add("banner.halgventmod.prefix", russian ? "[Реестр] " : "[Registry] ");
        add("banner.halgventmod.registered", russian ? "Знамя «%s» внесено под id %s" : "Banner \"%s\" registered as %s");
        add("banner.halgventmod.updated", russian ? "Рисунок знамени «%s» (%s) обновлён" : "Design of banner \"%s\" (%s) updated");
        add("banner.halgventmod.renamed", russian ? "Знамя %s теперь называется «%s»" : "Banner %s is now called \"%s\"");
        add("banner.halgventmod.described", russian ? "Описание знамени «%s» обновлено" : "Description of banner \"%s\" updated");
        add("banner.halgventmod.given", russian ? "Выдано знамя «%s» ×%s: %s" : "Gave banner \"%s\" ×%s to %s");
        add("banner.halgventmod.removed", russian ? "Знамя «%s» (%s) убрано из реестра" : "Banner \"%s\" (%s) removed from the registry");
        add("banner.halgventmod.unknown", russian ? "В реестре нет знамени %s" : "No banner %s in the registry");
        add("banner.halgventmod.taken", russian
                ? "id %1$s уже занят. Сменить рисунок: /banner update %1$s"
                : "id %1$s is taken. To change the design: /banner update %1$s");
        add("banner.halgventmod.bad_id", russian
                ? "id — латиница в нижнем регистре, цифры и _, не длиннее %s"
                : "id: lowercase latin letters, digits and _, at most %s");
        add("banner.halgventmod.no_banner", russian ? "Возьмите баннер в основную руку" : "Hold a banner in your main hand");
        add("banner.halgventmod.name_too_long", russian ? "Название длиннее %s символов" : "Name is longer than %s characters");
        add("banner.halgventmod.description_too_long", russian
                ? "Описание длиннее %s символов" : "Description is longer than %s characters");
        add("banner.halgventmod.edited", russian ? "Знамя «%s» исправлено" : "Banner \"%s\" edited");
        String banners = "gui.halgventmod.banners.";
        add(banners + "title", russian ? "Реестр знамён" : "Banner Registry");
        add(banners + "pick", russian ? "Выберите знамя" : "Choose a Banner");
        add(banners + "hint", russian ? "Внести новое: /banner register <id> <название>" : "Add new: /banner register <id> <name>");
        add(banners + "pick_hint", russian ? "Щёлкните по знамени" : "Click a banner");
        add(banners + "search", russian ? "Поиск" : "Search");
        add(banners + "empty", russian ? "Реестр пуст" : "The registry is empty");
        add(banners + "not_found", russian ? "Ничего не найдено" : "Nothing found");
        add(banners + "in_points", russian ? "В точках: %s" : "In points: %s");
        add(banners + "take",russian ? "Взять" : "Take");
        add(banners + "edit", russian ? "Править" : "Edit");
        add(banners + "delete", russian ? "Удалить" : "Delete");
        add(banners + "delete_title", russian ? "Удалить знамя из реестра?" : "Remove the banner from the registry?");
        add(banners + "delete_question", russian
                ? "«%s» (%s) пропадёт из точек и книг. Внесёте снова под тем же id — вернётся."
                : "\"%s\" (%s) will vanish from points and books. Register it again under the same id to bring it back.");
        add(banners + "edit_title", russian ? "Знамя" : "Banner");
        add(banners + "name", russian ? "Название" : "Name");
        add(banners + "description", russian ? "Описание" : "Description");
        // Гербовник (ArmorialCommand, ArmorialScreen)
        String armorial = "armorial.halgventmod.";
        add(armorial + "prefix", russian ? "[Гербовник] " : "[Armorial] ");
        add(armorial + "created", russian ? "Книга «%s» создана под id %s" : "Book \"%s\" created as %s");
        add(armorial + "renamed", russian ? "Книга %s теперь называется «%s»" : "Book %s is now called \"%s\"");
        add(armorial + "given", russian ? "Выдана книга %s: %s" : "Gave book %s to %s");
        add(armorial + "removed", russian ? "Книга %s удалена" : "Book %s removed");
        add(armorial + "missing", russian ? "Книги %s нет" : "There is no book %s");
        add(armorial + "taken", russian ? "id %s уже занят" : "id %s is taken");
        add(armorial + "bad_id", russian
                ? "id — латиница в нижнем регистре, цифры и _, не длиннее %s"
                : "id: lowercase latin letters, digits and _, at most %s");
        add(armorial + "title_too_long", russian ? "Название длиннее %s символов" : "Title is longer than %s characters");
        add(armorial + "list_empty", russian ? "Книг пока нет (общая книга main есть всегда)" : "No books yet (the common book main always exists)");
        add(armorial + "list_header", russian ? "Книг: %s" : "Books: %s");
        add(armorial + "list_entry", russian ? "  %s — «%s», страниц: %s" : "  %s — \"%s\", pages: %s");
        add(armorial + "empty", russian ? "Книга пуста" : "The book is empty");
        add(armorial + "take_copy", russian ? "ПКМ — взять копию" : "Right-click: take a copy");
        add(armorial + "edit_title", russian ? "Правка книги «%s»" : "Editing \"%s\"");
        add(armorial + "add_page", russian ? "+ Страница в конец" : "+ Page at the end");
        add(armorial + "type_large", russian ? "1 знамя" : "1 banner");
        add(armorial + "type_grid", russian ? "9 знамён" : "9 banners");
        add(armorial + "page_title", russian ? "Название страницы" : "Page title");
        add(armorial + "change_banner", russian ? "Сменить знамя" : "Change banner");
        add(armorial + "edit_banner", russian ? "Править в реестре" : "Edit in registry");
        add(armorial + "move_left", russian ? "Сдвинуть страницу назад" : "Move page back");
        add(armorial + "move_right", russian ? "Сдвинуть страницу вперёд" : "Move page forward");
        add(armorial + "delete_page", russian ? "Удалить страницу" : "Delete page");
        add(armorial + "delete_page_question", russian ? "Удалить страницу %s?" : "Delete page %s?");
        add(armorial + "clear_cell", russian ? "Убрать знамя" : "Remove banner");
        add(armorial + "save_question", russian ? "Сохранить изменения в книге?" : "Save changes to the book?");
        add(armorial + "save_hint", russian ? "«Нет» — закрыть без сохранения" : "\"No\" closes without saving");
        add("tooltip.halgventmod.registry_banner", russian ? "Реестр знамён: %s" : "Banner registry: %s");
        add("lock.halgventmod.name_screen", russian ? "Подпись" : "Label");
        add("lock.halgventmod.ring_count", russian ? "Ключей: %1$s из %2$s" : "Keys: %1$s of %2$s");
        add("lock.halgventmod.ring_unnamed_key", russian ? "ключ без подписи" : "unlabelled key");

        // Эффекты перегруза
        add(ModEffects.BURDEN.get(), russian ? "Обуза" : "Burden");
        add(ModEffects.ENCUMBERED.get(), russian ? "Скованность" : "Encumbered");
        add(ModEffects.OVERLOADED.get(), russian ? "Тяжесть" : "Overloaded");

        // Субтитры звуков: обычные события и голоса (один ключ на вид крика для всех наборов)
        for (ModSounds.Definition sound : ModSounds.definitions()) {
            add(sound.subtitleKey(), russian ? sound.subtitleRu() : sound.subtitleEn());
        }
        for (Voices.Cry cry : Voices.Cry.values()) {
            add(cry.subtitleKey(), russian ? cry.subtitleRu() : cry.subtitleEn());
        }

        // ----- Кожевенное дело -----
        leatherTranslations();
        cookingTranslations();
        qualityTranslations();
    }

    /** Кожевенное дело: шкуры, растворы, станции, окно шитья (leather/*). */
    private void leatherTranslations() {
        for (Animals.Kind kind : Animals.all()) {
            add(Animals.langKey(kind.id()), russian ? kind.nameRu() : kind.nameEn());
        }
        for (Tannage tannage : Tannage.values()) {
            add(tannage.langKey(), russian ? switch (tannage) {
                case TANNED -> "дублёную кожу";
                case SOFT -> "мягкую кожу";
                case FUR -> "выделанный мех";
                case RAWHIDE -> "сыромять";
            } : switch (tannage) {
                case TANNED -> "tanned leather";
                case SOFT -> "soft leather";
                case FUR -> "dressed fur";
                case RAWHIDE -> "rawhide";
            });
        }
        solution(Solution.NONE, "пусто", "empty");
        solution(Solution.WATER, "вода", "water");
        solution(Solution.LYE, "зольный щёлок", "ash lye");
        solution(Solution.BARK, "корьевой раствор", "bark liquor");
        solution(Solution.ALUM, "квасцовый раствор", "alum bath");
        solution(Solution.FAT, "жировой раствор", "fat liquor");

        add("tooltip.halgventmod.hide", russian ? "%s шкура · кусков кожи: %s" : "%s hide · leather pieces: %s");
        add("tooltip.halgventmod.fleshed", russian ? "Мездра снята" : "Fleshed");
        add("tooltip.halgventmod.not_fleshed", russian ? "Мездра не снята" : "Not fleshed");
        add("tooltip.halgventmod.has_fur", russian ? "С мехом: в жировой чан — выйдет мех" : "Furred: the fat vat makes fur");
        add("tooltip.halgventmod.dries_into", russian ? "На раме высохнет в %s" : "Dries on the rack into %s");
        add("tooltip.halgventmod.oiled", russian ? "Смазана, пересохнет через %s сут." : "Oiled, dries out in %s days");
        add("tooltip.halgventmod.dried_out",
                russian ? "Пересохла: хуже защищает. Смажьте на столе кожевника" : "Dried out: protects less. Oil it at the leatherworker's table");

        add("gui.halgventmod.sewing.title", russian ? "Шитьё: %s" : "Sewing: %s");
        add("gui.halgventmod.sewing.progress", russian ? "Стежки: %s / %s · промахи: %s" : "Stitches: %s / %s · misses: %s");
        add("gui.halgventmod.sewing.hint",
                russian ? "ЛКМ или Пробел — проколоть, когда игла над отметкой" : "Click or Space to pierce when the needle is over the mark");
        add("gui.halgventmod.sewing.done", russian ? "Сшито!" : "Sewn!");

        add("gui.halgventmod.pattern.title", russian ? "Выкройки" : "Patterns");
        add("gui.halgventmod.pattern.choose", russian ? "Выбрать" : "Choose");
        add("gui.halgventmod.pattern.ingredients", russian ? "Материалы" : "Materials");
        add("gui.halgventmod.pattern.tools", russian ? "Инструменты" : "Tools");
        add("gui.halgventmod.pattern.current", russian ? "Выбрана на столе" : "On the table now");
        add("gui.halgventmod.pattern.leather", russian ? "Кожа (кроится ножом):" : "Leather (cut with the knife):");
        add("gui.halgventmod.pattern.extras", russian ? "Фурнитура (после раскроя):" : "Fittings (after cutting):");
        add("gui.halgventmod.pattern.thread", russian ? "Нитки: %s / %s" : "Thread: %s / %s");
        add("gui.halgventmod.pattern.stitches", russian ? "Стежков: %s" : "Stitches: %s");
        add("gui.halgventmod.pattern.cut_only", russian ? "Только раскроить, без шитья" : "Just cut, no sewing");
        leather("rotten", "Шкура сгнила. Годится только на клей: в чан с водой над огнём", "The hide has rotted. Only good for glue: a water vat over a fire");
        leather("beam_empty", "Положите на колоду шкуру: парную, солёную, золёную или сыромять", "Lay a hide on the beam: raw, salted, limed or rawhide");
        leather("salt_only_raw", "Солят только парную шкуру", "Only a raw hide is salted");
        leather("salted", "Шкура посолена: пролежит 16 суток", "Hide salted: it keeps for 16 days");
        leather("knife_wrong", "Мездряком работают по парной или солёной шкуре", "The fleshing knife is for a raw or salted hide");
        leather("already_fleshed", "Мездра уже снята. Дальше — чан: щёлок (зола) или жир для меха", "Already fleshed. Next: the vat — lye (ash) or fat for fur");
        leather("fleshed", "Мездра снята", "Fleshed");
        leather("flesh_layer", "Слой снят, на этом клочке ещё: %s", "A layer off, this patch has %s more");
        leather("flesh_left", "Клочок счищен. Осталось клочков мездры: %s", "Patch cleared. Flesh patches left: %s");
        leather("flesh_miss", "Здесь чисто — бейте по клочкам мездры (осталось: %s)", "Clean here: strike the flesh patches (%s left)");
        leather("dehaired", "Волос снят — получилось голье", "Hair scraped off — you have a pelt");
        leather("parchment_small_only", "Пергамент выходит только из мелкой шкуры: козы, овцы, кролика", "Parchment comes only from a small hide: goat, sheep, rabbit");
        leather("parchment", "Выскоблен пергамент", "Parchment scraped");
        leather("scraper_wrong", "Стругом снимают волос с золёной шкуры или скоблят сыромять", "The scraper is for a limed hide or rawhide");
        leather("cut", "Спешка! Нож прорезал шкуру — она стала хуже", "Too hasty! The blade cut the hide — it is worse now");
        leather("stroke", "%s / %s", "%s / %s");

        leather("vat_busy", "В чане уже что-то есть", "Something is already in the vat");
        leather("vat_dry", "Чан пуст: налейте воды ведром", "The vat is empty: pour water with a bucket");
        leather("vat_take_first", "Сначала выньте всё из чана", "Take everything out of the vat first");
        leather("vat_already", "В чане уже %s: добавлять нечего", "The vat already holds %s");
        leather("additives", "В воде: зола %s/%s · кора %s/%s · квасцы %s/%s, соль %s · сало %s/%s",
                "In the water: ash %s/%s · bark %s/%s · alum %s/%s, salt %s · fat %s/%s");
        leather("solution_ready", "Готов %s", "Ready: %s");
        leather("vat_full", "В чане нет места", "The vat is full");
        leather("flesh_first", "Сначала снимите мездру на колоде", "Flesh the hide on the beam first");
        leather("fat_needs_fur", "В жир кладут шкуру с мехом", "Only a furred hide goes into fat");
        leather("vat_wrong", "Это не идёт в %s", "This doesn't go into %s");
        leather("vat_spent", "Раствор выработан на шкуры, что уже лежат", "The bath is used up by the hides already in it");
        leather("needs_fire", "Опущено. Разведите огонь под чаном", "Sunk. Light a fire under the vat");
        leather("soaking", "Мокнет: %s", "Soaking: %s");
        leather("undertanned", "Вынута раньше срока: кожа недодублена", "Taken out early: the leather is under-tanned");
        leather("too_early", "Рано: готово на %s%%. Работа сначала", "Too early: %s%% done. Starting over");
        leather("vat_status", "%s, хватит ещё на шкур: %s", "%s, enough for %s more hides");

        leather("rack_empty", "Натяните мокрую кожу или голье", "Stretch wet leather or a pelt on it");
        leather("rack_placed", "Натянута. В эту пору сохнет суток: %s", "Stretched. In this season it dries in %s days");
        leather("rack_not_dry", "Ещё не высохла: %s%%", "Not dry yet: %s%%");
        leather("rack_taken", "Снято с рамы", "Taken off the rack");
        leather("already_softened", "Уже разбита колотушкой", "Already softened");
        leather("mallet_rush", "Не спешите: бейте ровно", "Don't rush: strike evenly");
        leather("softened", "Кожа размята: стала лучше", "The leather is softened: it is better now");
        leather("already_oiled", "Уже пропитана", "Already dressed with oil");
        leather("oiled", "Пропитана жиром: стала лучше", "Dressed with oil: it is better now");

        leather("table_busy", "Сначала доделайте начатое или заберите кожу", "Finish what you started or take the leather back");
        leather("pick_pattern", "E пустой рукой — выбрать выкройку", "E with an empty hand picks a pattern");
        leather("pattern", "Выкройка: %1$s — кожа: %2$s; затем: %3$s; нитки %4$s, стежков %5$s",
                "Pattern: %1$s — leather: %2$s; then: %3$s; thread %4$s, stitches %5$s");
        leather("pattern_simple", "Выкройка: %1$s — кожа: %2$s; нитки %4$s, стежков %5$s",
                "Pattern: %1$s — leather: %2$s; thread %4$s, stitches %5$s");
        leather("pattern_cut_only", "Выкройка: %1$s — кожа: %2$s; только раскроить", "Pattern: %1$s — leather: %2$s; just cut");
        leather("enough_of", "%s хватает", "Enough %s");
        leather("wrong_leather", "Это не по выкройке", "Not for this pattern");
        leather("wrong_extra", "Это не нужно этой вещи", "This piece doesn't need that");
        leather("need_leather", "Не хватает кожи", "Not enough leather");
        leather("already_cut", "Уже раскроено: шейте иглой", "Already cut: sew it with the needle");
        leather("cut_sew", "Раскроено. Шейте иглой", "Cut. Sew it with the needle");
        leather("cut_extras", "Раскроено. Добавьте фурнитуру и шейте иглой", "Cut. Add the fittings and sew with the needle");
        leather("cut_first", "Сначала раскроите кожу ножом", "Cut the leather with the knife first");
        leather("need_extras", "Не хватает фурнитуры", "Fittings are missing");
        leather("need_thread", "Нужно ниток (или нити): %s", "You need thread: %s");
        leather("need_needle", "Держите иглу в руке", "Hold the needle in your hand");
        leather("sewn_clean", "Ни одного промаха — вещь вышла лучше", "Not a single miss — the piece came out better");
        leather("sewn", "Сшито, промахов: %s", "Sewn, misses: %s");
        leather("sewn_sloppy", "Шов кривой (промахов: %s) — вещь вышла хуже", "A crooked seam (misses: %s) — the piece came out worse");
        leather("made", "Готово: %s", "Done: %s");
        leather("table_cut", "Кожа раскроена — назад не сложить. Доделайте вещь", "The leather is cut — it can't go back. Finish the piece");
        leather("item_oiled", "%s смазана", "%s oiled");
        leather("need_oil", "Нужно сало или воск в инвентаре", "You need salo or wax in your inventory");
    }

    private void solution(Solution solution, String ru, String en) {
        add(solution.langKey(), russian ? ru : en);
    }

    private void leather(String key, String ru, String en) {
        add("message.halgventmod.leather." + key, russian ? ru : en);
    }

    /** Готовка: очаг, котелок, жарка, печь, жёрнов, заготовки (cooking/*). */
    private void cookingTranslations() {
        add("tooltip.halgventmod.dish_food", russian ? "Сытость %s, жажда %s" : "Nutrition %s, thirst %s");
        add("tooltip.halgventmod.dish_of", russian ? "Сварено из: %s" : "Made of: %s");
        add("tooltip.halgventmod.pie_filling.meat", russian ? "Начинка: мясо" : "Filling: meat");
        add("tooltip.halgventmod.pie_filling.mushroom", russian ? "Начинка: грибы" : "Filling: mushrooms");
        add("tooltip.halgventmod.pie_filling.berry", russian ? "Начинка: ягоды" : "Filling: berries");

        add("gui.halgventmod.kitchen.title", russian ? "Рецепты" : "Recipes");
        add("gui.halgventmod.kitchen.ingredients", russian ? "Продукты" : "Ingredients");
        add("gui.halgventmod.kitchen.extras", russian ? "Добавки" : "Extras");
        add("gui.halgventmod.kitchen.kneads", russian ? "Месить: %s раз" : "Knead: %s times");
        cooking("kitchen_pick", "E пустой рукой — выбрать рецепт", "E with an empty hand picks a recipe");
        cooking("kitchen_busy", "Сначала доделайте или заберите выложенное", "Finish or take back what is on the board first");
        cooking("kitchen_take_first", "Сначала заберите готовое", "Take what is ready first");
        cooking("kitchen_has_water", "Воды уже хватает", "Enough water already");
        cooking("kitchen_has_salt", "Уже посолено", "Already salted");
        cooking("kitchen_enough", "%s хватает", "Enough %s");
        cooking("kitchen_spoiled", "Испорченное не берут", "Spoiled food won't do");
        cooking("kitchen_wrong", "Это не по рецепту", "Not in this recipe");
        cooking("kitchen_need", "Нужно ещё: %s × %s", "Still needed: %s × %s");
        cooking("kitchen_need_water", "Полейте водой из ведра", "Pour in water from a bucket");
        cooking("kitchen_ready_to_knead", "Всё на доске: месите пустой рукой", "All on the board: knead with an empty hand");
        cooking("kitchen_knead", "%s / %s", "%s / %s");
        cooking("kitchen_kneaded", "Уже замешано — доделайте", "Already kneaded — finish it");
        cooking("kitchen_done", "Готово: %s", "Done: %s");
        cooking("diet_bored", "%s приелось: сытость %s%%", "Tired of %s: %s%% as filling");
        cooking("raw_sick", "Сырое пошло не впрок", "The raw food did not sit well");

        cooking("fuel_full", "Дров хватит: больше не положить", "Enough firewood: no room for more");
        cooking("fuel_added", "Подложили дров: запас %s%%", "Firewood added: %s%% stocked");
        cooking("fuel_unlit", "Дрова сложены (%s%%). Разожгите огнивом или факелом", "Firewood stacked (%s%%). Light it with a flint or torch");
        cooking("already_lit", "Уже горит", "Already burning");
        cooking("no_fuel", "Нечему гореть: сначала дрова", "Nothing to burn: add firewood first");
        cooking("fire_status", "Жар %s, дров %s%%", "Heat %s, firewood %s%%");
        cooking("fire_out", "Огонь не горит (дров %2$s%%)", "The fire is out (firewood %2$s%%)");

        cooking("pot_already", "Котелок уже стоит", "A pot is already on");
        cooking("grill_busy", "Сначала снимите всё с углей", "Take everything off the coals first");
        cooking("pot_has_water", "Вода уже налита", "There is water already");
        cooking("need_water", "Налейте в котелок воды ведром", "Pour water into the pot with a bucket");
        cooking("pot_water_only", "Одна вода: положите продукты", "Just water: add some food");
        cooking("pot_full", "Котелок полон", "The pot is full");
        cooking("pot_serving", "Из котелка уже разливают — сначала доешьте", "The pot is being served — finish it first");
        cooking("pot_busy", "В котелке варится — не снять", "Something is cooking — can't take it off");
        cooking("pot_cold", "Положено %s из %s. Огонь не горит", "%s of %s added. The fire is out");
        cooking("pot_cooking", "Варится: %s%%, положено %s из %s", "Cooking: %s%%, %s of %s added");
        cooking("pot_stir", "Пригорает! Помешайте половником (%s%%, %s из %s)", "Scorching! Stir it with a ladle (%s%%, %s of %s)");
        cooking("stirred", "Помешали", "Stirred");
        cooking("pot_ready", "Готово: %s, порций %s", "Ready: %s, %s portions");
        cooking("portions_left", "Осталось порций: %s", "Portions left: %s");
        cooking("need_bowl", "Черпают миской", "Serve it with a bowl");
        cooking("need_bottle", "Отвар наливают в склянку", "Pour the tea into a bottle");
        cooking("grill_full", "На углях нет места", "No room on the coals");

        cooking("oven_full", "В печи нет места", "The oven is full");
        cooking("oven_cold", "Печь не прогрета (%s°): не печётся", "The oven is cold (%s°): nothing bakes");
        cooking("oven_status", "Жар %s°, дров %s%%. Печётся: %s", "Heat %s°, firewood %s%%. Baking: %s");
        cooking("oven_status_empty", "Жар %s°, дров %s%%", "Heat %s°, firewood %s%%");
        cooking("baked_raw", "Ещё сырое", "Still raw");
        cooking("baked_under", "%s: недопечено", "%s: underbaked");
        cooking("baked_good", "%s: в самый раз", "%s: just right");
        cooking("baked_over", "%s: пересушено", "%s: overbaked");
        cooking("baked_burnt", "Сгорело", "Burnt");

        cooking("quern_full", "Жёрнов полон", "The quern is full");
        cooking("quern_grain", "Засыпано зерна: %s", "Grain in: %s");
        cooking("quern_empty", "Засыпьте пшеницу", "Pour in some wheat");
        cooking("quern_turn", "%s / %s", "%s / %s");
        cooking("quern_flour", "Смолото! Осталось зерна: %s", "Ground! Grain left: %s");
        cooking("quern_spilled", "Спешка — зерно просыпалось. Осталось: %s", "Too hasty — the grain spilled. Left: %s");

        cooking("barrel_empty", "Положите сырое мясо или рыбу", "Put in raw meat or fish");
        cooking("barrel_full", "Бочка полна", "The barrel is full");
        cooking("barrel_spoiled", "Испорченное не солят", "Spoiled food is not salted");
        cooking("barrel_salt_enough", "Соли хватает", "Enough salt");
        cooking("barrel_need_salt", "В бочке %s, соли %s: нужно по соли на кусок", "%s in the barrel, salt %s: one salt per piece");
        cooking("barrel_salting", "Солится %s, ждать суток: %s", "Salting %s, days left: %s");
        cooking("barrel_take_first", "Сначала заберите готовое", "Take out what is ready first");
        cooking("barrel_done", "Засолено: %s", "Salted: %s");

        cooking("hook_empty", "Повесьте сырое мясо или рыбу — коптить, травы или грибы — сушить",
                "Hang raw meat or fish to smoke, herbs or mushrooms to dry");
        cooking("hook_full", "На крюке нет места", "No room on the hook");
        cooking("hook_mixed", "Мясо и травы вместе не вешают", "Meat and herbs don't hang together");
        cooking("hook_take_first", "Сначала снимите готовое", "Take off what is ready first");
        cooking("hook_no_smoke", "Внизу нет огня: не коптится. Повесьте над очагом или костром",
                "No fire below: it won't smoke. Hang it above a hearth or campfire");
        cooking("hook_no_smoke_status", "Коптится %s%%, но внизу нет огня", "Smoked %s%%, but there is no fire below");
        cooking("hook_smoking", "Коптится: %s%%", "Smoking: %s%%");
        cooking("hook_drying", "Сохнет: %s%%", "Drying: %s%%");
        cooking("hook_smoked", "Прокоптилось: %s", "Smoked: %s");
        cooking("hook_dried", "Высохло: %s", "Dried: %s");
    }

    private void cooking(String key, String ru, String en) {
        add("message.halgventmod.cooking." + key, russian ? ru : en);
    }

    /** Качество снаряжения и типы урона (quality/*). Сами признаки качества — из ModTraits. */
    private void qualityTranslations() {
        add("tooltip.halgventmod.quality.weapon", russian ? "Урон ×%s, пробитие %s%% (%s)" : "Damage ×%s, penetration %s%% (%s)");
        add("tooltip.halgventmod.quality.armor", russian ? "Защита ×%s, стойкость ×%s" : "Protection ×%s, resistance ×%s");
        add("damage_kind.halgventmod.chopping", russian ? "рубящий" : "chopping");
        add("damage_kind.halgventmod.piercing", russian ? "колющий" : "piercing");
        add("damage_kind.halgventmod.blunt", russian ? "дробящий" : "blunt");
        add("damage_kind.halgventmod.penetrating", russian ? "проникающий" : "penetrating");
        add("command.halgventmod.quality.set", russian ? "Качество задано: %s" : "Quality set: %s");
        add("command.halgventmod.quality.not_gear",
                russian ? "Возьмите в руку оружие или броню и укажите качество" : "Hold a weapon or armor and name a quality");
    }

    private void squad(String key, String ru, String en) {
        add("squad.halgventmod." + key, russian ? ru : en);
    }

    private void squadGui(String key, String ru, String en) {
        add("gui.halgventmod.squad." + key, russian ? ru : en);
    }
}