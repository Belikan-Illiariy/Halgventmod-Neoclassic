package com.nrdhvnstd.halgventmod.datagen;

/** Переводы кузнечества (smithing/*, client/smithing/*): сообщения станций, окна ковки, вкладки, подсказки. */
final class SmithingLang {
    interface Sink {
        void add(String key, String ru, String en);
    }

    private SmithingLang() {}

    static void add(Sink sink) {
        messages(sink);
        work(sink);
        steps(sink);
        windows(sink);
        other(sink);
    }

    private static void m(Sink sink, String key, String ru, String en) {
        sink.add("message.halgventmod.smithing." + key, ru, en);
    }

    private static void w(Sink sink, String key, String ru, String en) {
        sink.add("gui.halgventmod.smith_work." + key, ru, en);
    }

    private static void messages(Sink s) {
        // Добыча
        m(s, "need_pickaxe", "Нужна кирка", "You need a pickaxe");
        m(s, "need_shovel", "Нужна лопата", "You need a shovel");
        m(s, "vein_empty", "Жила выработана — следующая ступень вернётся через %s", "The vein is worked out: the next grade returns in %s");
        m(s, "silt_empty", "Ил вычерпан — руда снова наберётся через %s", "The silt is dug out: ore gathers again in %s");
        m(s, "strike", "Удар: %s / %s", "Blow: %s / %s");
        m(s, "strike_spark", "По искре! %s / %s", "On the spark! %s / %s");
        m(s, "barren", "Пустая порода", "Barren rock");
        m(s, "lump", "Добыто: %s ×%s", "Mined: %s ×%s");
        m(s, "vein_exhausted", "Жила иссякла — первая ступень вернётся через %s",
                "The vein is exhausted; the first grade returns in %s");
        m(s, "silt_exhausted", "Ил вычерпан — руда наберётся через %s",
                "The silt is dug out; ore will gather in %s");
        m(s, "placer_empty", "Россыпь пуста — река намоет новую через %s",
                "The placer is empty; the river washes in more in %s");
        m(s, "wait_days", "~%s сут.", "about %s days");
        m(s, "wait_hours", "~%s ч", "about %s h");
        m(s, "placer_report_0", "Россыпь пуста", "The placer is empty");
        m(s, "placer_report_1", "Бедная россыпь. E лотком — промывать", "A poor placer. E with a pan to wash");
        m(s, "placer_report_2", "Россыпь. E лотком — промывать", "A placer. E with a pan to wash");
        m(s, "placer_report_3", "Богатая россыпь. E лотком — промывать", "A rich placer. E with a pan to wash");
        m(s, "panned", "Намыто золотого песка: %s", "Gold dust washed: %s");
        m(s, "panned_nothing", "Ни крупинки — промывайте бережнее", "Not a grain: wash more carefully");
        // Горн
        m(s, "forge_full", "Горн полон углём", "The forge is full of coal");
        m(s, "forge_wood", "Дрова не дадут кузнечного жара — нужен уголь", "Firewood won't give a forge heat: you need coal");
        m(s, "forge_pump", "Мехи: жар %s°", "Bellows: heat %s°");
        m(s, "forge_lit_already", "Горн уже горит", "The forge is already lit");
        m(s, "forge_no_fuel", "Сначала подкиньте угля", "Add coal first");
        m(s, "forge_out", "Горн погас. Угля на %s мин, жар %s°. Огниво — разжечь",
                "The forge is out. Coal for %s min, heat %s°. Flint and steel to light it");
        m(s, "forge_empty", "Горн пуст: подкиньте древесного или каменного угля", "The forge is empty: add charcoal or coal");
        m(s, "forge_report", "Горн: жар %s°, угля на %s мин. E пустой рукой — окно, Shift + E — мехи",
                "Forge: heat %s°, coal for %s min. E with an empty hand opens it, Shift + E works the bellows");
        m(s, "forge_warming", "Горн разгорается: %s° — работать можно с 400°", "The forge is warming up: %s°; work from 400°");
        // Точило
        m(s, "wheel_not_blade", "Это на точиле не точат", "That isn't sharpened on a wheel");
        m(s, "wheel_sharp", "Уже наточено", "It's sharp already");
        m(s, "wheel_reforge", "Точило больше не поможет — нужна перековка на наковальне",
                "The wheel won't help any more: it needs reforging at the anvil");
        m(s, "wheel_report", "Точило. E затупившимся клинком или инструментом — подточить",
                "A grinding wheel. E with a dull blade or tool to touch it up");
        m(s, "wheel_done", "Подточено: прочность %s%%", "Sharpened: durability %s%%");
        m(s, "wheel_burnt", "Перегрели кромку — металл сняли зря. Прочность %s%%",
                "You burnt the edge and ground metal away for nothing. Durability %s%%");
        // Наковальня и литейный стол
        m(s, "pick_product", "Выберите изделие", "Pick a piece");
        m(s, "not_available", "Этого здесь не сделать: нет нужного мода", "This can't be made here: the mod is missing");
        m(s, "need_materials", "Не хватает: %s", "Missing: %s");
        m(s, "need_tool", "Нужен инструмент в инвентаре: %s", "You need a tool in your inventory: %s");
        m(s, "need_forge", "Рядом нет горящего кузнечного горна", "There is no lit forge nearby");
        m(s, "forge_cold", "Горн ещё не разгорелся — качните мехи", "The forge isn't hot yet: work the bellows");
        m(s, "need_tub", "Для закалки рядом нужен чан с водой, рассолом (вода с солью) или жиром",
                "Quenching needs a vat with water, brine (water with salt) or fat nearby");
        m(s, "need_wheel", "Рядом нет точила", "There is no grinding wheel nearby");
        m(s, "product", "Изделие: %s — %s", "Piece: %s — %s");
        m(s, "made", "Сделано: %s", "Made: %s");
        m(s, "worked", "Работа: %s из 100", "Work: %s of 100");
        m(s, "worked_clean", "Работа: %s из 100 — вещь вышла лучше", "Work: %s of 100: the piece came out better");
        m(s, "worked_rough", "Работа: %s из 100 — вещь вышла хуже", "Work: %s of 100: the piece came out worse");
        m(s, "work_saved", "Сделанное сохранено: этапов %s из %s — продолжите, когда вернётесь", "Your work is kept: %s of %s steps; carry on when you come back");
        m(s, "crucible_cracked", "Опустевший тигель треснул", "The empty crucible cracked");
        // Тигель и литьё
        m(s, "crucible_none", "Поставьте в горн тигель — шихта ляжет в него", "Put a crucible in the forge: the charge goes into it");
        m(s, "crucible_broken", "Тигель треснул — в нём не плавят", "The crucible is cracked: nothing is melted in it");
        m(s, "crucible_lid_already", "Тигель уже накрыт глиной", "The crucible is already lidded with clay");
        m(s, "crucible_iron", "Железо и сталь в тигле не плавят — их куют", "Iron and steel are not melted in a crucible: they are forged");
        m(s, "crucible_not_metal", "Это не шихта для тигля", "That is no charge for a crucible");
        m(s, "crucible_full", "Тигель полон — до десяти слитков", "The crucible is full: ten ingots at most");
        m(s, "crucible_mix", "Это с тем, что в тигле, не сплавить", "That won't alloy with what is in the crucible");
        m(s, "crucible_empty", "Тигель пуст — положите шихту", "The crucible is empty: add a charge");
        m(s, "crucible_liquid", "Металл в тигле уже жидкий — несите к литейному столу", "The metal is already molten: take it to the casting table");
        m(s, "crucible_no_alloy", "Из этого ничего не выплавить: галмей сплавляют только с медью", "Nothing comes of that: calamine is only alloyed with copper");
        m(s, "crucible_need_tongs", "Тигель с жидким металлом голыми руками не взять — нужны клещи", "You can't lift a crucible of molten metal bare-handed: you need tongs");
        m(s, "forge_has_crucible", "В горне уже стоит тигель", "There is already a crucible in the forge");
        m(s, "forge_not_lit", "Горн не горит", "The forge isn't lit");
        m(s, "forge_no_crucible", "Поставьте в горн тигель", "Put a crucible in the forge");
        m(s, "melt_quit", "Плавка брошена: шихта осталась в тигле", "Melting abandoned: the charge is still in the crucible");
        m(s, "melted", "Расплав: %s, %s сл. (плавка: %s из 100)", "Molten: %s, %s ingots (melting: %s of 100)");
        m(s, "zinc_burnt", "Без глиняной крышки часть галмея выгорела", "Without a clay lid some of the calamine burnt off");
        m(s, "need_melt", "Нужен тигель с жидким металлом (%s): расплавьте шихту в кузнечном горне",
                "You need a crucible of molten metal (%s): melt a charge in the forge");
        m(s, "melt_solid", "Металл в тигле застыл — разогрейте его в кузнечном горне (%s)",
                "The metal in the crucible has set: heat it again in the forge (%s)");
        m(s, "melt_wrong", "В тигле не тот металл — нужен %s", "Wrong metal in the crucible: you need %s");
        m(s, "melt_short", "Мало расплава в тигле: %s", "Not enough melt in the crucible: %s");
        m(s, "panned_nugget", "Самородок! Сверху ещё два золотого песка", "A nugget! Two more gold dust on top");
        // Сыродутный горн
        m(s, "bloomery_busy", "Идёт плавка", "Smelting is under way");
        m(s, "bloomery_need_crucible", "Поставьте в окно горна тигель — металл стекает в него",
                "Put a crucible in the furnace window: the metal runs into it");
        m(s, "cupel_wrong_metal", "В купель льют только свинец с серебром", "Only silver-bearing lead goes into a cupel");
        m(s, "cupel_lead_solid", "Свинец в тигле застыл — переплавьте его в кузнечном горне", "The lead in the crucible has set: remelt it in the forge");
        m(s, "cupel_lead_little", "В тигле меньше слитка свинца", "Less than an ingot of lead in the crucible");
        m(s, "cupel_poured", "Слито в купель: %s сл. свинца с серебром", "Poured into the cupel: %s ingots of silver-bearing lead");
        m(s, "smelted_crucible", "Выплавлено в тигель: %s сл. — %s (мехи: %s из 100). Жидкий минуту — клещами к литейному столу или в кузнечный горн",
                "Smelted into the crucible: %s ingots of %s (bellows: %s of 100). Molten for a minute: tongs, to the casting table or the forge");
        m(s, "bloomery_other_ore", "В горне уже другая руда — Shift + E выгрести",
                "There is other ore in the furnace: Shift + E to rake it out");
        m(s, "bloomery_full", "Горн полон", "The furnace is full");
        m(s, "bloomery_coal_warning", "Каменный уголь горит, но сера портит металл: железо выйдет ломким",
                "Coal burns, but its sulphur spoils the metal: iron comes out brittle");
        m(s, "bloomery_wood", "Дрова не дадут жара для плавки — нужен древесный уголь",
                "Firewood won't give smelting heat: you need charcoal");
        m(s, "bloomery_not_this", "Это в сыродутном горне не плавят", "That isn't smelted in a bloomery");
        m(s, "bloomery_not_enough", "На закладку нужно %s руды и %s угля", "A charge takes %s ore and %s charcoal");
        m(s, "bloomery_report", "В горне: руды %s (%s), угля %s, флюса %s — закладок: %s. Огниво — плавить",
                "In the furnace: ore %s (%s), charcoal %s, flux %s, charges: %s. Flint and steel to smelt");
        m(s, "bloomery_quit", "Плавку бросили — заложенное осталось в горне",
                "Smelting abandoned: the charge is still in the furnace");
        m(s, "smelted", "Выплавлено: %s × %s (мехи: %s из 100)", "Smelted: %s × %s (bellows: %s of 100)");
        m(s, "smelt_failed", "Плавка не удалась: всё ушло в шлак (мехи: %s из 100)",
                "The smelt failed: it all went to slag (bellows: %s of 100)");
        m(s, "smelt_slag", "В шлак ушло закладок: %s", "Charges lost to slag: %s");
        // Купель
        m(s, "cupel_busy", "Купель на огне", "The cupel is on the fire");
        m(s, "cupel_full", "Купель полна (до %s)", "The cupel is full (up to %s)");
        m(s, "cupel_not_enough", "Нужен свинец с серебром и древесный уголь (%s)",
                "You need silver-bearing lead and charcoal (%s)");
        m(s, "cupel_quit", "Купель сняли — свинец остался", "Taken off the fire: the lead is still there");
        m(s, "cupel_report", "На купели: свинца %s, угля %s из %s. Капля серебра: %s%% слитка",
                "On the cupel: lead %s, charcoal %s of %s. Silver bead: %s%% of an ingot");
        m(s, "cupel_done", "Серебра: %s, свинца осталось: %s (капля: %s%%)", "Silver: %s, lead left: %s (bead: %s%%)");
        m(s, "cupel_early", "Сняли рано — серебро со свинцом. Серебра: %s, свинца: %s",
                "Taken off early: the silver holds lead. Silver: %s, lead: %s");
        m(s, "cupel_late", "Сняли поздно — часть серебра выгорела. Серебра: %s, свинца: %s",
                "Taken off late: some silver burnt away. Silver: %s, lead: %s");
        // Угольная куча
        m(s, "pile_covered", "Куча уже обсыпана", "The clamp is already covered");
        m(s, "pile_full", "Больше %s дров не сложить", "No more than %s firewood fits");
        m(s, "pile_no_wood", "Сначала сложите дрова", "Stack firewood first");
        m(s, "pile_covered_enough", "Обсыпано — можно поджигать", "Covered: you can light it");
        m(s, "pile_rushed", "Копайте с полного замаха", "Dig with a full swing");
        m(s, "pile_not_covered", "Сначала обсыпьте землёй: ударов лопатой нужно %s",
                "Cover it with earth first: it takes %s shovel strokes");
        m(s, "pile_lit", "Куча тлеет. Вскрывать — примерно через %s мин", "The clamp smoulders. Open it in about %s min");
        m(s, "pile_burning", "Тлеет. Вскрывать рано — ещё около %s мин", "Smouldering. Too early to open: about %s min more");
        m(s, "pile_ready", "Уголь готов — вскройте (в запасе %s мин)", "The charcoal is ready: open it (%s min to spare)");
        m(s, "pile_too_long", "Тлеет слишком долго — уголь прогорает в золу!", "It's been smouldering too long: the charcoal burns to ash!");
        m(s, "pile_empty", "Пустая куча: E дровами — сложить (до %s)", "An empty clamp: E with firewood to stack it (up to %s)");
        m(s, "pile_report", "Дров %s / %s, обсыпано %s из %s. E лопатой — обсыпать, огниво — поджечь",
                "Firewood %s / %s, covered %s of %s. E with a shovel to cover, flint and steel to light");
        m(s, "pile_early", "Вскрыли рано: угля %1$s, головешек %2$s", "Opened early: charcoal %1$s, brands %2$s");
        m(s, "pile_underdone", "Недожгли: угля %1$s", "Underburnt: charcoal %1$s");
        m(s, "pile_done", "Уголь: %1$s", "Charcoal: %1$s");
        m(s, "pile_overdone", "Пережгли: угля %1$s, золы %3$s", "Overburnt: charcoal %1$s, ash %3$s");
        m(s, "pile_burnt", "Всё прогорело — одна зола (%3$s)", "It all burnt away: only ash (%3$s)");
        // Цементационный ящик
        m(s, "box_full", "Ящик полон (до %s)", "The box is full (up to %s)");
        m(s, "box_not_ready", "Нужны слитки и уголь — по два на слиток (%s)", "You need ingots and charcoal, two per ingot (%s)");
        m(s, "box_sealed", "Запечатано глиной. Огниво — разжечь", "Sealed with clay. Flint and steel to light it");
        m(s, "box_lit_already", "Ящик уже тлеет", "The box is already smouldering");
        m(s, "box_not_sealed", "Сначала запечатайте комом глины", "Seal it with a clay ball first");
        m(s, "box_lit", "Уголь тлеет. Сталь будет примерно через %s мин", "The charcoal smoulders. Steel in about %s min");
        m(s, "box_unsealed", "Печать расколота — глина пропала", "The seal is broken; the clay is lost");
        m(s, "box_early", "Вскрыли рано: стали %s, железа %s — сталь неровная", "Opened early: steel %s, iron %s, uneven steel");
        m(s, "box_best", "В самый раз: стали %s", "Just right: steel %s");
        m(s, "box_done", "Сталь: %s", "Steel: %s");
        m(s, "box_over", "Передержали — сталь хрупкая: %s", "Overdone: brittle steel %s");
        m(s, "box_soaking", "Томится: слитков %s, ещё около %s мин", "Soaking: %s ingots, about %s min more");
        m(s, "box_ready", "Готово — вскрывайте (лучше в ближайшие %s мин)", "Ready: open it (best within %s min)");
        m(s, "box_report_done", "Сталь готова, но долго не держите", "The steel is done; don't leave it long");
        m(s, "box_report_over", "Передержано — сталь станет хрупкой", "Overdone: the steel will be brittle");
        m(s, "box_report_sealed", "Запечатано: слитков %s. Огниво — разжечь", "Sealed: %s ingots. Flint and steel to light it");
        m(s, "box_report", "В ящике: слитков %s / %s, угля %s (нужно %s). Ком глины — запечатать",
                "In the box: ingots %s / %s, charcoal %s (needs %s). A clay ball seals it");
        // Ступа и желоб
        m(s, "mortar_full", "Ступа полна (до %s одного рода)", "The mortar is full (up to %s of one kind)");
        m(s, "mortar_empty", "Ступа пуста: положите золотую руду, киноварь, сушёную траву или кость",
                "The mortar is empty: put in gold ore, cinnabar, a dried herb or a bone");
        m(s, "mortar_done", "Истолчено: %s", "Pounded: %s");
        // Клеймо мастера
        m(s, "hallmark_set", "%s: поставлено клеймо «%s»", "%s: stamped with the mark «%s»");
        m(s, "hallmark_already", "Уже заклеймено: «%s»", "Already marked: «%s»");
        m(s, "mortar_spilled", "Истолчено: %s, просыпалось: %s", "Pounded: %s, spilled: %s");
        m(s, "sluice_full", "Желоб полон (до %s)", "The sluice is full (up to %s)");
        m(s, "sluice_empty", "В желобе нет толчёной руды", "There is no crushed ore in the sluice");
        m(s, "sluice_report", "В желобе толчёной руды: %s / %s. E лотком — промывать",
                "Crushed ore in the sluice: %s / %s. E with a pan to wash");
        m(s, "sluice_done", "Намыто золотого песка: %s", "Gold dust washed: %s");
        m(s, "sluice_done_mercury", "Намыто с ртутью: %s (склянка ушла)", "Washed with quicksilver: %s (a vial used)");
        // Молотобоец
        m(s, "striker_joined", "Вы молотобоец у %s: бейте в такт", "You are striking for %s: keep the beat");
        m(s, "striker_alone", "Кузнец ушёл — бить некому", "The smith has gone: no one to strike for");
    }

