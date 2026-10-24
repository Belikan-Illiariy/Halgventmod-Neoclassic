package com.nrdhvnstd.halgventmod.smithing;

import com.nrdhvnstd.halgventmod.draw.DrawType;
import com.nrdhvnstd.halgventmod.item.Category;
import com.nrdhvnstd.halgventmod.item.Gender;
import com.nrdhvnstd.halgventmod.item.ModLore;
import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.pottery.VesselForm;
import com.nrdhvnstd.halgventmod.pottery.WareItem;
import com.nrdhvnstd.halgventmod.weight.ModWeights;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;

import static com.nrdhvnstd.halgventmod.registry.ModItems.item;

/**
 * Предметы кузнечества: руды и минералы, металлы и сплавы, кузнечные части, инструменты, станции.
 * Регистрируются через билдер ModItems ({@link #init()} зовёт ModItems.register). Ванильные древесный и каменный
 * уголь — свои описания и вес здесь же.
 */
public final class SmithingItems {
    // ----- Руды и минералы -----
    public static final DeferredItem<Item> BOG_IRON_ORE = item("bog_iron_ore", "Болотная руда", "Bog iron ore", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MINERALS)
            .lore("Бурые комья лимонита со дна торфяных болот — главная железная руда низин и побережья. Её копают "
                            + "лопатами, сушат и обжигают в гончарном горне перед плавкой.",
                    "Brown lumps of limonite from the bottom of peat bogs, the iron ore of the lowlands and coast. It is "
                            + "dug with shovels, dried and roasted in a pottery kiln before smelting.")
            .kg(2)
            .register();
    public static final DeferredItem<Item> IRON_ORE = item("iron_ore", "Железняк", "Ironstone", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.MINERALS)
            .lore("Тяжёлый бурый камень из горных рудников. Железа в нём больше, чем в болотной руде, и крица выходит чище.",
                    "A heavy brown stone from the mountain mines. It holds more iron than bog ore, and the bloom comes out cleaner.")
            .kg(2.5)
            .register();
    public static final DeferredItem<Item> COPPER_ORE = item("copper_ore", "Медная руда", "Copper ore", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MINERALS)
            .lore("Зелёный малахит и золотистый медный колчедан. Колчедан перед плавкой долго обжигают, иначе медь "
                            + "выходит ломкой.",
                    "Green malachite and golden copper pyrite. The pyrite is roasted long before smelting, or the copper "
                            + "comes out brittle.")
            .kg(2.5)
            .register();
    public static final DeferredItem<Item> TIN_ORE = item("tin_ore", "Оловянный камень", "Tinstone", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.MINERALS)
            .lore("Тёмные тяжёлые зёрна касситерита. Оловянной руды мало, поэтому олово, а вместе с ним и бронза, дороги.",
                    "Dark heavy grains of cassiterite. Tin ore is scarce, so tin, and bronze with it, are dear.")
            .kg(2)
            .register();
    public static final DeferredItem<Item> LEAD_ORE = item("lead_ore", "Свинцовый блеск", "Galena", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.MINERALS)
            .lore("Блестящие серые кубики галенита. Из него выплавляют свинец, а из этого свинца — серебро: почти всё "
                            + "серебро добывают именно так.",
                    "Shiny grey cubes of galena. Lead is smelted from it, and silver from that lead: nearly all silver is "
                            + "won this way.")
            .kg(3)
            .register();
    public static final DeferredItem<Item> SILVER_ORE = item("silver_ore", "Серебряная руда", "Silver ore", Gender.F)
            .traits(ModTraits.UNCOMMON)
            .category(Category.MINERALS)
            .lore("Нити самородного серебра в кварце — редкая удача рудокопа. Такую руду плавят сразу на серебро, без свинца.",
                    "Threads of native silver in quartz, a miner's rare luck. Such ore is smelted straight to silver, "
                            + "without lead.")
            .kg(1.5)
            .register();
    public static final DeferredItem<Item> GOLD_ORE = item("gold_ore", "Золотая руда", "Gold ore", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.MINERALS)
            .lore("Белый кварц с жилками и крапинами золота. Золото из камня не выплавляют: руду толкут в ступе, "
                            + "промывают лотком, а самые мелкие крупинки собирают ртутью.",
                    "White quartz with veins and specks of gold. Gold is not smelted from stone: the ore is pounded in a "
                            + "mortar, washed in a pan, and the finest grains are caught with quicksilver.")
            .kg(2.5)
            .register();
    public static final DeferredItem<Item> CRUSHED_GOLD_ORE = item("crushed_gold_ore", "Толчёная золотая руда",
            "Crushed gold ore", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.MINERALS)
            .lore("Кварц, истолчённый в ступе в песок. Промойте его лотком в промывочном желобе — тяжёлое золото "
                            + "останется на дне.",
                    "Quartz pounded to sand in a mortar. Wash it with a pan at a sluice: the heavy gold stays at the bottom.")
            .kg(2.5)
            .register();
    public static final DeferredItem<Item> GOLD_DUST = item("gold_dust", "Золотой песок", "Gold dust", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.MINERALS)
            .lore("Крупинки золота, намытые лотком из речного песка. Ценен сам по себе: торговцы принимают его на вес.",
                    "Grains of gold washed out of river sand with a pan. Precious in itself: traders take it by weight.")
            .grams(100)
            .register();
    public static final DeferredItem<Item> ZINC_ORE = item("zinc_ore", "Галмей", "Calamine", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.MINERALS)
            .lore("Белёсая цинковая руда. Цинк из неё не выплавляют: галмей прокаливают вместе с медью в закрытом тигле, "
                            + "и медь становится латунью.",
                    "A pale zinc ore. Zinc is not smelted from it: calamine is heated with copper in a closed crucible, "
                            + "and the copper turns to brass.")
            .kg(2)
            .register();
    public static final DeferredItem<Item> CINNABAR = item("cinnabar", "Киноварь", "Cinnabar", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.MINERALS)
            .lore("Кроваво-красный камень. При обжиге из него выходит ртуть, а растёртый в ступе в порошок он даёт "
                            + "лучшую красную краску.",
                    "A blood-red stone. Roasted, it gives up quicksilver; ground to powder in a mortar it makes the best "
                            + "red paint.")
            .kg(1)
            .register();
    public static final DeferredItem<Item> FLUX = item("flux", "Флюс", "Flux", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.MINERALS)
            .lore("Толчёный известняк. Его засыпают в горн вместе с рудой и углём, чтобы пустая порода стекала шлаком, "
                            + "а не оставалась в металле. Им же посыпают железо для кузнечной сварки.",
                    "Crushed limestone. It goes into the furnace with the ore and charcoal so the waste rock runs off as "
                            + "slag instead of staying in the metal. Smiths also sprinkle it on iron to forge-weld.")
            .kg(1)
            .register();

