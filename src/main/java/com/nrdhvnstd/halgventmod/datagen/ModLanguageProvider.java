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
        add("tooltip.halgventmod.armor_slot", russian ? "Слот: %s" : "Slot: %s");
        add("message.halgventmod.hood_over_helmet",
                russian ? "Капюшон не надеть поверх этого шлема" : "The hood won't fit over this helmet");
        add("message.halgventmod.remove_hood_first", russian ? "Сначала снимите капюшон" : "Take off the hood first");

        // Срок годности
        add("tooltip.halgventmod.spoils_in", russian ? "Испортится через: %s сут." : "Spoils in: %s d.");
        add("tooltip.halgventmod.spoiled", russian ? "Испорчено" : "Spoiled");

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
        add("key.halgventmod.codex", russian ? "Кодекс" : "Codex");

        // Вкладки над окнами игрока
        add("gui.halgventmod.tab.inventory", russian ? "Инвентарь" : "Inventory");
        add("gui.halgventmod.tab.character", russian ? "Персонаж" : "Character");
        add("gui.halgventmod.tab.map", russian ? "Карта" : "Map");
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
}