    private static void work(Sink s) {
        w(s, "title_of", "Работа: %s", "Work: %s");
        w(s, "stage", "Этап %s из %s: %s", "Step %s of %s: %s");
        w(s, "stage_done", "Этап готов: %s из 100", "Step done: %s of 100");
        w(s, "finishing", "Заканчиваем…", "Finishing…");
        w(s, "quality", "Выйдет: %s", "Will come out: %s");
        w(s, "in_forge", "В горне… ЛКМ — мехи, пробел — вынуть", "In the forge… left click works the bellows, space takes it out");
        w(s, "annealing", "Отжиг в горне…", "Annealing in the forge…");
        w(s, "forge_hint", "%s Пробел — в горн и из горна.", "%s Space puts it in the forge and takes it out.");
        w(s, "striker", "Молотобоец рядом", "A striker is with you");
        w(s, "cracked_cold", "Холодно — трещина!", "Too cold: a crack!");
        w(s, "cracked_hard", "Переклепали — трещина! Отожгите", "Work-hardened: a crack! Anneal it");
        w(s, "burnt", "Пережог!", "Burnt!");
        w(s, "annealed", "Отожжено", "Annealed");
        w(s, "striker_joined", "Молотобоец встал к наковальне", "A striker has joined you");
        w(s, "striker_left", "Молотобоец отошёл", "The striker has left");
        w(s, "striker_idle", "Молотобоец ждёт — этот этап без него", "The striker waits: this step needs no striker");
        w(s, "striker_aim", "Покажите молотком, куда бить", "Point your hammer where to strike");
        String[][] ops = {
                {"consolidate", "Обжим крицы", "Consolidating"}, {"draw", "Вытяжка", "Drawing out"},
                {"upset", "Осадка", "Upsetting"}, {"bend", "Гибка", "Bending"}, {"punch", "Пробивка", "Punching"},
                {"cut", "Рубка", "Cutting"}, {"weld", "Кузнечная сварка", "Forge welding"}, {"raise", "Выколотка", "Raising"},
                {"quench", "Закалка", "Quenching"}, {"temper", "Отпуск", "Tempering"}, {"grind", "Заточка", "Grinding"},
                {"wire", "Волочение", "Wire drawing"}, {"weave", "Плетение", "Mail weaving"}, {"rivet", "Клёпка", "Riveting"},
                {"gild", "Позолота", "Gilding"}, {"mint", "Чеканка", "Minting"}, {"melt", "Плавка", "Melting"},
                {"pour", "Заливка", "Pouring"}, {"bellows", "Мехи", "Bellows"}, {"cupel", "Купель", "Cupellation"},
                {"stamp", "Толчение", "Pounding"}, {"pan", "Промывка", "Panning"}};
        for (String[] op : ops) {
            w(s, "op." + op[0], op[1], op[2]);
        }
        w(s, "bloomery_title", "Плавка: %s", "Smelting: %s");
        w(s, "cupel_title", "Купель", "Cupel");
        w(s, "mortar_title", "Ступа", "Mortar");
        w(s, "pan_title", "Промывка", "Panning");
        w(s, "sharpen_title", "Точило", "Grinding wheel");
        w(s, "liquid_1", "вода", "water");
        w(s, "liquid_2", "рассол", "brine");
        w(s, "liquid_3", "жир", "fat");
    }