    // ----- Плавка -----
    public static final DeferredItem<Item> IRON_BLOOM = item("iron_bloom", "Железная крица", "Iron bloom", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Губчатый ком железа со шлаком внутри — то, что остаётся в сыродутном горне после плавки. Кузнец "
                            + "проковывает его на наковальне, выбивая шлак, — выходят железные слитки.",
                    "A spongy lump of iron with slag inside, what is left in the bloomery after smelting. The smith beats "
                            + "it on the anvil, driving the slag out, and gets iron ingots.")
            .stacksTo(16)
            .kg(3)
            .register();

    // ----- Металлы и сплавы -----
    public static final DeferredItem<Item> IRON_INGOT = item("iron_ingot", "Железный слиток", "Iron ingot", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Прокованное кричное железо, мягкое и вязкое. Из него куют почти всё железное, из него же в "
                            + "цементационном ящике получают сталь.",
                    "Beaten bloom iron, soft and tough. Nearly everything iron is forged from it, and steel is made of it "
                            + "in a cementation box.")
            .kg(1)
            .register();
    public static final DeferredItem<Item> STEEL_INGOT = item("steel_ingot", "Стальной слиток", "Steel ingot", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.METALS)
            .lore("Железная полоса, которую несколько дней томили в закрытом ящике с углём, пока она не набрала "
                            + "углерода. Держит заточку и закалку: клинки, пластины лат, бодкины.",
                    "An iron bar soaked for days in a sealed box of charcoal until it took up carbon. It holds an edge and "
                            + "a temper: blades, armour plates, bodkins.")
            .kg(1)
            .register();
    public static final DeferredItem<Item> COPPER_INGOT = item("copper_ingot", "Медный слиток", "Copper ingot", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Слиток красной меди. Из неё куют котлы и посуду, а с оловом или галмеем она становится бронзой "
                            + "или латунью.",
                    "An ingot of red copper. Kettles and vessels are forged from it, and with tin or calamine it becomes "
                            + "bronze or brass.")
            .kg(1)
            .register();
    public static final DeferredItem<Item> TIN_INGOT = item("tin_ingot", "Оловянный слиток", "Tin ingot", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.METALS)
            .lore("Мягкий белый металл. Сам по себе почти не нужен, но без него нет ни бронзы, ни посуды из пьютера, "
                            + "ни лужёных котлов.",
                    "A soft white metal. Little use on its own, but without it there is no bronze, no pewter ware and no "
                            + "tinned kettles.")
            .grams(800)
            .register();
    public static final DeferredItem<Item> LEAD_INGOT = item("lead_ingot", "Свинцовый слиток", "Lead ingot", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Тяжёлый мягкий металл: им кроют храмы, из него льют трубы, грузила и печати. После отделения "
                            + "серебра свинца остаётся много.",
                    "A heavy soft metal: temples are roofed with it, pipes, weights and seals are cast from it. Plenty of "
                            + "lead is left once the silver is parted.")
            .kg(2)
            .register();
    public static final DeferredItem<Item> ARGENTIFEROUS_LEAD = item("argentiferous_lead", "Свинец с серебром",
            "Silver-bearing lead", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Свинец, выплавленный из свинцового блеска: в нём растворено немного серебра. Серебро отделяют на "
                            + "купели — свинец выгорает в глёт, серебро остаётся.",
                    "Lead smelted from galena with a little silver dissolved in it. The silver is parted on a cupel: the "
                            + "lead burns off to litharge, the silver stays.")
            .kg(2)
            .register();
    public static final DeferredItem<Item> SILVER_INGOT = item("silver_ingot", "Серебряный слиток", "Silver ingot", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.METALS)
            .lore("Серебро, отделённое от свинца на купели: свинец выгорает в глёт, серебро остаётся блестящей каплей. "
                            + "Из слитков чеканят монету.",
                    "Silver parted from lead on a cupel: the lead burns to litharge and the silver stays as a bright bead. "
                            + "Coins are struck from such ingots.")
            .grams(500)
            .register();
    public static final DeferredItem<Item> GOLD_INGOT = item("gold_ingot", "Золотой слиток", "Gold ingot", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.METALS)
            .lore("Переплавленный золотой песок. Из слитков чеканят гульдены, тянут золотую нить и делают позолоту.",
                    "Remelted gold dust. Guldens are struck from such ingots, gold thread is drawn from them and gilding made.")
            .grams(500)
            .register();
    public static final DeferredItem<Item> BRONZE_INGOT = item("bronze_ingot", "Бронзовый слиток", "Bronze ingot", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.METALS)
            .lore("Сплав меди с оловом — твёрже меди и звонче её. Из бронзы льют колокола, ступы, котлы и застёжки.",
                    "Copper alloyed with tin, harder and more ringing than copper. Bells, mortars, cauldrons and clasps "
                            + "are cast from bronze.")
            .kg(1)
            .register();
    public static final DeferredItem<Item> BRASS_INGOT = item("brass_ingot", "Латунный слиток", "Brass ingot", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.METALS)
            .lore("Медь, прокалённая с галмеем в закрытом тигле. Золотистая и ковкая — для пряжек, оковок и украшения "
                            + "доспехов.",
                    "Copper heated with calamine in a closed crucible. Golden and malleable: for buckles, fittings and "
                            + "armour trim.")
            .kg(1)
            .register();
    public static final DeferredItem<Item> PEWTER_INGOT = item("pewter_ingot", "Пьютер", "Pewter", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Олово со свинцом — металл посуды горожан: кружки, блюда, ложки и паломнические значки.",
                    "Tin with lead, the metal of townsfolk's tableware: tankards, plates, spoons and pilgrim badges.")
            .grams(800)
            .register();
    public static final DeferredItem<Item> MERCURY = item("mercury", "Ртуть", "Quicksilver", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.METALS)
            .lore("Жидкое серебро в запаянной склянке. Ею золотят: растворяют золото, наносят на бронзу и выпаривают "
                            + "на огне. И мелкое золото с промывки собирает только она.",
                    "Liquid silver in a sealed vial. It is used for gilding: gold is dissolved in it, brushed on bronze "
                            + "and driven off over a fire. Only it catches the finest gold when washing.")
            .stacksTo(16)
            .grams(500)
            .register();

