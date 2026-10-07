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