    private static void steps(Sink s) {
        w(s, "draw_status", "Отклонение %s%%, перетянуто %s, ударов %s", "Off by %s%%, overdrawn %s, blows %s");
        w(s, "draw_hint", "Зажмите ЛКМ над местом — замах, отпустите — удар. Enter — хватит.",
                "Hold left click over a spot to swing, release to strike. Enter when done.");
        w(s, "consolidate_status", "Шлака осталось %s%%, раскрошено %s, ударов %s", "Slag left %s%%, crumbled %s, blows %s");
        w(s, "consolidate_hint", "Бейте по бурым пятнам шлака: ЛКМ — замах и удар. Enter — хватит.",
                "Strike the brown slag spots: left click to swing and strike. Enter when done.");
        w(s, "upset_status", "Головка %s%%, наклон %s°, повело %s", "Head %s%%, lean %s°, bent %s");
        w(s, "upset_hint", "Удар левее середины клонит вправо — правьте ударами с другой стороны.",
                "A blow left of centre leans it right: straighten it with blows from the other side.");
        w(s, "upset_bent", "Повело!", "It bent!");
        w(s, "bend_status", "Загнуто %s%%, надломов %s", "Bent %s%%, kinks %s");
        w(s, "bend_hint", "Возьмитесь у метки (ЛКМ) и ведите по дуге.", "Grab at the mark (left click) and follow the curve.");
        w(s, "bend_kink", "Надлом!", "A kink!");
        w(s, "bend_off", "Сошли с дуги", "Off the curve");
        w(s, "punch_status", "Дыра %s из %s, удар %s из 2", "Hole %s of %s, blow %s of 2");
        w(s, "punch_hint", "ЛКМ, когда бородок над меткой.", "Left click when the punch is over the mark.");
        w(s, "punch_clean", "Чисто", "Clean");
        w(s, "punch_off", "Криво", "Off-centre");
        w(s, "cut_status", "Перерублено %s из %s, зарубок %s", "Cut %s of %s, nicks %s");
        w(s, "cut_hint", "Зубило — мышью, ЛКМ — удар. Три удара в метку.", "Move the chisel with the mouse, left click strikes. Three blows on a mark.");
        w(s, "cut_stray", "Мимо метки — зарубка", "Off the mark: a nick");
        w(s, "weld_status", "Шов %s%%, мимо такта %s", "Weld %s%%, off the beat %s");
        w(s, "weld_status_noflux", "Нет флюса! Шов %s%%", "No flux! Weld %s%%");
        w(s, "weld_hint", "ПКМ — флюс. Греть до белого, ЛКМ в такт пульсу.", "Right click for flux. Heat to white, left click on the beat.");
        w(s, "weld_fluxed", "Посыпано флюсом", "Fluxed");
        w(s, "weld_cold", "Остыло — не схватилось", "Cooled: it didn't take");
        w(s, "weld_noflux", "Без флюса не схватывается", "It won't take without flux");
        w(s, "raise_status", "Отклонение %s%%, перебито %s", "Off by %s%%, overworked %s");
        w(s, "raise_hint", "ЛКМ — выколотить (светлее — глубже), ПКМ — проглаживать. Enter — хватит.",
                "Left click raises (lighter is deeper), right click planishes. Enter when done.");
        w(s, "quench_wait", "Ждите цвета — %s. ЛКМ — окунуть", "Wait for the colour: %s. Left click to dip");
        w(s, "quench_dip", "В бочке: %s%%", "In the tub: %s%%");
        w(s, "quench_cracked", "Треснуло при закалке!", "It cracked in the quench!");
        w(s, "quench_hint", "ЛКМ — окунуть на вишнёво-оранжевом, держать и водить вверх-вниз.",
                "Left click to dip at cherry-orange, hold and move up and down.");
        w(s, "quench_crack", "Трещина!", "A crack!");
        w(s, "quench_soft", "Остыло — закалка слабая", "It cooled: a soft quench");
        w(s, "temper_status", "Цель — %s, отклонение %s%%", "Target: %s, off by %s%%");
        w(s, "temper_hint", "Мышью — где жар под вещью. ЛКМ — снять.", "The mouse moves the heat under the piece. Left click takes it off.");
        w(s, "temper_straw", "соломенный", "straw");
        w(s, "temper_purple", "пурпурный", "purple");
        w(s, "temper_blue", "синий", "blue");
        w(s, "grind_status", "Наточено %s%%, пережжено %s", "Sharpened %s%%, burnt %s");
        w(s, "grind_hint", "Мышь — вести клинок по кругу, ЛКМ — прижать (дольше — сильнее, но греет), ПКМ — полить водой. Enter — хватит.",
                "The mouse slides the blade over the wheel, hold left click to press (longer is harder, but heats it), right click pours water. Enter when done.");
        w(s, "grind_burnt", "Кромка посинела — пережгли", "The edge turned blue: burnt");
        w(s, "wire_status", "Проход %s из %s, наклёп %s%%, обрывов %s", "Pass %s of %s, hardening %s%%, breaks %s");
        w(s, "wire_hint", "ЛКМ — тянуть, отпустите до красного. ПКМ — отжечь.", "Hold left click to draw, let go before the red. Right click anneals.");
        w(s, "wire_broke", "Лопнула!", "It snapped!");
        w(s, "weave_status", "Кольцо %s из %s, ошибок %s, кривых заклёпок %s", "Ring %s of %s, mistakes %s, bad rivets %s");
        w(s, "weave_hint", "ЛКМ по кольцам, в которые продеть, потом — заклепать в зелёном.",
                "Left click the rings to link through, then rivet in the green.");
        w(s, "weave_late", "Не успели — кольцо кривое", "Too slow: a crooked ring");
        w(s, "weave_rivet_bad", "Заклёпка легла криво", "The rivet went in crooked");
        w(s, "weave_wrong", "Не то кольцо — разрыв", "Wrong ring: a gap");
        w(s, "rivet_status", "Заклёпка %s из %s, удар %s из 2, огрехов %s", "Rivet %s of %s, blow %s of 2, faults %s");
        w(s, "rivet_hint", "Первый удар — тяжёлый, второй — в меру; бить, когда стоит прямо.",
                "First blow heavy, second moderate; strike when it stands straight.");
        w(s, "rivet_bent", "Погнули", "Bent");
        w(s, "rivet_loose", "Слабо — болтается", "Too light: it's loose");
        w(s, "rivet_flat", "Сплющили головку", "The head is flattened");
        w(s, "rivet_good", "Ладно", "Good");
        w(s, "gild_paint", "Нанесено %s%%, мимо %s", "Coated %s%%, off the pattern %s");
        w(s, "gild_paint_hint", "Зажмите ЛКМ и закрашивайте узор. Enter — к огню.", "Hold left click and paint the pattern. Enter to go to the fire.");
        w(s, "gild_fire", "Ртуть уходит: %s%%", "The quicksilver is going off: %s%%");
        w(s, "gild_fire_hint", "Держите ЛКМ — ближе к огню, отпустите — дальше. Жар в зелёном.",
                "Hold left click to bring it closer to the fire, release to lift it. Keep the heat in the green.");
        w(s, "gild_now_fire", "Нанесено — теперь к огню", "Coated: now to the fire");
        w(s, "mint_status", "Монета %s из %s, слабых %s", "Coin %s of %s, weak %s");
        w(s, "mint_hint", "Зажмите ЛКМ — замах; отпустите, когда штемпель над кружком.",
                "Hold left click to swing; release when the die is over the blank.");
        w(s, "mint_weak", "Слабо — оттиск неясный", "Too light: a faint strike");
        w(s, "mint_clean", "Чисто", "Clean");
        w(s, "mint_off", "Кривой чекан", "An off-centre strike");
        w(s, "melt_status", "Расплав %s°, расплавлено %s%%, шлака %s", "Melt %s°, melted %s%%, dross %s");
        w(s, "melt_hint", "Мышь — тигель к жару (вправо) и от него, ЛКМ — мехи, ПКМ — снять шлак. Лить — %s–%s°.",
                "The mouse moves the crucible into the heat (right) and out, left click works the bellows, right click skims dross. Pour at %s–%s°.");
        w(s, "melt_title", "Плавка: %s", "Melting: %s");
        w(s, "melt_edge", "край горна", "forge edge");
        w(s, "melt_center", "самый жар", "hottest");
        w(s, "melt_ready", "готов к заливке", "ready to pour");
        w(s, "melt_molten", "Расплавилось!", "Molten!");
        w(s, "melt_froze", "Схватилось коркой — жарче!", "It crusted over: hotter!");
        w(s, "pour_status", "Форма %s%%, расплав жидок ещё %s с", "Mould %s%%, the melt stays liquid %s s more");
        w(s, "pour_hint", "Держите ЛКМ — наклонить тигель. Ровной струёй, остановитесь у метки.",
                "Hold left click to tip the crucible. A steady stream; stop at the mark.");
        w(s, "bellows_status", "%s — плавка %s%%, уголь %s%%", "%s: smelt %s%%, charcoal %s%%");
        w(s, "bellows_phase_0", "Прогрев", "Warming");
        w(s, "bellows_phase_1", "Восстановление", "Reduction");
        w(s, "bellows_phase_2", "Спекание", "Bloom forming");
        w(s, "bellows_hint", "ЛКМ — мехи, не чаще пары раз в секунду. ПКМ — подсыпать угля.",
                "Left click works the bellows, no faster than twice a second. Right click adds charcoal.");
        w(s, "bellows_hint_coal", "Каменный уголь в горне — металл выйдет хуже. ЛКМ — мехи, ПКМ — уголь.",
                "Coal in the furnace: the metal will be worse. Left click bellows, right click charcoal.");
        w(s, "bellows_hint_noflux", "Флюса мало — шлак останется. ЛКМ — мехи, ПКМ — уголь.",
                "Short of flux: slag will stay. Left click bellows, right click charcoal.");
        w(s, "cupel_status", "Свинец выгорел на %s%%, глёт снят %s, запёкся %s", "Lead burnt off %s%%, litharge skimmed %s, baked %s");
        w(s, "cupel_hint", "Пробел — мехи, жар в полосе. Держите ЛКМ и сгоняйте глёт к краю чаши, пока не запёкся.",
                "Space works the bellows: keep the heat in the band. Hold left click and push the litharge to the rim before it bakes.");
        w(s, "cupel_spatter", "Перегрели — серебро брызжет!", "Too hot: the silver spits!");
        w(s, "cupel_baked", "Глёт запёкся — унёс серебро", "The litharge baked: it took silver with it");
        w(s, "cupel_flash", "Серебро вспыхнуло — готово!", "The silver flashes: done!");
        w(s, "cupel_early", "Рано!", "Too early!");
        w(s, "cupel_late", "Поздно — серебро выгорает", "Too late: the silver burns away");
        w(s, "cupel_on_time", "В самый раз", "Just right");
        w(s, "stamp_status", "Горсть %s из %s, истолчено %s%%, мимо такта %s", "Handful %s of %s, pounded %s%%, off the beat %s");
        w(s, "stamp_hint", "ЛКМ, когда пест внизу (зелёное).", "Left click when the pestle is down (green).");
        w(s, "stamp_off", "Вразнобой — просыпалось", "Out of rhythm: it spilled");
        w(s, "pan_status", "Отмыто %s%%, золота в лотке %s%%, воды %s%%", "Washed %s%%, gold in the pan %s%%, water %s%%");
        w(s, "pan_hint", "Пробел — зачерпнуть воды. Кружите мышью (не быстро — плеснёт), ЛКМ — слить край. ПКМ — выкинуть камень, размять ком, взять самородок. Enter — хватит.",
                "Space scoops water. Circle the mouse (not too fast or it slops), hold left click to pour off the rim. Right click flicks out a stone, breaks a clod, picks a nugget. Enter when done.");
        w(s, "pan_clod", "Ком размят", "Clod broken");
        w(s, "pan_clod_lost", "Ком глины с золотом ушёл за край!", "A clay clod with gold went over the rim!");
        w(s, "pan_nugget", "Самородок!", "A nugget!");
        w(s, "pan_lost", "Слили крупинку!", "A grain went over the rim!");
    }