    // ----- Кузнечные части и мелочь -----
    public static final DeferredItem<Item> NAILS = item("nails", "Гвозди", "Nails", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Кованые гвозди с широкой шляпкой. Идут на верстак плотника и в сбрую.",
                    "Hand-forged nails with broad heads. They go to the carpenter's bench and into harness.")
            .grams(20)
            .register();
    public static final DeferredItem<Item> RIVETS = item("rivets", "Заклёпки", "Rivets", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Короткие железные заклёпки: ими крепят пластины бригантин, ремни лат и обкладки рукоятей.",
                    "Short iron rivets: they fasten brigandine plates, armour straps and hilt scales.")
            .grams(10)
            .register();
    public static final DeferredItem<Item> IRON_WIRE = item("iron_wire", "Железная проволока", "Iron wire", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Протянута через волочильную доску. Из неё навивают кольца кольчуги.",
                    "Drawn through a draw plate. Mail rings are coiled from it.")
            .grams(250)
            .register();
    public static final DeferredItem<Item> CHAIN = item("chain", "Цепь", "Chain", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Кованая цепь из сваренных звеньев — для кистеней, котлов и ворот.",
                    "A forged chain of welded links, for flails, kettle hooks and gates.")
            .stacksTo(16)
            .grams(500)
            .register();
    public static final DeferredItem<Item> GOLD_THREAD = item("gold_thread", "Золотая нить", "Gold thread", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.TEXTILES)
            .lore("Золото, вытянутое в тончайшую нить. Ею вышивают знать и церковь.",
                    "Gold drawn into the finest thread. The nobility and the church are embroidered with it.")
            .grams(10)
            .register();
    public static final DeferredItem<Item> BRONZE_CLASP = item("bronze_clasp", "Бронзовая застёжка", "Bronze clasp", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Литая застёжка плаща или пояса. Идёт на шитейный стол.",
                    "A cast cloak or belt clasp. Goes to the sewing table.")
            .grams(40)
            .register();
    public static final DeferredItem<Item> BRASS_BUCKLE = item("brass_buckle", "Латунная пряжка", "Brass buckle", Gender.F)
            .traits(ModTraits.UNCOMMON)
            .category(Category.METALS)
            .lore("Золотистая пряжка для ремней доспеха и пояса.", "A golden buckle for armour straps and belts.")
            .grams(40)
            .register();
    public static final DeferredItem<Item> BRASS_FITTINGS = item("brass_fittings", "Латунные оковки", "Brass fittings", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.METALS)
            .lore("Литые накладки и каймы: ими отделывают дворянские бригантины, парадные кирасы и ножны.",
                    "Cast mounts and edgings that trim noble brigandines, parade cuirasses and scabbards.")
            .grams(150)
            .register();
    public static final DeferredItem<Item> LEAD_SEAL = item("lead_seal", "Свинцовая печать", "Lead seal", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.METALS)
            .lore("Свинцовая булла на шнуре: ею скрепляют грамоты и опечатывают тюки.",
                    "A lead bulla on a cord: it seals charters and bales.")
            .grams(60)
            .register();