    /** Окна сыродутного горна и цементационной печи, книги «что сделать» у горна, куба и бродильной бочки. */
    private static void windows(Sink s) {
        s.add("gui.halgventmod.recipes.what", "Что сделать", "What to make");
        // Сыродутный горн
        s.add("gui.halgventmod.bloomery.charge", "Закладка", "Charge");
        s.add("gui.halgventmod.bloomery.title", "Плавка", "Smelting");
        s.add("gui.halgventmod.bloomery.ore", "Руда", "Ore");
        s.add("gui.halgventmod.bloomery.fuel", "Древесный уголь", "Charcoal");
        s.add("gui.halgventmod.bloomery.flux", "Флюс", "Flux");
        s.add("gui.halgventmod.bloomery.crucible", "Тигель", "Crucible");
        s.add("gui.halgventmod.bloomery.crucible_for", "всё, кроме крицы", "all but the bloom");
        s.add("gui.halgventmod.bloomery.to_crucible", "Стечёт в тигель жидким — вынимать клещами", "Runs into the crucible molten: lift it with tongs");
        s.add("gui.halgventmod.bloomery.take", "Выгрести", "Rake out");
        s.add("gui.halgventmod.bloomery.smelt", "Плавить", "Smelt");
        s.add("gui.halgventmod.bloomery.empty", "Горн пуст. Положите руду одного рода, древесный уголь и флюс.",
                "The furnace is empty. Put in ore of one kind, charcoal and flux.");
        s.add("gui.halgventmod.bloomery.burning", "Идёт плавка.", "Smelting is under way.");
        s.add("gui.halgventmod.bloomery.per_batch", "На закладку: руды %s, угля %s", "Per charge: ore %s, charcoal %s");
        s.add("gui.halgventmod.bloomery.batches", "Закладок: %s из %s", "Charges: %s of %s");
        s.add("gui.halgventmod.bloomery.not_enough", "Мало: на закладку нужно %s руды и %s угля",
                "Not enough: a charge takes %s ore and %s charcoal");
        s.add("gui.halgventmod.bloomery.roasted", "Обожжено: %s из %s — лучше", "Roasted: %s of %s, better");
        s.add("gui.halgventmod.bloomery.unroasted", "Обожжено: %s из %s — обожгите в гончарном горне",
                "Roasted: %s of %s; roast it in a pottery kiln");
        s.add("gui.halgventmod.bloomery.coal", "Каменный уголь: металл выйдет хуже", "Coal: the metal comes out worse");
        s.add("gui.halgventmod.bloomery.no_flux", "Не хватает флюса на %s закладок: хуже", "Flux short for %s charges: worse");
        s.add("gui.halgventmod.bloomery.book_hint", "На закладку — %s древесного угля и флюс; за плавку до %s закладок.",
                "Each charge takes %s charcoal and flux; up to %s charges per smelt.");
        String[][] smelts = {
                {"iron", "Железняк → крица", "Ironstone → bloom", "2 железняка на закладку. Крицу проковывают на наковальне в железо.",
                        "2 ironstone per charge. The bloom is beaten into iron on the anvil."},
                {"bog", "Болотная руда → крица", "Bog ore → bloom", "3 болотной руды на закладку; обожжённая — лучше.",
                        "3 bog ore per charge; roasted is better."},
                {"copper", "Медная руда → медь", "Copper ore → copper", "2 руды на закладку; без обжига медь хуже. Медь стекает в тигель.",
                        "2 ore per charge; unroasted copper is worse. The copper runs into a crucible."},
                {"tin", "Оловянный камень → олово", "Tinstone → tin", "2 камня на закладку. Олово стекает в тигель.", "2 tinstone per charge. The tin runs into a crucible."},
                {"lead", "Свинцовый блеск → свинец с серебром", "Galena → silver-bearing lead",
                        "1 блеск на закладку. Стекает в тигель; жидким — E тиглем по купели, там отделяют серебро.", "1 galena per charge. It runs into a crucible; while molten, E the cupel with it to part the silver."},
                {"silver", "Серебряная руда → серебро", "Silver ore → silver", "3 руды на закладку. Серебро стекает в тигель.", "3 ore per charge. The silver runs into a crucible."}};
        for (String[] smelt : smelts) {
            s.add("gui.halgventmod.bloomery.book." + smelt[0], smelt[1], smelt[2]);
            s.add("gui.halgventmod.bloomery.book." + smelt[0] + ".how", smelt[3], smelt[4]);
        }
        // Цементационная печь
        s.add("gui.halgventmod.cementation.chamber", "Камера", "Chamber");
        s.add("gui.halgventmod.cementation.soak", "Томление", "Soaking");
        s.add("gui.halgventmod.cementation.charcoal", "Уголь: надо", "Charcoal: need");
        s.add("gui.halgventmod.cementation.sealed", "Запечатано глиной", "Sealed with clay");
        s.add("gui.halgventmod.cementation.seal", "Запечатать", "Seal");
        s.add("gui.halgventmod.cementation.light", "Разжечь", "Light");
        s.add("gui.halgventmod.cementation.open", "Вскрыть", "Open");
        s.add("gui.halgventmod.cementation.days", "Томится: %s сут.", "Soaking: %s days");
        s.add("gui.halgventmod.cementation.empty", "Положите железные слитки и древесный уголь — по два на слиток.",
                "Put in iron ingots and charcoal, two per ingot.");
        s.add("gui.halgventmod.cementation.need_charcoal", "Мало угля: нужно %s.", "Not enough charcoal: %s needed.");
        s.add("gui.halgventmod.cementation.ready_seal", "Готово к печати: «Запечатать» — ком глины из инвентаря.",
                "Ready to seal: \"Seal\" uses a clay ball from your inventory.");
        s.add("gui.halgventmod.cementation.ready_light", "Запечатано. «Разжечь» — огниво из инвентаря.",
                "Sealed. \"Light\" uses a flint and steel from your inventory.");
        s.add("gui.halgventmod.cementation.soaking", "Железо набирает углерод. До стали — около %s мин.",
                "The iron is taking up carbon. Steel in about %s min.");
        s.add("gui.halgventmod.cementation.best", "В самый раз — вскрывайте: сталь лучше металла.",
                "Just right: open it, the steel beats the iron.");
        s.add("gui.halgventmod.cementation.done", "Сталь готова — не передержите.", "The steel is done; don't overdo it.");
        s.add("gui.halgventmod.cementation.over", "Передержано — сталь станет хрупкой.", "Overdone: the steel turns brittle.");
        m(s, "box_need_clay", "Нужен ком глины в инвентаре", "You need a clay ball in your inventory");
        // Перегонный куб
        s.add("gui.halgventmod.still.book_hint", "%s порции — одна склянка крепкого. Жар 2 — в самый раз, 3 — быстрее, но первач.",
                "%s portions make one bottle of spirit. Heat 2 is right, 3 is faster but rough.");
        String[][] still = {
                {"bread", "Пиво → хлебное вино", "Beer → bread wine", "Залейте пиво, разведите огонь под кубом.",
                        "Pour in beer and light the fire under the still."},
                {"brandy", "Вино → бренди", "Wine → brandy", "Виноградное и ягодное вино.", "Grape and berry wine."},
                {"fruit", "Вишнёвое, сливовое → вишнёвка, сливовица", "Cherry, plum → cherry, plum brandy",
                        "Вино из вишни или сливы.", "Wine of cherries or plums."},
                {"apple", "Сидр → яблочная, грушевая водка", "Cider → apple, pear brandy",
                        "Яблочный или грушевый сидр.", "Apple or pear cider."},
                {"mead", "Медовуха → медовая водка", "Mead → honey spirit", "Медовуха из бродильной бочки.",
                        "Mead from the fermenting barrel."}};
        for (String[] r : still) {
            s.add("gui.halgventmod.still.book." + r[0], r[1], r[2]);
            s.add("gui.halgventmod.still.book." + r[0] + ".how", r[3], r[4]);
        }
        // Бродильная бочка
        s.add("gui.halgventmod.barrel.book_hint", "Бродит только запечатанная. Летом быстрее, зимой медленнее, но чище. "
                        + "Готовое дольше %s суток — уксус (кроме настойки).",
                "Only a sealed barrel ferments. Faster in summer, slower but cleaner in winter. "
                        + "Ready longer than %s days turns to vinegar (not tinctures).");
        String[][] barrel = {
                {"beer", "Пиво", "Beer", "Сусло из горшка: солод с водой. Бродит 2 суток.",
                        "Wort from a pot: malt and water. Ferments 2 days.", "Пьют свежим: не выдерживают.", "Drunk fresh: not aged."},
                {"cider", "Сидр", "Cider", "Яблочное или грушевое сусло с давильни. 4 суток.",
                        "Apple or pear must from the press. 4 days.", "В бочке для выдержки становится лучше.", "Improves in an aging cask."},
                {"wine", "Вино", "Wine", "Сусло с давильни: виноград, вишня, слива, ягоды. 4 суток.",
                        "Must from the press: grapes, cherries, plums, berries. 4 days.", "В бочке для выдержки становится лучше.",
                        "Improves in an aging cask."},
                {"mead", "Медовуха", "Mead", "Медовое сусло из горшка: мёд с водой. 6 суток.",
                        "Honey must from a pot: honey and water. 6 days.", "В бочке для выдержки становится лучше.",
                        "Improves in an aging cask."},
                {"tincture", "Настойка", "Tincture", "Крепкое с перегонного куба и добавка — мёд, травы или ягоды. 3 суток.",
                        "Spirit from the still and an addition: honey, herbs or berries. 3 days.", "Не киснет.", "Never sours."}};
        for (String[] r : barrel) {
            s.add("gui.halgventmod.barrel.book." + r[0], r[1], r[2]);
            s.add("gui.halgventmod.barrel.book." + r[0] + ".how", r[3], r[4]);
            s.add("gui.halgventmod.barrel.book." + r[0] + ".serve", r[5], r[6]);
        }
    }

    private static void other(Sink s) {
        s.add("gui.halgventmod.workshop.forge", "Ковать", "Forge");
        s.add("gui.halgventmod.workshop.cast", "Лить", "Cast");
        s.add("gui.halgventmod.workshop.reforge", "Перековка: клинок или инструмент", "Reforge a blade or tool");
        s.add("gui.halgventmod.workshop.on_anvil", "На наковальне", "On the anvil");
        s.add("gui.halgventmod.workshop.on_table", "На столе", "On the table");
        s.add("gui.halgventmod.workshop.tab.metal", "Металл", "Metal");
        s.add("gui.halgventmod.workshop.tab.tools", "Инструменты", "Tools");
        s.add("gui.halgventmod.workshop.tab.weapons", "Оружие", "Weapons");
        s.add("gui.halgventmod.workshop.tab.armor", "Доспехи", "Armour");
        s.add("gui.halgventmod.workshop.tab.alloys", "Слитки из тигля", "Ingots from the crucible");
        s.add("gui.halgventmod.workshop.melt_need", "Расплав: %s", "Melt: %s");
        s.add("gui.halgventmod.workshop.tab.castings", "Отливки", "Castings");
        s.add("gui.halgventmod.striker.title", "Молотобоец", "Striker");
        s.add("gui.halgventmod.striker.hint", "ЛКМ, когда кувалда внизу — в такт", "Left click when the sledge is down, on the beat");
        s.add("gui.halgventmod.striker.status", "Ударов %s, последний: %s из 100", "Blows %s, last: %s of 100");
        s.add("hud.halgventmod.use.mine", "Бить: %s", "Strike: %s");
        s.add("hud.halgventmod.use.need_pickaxe", "Нужна кирка", "Needs a pickaxe");
        s.add("hud.halgventmod.use.need_shovel", "Нужна лопата", "Needs a shovel");
        s.add("hud.halgventmod.use.vein_empty", "Выработано", "Worked out");
        s.add("tooltip.halgventmod.roasted", "Обожжена", "Roasted");
        s.add("tooltip.halgventmod.hallmark", "Клеймо: %s", "Maker's mark: %s");
        s.add("tooltip.halgventmod.hallmark.stamp", "Знак: %s", "Mark: %s");
        s.add("tooltip.halgventmod.hallmark.blank", "Знак не выбит: выбьется знак того, кто клеймит первым", "No mark yet: the first to use it strikes their own");
        crucible(s);
    }