    // ----- Изделия из пьютера, серебра и золота -----
    // Кружка и кубок — как глиняная кружка, блюдо — как миска (WareItem), но металл не трескается
    public static final DeferredItem<WareItem> PEWTER_TANKARD = item("pewter_tankard", "Пьютеровая кружка", "Pewter tankard", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("Литая кружка с крышкой — посуда горожанина. Мера воды или питья: ПКМ по воде — набрать, ПКМ — пить; "
                            + "налить можно из кувшина, корчаги, бочки. Не бьётся, как глиняная.",
                    "A cast lidded tankard, a townsman's tableware. One measure of water or a drink: right-click water to fill, "
                            + "right-click to drink; fill it from a jug, amphora or cask. It doesn't break like clay.")
            .stacksTo(1)
            .grams(450)
            .register(properties -> new WareItem(properties, VesselForm.CUP, true));
    public static final DeferredItem<WareItem> PEWTER_PLATE = item("pewter_plate", "Пьютеровое блюдо", "Pewter plate", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("Плоское литое блюдо для стола горожанина. E по горшку с готовым — положить порцию; после еды остаётся в руке.",
                    "A flat cast plate for a townsman's table. E at a pot of cooked food to serve a portion; stays in hand after eating.")
            .stacksTo(16)
            .grams(500)
            .register(properties -> new WareItem(properties, VesselForm.BOWL, true));
    public static final DeferredItem<Item> PEWTER_SPOON = item("pewter_spoon", "Пьютеровая ложка", "Pewter spoon", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("Литая ложка с витым черенком.", "A cast spoon with a twisted handle.")
            .grams(40)
            .register();
    public static final DeferredItem<Item> PILGRIM_BADGE = item("pilgrim_badge", "Паломнический значок", "Pilgrim badge", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.JEWELRY)
            .lore("Литой оловянный значок со святыми — его привозят из паломничества и носят на шляпе.",
                    "A cast pewter badge with the saints, brought back from pilgrimage and worn on the hat.")
            .grams(15)
            .register();
    public static final DeferredItem<Item> SILVER_RING = item("silver_ring", "Серебряное кольцо", "Silver ring", Gender.N)
            .traits(ModTraits.UNCOMMON)
            .category(Category.JEWELRY)
            .lore("Кованое серебряное кольцо.", "A forged silver ring.")
            .stacksTo(16)
            .grams(8)
            .register();
    public static final DeferredItem<Item> GOLD_RING = item("gold_ring", "Золотое кольцо", "Gold ring", Gender.N)
            .traits(ModTraits.RARE)
            .category(Category.JEWELRY)
            .lore("Кованое золотое кольцо — знак достатка или обручения.", "A forged gold ring, a sign of wealth or betrothal.")
            .stacksTo(16)
            .grams(8)
            .register();
    public static final DeferredItem<WareItem> SILVER_GOBLET = item("silver_goblet", "Серебряный кубок", "Silver goblet", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.JEWELRY)
            .lore("Литой кубок на ножке — посуда знати. Мера воды или питья: ПКМ по воде — набрать, ПКМ — пить; налить можно "
                            + "из кувшина, корчаги, бочки. Не бьётся.",
                    "A cast footed goblet, tableware of the nobility. One measure of water or a drink: right-click water to fill, "
                            + "right-click to drink; fill it from a jug, amphora or cask. It doesn't break.")
            .stacksTo(1)
            .grams(350)
            .register(properties -> new WareItem(properties, VesselForm.CUP, true));

    // ----- Инструменты -----
    public static final DeferredItem<Item> TONGS = item("tongs", "Клещи", "Tongs", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Держат раскалённую заготовку у наковальни. Без клещей горячее не взять. Берутся из инвентаря.",
                    "Hold the glowing workpiece at the anvil. Without tongs you can't take hot iron. Taken from your inventory.")
            .durability(500)
            .kg(1.2)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> SMITH_HAMMER = item("smith_hammer", "Кузнечный молот", "Smith's hammer", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Ручник кузнеца: им куют на наковальне. Берётся из инвентаря.",
                    "The smith's hand hammer for work at the anvil. Taken from your inventory.")
            .durability(600)
            .kg(1.5)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> SLEDGEHAMMER = item("sledgehammer", "Кувалда", "Sledgehammer", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Молот молотобойца. Встаньте у наковальни, где куёт кузнец, и E кувалдой — бить в такт по его меткам: "
                            + "крупная работа пойдёт вдвое быстрее.",
                    "The striker's sledge. Stand by an anvil where a smith is working and press E with it to strike in "
                            + "time on his marks: heavy work goes twice as fast.")
            .durability(500)
            .kg(5)
            .draw(DrawType.HEAVY)
            .register();
    public static final DeferredItem<Item> DRAW_PLATE = item("draw_plate", "Волочильная доска", "Draw plate", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Стальная доска с рядом всё более узких отверстий: через них тянут проволоку и золотую нить. "
                            + "Берётся из инвентаря.",
                    "A steel plate with a row of ever narrower holes to draw wire and gold thread through. Taken from "
                            + "your inventory.")
            .durability(300)
            .kg(1)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> GOLD_PAN = item("gold_pan", "Лоток", "Gold pan", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Деревянный лоток старателя. E по золотоносному песку или по промывочному желобу с толчёной рудой — "
                            + "промывать: покачивайте и сливайте лёгкое, тяжёлое золото останется.",
                    "A prospector's wooden pan. E at gold-bearing sand, or at a sluice holding crushed ore, to wash: rock "
                            + "it and pour off the light stuff, the heavy gold stays.")
            .durability(200)
            .grams(800)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<SmithStampItem> SMITH_STAMP = item("smith_stamp", "Клеймо", "Maker's stamp", Gender.N)
            .traits(ModTraits.UNCOMMON)
            .category(Category.TOOLS)
            .lore("Стальной пуансон с личным знаком мастера — его выбивают, когда клеймо куют. В инвентаре ПКМ клеймом по "
                            + "металлической вещи — поставить знак: в подсказке будет видно, чья работа.",
                    "A steel punch bearing the maker's own mark, struck when the stamp is forged. In your inventory, right-click it "
                            + "onto a metal piece to mark it: the tooltip will show whose work it is.")
            .durability(200)
            .grams(250)
            .draw(DrawType.TOOL)
            .register(SmithStampItem::new);
    public static final DeferredItem<Item> COIN_DIE = item("coin_die", "Штемпель", "Coin die", Gender.M)
            .traits(ModTraits.UNIQUE)
            .category(Category.TOOLS)
            .lore("Монетный штемпель — право чеканить монету. Выдаёт его казна, а не кузнец. Берётся из инвентаря.",
                    "A coin die, the right to strike coin. It is issued by the treasury, not forged. Taken from your inventory.")
            .durability(400)
            .grams(900)
            .draw(DrawType.TOOL)
            .register();

    // ----- Станции и места добычи -----
    public static final DeferredItem<BlockItem> IRON_VEIN = vein("iron_vein", "Жила железняка", "Ironstone vein", Gender.F,
            SmithingBlocks.IRON_VEIN);
    public static final DeferredItem<BlockItem> COPPER_VEIN = vein("copper_vein", "Медная жила", "Copper vein", Gender.F,
            SmithingBlocks.COPPER_VEIN);
    public static final DeferredItem<BlockItem> TIN_VEIN = vein("tin_vein", "Оловянная жила", "Tin vein", Gender.F,
            SmithingBlocks.TIN_VEIN);
    public static final DeferredItem<BlockItem> LEAD_VEIN = vein("lead_vein", "Жила свинцового блеска", "Galena vein", Gender.F,
            SmithingBlocks.LEAD_VEIN);
    public static final DeferredItem<BlockItem> SILVER_VEIN = vein("silver_vein", "Серебряная жила", "Silver vein", Gender.F,
            SmithingBlocks.SILVER_VEIN);
    public static final DeferredItem<BlockItem> GOLD_VEIN = vein("gold_vein", "Золотоносный кварц", "Gold quartz vein", Gender.M,
            SmithingBlocks.GOLD_VEIN);
    public static final DeferredItem<BlockItem> ZINC_VEIN = vein("zinc_vein", "Жила галмея", "Calamine vein", Gender.F,
            SmithingBlocks.ZINC_VEIN);
    public static final DeferredItem<BlockItem> CINNABAR_VEIN = vein("cinnabar_vein", "Жила киновари", "Cinnabar vein", Gender.F,
            SmithingBlocks.CINNABAR_VEIN);
    public static final DeferredItem<BlockItem> LIMESTONE_VEIN = vein("limestone_vein", "Известняковый пласт", "Limestone bed", Gender.M,
            SmithingBlocks.LIMESTONE_VEIN);
    public static final DeferredItem<BlockItem> BOG_SILT = item("bog_silt", "Болотный ил", "Bog silt", Gender.M)
            .category(Category.MINERALS)
            .lore("Ил с комьями болотной руды; блок ставят строители. ЛКМ лопатой — копать руду: каждый удар тратит "
                            + "выносливость. Вычерпанный ил за несколько дней снова набирает руду.",
                    "Silt with lumps of bog ore, placed by builders. Left-click with a shovel to dig ore: each stroke costs "
                            + "stamina. A dug-out bed gathers ore again over a few days.")
            .kg(30)
            .register(properties -> new BlockItem(SmithingBlocks.BOG_SILT.get(), properties));
    public static final DeferredItem<BlockItem> GOLD_PLACER = item("gold_placer", "Золотоносный песок", "Gold-bearing sand", Gender.M)
            .category(Category.MINERALS)
            .lore("Речной песок с крупинками золота; блок ставят строители у воды. E лотком — промывать. "
                            + "Россыпь беднеет и за несколько дней намывается вновь.",
                    "River sand with grains of gold, placed by builders by the water. E with a pan to wash it. "
                            + "The placer runs poor and is washed rich again over a few days.")
            .kg(30)
            .register(properties -> new BlockItem(SmithingBlocks.GOLD_PLACER.get(), properties));
    public static final DeferredItem<BlockItem> CHARCOAL_PILE = item("charcoal_pile", "Угольная куча", "Charcoal clamp", Gender.F)
            .category(Category.TOOLS)
            .lore("Куча углежога. E дровами — сложить (до 16), E лопатой — обсыпать землёй вокруг (удары тратят выносливость), огнивом — поджечь. Тлеет сутки; "
                            + "вскроете рано — головешки, поздно — зола. ПКМ — как идёт дело.",
                    "A charcoal burner's clamp. E with firewood stacks it (up to 16), E with a shovel covers it with the earth "
                            + "around (strokes cost stamina), a flint and steel lights it. It smoulders a day: open early and you get brands, late and you get ash.")
            .kg(40)
            .register(properties -> new BlockItem(SmithingBlocks.CHARCOAL_PILE.get(), properties));
    public static final DeferredItem<BlockItem> BLOOMERY = item("bloomery", "Сыродутный горн", "Bloomery", Gender.M)
            .category(Category.TOOLS)
            .lore("Массивная глиняная шахта для плавки руды: 2×2 в основании, 3 блока высотой. E пустой рукой — окно: руда, древесный уголь, флюс и «Плавить»; "
                            + "или E рудой, углём, флюсом — заложить, огнивом — начать плавку: качайте мехи и держите жар. Выйдет крица, а медь, олово, серебро и свинец с серебром стекают расплавом в тигель (поставьте его в окно).",
                    "A massive clay shaft furnace to smelt ore: 2×2 at the base, 3 blocks high. E with an empty hand opens it (ore, charcoal, flux, \"Smelt\"), or E with ore, charcoal and flux to charge it, a flint and steel to start: "
                            + "work the bellows and hold the heat. Out comes a bloom; copper, tin, silver and silver-bearing lead run molten into a crucible (put one in the window).")
            .kg(120)
            .register(properties -> new BlockItem(SmithingBlocks.BLOOMERY.get(), properties));
    public static final DeferredItem<BlockItem> CUPEL = item("cupel", "Купель", "Cupel hearth", Gender.F)
            .category(Category.TOOLS)
            .lore("Пористая чаша из золы и костяной муки. E тиглем с жидким свинцом с серебром — слить (или слитком — положить), огнивом — разжечь: свинец "
                            + "выгорает, а серебро вспыхивает каплей — снимите вовремя.",
                    "A porous dish of ash and bone meal. E with a crucible of molten silver-bearing lead pours it in (or E with an ingot), a flint and steel fires "
                            + "it: the lead burns off and the silver flashes as a bead. Take it off in time.")
            .kg(40)
            .register(properties -> new BlockItem(SmithingBlocks.CUPEL.get(), properties));
    public static final DeferredItem<BlockItem> SMITHY_FORGE = item("smithy_forge", "Кузнечный горн", "Smithy forge", Gender.M)
            .category(Category.TOOLS)
            .lore("Горн кузнеца: топится древесным или каменным углём, E углём — подкинуть, огнивом — разжечь, "
                            + "E пустой рукой — окно, Shift + E — мехи. Нужен наковальне рядом. В окне — тигель: шихта, сплавы и плавка.",
                    "The smith's hearth, fired with charcoal or coal. E with coal feeds it, a flint and steel lights it, "
                            + "an empty hand opens it, Shift + E works the bellows. An anvil nearby needs it. In its window: the crucible, charge, alloys and melting.")
            .kg(150)
            .register(properties -> new BlockItem(SmithingBlocks.SMITHY_FORGE.get(), properties));
    public static final DeferredItem<BlockItem> SMITH_ANVIL = item("smith_anvil", "Наковальня", "Anvil", Gender.F)
            .category(Category.TOOLS)
            .lore("E — окно наковальни: выбрать изделие, выложить металл и прочее, «Ковать» — этапы работы молотом "
                            + "и клещами из инвентаря. Рядом должен гореть кузнечный горн.",
                    "E opens the anvil: pick a piece, lay out metal and the rest, \"Forge\" works through the steps with "
                            + "hammer and tongs from your inventory. A lit forge must be nearby.")
            .kg(100)
            .register(properties -> new BlockItem(SmithingBlocks.SMITH_ANVIL.get(), properties));
    public static final DeferredItem<BlockItem> SHARPENING_WHEEL = item("sharpening_wheel", "Точило", "Sharpening wheel", Gender.N)
            .category(Category.TOOLS)
            .lore("Точильный круг. Нужен наковальне для заточки. E затупившимся клинком или инструментом — подточить "
                            + "(не выше прежнего износа: дальше — только перековка).",
                    "A grinding wheel the anvil needs for sharpening. E with a dull blade or tool to touch it up (not past "
                            + "its old wear: beyond that only reforging helps).")
            .kg(35)
            .register(properties -> new BlockItem(SmithingBlocks.SHARPENING_WHEEL.get(), properties));
    public static final DeferredItem<BlockItem> CEMENTATION_BOX = item("cementation_box", "Цементационный ящик",
            "Cementation box", Gender.M)
            .category(Category.TOOLS)
            .lore("Печь в два блока с глиняным ящиком для стали. E пустой рукой — окно: железные слитки и древесный "
                            + "уголь (по два на слиток), «Запечатать» (ком глины), «Разжечь» (огниво). Уголь тлеет, железо "
                            + "набирает углерод; через полтора дня вскройте — будет сталь, передержите — хрупкая.",
                    "A two-block furnace with a clay box for steel. E with an empty hand opens it: iron ingots and charcoal "
                            + "(two per ingot), \"Seal\" (a clay ball), \"Light\" (a flint and steel). The charcoal smoulders and "
                            + "the iron takes up carbon; open it after a day and a half for steel, too long and it turns brittle.")
            .kg(30)
            .register(properties -> new BlockItem(SmithingBlocks.CEMENTATION_BOX.get(), properties));
    public static final DeferredItem<BlockItem> CASTING_TABLE = item("casting_table", "Литейный стол", "Casting table", Gender.M)
            .category(Category.TOOLS)
            .lore("Стол с формовочной землёй — она служит раз за разом. E — окно: выбрать отливку или слитки, «Лить» — "
                            + "залить форму из тигля с жидким металлом при себе (расплавьте шихту в кузнечном горне).",
                    "A table of moulding sand that serves again and again. E opens it: pick a casting or ingots, \"Cast\" pours the "
                            + "mould from a crucible of molten metal you carry (melt the charge in the forge).")
            .kg(40)
            .register(properties -> new BlockItem(SmithingBlocks.CASTING_TABLE.get(), properties));
    public static final DeferredItem<BlockItem> MORTAR = item("mortar", "Ступа", "Mortar", Gender.F)
            .category(Category.TOOLS)
            .lore("Тяжёлая бронзовая ступа с пестом. E золотой рудой, киноварью, сушёной травой или костью — положить (до 4), "
                            + "пустой рукой — толочь в ритм. Выходит толчёная руда, красная краска, травяной порошок или костная мука.",
                    "A heavy bronze mortar and pestle. E with gold ore, cinnabar, a dried herb or a bone to put it in (up to 4), "
                            + "an empty hand to pound in rhythm. You get crushed ore, red paint, herb powder or bone meal.")
            .kg(30)
            .register(properties -> new BlockItem(SmithingBlocks.MORTAR.get(), properties));
    public static final DeferredItem<BlockItem> SLUICE = item("sluice", "Промывочный желоб", "Sluice", Gender.M)
            .category(Category.TOOLS)
            .lore("Корыто с проточной водой. E толчёной рудой — засыпать (до 4), E лотком — промывать. Ртуть в "
                            + "инвентаре соберёт и мелкое золото — тратится склянка.",
                    "A trough of running water. E with crushed ore to fill it (up to 4), E with a pan to wash. Quicksilver "
                            + "in your inventory catches the fine gold too, using up a vial.")
            .kg(25)
            .register(properties -> new BlockItem(SmithingBlocks.SLUICE.get(), properties));

    static {
        // Ванильный уголь: древесный — из угольной кучи, каменный — из копей
        ModLore.lore(Items.CHARCOAL, "Уголь, выжженный в угольной куче. Только он даёт жар, нужный для плавки руды, — "
                        + "дрова для этого не годятся.",
                "Charcoal burnt in a clamp. Only it gives the heat ore smelting needs; firewood won't do.");
        ModLore.lore(Items.COAL, "Чёрный горючий камень из редких копей. Даёт жар сильнее древесного угля, но в плавку "
                        + "руды не годится: сера делает железо ломким. Его жгут в кузнях и очагах.",
                "A black burning stone from rare pits. It burns hotter than charcoal but is no good for smelting ore: "
                        + "sulphur makes the iron brittle. Smithies and hearths burn it.");
        ModWeights.kg(Items.CHARCOAL, 1);
        ModWeights.kg(Items.COAL, 1.5);
    }

    private SmithingItems() {}

    /** Загрузить класс: предметы попадают в ModItems до регистрации. */
    public static void init() {
    }

    private static DeferredItem<BlockItem> vein(String id, String ru, String en, Gender gender,
                                                java.util.function.Supplier<? extends net.minecraft.world.level.block.Block> block) {
        return item(id, ru, en, gender)
                .category(Category.MINERALS)
                .lore("Рудная жила; блок ставят строители. ЛКМ киркой — добывать: каждый удар тратит выносливость, "
                                + "попадёте по искре — удар за два. Жила беднеет и за несколько дней восстанавливается.",
                        "An ore vein, placed by builders. Left-click with a pickaxe to mine: each blow costs stamina, a "
                                + "blow on the spark counts twice. The vein runs poor and recovers over a few days.")
                .kg(50)
                .register(properties -> new BlockItem(block.get(), properties));
    }
}