    /** Металлы, тигель, окно кузнечного горна и его книга сплавов. */
    private static void crucible(Sink s) {
        String[][] metals = {
                {"copper", "медь", "copper"}, {"tin", "олово", "tin"}, {"zinc", "галмей", "calamine"},
                {"lead", "свинец", "lead"}, {"silver", "серебро", "silver"}, {"gold", "золото", "gold"},
                {"bronze", "бронза", "bronze"}, {"brass", "латунь", "brass"}, {"pewter", "пьютер", "pewter"}, {"silver_lead", "свинец с серебром", "silver-bearing lead"}};
        for (String[] metal : metals) {
            s.add("metal.halgventmod." + metal[0], metal[1], metal[2]);
        }
        s.add("tooltip.halgventmod.crucible.melt", "Расплав: %s, %s сл.", "Melt: %s, %s ingots");
        s.add("tooltip.halgventmod.crucible.liquid", "Жидкий ещё %s с", "Liquid for %s s more");
        s.add("tooltip.halgventmod.crucible.solid", "Застыл — переплавить в кузнечном горне", "Set: remelt it in the forge");
        s.add("tooltip.halgventmod.crucible.charge", "Шихта: %s", "Charge: %s");
        s.add("tooltip.halgventmod.crucible.lid", "Накрыт глиной", "Lidded with clay");
        // Окно горна
        s.add("gui.halgventmod.forge.pump", "Мехи", "Bellows");
        s.add("gui.halgventmod.forge.melt", "Плавить", "Melt");
        s.add("gui.halgventmod.forge.crucible", "Тигель", "Crucible");
        s.add("gui.halgventmod.forge.crucible_slot", "Тигель", "Crucible");
        s.add("gui.halgventmod.forge.amount", "%s из %s сл.", "%s of %s ingots");
        s.add("gui.halgventmod.forge.no_crucible", "нет тигля", "no crucible");
        s.add("gui.halgventmod.forge.charge", "Шихта", "Charge");
        s.add("gui.halgventmod.forge.charge_hint", "слитки, песок, галмей", "ingots, dust, calamine");
        s.add("gui.halgventmod.forge.empty", "Тигель пуст: положите в шихту слитки, золотой песок или галмей.",
                "The crucible is empty: charge it with ingots, gold dust or calamine.");
        s.add("gui.halgventmod.forge.only_lid", "Тигель накрыт глиной, но пуст — положите шихту.",
                "The crucible is lidded but empty: add a charge.");
        s.add("gui.halgventmod.forge.no_alloy", "Ничего не выйдет: галмей — только с медью", "Nothing will come: calamine only goes with copper");
        s.add("gui.halgventmod.forge.gives", "Выйдет: %s", "Gives: %s");
        s.add("gui.halgventmod.forge.ratio_good", "В самый раз: %s %s%%", "Just right: %s %s%%");
        s.add("gui.halgventmod.forge.ratio_low", "Мало: %s %s%%, лучше %s%%", "Too little: %s %s%%, best %s%%");
        s.add("gui.halgventmod.forge.ratio_high", "Много: %s %s%%, лучше %s%%", "Too much: %s %s%%, best %s%%");
        s.add("gui.halgventmod.forge.no_lid", "Без крышки (ком глины) галмей выгорит", "Without a lid (a clay ball) the calamine burns off");
        s.add("gui.halgventmod.forge.unmelted", "Не плавлено — «Плавить»", "Not melted yet: \"Melt\"");
        s.add("gui.halgventmod.forge.liquid", "Жидкий: %s — клещами к столу", "Molten: %s; tongs, to the table");
        s.add("gui.halgventmod.forge.solid", "Застыл — плавить снова", "Set: melt it again");
        s.add("gui.halgventmod.forge.out", "Погас", "Out");
        s.add("gui.halgventmod.forge.hot", "Жар для работы", "Working heat");
        s.add("gui.halgventmod.forge.warming", "Разгорается", "Warming up");
        s.add("gui.halgventmod.forge.coal", "кам. уголь", "coal");
        s.add("gui.halgventmod.forge.book_hint", "В тигель — до %s слитков. Вынутый из горна расплав жидок %s с, вынимают его клещами. С жидким тиглем — к литейному столу.",
                "A crucible holds up to %s ingots. Out of the forge the melt stays liquid %s s; lift it with tongs. Take the molten crucible to the casting table.");
        String[][] book = {
                {"bronze", "Бронза", "Bronze",
                        "9 медных слитков и 1 оловянный: олова — десятая часть. Больше олова — хрупкая, меньше — мягкая.",
                        "9 copper ingots and 1 tin: a tenth tin. More tin and it turns brittle, less and it stays soft."},
                {"brass", "Латунь", "Brass",
                        "2 медных слитка, 2 галмея и ком глины — крышка: без неё галмей выгорает. Не перегревайте — выше 1040° цинк уходит дымом.",
                        "2 copper ingots, 2 calamine and a clay ball for a lid: without it the calamine burns off. Don't overheat: above 1040° the zinc goes up in smoke."},
                {"pewter", "Пьютер", "Pewter",
                        "4 оловянных слитка и 1 свинцовый. Плавится у края горна, без мехов.",
                        "4 tin ingots and 1 lead. It melts at the edge of the forge without the bellows."},
                {"gold", "Золото", "Gold",
                        "5 золотого песка — слиток. Плавят в самом жару, с мехами.",
                        "5 gold dust make an ingot. Melt it in the hottest spot, with the bellows."},
                {"pure", "Чистые металлы", "Pure metals",
                        "Медь, олово, свинец, серебро — по одному, без примесей: на отливки или снова в слитки.",
                        "Copper, tin, lead, silver: one at a time, unmixed, for castings or back into ingots."},
                {"remelt", "Переплавка", "Remelting",
                        "Застывший металл ставят в горн в том же тигле и плавят снова. Лучше он от этого не станет.",
                        "Set metal goes back into the forge in its crucible and is melted again. It won't get any better for it."}};
        for (String[] entry : book) {
            s.add("gui.halgventmod.forge.book." + entry[0], entry[1], entry[2]);
            s.add("gui.halgventmod.forge.book." + entry[0] + ".how", entry[3], entry[4]);
        }
    }
}
