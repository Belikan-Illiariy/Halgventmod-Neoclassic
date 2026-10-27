package com.nrdhvnstd.halgventmod.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorKind;
import com.nrdhvnstd.halgventmod.armor.ArmorPieceItem;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.CloakType;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.draw.DrawType;
import com.nrdhvnstd.halgventmod.draw.ModDraw;
import com.nrdhvnstd.halgventmod.food.DrinkItem;
import com.nrdhvnstd.halgventmod.food.FoodEffects;
import com.nrdhvnstd.halgventmod.cooking.RawDishItem;
import com.nrdhvnstd.halgventmod.gear.GearItem;
import com.nrdhvnstd.halgventmod.gear.GearType;
import com.nrdhvnstd.halgventmod.gear.ModGear;
import com.nrdhvnstd.halgventmod.storage.ContainerType;
import com.nrdhvnstd.halgventmod.food.Hunger;
import com.nrdhvnstd.halgventmod.food.ModFoods;
import com.nrdhvnstd.halgventmod.brewing.AlcoholItem;
import com.nrdhvnstd.halgventmod.item.Category;
import com.nrdhvnstd.halgventmod.item.Gender;
import com.nrdhvnstd.halgventmod.item.ModLore;
import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.leather.Hide;
import com.nrdhvnstd.halgventmod.leather.HideItem;
import com.nrdhvnstd.halgventmod.pottery.GreenwareItem;
import com.nrdhvnstd.halgventmod.pottery.VesselForm;
import com.nrdhvnstd.halgventmod.pottery.WareItem;
import com.nrdhvnstd.halgventmod.weaving.FiberToolItem;
import com.nrdhvnstd.halgventmod.weight.ModWeights;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.nrdhvnstd.halgventmod.lock.KeyRingItem;
import com.nrdhvnstd.halgventmod.farming.HarvestToolItem;
import com.nrdhvnstd.halgventmod.armorial.ArmorialItem;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HalgventMod.MODID);

    // Эти поля должны стоять ВЫШЕ списка предметов
    private static final List<Definition> DEFINITIONS = new ArrayList<>();
    private static final Map<ResourceLocation, Definition> BY_ID = new HashMap<>();

    // item(id, название по-русски, по-английски, род названия)
    //     .category(Category.METALS)                 — вкладка и папка иконки, по умолчанию MISC
    //     .traits(признаки...)                       — строка [Признак] [Признак], необязательно
    //     .lore(по-русски, по-английски)             — описание серым, необязательно
    //     .lore(по-русски, по-английски, 0xRRGGBB)   — описание своим цветом
    //     .stacksTo(n)                               — размер стака 1–99, по умолчанию 64
    //     .grams(n) или .kg(n)                       — вес одной штуки
    //     .durability(n)                             — прочность инструмента, стак 1
    //     .draw(DrawType.TOOL)                       — как достаётся в руку: SMALL, TOOL, HEAVY; по умолчанию
    //                                                  блок — HEAVY, остальное — SMALL (ModDraw)
    //   Еда и питьё:
    //     .food(сытость, насыщение)                  — съедобно, в единицах шкалы голода 0–100
    //     .drink()                                   — пьётся, а не ешется
    //     .thirst(n)                                 — жажда: + утоляет, − усиливает (шкала 0–100)
    //     .shelfLife(суток)                          — срок годности, без него не портится
    //     .returns(Items.BOWL)                       — что остаётся в руке (миска, бутылка)
    //     .satiety(60) / .wellFed(120)               — эффект «Сытость» / «Улучшенное насыщение» на столько секунд
    //   Броня:
    //     .armor(ArmorSlot.HELMET)                   — вещь брони своего слота; стак 1, надевается ПКМ
    //     .armorKind(ArmorKind.PLATE)                — материал для звука, если не как у слота (ArmorKind.forSlot)
    //     .layerOrder(n)                             — свой порядок отрисовки вместо порядка слота
    //     .dyeable()                                 — красится как ткань (текстура в сером + <id>_overlay.png)
    //     .enamel()                                  — покрывается эмалью (<id>.png в цвете + серый <id>_enamel.png)
    //     .painted()                                 — покрывается росписью (<id>.png в цвете + серый <id>_painted.png)
    //                                                  можно оба: .enamel().painted() — первое по умолчанию
    //     .armorModel("hounskull_visor")             — выступающие детали (модели), необязательно
    //     .visor()                                   — подвижное забрало: клавиша V, опущенное сужает обзор; кость visor в модели детали, если она есть
    //     .visor(2, 1)                               — то же, и опущенное забрало прибавляет к защите (броня, твёрдость)
    //     .protection(2, 1)                          — защита: броня и твёрдость брони (временно, ванильные атрибуты)
    //     .narrowView()                              — только шлем без забрала: узкий вырез, обзор сужен всегда
    //     .hoodCompatible()                          — только шлем: поверх него можно надеть капюшон
    //     .cloak(CloakType.HOODED_CAPE)              — только накидка: капюшон, плащ или оба
    //   Снаряжение и сумки:
    //     .gear(GearType.BELT)                       — надеваемое снаряжение своей ячейки; стак 1, надевается ПКМ
    //     .container(ContainerType.BACKPACK, 12)     — контейнер на n ячеек (рюкзак, сумка, подсумок, колчан)
    //     .carryBonus(20)                            — только рюкзак и сумка: +кг к грузоподъёмности, пока надеты
    //   Плоская текстура брони: assets/halgventmod/textures/armor/<папка слота>/<id>.png (128×64)
    //     .register()
    // Иконка: assets/halgventmod/textures/item/<папка категории>/<id>.png
    // Модель и переводы делает runData.

    // ===================================================================
// Вставить в список предметов ModItems, рядом со своими.
// Новых импортов не нужно: всё нужное в ModItems уже импортировано.
// Иконки: textures/item/tools/key.png, padlock.png, locksmith_kit.png
// ===================================================================

    // ----- Замки и ключи -----
    public static final DeferredItem<Item> KEY = item("key", "Ключ", "Key", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Подходит только к своему замку.", "Fits only its own lock.")
            .stacksTo(16)
            .grams(30)
            .register();
    public static final DeferredItem<Item> PADLOCK = item("padlock", "Навесной замок", "Padlock", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Вешается на дверь или сундук. Отпертый замок может снять кто угодно.",
                    "Hangs on a door or a chest. Anyone can take an unlocked padlock.")
            .stacksTo(16)
            .grams(300)
            .register();
    public static final DeferredItem<Item> LOCKSMITH_KIT = item("locksmith_kit", "Набор ключника", "Locksmith's kit", Gender.M)
            .traits(ModTraits.LEGENDARY)
            .category(Category.TOOLS)
            .lore("Для администрации. E — открыть и запертое, по месту без замка — повесить навесной; "
                            + "Shift + E — врезной замок или снять любой.",
                    "For administration. E opens even locked ones, or hangs a padlock where there is none; "
                            + "Shift + E fits a mortise lock or removes any lock.")
            .stacksTo(1)
            .register();
    public static final DeferredItem<KeyRingItem> KEY_RING = item("key_ring", "Связка ключей", "Key ring", Gender.F)
            .category(Category.TOOLS)
            .lore("Shift + E со связкой по замку отпирает его, если на связке есть подходящий ключ.",
                    "Shift + E at a lock with the ring opens it if any key on it fits.")
            .stacksTo(1)
            .grams(20)
            .register(KeyRingItem::new);

    // ----- Невидимый источник звуков места (администрация) -----
    public static final DeferredItem<BlockItem> SOUND_EMITTER = item("sound_emitter", "Источник звука", "Sound emitter", Gender.M)
            .traits(ModTraits.LEGENDARY)
            .category(Category.TOOLS)
            .draw(DrawType.SMALL) // предмет-блок по умолчанию тяжёлый, а это инструмент администрации
            .lore("Невидимый источник звуков места, для администрации. С ним в руке источники видны; "
                            + "E по источнику — настроить.",
                    "An invisible source of place sounds, for administration. Hold it to see sources; "
                            + "E at a source to set it up.")
            .register(properties -> new BlockItem(ModBlocks.SOUND_EMITTER.get(), properties));

    // ----- Фермерство -----
    // ----- Гербовник: книга знамён, выдаёт администрация (/armorial give), рецепта нет -----
    public static final DeferredItem<ArmorialItem> ARMORIAL = item("armorial", "Гербовник", "Armorial", Gender.M)
            .lore("Книга знамён. ПКМ — открыть.", "A book of banners. Right-click to open.")
            .stacksTo(1)
            .register(ArmorialItem::new);

    public static final DeferredItem<HarvestToolItem> SICKLE = item("sickle", "Серп", "Sickle", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Срезает растение любой стадии роста. Недозрелое даёт лишь семена.",
                    "Cuts a crop at any stage of growth. An unripe one yields only seeds.")
            .durability(250)
            .grams(300)
            .draw(DrawType.TOOL)
            .register(HarvestToolItem::sickle);
    public static final DeferredItem<Item> WHETSTONE = item("whetstone", "Точильный камень", "Whetstone", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("На верстаке с затупленным оружием — заточить: снимает «Затуплен», но оружие теряет 5% прочности. "
                            + "Камень остаётся, понемногу стираясь.",
                    "With a dull weapon on a crafting table: sharpens it, removing «Dull», but the weapon loses 5% of its "
                            + "durability. The stone stays, slowly wearing down.")
            .durability(4)
            .grams(300)
            .register(Item::new);
    public static final DeferredItem<HarvestToolItem> SCYTHE = item("scythe", "Коса", "Scythe", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Срезает растения рядом по три поперёк взгляда. Каждый стебель тупит лезвие.",
                    "Cuts crops three in a row across your view. Every stalk dulls the blade.")
            .durability(250)
            .kg(2.5)
            .draw(DrawType.HEAVY)
            .register(HarvestToolItem::scythe);
    // Ванильные лопата и топор во всём остальном (зола, ил, куча углежога, колода для колки);
    // копка песка и глины, рубка брёвен — ToolGathering
    public static final DeferredItem<AxeItem> AXE = item("axe", "Топор", "Axe", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("ПКМ по дереву — нарубить бревно. На колоде колет брёвна на дрова.",
                    "Right-click a tree to chop a log. Splits logs into firewood on a chopping block.")
            .kg(1.5)
            .draw(DrawType.TOOL)
            .register(properties -> new AxeItem(Tiers.IRON,
                    properties.attributes(AxeItem.createAttributes(Tiers.IRON, 6.0F, -3.1F))));
    public static final DeferredItem<ShovelItem> SHOVEL = item("shovel", "Лопата", "Shovel", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("ПКМ по песку или глине — вскопать комок. Выгребает золу из костра и очага, копает болотный ил.",
                    "Right-click sand or clay to dig a lump. Shovels ash out of a campfire or hearth and digs bog silt.")
            .kg(1.5)
            .draw(DrawType.TOOL)
            .register(properties -> new ShovelItem(Tiers.IRON,
                    properties.attributes(ShovelItem.createAttributes(Tiers.IRON, 1.5F, -3.0F))));



    // ----- Снаряжение и сумки (проверочный набор концепта инвентаря; вес временный) -----
    public static final DeferredItem<Item> BACKPACK = item("backpack", "Заплечный мешок", "Backpack", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.GEAR)
            .lore("Носится на спине. Пока надет, позволяет нести больше.",
                    "Worn on the back. While worn, lets you carry more.")
            .container(ContainerType.BACKPACK, 15)
            .carryBonus(20)
            .kg(0.8)
            .register();
    public static final DeferredItem<Item> SHOULDER_BAG = item("shoulder_bag", "Сумка через плечо", "Shoulder bag", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.GEAR)
            .lore("Носится на спине или на боку.", "Worn on the back or at the side.")
            .container(ContainerType.BAG, 6)
            .carryBonus(10)
            .kg(0.5)
            .register();
    public static final DeferredItem<Item> LEATHER_BELT = item("leather_belt", "Кожаный пояс", "Leather belt", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.GEAR)
            .lore("На поясе висят фонарь, колчан и подсумки.", "A lantern, a quiver and pouches hang from the belt.")
            .gear(GearType.BELT)
            .kg(0.3)
            .register();
    public static final DeferredItem<Item> POUCH = item("pouch", "Подсумок", "Pouch", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.GEAR)
            .lore("Вешается на пояс.", "Hangs from the belt.")
            .container(ContainerType.POUCH, 6)
            .kg(0.2)
            .register();
    public static final DeferredItem<Item> SMALL_POUCH = item("small_pouch", "Малый подсумок", "Small pouch", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.GEAR)
            .lore("Вешается на пояс.", "Hangs from the belt.")
            .container(ContainerType.POUCH, 3)
            .kg(0.1)
            .register();
    public static final DeferredItem<Item> BELT_LANTERN = item("belt_lantern", "Поясной фонарь", "Belt lantern", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.GEAR)
            .lore("Вешается на пояс и светит.", "Hangs from the belt and gives light.")
            .gear(GearType.LANTERN)
            .kg(0.6)
            .register();
    public static final DeferredItem<Item> QUIVER = item("quiver", "Колчан", "Quiver", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.GEAR)
            .lore("Вешается на пояс. Держит до 20 стрел, лук и арбалет берут стрелы сначала из него.",
                    "Hangs from the belt. Holds up to 20 arrows; bows and crossbows draw from it first.")
            .container(ContainerType.QUIVER, 1)
            .kg(0.4)
            .register();
    public static final DeferredItem<Item> COPPER_RING = item("copper_ring", "Медное кольцо", "Copper ring", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.JEWELRY)
            .gear(GearType.RING)
            .grams(20)
            .register();
    public static final DeferredItem<Item> COPPER_PENDANT = item("copper_pendant", "Медная подвеска", "Copper pendant", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.JEWELRY)
            .gear(GearType.NECK)
            .grams(20)
            .register();
    public static final DeferredItem<Item> COPPER_BRACELET = item("copper_bracelet", "Медный браслет", "Copper bracelet", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.JEWELRY)
            .gear(GearType.BRACELET)
            .grams(20)
            .register();

    // ----- Доспехи -----
    // Шлемы, которые надеваются под капюшон
    public static final DeferredItem<Item> CERVELLIERE = item("cervelliere", "Цервельер", "Cervelliere", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .lore("Простая железная шапка по форме головы. Лёгкая и неприметная: прячется под капюшоном.",
                    "A plain iron skullcap shaped to the head. Light and unobtrusive, it hides under a hood.")
            .armor(ArmorSlot.HELMET)
            .dyeable()
            .hoodCompatible()
            .kg(1.2)
            .register();
    public static final DeferredItem<Item> CERVELLIERE_SCALED = item("cervelliere_scaled", "Чешуйчатый цервельер", "Scaled cervelliere", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .lore("Цервельер, набранный из железных чешуек на кожаной основе. Сидит плотнее кованого и держится ремешком под подбородком.",
                    "A cervelliere of iron scales riveted to a leather cap. It fits closer than a forged one and is held by a chin strap.")
            .armor(ArmorSlot.HELMET)
            .hoodCompatible()
            .kg(1.4)
            .register();
    public static final DeferredItem<Item> BASCINET_HALF = item("bascinet_half", "Полубацинет", "Open bascinet", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .lore("Невысокий бацинет с открытым лицом. Прикрывает темя и затылок, но не виски.",
                    "A low bascinet with an open face. It guards the crown and the back of the head, but not the temples.")
            .armor(ArmorSlot.HELMET)
            .hoodCompatible()
            .kg(1.8)
            .register();
    public static final DeferredItem<Item> BASCINET = item("bascinet", "Бацинет", "Bascinet", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .lore("Остроконечный шлем, опущенный на виски и затылок. Оставляет открытым только лицо.",
                    "A pointed helmet drawn down over the temples and the nape. Only the face is left bare.")
            .armor(ArmorSlot.HELMET)
            .hoodCompatible()
            .kg(2.2)
            .register();
    public static final DeferredItem<Item> BARBUTE = item("barbute", "Барбют", "Barbute", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .lore("Глухой шлем, закрывающий голову до шеи; лицо открыто узким вырезом. Его нередко покрывают эмалью.",
                    "A closed helmet reaching down to the neck, with a narrow opening for the face. It is often enamelled.")
            .armor(ArmorSlot.HELMET)
            .narrowView()
            .enamel()
            .hoodCompatible()
            .kg(2.4)
            .register();
    // Новые шлемы: вес временный, описаний пока нет
    public static final DeferredItem<Item> GILDED_BARBUTE = item("gilded_barbute", "Золочёный барбют", "Gilded barbute", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .narrowView()
            .hoodCompatible()
            .kg(2.4)
            .register();
    public static final DeferredItem<Item> GREAT_BASCINET = item("great_bascinet", "Большой бацинет", "Great bascinet", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .visor() // модели забрала пока нет: только обзор и клавиша V
            .kg(3.5)
            .register();
    public static final DeferredItem<Item> HUNDSGUGEL_BASCINET = item("hundsgugel_bascinet", "Хундсгугель", "Hounskull", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .armorModel("hundsgugel_bascinet_visor")
            .protection(2, 1)
            .visor(2, 1)
            .kg(3.2)
            .register();
    public static final DeferredItem<Item> SCHWEINSGUGEL_BASCINET = item("schweinsgugel_bascinet", "Швайнсгугель", "Pig-faced bascinet", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .visor() // модели забрала пока нет: только обзор и клавиша V
            .kg(3.2)
            .register();
    public static final DeferredItem<Item> KLAPPVISOR_BASCINET = item("klappvisor_bascinet", "Бацинет с клапвизором", "Klappvisor bascinet", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .visor() // модели забрала пока нет: только обзор и клавиша V
            .kg(3)
            .register();
    public static final DeferredItem<Item> ARMET = item("armet", "Армет", "Armet", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .visor() // модели забрала пока нет: только обзор и клавиша V
            .kg(3.5)
            .register();
    public static final DeferredItem<Item> INFANTRY_SALLET = item("infantry_sallet", "Пехотный салад", "Infantry sallet", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .narrowView()
            .kg(2.2)
            .register();
    public static final DeferredItem<Item> KNIGHT_SALLET = item("knight_sallet", "Рыцарский салад", "Knight's sallet", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .narrowView()
            .kg(3)
            .register();
    public static final DeferredItem<Item> LIGHT_TOPFHELM = item("light_topfhelm", "Лёгкий топфхельм", "Light pot helm", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .visor() // модели забрала пока нет: только обзор и клавиша V
            .dyeable()
            .kg(2.5)
            .register();
    public static final DeferredItem<Item> HEAVY_TOPFHELM = item("heavy_topfhelm", "Тяжёлый топфхельм", "Heavy pot helm", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .visor() // модели забрала пока нет: только обзор и клавиша V
            .dyeable()
            .kg(3.5)
            .register();
    public static final DeferredItem<Item> KUBELHELM = item("kubelhelm", "Кюбельхельм", "Great helm", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .visor() // модели забрала пока нет: только обзор и клавиша V
            .enamel()
            .painted()
            .kg(4)
            .register();
    public static final DeferredItem<Item> HALGVENT_HELMET = item("halgvent_helmet", "Хальгвентский шлем", "Halgvent helmet", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .kg(3)
            .register();
    public static final DeferredItem<Item> ALLENDORIC_HELMET = item("allendoric_helmet", "Аллендорский шлем", "Allendoric helmet", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .kg(3)
            .register();
    public static final DeferredItem<Item> WENDISH_HELMET = item("wendish_helmet", "Вендский шлем", "Wendish helmet", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .kg(2)
            .register();
    public static final DeferredItem<Item> KETTLE_HAT = item("kettle_hat", "Капеллина", "Kettle hat", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .dyeable()
            .armorModel("kettle_hat_brim")
            .kg(2)
            .register();
    public static final DeferredItem<Item> KETTLE_HAT_BEVOR = item("kettle_hat_bevor", "Капеллина с бувигером", "Kettle hat with bevor", Gender.F)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .dyeable()
            .armorModel("kettle_hat_brim")
            .kg(2.8)
            .register();

    // Койфы: вес временный, описаний пока нет
    public static final DeferredItem<Item> MAIL_COIF = item("mail_coif", "Кольчужный капюшон", "Mail coif", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.COIF)
            .kg(2)
            .register();
    public static final DeferredItem<Item> VENTAIL_COIF = item("ventail_coif", "Койф с вентайлем", "Ventail coif", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.COIF)
            .kg(2.4)
            .register();
    public static final DeferredItem<Item> VENTAIL_COIF_NASAL = item("ventail_coif_nasal", "Койф с вентайлем и наносником", "Ventail coif with nasal", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.COIF)
            .kg(2.6)
            .register();

    // Подшлемники и головные уборы: вес временный, описаний пока нет
    public static final DeferredItem<Item> CALE = item("cale", "Чепец", "Cale", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.ARMING_CAP)
            .painted()
            .grams(150)
            .register();
    public static final DeferredItem<Item> CHAPERON = item("chaperon", "Шаперон", "Chaperon", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.ARMING_CAP)
            .dyeable()
            .grams(400)
            .register();
    public static final DeferredItem<Item> HOOD = item("hood", "Капюшон", "Hood", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.ARMING_CAP)
            .dyeable()
            .grams(400)
            .register();

    // Накидки: вес временный, описаний пока нет
    public static final DeferredItem<Item> WOOLEN_CLOAK = item("woolen_cloak", "Шерстяной плащ", "Woolen cloak", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.CLOAK)
            .cloak(CloakType.CAPE)
            .dyeable()
            .kg(1.8)
            .register();
    public static final DeferredItem<Item> FINE_CLOAK = item("fine_cloak", "Добротный плащ", "Fine cloak", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.CLOAK)
            .cloak(CloakType.CAPE)
            .dyeable()
            .kg(1.5)
            .register();
    public static final DeferredItem<Item> FUR_CLOAK = item("fur_cloak", "Меховой плащ", "Fur cloak", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.CLOAK)
            .cloak(CloakType.CAPE)
            .kg(3)
            .register();
    public static final DeferredItem<Item> SIMPLE_SURCOAT = item("simple_surcoat", "Простое сюрко", "Simple surcoat", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.CLOAK)
            .cloak(CloakType.NONE)
            .dyeable()
            .kg(0.8)
            .register();
    public static final DeferredItem<Item> SURCOAT = item("surcoat", "Сюрко", "Surcoat", Gender.N)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.CLOAK)
            .cloak(CloakType.NONE)
            .dyeable()
            .kg(1)
            .register();

    // Поддоспех
    public static final DeferredItem<Item> QUILTED_AKETON = item("quilted_aketon", "Акетон", "Aketon", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .lore("Стёганая куртка, набитая паклей или хлопком. Смягчает удары и не даёт кольчуге натирать тело.",
                    "A quilted jacket stuffed with tow or cotton. It softens blows and keeps mail from chafing.")
            .armor(ArmorSlot.PADDING)
            .kg(2.5)
            .register();
    // Новый поддоспех: вес временный, описаний пока нет
    public static final DeferredItem<Item> SIMPLE_AKETON = item("simple_aketon", "Простой акетон", "Simple aketon", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.PADDING)
            .dyeable()
            .kg(2)
            .register();
    public static final DeferredItem<Item> GAMBESON = item("gambeson", "Гамбезон", "Gambeson", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.PADDING)
            .dyeable()
            .kg(3.5)
            .register();
    public static final DeferredItem<Item> PADDED_JACKET = item("padded_jacket", "Стёганая куртка", "Padded jacket", Gender.F)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.PADDING)
            .painted()
            .kg(2.5)
            .register();
    public static final DeferredItem<Item> POURPOINT = item("pourpoint", "Пурпуэн", "Pourpoint", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.PADDING)
            .dyeable()
            .kg(2)
            .register();

    // Кольчуги: вес временный, описаний пока нет
    public static final DeferredItem<Item> MAIL_VEST = item("mail_vest", "Кольчужный жилет", "Mail vest", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.MAIL)
            .kg(4)
            .register();
    public static final DeferredItem<Item> LIGHT_MAIL = item("light_mail", "Лёгкая кольчуга", "Light mail", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.MAIL)
            .kg(6)
            .register();
    public static final DeferredItem<Item> HAUBERGEON = item("haubergeon", "Хаубержон", "Haubergeon", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.MAIL)
            .kg(7)
            .register();
    public static final DeferredItem<Item> HAUBERK = item("hauberk", "Хауберк", "Hauberk", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.MAIL)
            .kg(10)
            .register();
    public static final DeferredItem<Item> SALVAR_MAIL = item("salvar_mail", "Сальварская кольчуга", "Salvar mail", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.MAIL)
            .kg(8)
            .register();
    public static final DeferredItem<Item> PLATED_MAIL = item("plated_mail", "Кольчуга с пластинами", "Plated mail", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.MAIL)
            .kg(11)
            .register();

    // Нагрудники: вес временный, описаний пока нет
    public static final DeferredItem<Item> LEATHER_BRIGANDINE = item("leather_brigandine", "Кожаная бригантина", "Leather brigandine", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.BREASTPLATE)
            .kg(5)
            .register();
    public static final DeferredItem<Item> LIGHT_BRIGANDINE = item("light_brigandine", "Лёгкая бригантина", "Light brigandine", Gender.F)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.BREASTPLATE)
            .dyeable()
            .kg(6)
            .register();
    public static final DeferredItem<Item> HEAVY_BRIGANDINE = item("heavy_brigandine", "Тяжёлая бригантина", "Heavy brigandine", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.BREASTPLATE)
            .dyeable()
            .kg(10)
            .register();
    public static final DeferredItem<Item> NOBLE_BRIGANDINE = item("noble_brigandine", "Дворянская бригантина", "Noble brigandine", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.BREASTPLATE)
            .dyeable()
            .kg(8)
            .register();
    public static final DeferredItem<Item> LAMELLAR_ARMOR = item("lamellar_armor", "Ламеллярный доспех", "Lamellar armour", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.BREASTPLATE)
            .kg(8)
            .register();
    public static final DeferredItem<Item> SIMPLE_CUIRASS = item("simple_cuirass", "Простая кираса", "Simple cuirass", Gender.F)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.BREASTPLATE)
            .kg(6)
            .register();
    public static final DeferredItem<Item> GWALDEN_CUIRASS = item("gwalden_cuirass", "Гвальденская кираса", "Gwalden cuirass", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.BREASTPLATE)
            .kg(9)
            .register();
    public static final DeferredItem<Item> MARINIKIAN_CUIRASS = item("marinikian_cuirass", "Мариникийская кираса", "Marinikian cuirass", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.BREASTPLATE)
            .kg(9)
            .register();
    public static final DeferredItem<Item> KASTENBRUST = item("kastenbrust", "Кастенбруст", "Kastenbrust", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.BREASTPLATE)
            .kg(11)
            .register();
    public static final DeferredItem<Item> PARADE_CUIRASS = item("parade_cuirass", "Парадная кираса", "Parade cuirass", Gender.F)
            .traits(ModTraits.LEGENDARY)
            .category(Category.ARMOR)
            .armor(ArmorSlot.BREASTPLATE)
            .kg(9)
            .register();

    // Перчатки и рукавицы: вес временный, описаний пока нет
    public static final DeferredItem<Item> WOOLEN_MITTENS = item("woolen_mittens", "Шерстяные варежки", "Woolen mittens", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .armorKind(ArmorKind.PADDED)
            .grams(150)
            .register();
    public static final DeferredItem<Item> RAWHIDE_MITTENS = item("rawhide_mittens", "Сыромятные рукавицы", "Rawhide mittens", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .grams(250)
            .register();
    public static final DeferredItem<Item> PADDED_MITTENS = item("padded_mittens", "Стёганые рукавицы", "Padded mittens", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .armorKind(ArmorKind.PADDED)
            .grams(300)
            .register();
    public static final DeferredItem<Item> CLOTH_GAUNTLETS = item("cloth_gauntlets", "Тканевые перчатки", "Cloth gauntlets", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .armorKind(ArmorKind.PADDED)
            .dyeable()
            .grams(200)
            .register();
    public static final DeferredItem<Item> LEATHER_GAUNTLETS = item("leather_gauntlets", "Кожаные краги", "Leather gauntlets", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .grams(400)
            .register();
    public static final DeferredItem<Item> ARCHER_GLOVES = item("archer_gloves", "Лучные перчатки", "Archer's gloves", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .grams(200)
            .register();
    public static final DeferredItem<Item> MAIL_GLOVES = item("mail_gloves", "Кольчужные перчатки", "Mail gloves", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .armorKind(ArmorKind.MAIL)
            .grams(700)
            .register();
    public static final DeferredItem<Item> MUFFLERS = item("mufflers", "Кольчужные рукавицы", "Mufflers", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .armorKind(ArmorKind.MAIL)
            .grams(600)
            .register();
    public static final DeferredItem<Item> VAMBRACES = item("vambraces", "Наручи", "Vambraces", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .armorKind(ArmorKind.PLATE)
            .grams(800)
            .register();
    public static final DeferredItem<Item> LAMELLAR_GAUNTLETS = item("lamellar_gauntlets", "Ламеллярные перчатки", "Lamellar gauntlets", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .armorKind(ArmorKind.PLATE)
            .grams(900)
            .register();
    public static final DeferredItem<Item> SPLINTED_GAUNTLETS = item("splinted_gauntlets", "Шинные перчатки", "Splinted gauntlets", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .armorKind(ArmorKind.PLATE)
            .grams(900)
            .register();
    public static final DeferredItem<Item> SIMPLE_PLATE_GAUNTLETS = item("simple_plate_gauntlets", "Простые латные перчатки", "Simple plate gauntlets", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .armorKind(ArmorKind.PLATE)
            .kg(1)
            .register();
    public static final DeferredItem<Item> GWALDEN_GAUNTLETS = item("gwalden_gauntlets", "Гвальденские латные перчатки", "Gwalden gauntlets", Gender.PL)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .armorKind(ArmorKind.PLATE)
            .kg(1.2)
            .register();
    public static final DeferredItem<Item> MARINIKIAN_GAUNTLETS = item("marinikian_gauntlets", "Мариникийские латные перчатки", "Marinikian gauntlets", Gender.PL)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .armorKind(ArmorKind.PLATE)
            .kg(1.2)
            .register();



    // ----- Еда и питье -----
    public static final DeferredItem<Item> WHEAT_BREAD = item("wheat_bread", "Хлеб", "Bread", Gender.M)
            .traits(ModTraits.COMMON)
            .lore("Белый хлеб из пшеницы, подаваемый на стол к аристократам. Если много есть в детстве, то станешь пидаром.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(4)
            .category(Category.FOOD)
            .grams(500)
            .food(15, 7.5F)
            .shelfLife(3)
            .register();
    public static final DeferredItem<Item> RYE_BREAD = item("rye_bread", "Чёрный хлеб", "Black bread", Gender.M)
            .traits(ModTraits.COMMON)
            .lore("Крестьянский черный хлеб для настоящих мужиков.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(4)
            .category(Category.FOOD)
            .grams(500)
            .food(15, 7.5F)
            .shelfLife(3)
            .register();

    public static final DeferredItem<Item> SALO = item("salo", "Сало", "Salo", Gender.F)
            .traits(ModTraits.COMMON)
            .lore("Смачнейший кусок свиного жира. Отлично заходит с водочкой.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(4)
            .category(Category.FOOD)
            .grams(300)
            .food(20, 10.0F)
            .shelfLife(2)
            .register();

    public static final DeferredItem<Item> CHEESE = item("cheese", "Сыр", "Cheese", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .lore("Монастырский сыр.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(4)
            .category(Category.FOOD)
            .kg(1)
            .food(40, 20.0F)
            .shelfLife(32)
            .register();

    public static final DeferredItem<Item> BIRCH_BOLETE = item("birch_bolete", "Подберезовик", "Birch bolete", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .lore("Ебанный подберезовик.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(24)
            .category(Category.FOOD)
            .grams(100)
            .food(10, 5.0F)
            .shelfLife(2)
            .register();
    public static final DeferredItem<Item> CHANTERELLE = item("chanterelle", "Лисичка", "Chanterelle", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .lore("Ебанная лисичка.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(24)
            .category(Category.FOOD)
            .grams(100)
            .food(10, 5.0F)
            .shelfLife(2)
            .register();




    // ----- Еда по таблице баланса: сытость, жажда, срок, вес, эффекты — оттуда; готовка — горшок, очаг, стол, печь -----
    // Ванильные свинина, говядина, курица, зайчатина (кролик), баранина, лосось, картофель, морковь, свёкла, яблоко —
    // баланс в food/ModFoods, вес в weight/ModWeights, названия в ModLanguageProvider.
    // Мясо: оленина и гусятина — охотой и торговлей; на углях очага или костра — жареное
    public static final DeferredItem<Item> VENISON = item("venison", "Оленина", "Venison", Gender.F)
            .traits(ModTraits.COMMON, ModTraits.RAW).category(Category.FOOD)
            .lore("Добыча охотника. Сырой есть опасно: на углях — жареная оленина.", "A hunter's prize. Risky raw: grill it on the coals.")
            .stacksTo(8).grams(500).food(15, 7.5F).shelfLife(2).register();
    public static final DeferredItem<Item> GOOSE = item("goose", "Гусятина", "Goose", Gender.F)
            .traits(ModTraits.COMMON, ModTraits.RAW).category(Category.FOOD)
            .lore("Жирная птица. Сырой есть опасно: на углях — жареный гусь.", "A fat bird. Risky raw: grill it on the coals.")
            .stacksTo(8).grams(500).food(10, 5.0F).shelfLife(2).register();
    public static final DeferredItem<Item> COOKED_VENISON = item("cooked_venison", "Жареная оленина", "Cooked venison", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Сытно, но солоно — хочется пить.", "Filling, but salty — makes you thirsty.")
            .stacksTo(8).grams(400).food(30, 15.0F).thirst(-15).shelfLife(3).satiety(60).register();
    public static final DeferredItem<Item> COOKED_GOOSE = item("cooked_goose", "Жареный гусь", "Cooked goose", Gender.M)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Румяная гусятина с углей.", "Golden goose from the coals.")
            .stacksTo(8).grams(400).food(25, 12.5F).thirst(-10).shelfLife(3).satiety(60).register();
    // Рыба: лещ и окунь ловятся везде, сельдь и лосось — только в океане
    public static final DeferredItem<Item> HERRING = item("herring", "Сельдь", "Herring", Gender.F)
            .traits(ModTraits.COMMON, ModTraits.RAW).category(Category.FOOD)
            .lore("Морская рыба. На углях — жареная, в засолочной бочке — солёная.", "A sea fish. Grilled on the coals or salted in a brine barrel.")
            .stacksTo(16).grams(300).food(5, 2.5F).shelfLife(1).register();
    public static final DeferredItem<Item> BREAM = item("bream", "Лещ", "Bream", Gender.M)
            .traits(ModTraits.COMMON, ModTraits.RAW).category(Category.FOOD)
            .lore("Речная рыба. На углях — жареная рыба.", "A river fish. Grilled on the coals.")
            .stacksTo(8).grams(800).food(10, 5.0F).shelfLife(1).register();
    public static final DeferredItem<Item> PERCH = item("perch", "Окунь", "Perch", Gender.M)
            .traits(ModTraits.COMMON, ModTraits.RAW).category(Category.FOOD)
            .lore("Полосатая речная рыба. На углях — жареная рыба.", "A striped river fish. Grilled on the coals.")
            .stacksTo(16).grams(300).food(5, 2.5F).shelfLife(1).register();
    public static final DeferredItem<Item> COOKED_HERRING = item("cooked_herring", "Жареная сельдь", "Cooked herring", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Жирная, солоноватая.", "Oily and a little salty.")
            .stacksTo(16).grams(250).food(15, 7.5F).thirst(-10).shelfLife(2).satiety(60).register();
    public static final DeferredItem<Item> FRIED_FISH = item("fried_fish", "Жареная рыба", "Fried fish", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Лещ или окунь с углей.", "Bream or perch off the coals.")
            .stacksTo(16).grams(250).food(15, 7.5F).thirst(-10).shelfLife(2).satiety(60).register();
    public static final DeferredItem<Item> DRIED_FISH = item("dried_fish", "Вяленая рыба", "Dried fish", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Солёная рыба, провяленная на крюке. Не портится, но очень хочется пить.",
                    "Salted fish dried on a hook. Never spoils, but makes you very thirsty.")
            .stacksTo(16).grams(200).food(15, 7.5F).thirst(-15).register();
    // Овощи и приправы — торговля
    public static final DeferredItem<ItemNameBlockItem> ONION = item("onion", "Лук", "Onion", Gender.M)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("В горшок — к картофелю и мясу. ПКМ по пашне — посадить.", "Into the pot with potatoes and meat. Right-click farmland to plant.")
            .grams(100).food(3, 1.5F).register(properties -> new ItemNameBlockItem(ModBlocks.ONION_CROP.get(), properties));
    public static final DeferredItem<ItemNameBlockItem> TURNIP = item("turnip", "Репа", "Turnip", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Крестьянский овощ. В горшке с другими овощами — овощная похлёбка. ПКМ по пашне — посадить.", "A peasant's vegetable. With other vegetables in a pot — a vegetable stew. Right-click farmland to plant.")
            .grams(300).food(5, 2.5F).register(properties -> new ItemNameBlockItem(ModBlocks.TURNIP_CROP.get(), properties));
    public static final DeferredItem<ItemNameBlockItem> BEANS = item("beans", "Фасоль", "Beans", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Сырой не едят — только в горшок. ПКМ по пашне — посадить.", "Not eaten raw — only in a pot. Right-click farmland to plant.")
            .grams(200).register(properties -> new ItemNameBlockItem(ModBlocks.BEANS_CROP.get(), properties));
    public static final DeferredItem<ItemNameBlockItem> CABBAGE = item("cabbage", "Капуста", "Cabbage", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Сочный кочан: немного утоляет и жажду. ПКМ по пашне — посадить.", "A juicy head: eases thirst a little too. Right-click farmland to plant.")
            .stacksTo(16).kg(1).food(5, 2.5F).thirst(5).register(properties -> new ItemNameBlockItem(ModBlocks.CABBAGE_CROP.get(), properties));
    public static final DeferredItem<ItemNameBlockItem> GARLIC = item("garlic", "Чеснок", "Garlic", Gender.M)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Приправа: в горшок и к баранине на кухонном столе. ПКМ по пашне — посадить зубчик.", "A seasoning: for the pot and for mutton at a kitchen table. Right-click farmland to plant a clove.")
            .grams(50).register(properties -> new ItemNameBlockItem(ModBlocks.GARLIC_CROP.get(), properties));
    public static final DeferredItem<Item> SPICES = item("spices", "Пряности", "Spices", Gender.PL)
            .traits(ModTraits.UNCOMMON).category(Category.FOOD)
            .lore("Заморские пряности. С бараниной и чесноком — изысканное блюдо.", "Spices from overseas. With mutton and garlic — an exquisite dish.")
            .grams(50).register();
    // Зерно: ячмень и овёс — каши
    public static final DeferredItem<ItemNameBlockItem> BARLEY = item("barley", "Ячмень", "Barley", Gender.M)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Зерно. В горшке — ячменная каша. ПКМ по пашне — посеять; дикий попадается в траве.", "Grain. In a pot — barley porridge. Right-click farmland to sow; wild barley turns up in grass.")
            .grams(50).register(properties -> new ItemNameBlockItem(ModBlocks.BARLEY_CROP.get(), properties));
    public static final DeferredItem<ItemNameBlockItem> OATS = item("oats", "Овёс", "Oats", Gender.M)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Зерно. В горшке — овсяная каша. ПКМ по пашне — посеять; дикий попадается в траве.", "Grain. In a pot — oat porridge. Right-click farmland to sow; wild oats turn up in grass.")
            .grams(50).register(properties -> new ItemNameBlockItem(ModBlocks.OATS_CROP.get(), properties));
    // Блюда из горшка (сытость и жажда порции — отсюда, Cookery)
    public static final DeferredItem<Item> POTATO_STEW = item("potato_stew", "Картофельная похлёбка", "Potato stew", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Картофель, сваренный в горшке; с луком и солью — вкуснее.", "Potatoes boiled in a pot; tastier with onion and salt.")
            .stacksTo(4).grams(600).food(25, 12.5F).thirst(15).returns(Items.BOWL).shelfLife(2).wellFed(120).register();
    public static final DeferredItem<Item> WHEAT_PORRIDGE = item("wheat_porridge", "Пшеничная каша", "Wheat porridge", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Пшеница, сваренная в горшке.", "Wheat boiled in a pot.")
            .stacksTo(4).grams(500).food(25, 12.5F).thirst(5).returns(Items.BOWL).shelfLife(2).satiety(60).register();
    public static final DeferredItem<Item> RYE_PORRIDGE = item("rye_porridge", "Ржаная каша", "Rye porridge", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Рожь, сваренная в горшке.", "Rye boiled in a pot.")
            .stacksTo(4).grams(500).food(20, 10.0F).thirst(5).returns(Items.BOWL).shelfLife(2).satiety(60).register();
    public static final DeferredItem<Item> BARLEY_PORRIDGE = item("barley_porridge", "Ячменная каша", "Barley porridge", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Ячмень, сваренный в горшке.", "Barley boiled in a pot.")
            .stacksTo(4).grams(500).food(20, 10.0F).thirst(5).returns(Items.BOWL).shelfLife(2).satiety(60).register();
    public static final DeferredItem<Item> OAT_PORRIDGE = item("oat_porridge", "Овсяная каша", "Oat porridge", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Овёс, сваренный в горшке.", "Oats boiled in a pot.")
            .stacksTo(4).grams(500).food(20, 10.0F).thirst(5).returns(Items.BOWL).shelfLife(2).satiety(60).register();
    // Блюда из печи: заготовка с кухонного стола (что — компонент pie_filling), в печи — готовое
    public static final DeferredItem<RawDishItem> RAW_DISH = item("raw_dish", "Заготовка", "Raw dish", Gender.F)
            .traits(ModTraits.COMMON, ModTraits.RAW).category(Category.FOOD)
            .lore("Собрана на кухонном столе. E по растопленной печи — посадить.", "Put together at a kitchen table. E at a fired oven to bake it.")
            .stacksTo(4).grams(600).shelfLife(2).register(RawDishItem::new);
    public static final DeferredItem<Item> SALMON_WITH_HERBS = item("salmon_with_herbs", "Лосось с травами", "Salmon with herbs", Gender.M)
            .traits(ModTraits.UNCOMMON).category(Category.FOOD)
            .lore("Запечён в печи с пряными травами.", "Baked in an oven with savoury herbs.")
            .stacksTo(4).grams(600).food(35, 17.5F).thirst(-5).shelfLife(2).wellFed(120).register();
    public static final DeferredItem<Item> HARE_WITH_HERBS = item("hare_with_herbs", "Зайчатина с травами", "Hare with herbs", Gender.F)
            .traits(ModTraits.UNCOMMON).category(Category.FOOD)
            .lore("Заяц, запечённый с травами.", "Hare baked with herbs.")
            .stacksTo(4).grams(500).food(30, 15.0F).thirst(-10).shelfLife(2).wellFed(120).register();
    public static final DeferredItem<Item> EXQUISITE_MUTTON = item("exquisite_mutton", "Изысканная баранина", "Exquisite mutton", Gender.F)
            .traits(ModTraits.RARE).category(Category.FOOD)
            .lore("Баранина с заморскими пряностями и чесноком. Блюдо для знатного стола.",
                    "Mutton with spices from overseas and garlic. A dish for a noble table.")
            .stacksTo(4).grams(600).food(40, 20.0F).thirst(-10).shelfLife(2).wellFed(120).register();
    // Ягоды и фрукты — сбор и торговля
    public static final DeferredItem<Item> BLUEBERRY = item("blueberry", "Черника", "Blueberry", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Лесная ягода. Быстро мнётся и портится.", "A forest berry. Bruises and spoils quickly.")
            .grams(100).food(3, 1.5F).thirst(2).shelfLife(1).register();
    public static final DeferredItem<Item> LINGONBERRY = item("lingonberry", "Брусника", "Lingonberry", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Кисловатая лесная ягода.", "A tart forest berry.")
            .grams(100).food(2, 1.0F).thirst(2).shelfLife(2).register();
    public static final DeferredItem<Item> CRANBERRY = item("cranberry", "Клюква", "Cranberry", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Болотная ягода, лежит дольше других.", "A bog berry; keeps longer than others.")
            .grams(100).food(2, 1.0F).thirst(3).shelfLife(3).register();
    public static final DeferredItem<Item> CHERRY = item("cherry", "Вишня", "Cherry", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Садовая ягода.", "An orchard berry.")
            .grams(100).food(4, 2.0F).thirst(3).shelfLife(1).register();
    public static final DeferredItem<Item> PLUM = item("plum", "Слива", "Plum", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Садовый плод.", "An orchard fruit.")
            .grams(150).food(5, 2.5F).thirst(3).shelfLife(2).register();
    public static final DeferredItem<Item> PEAR = item("pear", "Груша", "Pear", Gender.F)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Сочный садовый плод.", "A juicy orchard fruit.")
            .grams(200).food(6, 3.0F).thirst(4).shelfLife(2).register();
    public static final DeferredItem<Item> GRAPE = item("grape", "Виноград", "Grape", Gender.M)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("С виноградников Розенмайра. На давильне — ягодный сок для вина.", "From the vineyards of Rosenmeier. A press makes berry juice for wine.")
            .grams(300).food(4, 2.0F).thirst(4).shelfLife(2).register();
    public static final DeferredItem<Item> ORANGE = item("orange", "Апельсин", "Orange", Gender.M)
            .traits(ModTraits.RARE).category(Category.FOOD)
            .lore("Заморский плод из Лебенскапа.", "An overseas fruit from Lebenskap.")
            .grams(200).food(6, 3.0F).thirst(6).shelfLife(3).register();
    public static final DeferredItem<Item> OLIVES = item("olives", "Оливки", "Olives", Gender.PL)
            .traits(ModTraits.UNCOMMON).category(Category.FOOD)
            .lore("Солёные оливки с юга. Не портятся.", "Salted olives from the south. Never spoil.")
            .grams(200).food(5, 2.5F).thirst(-3).register();
    // Кусты и плодовая листва — для строителей: ставятся в мир, спелые плоды собирают на E (farming/*)
    public static final DeferredItem<BlockItem> BLUEBERRY_BUSH = plant("blueberry_bush", "Куст черники", "Blueberry bush", Gender.M, ModBlocks.BLUEBERRY_BUSH);
    public static final DeferredItem<BlockItem> LINGONBERRY_BUSH = plant("lingonberry_bush", "Куст брусники", "Lingonberry bush", Gender.M, ModBlocks.LINGONBERRY_BUSH);
    public static final DeferredItem<BlockItem> CRANBERRY_BUSH = plant("cranberry_bush", "Куст клюквы", "Cranberry bush", Gender.M, ModBlocks.CRANBERRY_BUSH);
    public static final DeferredItem<BlockItem> CHERRY_LEAVES = plant("cherry_leaves", "Вишнёвая листва", "Cherry leaves", Gender.F, ModBlocks.CHERRY_LEAVES);
    public static final DeferredItem<BlockItem> PLUM_LEAVES = plant("plum_leaves", "Сливовая листва", "Plum leaves", Gender.F, ModBlocks.PLUM_LEAVES);
    public static final DeferredItem<BlockItem> PEAR_LEAVES = plant("pear_leaves", "Грушевая листва", "Pear leaves", Gender.F, ModBlocks.PEAR_LEAVES);
    public static final DeferredItem<BlockItem> ORANGE_LEAVES = plant("orange_leaves", "Апельсиновая листва", "Orange leaves", Gender.F, ModBlocks.ORANGE_LEAVES);
    public static final DeferredItem<BlockItem> OLIVE_LEAVES = plant("olive_leaves", "Оливковая листва", "Olive leaves", Gender.F, ModBlocks.OLIVE_LEAVES);
    public static final DeferredItem<BlockItem> GRAPE_VINE = plant("grape_vine", "Виноградная лоза", "Grape vine", Gender.F, ModBlocks.GRAPE_VINE);
    public static final DeferredItem<BlockItem> APPLE_LEAVES = plant("apple_leaves", "Яблоневая листва", "Apple leaves", Gender.F, ModBlocks.APPLE_LEAVES);

    // Яйцо призыва гуся — для администрации и строителей
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> GOOSE_SPAWN_EGG =
            item("goose_spawn_egg", "Яйцо призыва гуся", "Goose spawn egg", Gender.N)
                    .category(Category.MISC)
                    .lore("Для администрации: призывает гуся.", "For administration: summons a goose.")
                    .register(properties -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.GOOSE,
                            0xE8E4DC, 0xE07820, properties));

    private static <B extends net.minecraft.world.level.block.Block> DeferredItem<BlockItem> plant(String id, String ru, String en,
                                                                                                   Gender gender, net.neoforged.neoforge.registries.DeferredBlock<B> block) {
        return item(id, ru, en, gender)
                .category(Category.MATERIALS)
                .lore("Для строителей. Плоды зреют сами (кроме зимы); спелые — E пустой рукой, собрать.",
                        "For builders. The fruit ripens by itself (except in winter); E with an empty hand picks it when ripe.")
                .grams(500)
                .draw(DrawType.SMALL)
                .register(properties -> new BlockItem(block.get(), properties));
    }

    public static final DeferredItem<Item> BERRY_JUICE = item("berry_juice", "Ягодный морс", "Berry juice", Gender.M)
            .traits(ModTraits.COMMON).category(Category.FOOD)
            .lore("Ягоды с водой и склянка — на кухонном столе. Отлично утоляет жажду.",
                    "Berries, water and a bottle — at a kitchen table. Quenches thirst very well.")
            .stacksTo(8).grams(600).drink().food(3, 1.5F).thirst(30).returns(Items.GLASS_BOTTLE).shelfLife(1)
            .register();

    // ----- Материалы -----
    public static final DeferredItem<Item> BEAR_HIDE = item("bear_hide", "Шкура", "Hide", Gender.F)
            .traits(ModTraits.UNCOMMON, ModTraits.RAW)
            .lore("Большая шкура короля лесов. Лучше использовать ее как ковер: кошелек из медведя будет расточительным.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(4)
            .category(Category.MATERIALS)
            .kg(14)
            .register();



    // ----- Кожевенное дело (leather/*; станции — в ModBlocks) -----
    // Цепочка: парная шкура → (соль) солёная → колода: мездра → чан с золой: золёная → колода, струг: голье →
    // чан (кора / квасцы) или жир для меха → мокрая кожа → рама → кожа → шитейный стол → вещь.
    // Шкура и качество идут компонентами halgventmod:hide и halgventmod:leather_quality (LeatherQuality).
    public static final DeferredItem<HideItem> RAW_HIDE = item("raw_hide", "Парная шкура", "Raw hide", Gender.F)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.MATERIALS)
            .lore("Только что снята со зверя. Через двое суток сгниёт: посолите её или сразу снимите мездру на колоде.",
                    "Freshly taken from the beast. It rots in two days: salt it or flesh it on the beam at once.")
            .stacksTo(4)
            .kg(4)
            .shelfLife(2)
            .register(properties -> new HideItem(properties, ""));
    public static final DeferredItem<HideItem> SALTED_HIDE = item("salted_hide", "Солёная шкура", "Salted hide", Gender.F)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.MATERIALS)
            .lore("Пересыпана солью и пролежит полмесяца. Дальше — колода и чан.",
                    "Packed with salt, it keeps for half a month. Next: the beam and the vat.")
            .stacksTo(4)
            .kg(3.5)
            .shelfLife(16)
            .register(properties -> new HideItem(properties, ".lower"));
    public static final DeferredItem<Item> LIMED_HIDE = item("limed_hide", "Золёная шкура", "Limed hide", Gender.F)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.MATERIALS)
            .lore("Вымочена в зольном щёлоке: волос лезет сам. Снимите его стругом на колоде.",
                    "Soaked in ash lye, the hair is slipping. Scrape it off on the beam.")
            .stacksTo(4)
            .kg(3)
            .shelfLife(4)
            .register();
    public static final DeferredItem<Item> PELT = item("pelt", "Голье", "Pelt", Gender.N)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.MATERIALS)
            .lore("Чистая шкура без волоса и мездры. В корьевой чан — дублёная кожа, в квасцовый — мягкая, "
                            + "на раму — сыромять.",
                    "A clean hide without hair or flesh. Bark vat for tanned leather, alum vat for soft, "
                            + "the rack for rawhide.")
            .stacksTo(4)
            .kg(2.5)
            .shelfLife(4)
            .register();
    public static final DeferredItem<Item> WET_LEATHER = item("wet_leather", "Мокрая кожа", "Wet leather", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Только из чана. Натяните на раму и сушите, пока не испортилась.",
                    "Fresh from the vat. Stretch it on the rack and dry it before it spoils.")
            .stacksTo(4)
            .kg(2.5)
            .shelfLife(4)
            .register();
    public static final DeferredItem<Item> TANNED_LEATHER = item("tanned_leather", "Дублёная кожа", "Tanned leather", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Плотная кожа корьевого дубления: пояса, сумки, колчаны. Сваренная в кипятке — варёная кожа.",
                    "Firm bark-tanned leather: belts, bags, quivers. Boiled in water it becomes hardened leather.")
            .stacksTo(16)
            .grams(600)
            .register();
    public static final DeferredItem<Item> SOFT_LEATHER = item("soft_leather", "Мягкая кожа", "Soft leather", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Замша квасцового дубления: тонкая и тянется. На перчатки и подкладку.",
                    "Alum-tawed suede, thin and supple. For gloves and linings.")
            .stacksTo(16)
            .grams(400)
            .register();
    public static final DeferredItem<Item> RAWHIDE = item("rawhide", "Сыромять", "Rawhide", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Высушенное без дубления голье: жёсткое, но боится воды. Из мелкой шкуры стругом выскабливают пергамент.",
                    "Pelt dried without tanning: stiff, but afraid of water. A small one scraped down gives parchment.")
            .stacksTo(16)
            .grams(500)
            .register();
    public static final DeferredItem<HideItem> DRESSED_FUR = item("dressed_fur", "Выделанный мех", "Dressed fur", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Мех, выделанный на жиру: мягкий и тёплый. На плащи и подбой.",
                    "Fur dressed with fat: soft and warm. For cloaks and lining.")
            .stacksTo(16)
            .grams(700)
            .register(properties -> new HideItem(properties, ".of"));
    public static final DeferredItem<Item> BOILED_LEATHER = item("boiled_leather", "Варёная кожа", "Hardened leather", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Дублёная кожа, сваренная в кипятке: твёрдая, как рог. На краги и бригантину.",
                    "Tanned leather boiled until hard as horn. For gauntlets and brigandines.")
            .stacksTo(16)
            .grams(700)
            .register();
    public static final DeferredItem<Item> PARCHMENT = item("parchment", "Пергамент", "Parchment", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.MATERIALS)
            .lore("Тонко выскобленная сыромять мелкой шкуры. На письма, книги и лекала.",
                    "Finely scraped rawhide of a small beast. For letters, books and patterns.")
            .stacksTo(16)
            .grams(50)
            .register();
    public static final DeferredItem<Item> LEATHER_SCRAPS = item("leather_scraps", "Обрезки кожи", "Leather scraps", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Остаются после раскроя. Из них кроят ремешки.", "Left after cutting. Straps are cut from them.")
            .grams(50)
            .register();
    public static final DeferredItem<Item> LEATHER_STRAP = item("leather_strap", "Кожаный ремешок", "Leather strap", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Ремень, завязка, петля. Нужен сумкам, колчанам и доспеху.",
                    "A strap, a tie, a loop. Bags, quivers and armour need them.")
            .grams(40)
            .register();
    public static final DeferredItem<Item> FLESH = item("flesh", "Мездра", "Flesh scrapings", Gender.F)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.MATERIALS)
            .lore("Жир и плёнки с изнанки шкуры. Вываренная в чане, даёт клей.",
                    "Fat and membrane from the inside of a hide. Boiled in a vat, it gives glue.")
            .stacksTo(16)
            .grams(200)
            .shelfLife(2)
            .register();
    public static final DeferredItem<Item> HIDE_GLUE = item("hide_glue", "Мездровый клей", "Hide glue", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Склеивает слои варёной кожи. Нужен крагам и бригантине.",
                    "Binds layers of hardened leather. Gauntlets and brigandines need it.")
            .stacksTo(16)
            .grams(100)
            .register();
    public static final DeferredItem<Item> SALT = item("salt", "Соль", "Salt", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MINERALS)
            .lore("Выпаривается из воды в чане над огнём. Сохраняет шкуры и идёт в квасцовый раствор.",
                    "Boiled out of water in a vat over a fire. Keeps hides and goes into the alum bath.")
            .grams(100)
            .register();
    public static final DeferredItem<Item> ASH = item("ash", "Зола", "Ash", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MINERALS)
            .lore("ПКМ лопатой по горящему костру. Три горсти в чан с водой — щёлок для золки шкур.",
                    "Right-click a burning campfire with a shovel. Three handfuls in a water vat make lye for liming.")
            .grams(100)
            .register();
    public static final DeferredItem<Item> SAND_LUMP = item("sand_lump", "Комок песка", "Lump of sand", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.MINERALS)
            .lore("ПКМ лопатой по песку. Идёт в стекольную шихту и отощителем в бочку с глиной — как песок.",
                    "Right-click sand with a shovel. Goes into a glass batch and into a clay barrel as temper, like sand.")
            .grams(250)
            .register();
    public static final DeferredItem<Item> BARK =item("bark", "Кора", "Bark", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Отходит, когда колют неокорённое бревно на колоде. Четыре куска в чан с водой — корьевой раствор для дубления.",
                    "Comes off when an unbarked log is split on a chopping block. Four pieces in a water vat make the tanning liquor.")
            .grams(200)
            .register();
    public static final DeferredItem<Item> ALUM = item("alum", "Квасцы", "Alum", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.MINERALS)
            .lore("Белые кристаллы из кальцита. Две горсти и соль в чан с водой — квасцовый раствор для мягкой кожи.",
                    "White crystals found in calcite. Two handfuls and salt in a water vat make the alum bath.")
            .grams(100)
            .register();
    public static final DeferredItem<Item> THREADS = item("threads", "Нитки", "Thread", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Для шитья на шитейном столе. Подойдёт и простая нить; из мотка льняной пряжи — две пачки.",
                    "For sewing at the sewing table. Plain string will do; a skein of linen yarn makes two.")
            .grams(30)
            .register();

    // Инструменты кожевника: работают по станциям на E (использование блока с предметом в руке), тупятся от работы
    public static final DeferredItem<Item> FLESHING_KNIFE = item("fleshing_knife", "Мездряк", "Fleshing knife", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("E по клочкам мездры на шкуре (колода) — счищать их слой за слоем. Цельтесь в клочки и бейте ровно, "
                            + "дожидаясь замаха: по чистому месту и в спешке нож режет шкуру.",
                    "E at the flesh patches of a hide on the beam scrapes them layer by layer. Aim at the patches and strike "
                            + "evenly, waiting for the swing: a clean spot or haste cuts the hide.")
            .durability(300)
            .grams(400)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> SCRAPER = item("scraper", "Струг", "Scraper", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("E по золёной шкуре на колоде — снять волос; по сыромяти мелкой шкуры — выскоблить пергамент.",
                    "E at a limed hide on the beam scrapes the hair; at small rawhide it scrapes parchment.")
            .durability(300)
            .grams(350)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> LEATHER_MALLET = item("leather_mallet", "Колотушка", "Leather mallet", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("E по высохшей коже на раме — размять: она станет мягче и лучше.",
                    "E at dried leather on the rack softens it, making it better.")
            .durability(400)
            .grams(600)
            .draw(DrawType.TOOL)
            .register();
    // Простой нож. Кроить на шитейном столе можно и охотничьими ножами Conquest (тег halgventmod:knives)
    public static final DeferredItem<Item> KNIFE = item("knife", "Нож", "Knife", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Кроит кожу на шитейном столе: «Раскроить» в окне стола. Нож берётся из инвентаря.",
                    "Cuts leather at the sewing table: \"Cut\" in the table window. Taken from your inventory.")
            .durability(300)
            .grams(200)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> NEEDLE = item("needle", "Игла", "Needle", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Шьёт на шитейном столе: «Шить» в окне стола, прокалывайте, когда игла над отметкой. "
                            + "Игла и нитки берутся из инвентаря.",
                    "Sews at the sewing table: \"Sew\" in the table window, pierce when the needle is over a mark. "
                            + "The needle and thread are taken from your inventory.")
            .durability(250)
            .grams(10)
            .register();

    // Станции кожевника (блоки — ModBlocks, модели — из Conquest Reforged)
    public static final DeferredItem<BlockItem> FLESHING_BEAM = item("fleshing_beam", "Кожевенная колода", "Fleshing beam", Gender.F)
            .category(Category.TOOLS)
            .lore("E со шкурой в руке — положить; E мездряком, стругом или солью — работать.",
                    "E with a hide in hand lays it down; E with the fleshing knife, scraper or salt works it.")
            .kg(30)
            .register(properties -> new BlockItem(ModBlocks.FLESHING_BEAM.get(), properties));
    public static final DeferredItem<BlockItem> TANNING_VAT = item("tanning_vat", "Чан", "Vat", Gender.M)
            .category(Category.TOOLS)
            .lore("E ведром воды — налить; E золой, корой, квасцами с солью или салом — раствор; E шкурой — опустить. Над огнём варит. "
                            + "У наковальни служит закалочной бочкой: вода, рассол (вода с солью) или жир.",
                    "E with a water bucket fills it; E with ash, bark, alum and salt or fat makes the bath; E with a hide sinks it. Over a fire it boils. "
                            + "By an anvil it serves as a quench tub: water, brine (water with salt) or fat.")
            .kg(20)
            .register(properties -> new BlockItem(ModBlocks.TANNING_VAT.get(), properties));
    public static final DeferredItem<BlockItem> DRYING_RACK = item("drying_rack", "Рама для сушки", "Drying rack", Gender.F)
            .category(Category.TOOLS)
            .lore("E мокрой кожей или гольем — натянуть. Летом сохнет за сутки, зимой — за четверо. Дождь мешает.",
                    "E with wet leather or a pelt stretches it. Dries in a day in summer, four in winter. Rain gets in the way.")
            .kg(10)
            .register(properties -> new BlockItem(ModBlocks.DRYING_RACK.get(), properties));
    public static final DeferredItem<BlockItem> LEATHER_TABLE = item("leather_table", "Шитейный стол", "Sewing table", Gender.M)
            .category(Category.TOOLS)
            .lore("E — окно стола: выберите выкройку, положите кожу или ткань в ячейки, «Раскроить» ножом или ножницами, "
                            + "затем «Шить» иглой.",
                    "E opens the table: pick a pattern, put leather or cloth into the slots, \"Cut\" with a knife or shears, "
                            + "then \"Sew\" with a needle.")
            .kg(25)
            .register(properties -> new BlockItem(ModBlocks.LEATHER_TABLE.get(), properties));

    // ----- Готовка (cooking/*; станции — в ModBlocks) -----
    // Блюда из горшка: сытость и жажда — от состава (компонент halgventmod:dish), здесь — только для чужих подсказок
    public static final DeferredItem<Item> PORRIDGE = item("porridge", "Каша", "Porridge", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Зерно, сваренное в горшке. На молоке — сытнее.", "Grain boiled in a pot. With milk it is more filling.")
            .stacksTo(4)
            .grams(400)
            .food(30, 20.0F)
            .returns(Items.BOWL)
            .shelfLife(2)
            .register();
    public static final DeferredItem<Item> VEGETABLE_SOUP = item("vegetable_soup", "Овощная похлёбка", "Vegetable soup", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Овощи, сваренные в горшке. Утоляет и голод, и жажду.", "Vegetables boiled in a pot. Eases both hunger and thirst.")
            .stacksTo(4)
            .grams(600)
            .food(30, 15.0F)
            .thirst(15)
            .wellFed(120)
            .returns(Items.BOWL)
            .shelfLife(2)
            .register();
    public static final DeferredItem<Item> MUSHROOM_SOUP = item("mushroom_soup", "Грибная похлёбка", "Mushroom soup", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Грибы, сваренные в горшке.", "Mushrooms boiled in a pot.")
            .stacksTo(4)
            .grams(400)
            .food(20, 12.0F)
            .thirst(20)
            .returns(Items.BOWL)
            .shelfLife(2)
            .register();
    public static final DeferredItem<Item> MEAT_STEW = item("meat_stew", "Мясная похлёбка", "Meat stew", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Наваристое мясо с тем, что нашлось в котле.", "Rich meat with whatever was in the pot.")
            .stacksTo(4)
            .grams(600)
            .food(50, 25.0F)
            .thirst(15)
            .wellFed(120)
            .returns(Items.BOWL)
            .shelfLife(2)
            .register();
    public static final DeferredItem<Item> FISH_SOUP = item("fish_soup", "Уха", "Fish soup", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Рыба, сваренная в горшке.", "Fish boiled in a pot.")
            .stacksTo(4)
            .grams(400)
            .food(35, 20.0F)
            .thirst(20)
            .returns(Items.BOWL)
            .shelfLife(1)
            .register();
    public static final DeferredItem<Item> HERBAL_TEA = item("herbal_tea", "Травяной отвар", "Herbal tea", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Травы, заваренные в горшке. Хорошо утоляет жажду.", "Herbs steeped in a pot. Quenches thirst well.")
            .stacksTo(4)
            .grams(300)
            .drink()
            .thirst(35)
            .returns(Items.GLASS_BOTTLE)
            .shelfLife(3)
            .register();

    // Хлеб и пироги: мука с жёрнова, тесто — в верстаке, печь — хлебная
    public static final DeferredItem<Item> WHEAT_FLOUR = item("wheat_flour", "Пшеничная мука", "Wheat flour", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Смолота на жёрнове. На кухонном столе с водой — тесто.", "Ground on a quern. With water at a kitchen table it makes dough.")
            .stacksTo(16)
            .grams(200)
            .shelfLife(32)
            .register();
    public static final DeferredItem<Item> DOUGH = item("dough", "Тесто", "Dough", Gender.N)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.FOOD)
            .lore("E по растопленной печи — посадить хлеб. С начинкой на кухонном столе — сырой пирог.",
                    "E at a fired oven bakes bread. With a filling at a kitchen table — a raw pie.")
            .stacksTo(8)
            .grams(400)
            .shelfLife(2)
            .register();
    // Рожь: семена с травы, посев на пашне, мука на жёрнове, ржаное тесто — на кухонном столе, в печи — чёрный хлеб
    public static final DeferredItem<ItemNameBlockItem> RYE_SEEDS = item("rye_seeds", "Семена ржи", "Rye seeds", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Попадаются в траве. ПКМ по пашне — посеять.", "Found in the grass. Right-click farmland to sow.")
            .stacksTo(64)
            .grams(5)
            .register(properties -> new ItemNameBlockItem(ModBlocks.RYE.get(), properties));
    public static final DeferredItem<Item> RYE = item("rye", "Рожь", "Rye", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Зерно ржи. На жёрнове — ржаная мука, в горшке — каша.", "Rye grain. A quern grinds it into rye flour; a pot makes porridge.")
            .stacksTo(64)
            .grams(50)
            .register();
    public static final DeferredItem<Item> RYE_FLOUR = item("rye_flour", "Ржаная мука", "Rye flour", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Смолота на жёрнове. На кухонном столе с водой — ржаное тесто.", "Ground on a quern. With water at a kitchen table it makes rye dough.")
            .stacksTo(16)
            .grams(200)
            .shelfLife(32)
            .register();
    public static final DeferredItem<Item> RYE_DOUGH = item("rye_dough", "Ржаное тесто", "Rye dough", Gender.N)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.FOOD)
            .lore("E по растопленной печи — посадить: выйдет чёрный хлеб.", "E at a fired oven to bake it into black bread.")
            .stacksTo(8)
            .grams(400)
            .shelfLife(2)
            .register();
    public static final DeferredItem<Item> RAW_PIE = item("raw_pie", "Сырой пирог", "Raw pie", Gender.M)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.FOOD)
            .lore("E по растопленной печи — посадить.", "E at a fired oven to bake it.")
            .stacksTo(4)
            .grams(500)
            .shelfLife(2)
            .register();
    public static final DeferredItem<Item> RAW_BUN = item("raw_bun", "Сырая булочка", "Raw bun", Gender.F)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.FOOD)
            .lore("E по растопленной печи — посадить.", "E at a fired oven to bake it.")
            .stacksTo(8)
            .grams(150)
            .shelfLife(2)
            .register();
    public static final DeferredItem<Item> SWEET_BUN = item("sweet_bun", "Сладкая булочка", "Sweet bun", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Сдобная булочка на мёду.", "A rich bun made with honey.")
            .stacksTo(8)
            .grams(150)
            .food(25, 20.0F)
            .shelfLife(5)
            .register();
    public static final DeferredItem<Item> MEAT_PIE = item("meat_pie", "Пирог с мясом", "Meat pie", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Сытный пирог из печи.", "A filling pie from the oven.")
            .stacksTo(4)
            .grams(500)
            .food(60, 40.0F)
            .shelfLife(4)
            .register();
    public static final DeferredItem<Item> MUSHROOM_PIE = item("mushroom_pie", "Пирог с грибами", "Mushroom pie", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Пирог из печи с грибной начинкой.", "An oven pie with a mushroom filling.")
            .stacksTo(4)
            .grams(500)
            .food(45, 30.0F)
            .shelfLife(4)
            .register();
    public static final DeferredItem<Item> BERRY_PIE = item("berry_pie", "Ягодный пирог", "Berry pie", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.FOOD)
            .lore("Сладкий пирог из печи.", "A sweet pie from the oven.")
            .stacksTo(4)
            .grams(500)
            .food(25, 12.5F)
            .thirst(3)
            .shelfLife(2)
            .wellFed(120)
            .register();
    public static final DeferredItem<Item> BURNT_FOOD = item("burnt_food", "Горелое", "Burnt food", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Передержали на огне. Есть можно, но не стоит; горит в очаге.",
                    "Left too long on the fire. Edible, barely; burns in a hearth.")
            .stacksTo(16)
            .grams(100)
            .food(3, 0.0F)
            .shelfLife(32)
            .register();

    // Заготовки: соль, дым, сушка — лежат во много раз дольше
    public static final DeferredItem<Item> SALTED_MEAT = item("salted_meat", "Солонина", "Salted meat", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Мясо из засолочной бочки. Лежит долго, но солёное — хочется пить.",
                    "Meat from the brine barrel. Keeps long, but salty — makes you thirsty.")
            .stacksTo(8)
            .grams(300)
            .food(25, 10.0F)
            .thirst(-10)
            .shelfLife(64)
            .register();
    public static final DeferredItem<Item> SALTED_FISH = item("salted_fish", "Солёная рыба", "Salted fish", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Рыба из засолочной бочки.", "Fish from the brine barrel.")
            .stacksTo(8)
            .grams(250)
            .food(20, 8.0F)
            .thirst(-10)
            .shelfLife(64)
            .register();
    public static final DeferredItem<Item> SMOKED_MEAT = item("smoked_meat", "Копчёное мясо", "Smoked meat", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Прокоптилось над очагом.", "Smoked over a hearth.")
            .stacksTo(8)
            .grams(250)
            .food(35, 20.0F)
            .thirst(-5)
            .shelfLife(48)
            .register();
    public static final DeferredItem<Item> SMOKED_FISH = item("smoked_fish", "Копчёная рыба", "Smoked fish", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Прокоптилась над очагом.", "Smoked over a hearth.")
            .stacksTo(8)
            .grams(200)
            .food(30, 15.0F)
            .thirst(-5)
            .shelfLife(48)
            .register();
    public static final DeferredItem<Item> DRIED_MUSHROOMS = item("dried_mushrooms", "Сушёные грибы", "Dried mushrooms", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Высушены на крюке. Хороши в похлёбку.", "Dried on a hook. Good in a soup.")
            .stacksTo(16)
            .grams(50)
            .food(8, 4.0F)
            .shelfLife(128)
            .register();
    // Травы (herbs/Herb): срезают ножом, серпом или косой — каждую только с её кустика (HerbPlantBlock). Все идут
    // приправой в горшок (+1 к качеству блюда), на кухонный стол, в брожение «на травах»; сушатся на крюке.
    // Отвар — трава с одной водой в горшке, 2 порции на траву; действие — по траве, которой больше ⚠️
    public static final DeferredItem<Item> CHAMOMILE = herb("chamomile", "Ромашка", "Chamomile", Gender.F,
            "Срезают с куста ромашки. Отвар: снимает тошноту и отравление, Регенерация I на 8 с.",
            "Cut from a chamomile plant. Tea: clears nausea and food poisoning, Regeneration I for 8 s.");
    public static final DeferredItem<Item> MINT = herb("mint", "Мята", "Mint", Gender.F,
            "Срезают с куста мяты. Отвар: снимает тошноту, Скорость I на 30 с.",
            "Cut from a mint plant. Tea: clears nausea, Speed I for 30 s.");
    public static final DeferredItem<Item> ST_JOHNS_WORT = herb("st_johns_wort", "Зверобой", "St John's wort", Gender.M,
            "Срезают с куста зверобоя. Отвар: Сопротивление I на 60 с.",
            "Cut from a St John's wort plant. Tea: Resistance I for 60 s.");
    public static final DeferredItem<Item> YARROW = herb("yarrow", "Тысячелистник", "Yarrow", Gender.M,
            "Срезают с куста тысячелистника. Отвар: Регенерация I на 15 с.",
            "Cut from a yarrow plant. Tea: Regeneration I for 15 s.");
    public static final DeferredItem<Item> WORMWOOD = herb("wormwood", "Полынь", "Wormwood", Gender.F,
            "Срезают с куста полыни. Горький отвар: снимает отравление, голод и яд.",
            "Cut from a wormwood plant. Bitter tea: clears food poisoning, hunger and poison.");
    public static final DeferredItem<Item> NETTLE = herb("nettle", "Крапива", "Nettle", Gender.F,
            "Срезают с зарослей крапивы. Отвар: Бодрость на 60 с — выносливость восстанавливается в 1,3 раза быстрее.",
            "Cut from nettles. Tea: Vigour for 60 s, stamina recovers 1.3 times faster.");
    public static final DeferredItem<Item> THYME = herb("thyme", "Чабрец", "Thyme", Gender.M,
            "Срезают с куста чабреца. Отвар: Поглощение I на 60 с (запас здоровья).",
            "Cut from a thyme plant. Tea: Absorption I for 60 s (extra health).");
    public static final DeferredItem<Item> LAVENDER = herb("lavender", "Лаванда", "Lavender", Gender.F,
            "Срезают с куста лаванды. Отвар: снимает тошноту, слепоту и тьму, Регенерация I на 4 с.",
            "Cut from a lavender plant. Tea: clears nausea, blindness and darkness, Regeneration I for 4 s.");

    // Кустики трав (herbs/HerbPlantBlock) — для строителей; срезанный отрастает сам
    public static final DeferredItem<BlockItem> CHAMOMILE_PLANT = herbPlant("chamomile_plant", "Куст ромашки", "Chamomile plant",
            Gender.M, ModBlocks.CHAMOMILE_PLANT);
    public static final DeferredItem<BlockItem> MINT_PLANT = herbPlant("mint_plant", "Куст мяты", "Mint plant",
            Gender.M, ModBlocks.MINT_PLANT);
    public static final DeferredItem<BlockItem> ST_JOHNS_WORT_PLANT = herbPlant("st_johns_wort_plant", "Куст зверобоя",
            "St John's wort plant", Gender.M, ModBlocks.ST_JOHNS_WORT_PLANT);
    public static final DeferredItem<BlockItem> YARROW_PLANT = herbPlant("yarrow_plant", "Куст тысячелистника", "Yarrow plant",
            Gender.M, ModBlocks.YARROW_PLANT);
    public static final DeferredItem<BlockItem> WORMWOOD_PLANT = herbPlant("wormwood_plant", "Куст полыни", "Wormwood plant",
            Gender.M, ModBlocks.WORMWOOD_PLANT);
    public static final DeferredItem<BlockItem> NETTLE_PLANT = herbPlant("nettle_plant", "Заросли крапивы", "Nettles",
            Gender.PL, ModBlocks.NETTLE_PLANT);
    public static final DeferredItem<BlockItem> THYME_PLANT = herbPlant("thyme_plant", "Куст чабреца", "Thyme plant",
            Gender.M, ModBlocks.THYME_PLANT);
    public static final DeferredItem<BlockItem> LAVENDER_PLANT = herbPlant("lavender_plant", "Куст лаванды", "Lavender plant",
            Gender.M, ModBlocks.LAVENDER_PLANT);

    /** Кустик травы: блок для строителей. Модели Conquest — блок сам выбирает, срезан он или вырос. */
    private static DeferredItem<BlockItem> herbPlant(String id, String ru, String en, Gender gender,
                                                     net.neoforged.neoforge.registries.DeferredBlock<?> block) {
        return item(id, ru, en, gender)
                .category(Category.MATERIALS)
                .lore("Для строителей. E ножом, серпом или косой по выросшему — срезать траву; срезанный отрастает сам "
                                + "за полтора-два дня (зимой — нет). Сломать в приключении нельзя.",
                        "For builders. E with a knife, sickle or scythe at a grown plant cuts the herb; a cut plant grows "
                                + "back by itself in a day or two (not in winter). Can't be broken in adventure mode.")
                .grams(200)
                .draw(DrawType.SMALL)
                .register(properties -> new BlockItem(block.get(), properties));
    }

    /** Вид травы: срезают с её кустика (herbs/HerbPlantBlock), сушат на крюке, кладут в горшок и в рецепты. */
    private static DeferredItem<Item> herb(String id, String ru, String en, Gender gender, String loreRu, String loreEn) {
        return item(id, ru, en, gender)
                .traits(ModTraits.COMMON)
                .category(Category.FOOD)
                .lore(loreRu, loreEn)
                .stacksTo(16)
                .grams(20)
                .shelfLife(4)
                .register();
    }
    public static final DeferredItem<Item> DRIED_HERBS = item("dried_herbs", "Сушёные травы", "Dried herbs", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Высушены на крюке. Лежат почти вечно.", "Dried on a hook. Keep almost forever.")
            .stacksTo(16)
            .grams(10)
            .shelfLife(192)
            .register();

    // Посуда и станции
    public static final DeferredItem<Item> FIREWOOD = item("firewood", "Дрова", "Firewood", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Колотые поленья. E по очагу или печи — подложить; горят дольше досок.",
                    "Split logs. E at a hearth or oven to add them; they burn longer than planks.")
            .stacksTo(16)
            .kg(1.5)
            .register();
    public static final DeferredItem<Item> LADLE = item("ladle", "Половник", "Ladle", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("E по горшку — помешать, пока не пригорело (чадит и булькает).",
                    "E at a pot stirs it before it scorches (it smokes and bubbles).")
            .grams(150)
            .register();
    public static final DeferredItem<BlockItem> HEARTH = item("hearth", "Очаг", "Hearth", Gender.M)
            .category(Category.TOOLS)
            .lore("Дрова и огниво — огонь. На нём — горшок, а без горшка жарят прямо на углях. Золу выгребают лопатой.",
                    "Firewood and a flint make a fire. A pot goes on top, or meat is grilled right on the coals. Shovel out the ash.")
            .kg(20)
            .register(properties -> new BlockItem(ModBlocks.HEARTH.get(), properties));
    public static final DeferredItem<BlockItem> BAKING_OVEN = item("baking_oven", "Печь", "Oven", Gender.F)
            .category(Category.TOOLS)
            .lore("Большая печь 2×2×2: каменный под, кирпичный свод, дымоход. Дрова и огниво — растопить. E тестом или сырым "
                            + "пирогом — посадить, пустой рукой — вынуть. Сушит и солод.",
                    "A big 2×2×2 oven: stone hearth, brick vault, chimney. Firewood and a flint fire it up. E with dough or a raw "
                            + "pie to bake, an empty hand takes it out. It also dries malt.")
            .kg(800)
            .register(properties -> new BlockItem(ModBlocks.BAKING_OVEN.get(), properties));
    public static final DeferredItem<BlockItem> QUERN = item("quern", "Жёрнов", "Quern", Gender.M)
            .category(Category.TOOLS)
            .lore("E пшеницей, рожью или черепками — засыпать, пустой рукой — крутить. Не спешите — зерно просыпется.",
                    "E with wheat, rye or sherds to fill, an empty hand to turn. Don't rush — the grain spills.")
            .kg(30)
            .register(properties -> new BlockItem(ModBlocks.QUERN.get(), properties));
    public static final DeferredItem<BlockItem> KITCHEN_TABLE = item("kitchen_table", "Кухонный стол", "Kitchen table", Gender.M)
            .category(Category.TOOLS)
            .lore("E пустой рукой — рецепты. Продукты, вода и соль — на доску, пустой рукой — месить. Тесто и пироги — здесь.",
                    "E with an empty hand shows recipes. Food, water and salt go on the board, an empty hand kneads. Dough and pies are made here.")
            .kg(20)
            .register(properties -> new BlockItem(ModBlocks.KITCHEN_TABLE.get(), properties));
    public static final DeferredItem<BlockItem> BRINE_BARREL = item("brine_barrel", "Засолочная бочка", "Brine barrel", Gender.F)
            .category(Category.TOOLS)
            .lore("Сырое мясо или рыба и по соли на кусок — через двое суток солонина.",
                    "Raw meat or fish and a salt per piece — salted in two days.")
            .kg(15)
            .register(properties -> new BlockItem(ModBlocks.BRINE_BARREL.get(), properties));
    public static final DeferredItem<BlockItem> SMOKING_HOOK = item("smoking_hook", "Крюк", "Hook", Gender.M)
            .category(Category.TOOLS)
            .lore("Повесьте над очагом: мясо и рыба коптятся. Травы и грибы на нём сохнут.",
                    "Hang it above a hearth: meat and fish get smoked. Herbs and mushrooms dry on it.")
            .grams(500)
            .register(properties -> new BlockItem(ModBlocks.SMOKING_HOOK.get(), properties));

    // ----- Пчеловодство (beekeeping/*) -----
    public static final DeferredItem<Item> HONEY = item("honey", "Мёд", "Honey", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Отжат из сот на кухонном столе. Не портится. В горшок — сладость, в выпечку — начинка.",
                    "Pressed from combs at a kitchen table. Never spoils. Sweetens a pot, fills a pastry.")
            .stacksTo(16)
            .grams(300)
            .food(15, 12.0F)
            .register();
    public static final DeferredItem<Item> HONEYCOMB_FULL = item("honeycomb_full", "Медовые соты", "Honeycomb", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Срезаны ножом из улья. На кухонном столе — мёд и воск.",
                    "Cut from a hive with a knife. At a kitchen table they give honey and wax.")
            .stacksTo(16)
            .grams(250)
            .shelfLife(32)
            .register();
    public static final DeferredItem<Item> BEESWAX = item("beeswax", "Воск", "Beeswax", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Остаётся от сот. Пропитывает кожу и смазывает кожаные вещи.",
                    "Left from the combs. Dresses leather and oils leather goods.")
            .stacksTo(16)
            .grams(100)
            .register();
    public static final DeferredItem<Item> BEE_SMOKER = item("bee_smoker", "Дымарь", "Bee smoker", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("E по улью — окурить: пчёлы притихают на полминуты, и соты можно резать без укусов.",
                    "E at a hive smokes it: the bees calm down for half a minute, so the combs can be cut unstung.")
            .durability(64)
            .grams(400)
            .register();
    public static final DeferredItem<BlockItem> SKEP = item("skep", "Улей", "Skep", Gender.M)
            .category(Category.TOOLS)
            .lore("Поставьте у цветов: весной или летом в него заселится рой. Копит мёд, зимой пчёлы его едят — оставьте им запас.",
                    "Place it near flowers: a swarm settles in during spring or summer. It stores honey; in winter the bees eat it — leave them some.")
            .kg(3)
            .register(properties -> new BlockItem(ModBlocks.SKEP.get(), properties));

    // ----- Алкоголь (brewing/*) -----
    // Сусло и напитки: что именно — из состава (компонент halgventmod:brew), название собирает AlcoholItem
    public static final DeferredItem<AlcoholItem> MUST = item("must", "Сусло", "Must", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Сок с давильни, пивное сусло или медовый взвар из горшка. Влейте в бродильную бочку.",
                    "Juice from a press, beer wort or honey must from a pot. Pour it into a fermenting barrel.")
            .stacksTo(8).grams(400)
            .drink().food(4, 2.0F).thirst(10).returns(Items.GLASS_BOTTLE).shelfLife(3)
            .register(AlcoholItem::new);
    public static final DeferredItem<AlcoholItem> BEER = item("beer", "Пиво", "Beer", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.ALCOHOL)
            .lore("Слабое и сытное, хорошо утоляет жажду. Пьют свежим — скисает за неделю. Снимает похмелье.",
                    "Weak and filling, quenches thirst well. Drunk fresh — sours within a week. Cures a hangover.")
            .stacksTo(8).grams(500)
            .drink().food(10, 5.0F).thirst(15).returns(Items.GLASS_BOTTLE).shelfLife(7)
            .register(AlcoholItem::new);
    public static final DeferredItem<AlcoholItem> CIDER = item("cider", "Сидр", "Cider", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ALCOHOL)
            .lore("Яблочный, лёгкий. В бочке для выдержки становится лучше.", "Light, made of apples. Improves in an aging cask.")
            .stacksTo(8).grams(500)
            .drink().food(6, 3.0F).thirst(12).returns(Items.GLASS_BOTTLE)
            .register(AlcoholItem::new);
    public static final DeferredItem<AlcoholItem> WINE = item("wine", "Вино", "Wine", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.ALCOHOL)
            .lore("Ягодное или «болотное». Выдержанное — лучше.", "Berry or marsh wine. Better when aged.")
            .stacksTo(8).grams(500)
            .drink().food(6, 3.0F).thirst(6).returns(Items.GLASS_BOTTLE)
            .register(AlcoholItem::new);
    public static final DeferredItem<AlcoholItem> MEAD = item("mead", "Медовуха", "Mead", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.ALCOHOL)
            .lore("Медовый взвар, перебродивший неделю. Выдержанная — лучше.", "Honey must fermented for a week. Better when aged.")
            .stacksTo(8).grams(500)
            .drink().food(8, 4.0F).thirst(6).returns(Items.GLASS_BOTTLE)
            .register(AlcoholItem::new);
    public static final DeferredItem<AlcoholItem> SPIRIT = item("spirit", "Крепкое", "Spirit", Gender.N)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ALCOHOL)
            .lore("Из перегонного куба. Валит с ног быстро; с ягодами, травами или мёдом в бродильной бочке — настойка.",
                    "From a still. Knocks you down fast; with berries, herbs or honey in a fermenting barrel it becomes a tincture.")
            .stacksTo(8).grams(400)
            .drink().food(0, 0.0F).thirst(-10).returns(Items.GLASS_BOTTLE)
            .register(AlcoholItem::new);
    public static final DeferredItem<AlcoholItem> TINCTURE = item("tincture", "Настойка", "Tincture", Gender.F)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ALCOHOL)
            .lore("Крепкое, настоянное на ягодах, травах или мёде. Мягче крепкого.", "A spirit steeped with berries, herbs or honey. Gentler than a plain spirit.")
            .stacksTo(8).grams(400)
            .drink().food(2, 1.0F).thirst(-6).returns(Items.GLASS_BOTTLE)
            .register(AlcoholItem::new);
    public static final DeferredItem<AlcoholItem> VINEGAR = item("vinegar", "Уксус", "Vinegar", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.FOOD)
            .lore("Так бывает, если передержать брожение. Пить не стоит.", "What happens when fermentation goes too long. Better not to drink it.")
            .stacksTo(8).grams(400)
            .drink().food(0, 0.0F).thirst(-4).returns(Items.GLASS_BOTTLE)
            .register(AlcoholItem::new);
    // Солод: замочить на кухонном столе, прорастить на крюке, высушить в печи
    public static final DeferredItem<Item> WET_GRAIN = item("wet_grain", "Замоченное зерно", "Soaked grain", Gender.N)
            .traits(ModTraits.COMMON).category(Category.MATERIALS)
            .lore("Повесьте на крюк — прорастёт.", "Hang it on a hook to sprout.")
            .stacksTo(16).grams(60).shelfLife(4).register();
    public static final DeferredItem<Item> GREEN_MALT = item("green_malt", "Пророщенное зерно", "Green malt", Gender.N)
            .traits(ModTraits.COMMON).category(Category.MATERIALS)
            .lore("Высушите в печи: недолго — светлый солод, дольше — янтарный, ещё дольше — тёмный.",
                    "Dry it in an oven: briefly for pale malt, longer for amber, longer still for dark.")
            .stacksTo(16).grams(50).shelfLife(4).register();
    public static final DeferredItem<Item> PALE_MALT = item("pale_malt", "Светлый солод", "Pale malt", Gender.M)
            .traits(ModTraits.COMMON).category(Category.MATERIALS)
            .lore("В горшок с водой — пивное сусло.", "Into a pot with water for beer wort.").stacksTo(16).grams(40).register();
    public static final DeferredItem<Item> AMBER_MALT = item("amber_malt", "Янтарный солод", "Amber malt", Gender.M)
            .traits(ModTraits.COMMON).category(Category.MATERIALS)
            .lore("В горшок с водой — сусло для янтарного пива.", "Into a pot with water for amber beer wort.").stacksTo(16).grams(40).register();
    public static final DeferredItem<Item> DARK_MALT = item("dark_malt", "Тёмный солод", "Dark malt", Gender.M)
            .traits(ModTraits.COMMON).category(Category.MATERIALS)
            .lore("В горшок с водой — сусло для тёмного пива.", "Into a pot with water for dark beer wort.").stacksTo(16).grams(40).register();
    // Станции
    public static final DeferredItem<BlockItem> WINE_PRESS = item("wine_press", "Давильня", "Wine press", Gender.F)
            .category(Category.TOOLS)
            .lore("E яблоками, грушами, виноградом, вишней, сливой или ягодами — засыпать, пустой рукой — давить, склянкой, "
                            + "корчагой или кувшином — набрать сусло.",
                    "E with apples, pears, grapes, cherries, plums or berries to fill, an empty hand to press, a bottle, "
                            + "amphora or jug to draw the must.")
            .kg(40).register(properties -> new BlockItem(ModBlocks.WINE_PRESS.get(), properties));
    public static final DeferredItem<BlockItem> FERMENTING_BARREL = item("fermenting_barrel", "Бродильная бочка", "Fermenting barrel", Gender.F)
            .category(Category.TOOLS)
            .lore("Сусло — влить, мёд, травы, ягоды — добавки. Бродит несколько суток; готовое — склянкой. Бродит запечатанной; передержите — уксус. "
                            + "Алхимия: крепкое, уксус или вода + реагент — бальзамы, териак, эссенции, краска, купорос (книга в окне). ПКМ — окно бочки.",
                    "Pour in must; honey, herbs, berries are extras. Ferments for days; draw with a bottle. Ferments sealed; too long and it is vinegar. "
                            + "Alchemy: spirit, vinegar or water + a reagent make balms, theriac, essences, paint, vitriol (see the book). Right-click opens the barrel.")
            .kg(15).register(properties -> new BlockItem(ModBlocks.FERMENTING_BARREL.get(), properties));
    public static final DeferredItem<BlockItem> STILL = item("still", "Перегонный куб", "Still", Gender.M)
            .category(Category.TOOLS)
            .lore("E по очагу — поставить на огонь (или над костром). Пиво, вино, сидр, медовуха — залить; из четырёх склянок одна крепкого — в склянку или корчагу. ПКМ — окно куба.",
                    "E on a hearth puts it on the fire (or place it above a campfire). Pour in beer, wine, cider or mead; four bottles make one spirit — into a bottle or an amphora. Right-click opens the still.")
            .kg(30).register(properties -> new BlockItem(ModBlocks.STILL.get(), properties));
    public static final DeferredItem<BlockItem> AGING_CASK = item("aging_cask", "Бочка для выдержки", "Aging cask", Gender.F)
            .category(Category.TOOLS)
            .lore("Влейте вино, медовуху, сидр, крепкое или настойку (до 16 склянок одного питья) — с каждой неделей оно лучше; "
                            + "в погребе — быстрее. E склянкой или сосудом — влить, пустым — налить. ПКМ — окно. Сломаете — разольётся.",
                    "Pour in wine, mead, cider, a spirit or a tincture (up to 16 bottles of one drink): it gets better every week, "
                            + "faster in a cellar. E with a bottle or vessel pours in, with an empty one draws. Right-click opens it. "
                            + "Break it and it spills.")
            .kg(25).register(properties -> new BlockItem(ModBlocks.AGING_CASK.get(), properties));

    // ----- Гончарное дело (pottery/*; станции — в ModBlocks) -----
    // Цепочка: глина + вода + песок или шамот → бочка для замеса (промять) → гончарная глина → гончарный круг
    // (форма по вокселям) → сырец → полка (сушка, лощение) → горн → посуда.
    // Качество — компонент halgventmod:pottery_quality (Pottery), форма — halgventmod:vessel (Vessel).
    public static final DeferredItem<Item> POTTER_CLAY = item("potter_clay", "Гончарная глина", "Potter's clay", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("Промята в бочке для замеса. На гончарный круг — сосуд, в форму — кирпич. Пролежавшая в бочке трое суток — лучше.",
                    "Kneaded in a clay barrel. On the potter's wheel it becomes a vessel, in a mould — a brick. Clay left to age three days is better.")
            .stacksTo(16)
            .grams(800)
            .register();
    public static final DeferredItem<Item> GROG = item("grog", "Шамот", "Grog", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("Черепки, смолотые на жёрнове. Отощитель глины лучше песка: посуда реже трескается.",
                    "Sherds ground on a quern. A better temper than sand: the ware cracks less often.")
            .grams(150)
            .register();
    public static final DeferredItem<Item> SHERDS = item("sherds", "Черепки", "Sherds", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("Всё, что треснуло в горне или разбилось. На жёрнове — шамот.", "Whatever cracked in the kiln or broke. A quern grinds it into grog.")
            .grams(250)
            .register();
    public static final DeferredItem<Item> RAW_BRICK = item("raw_brick", "Кирпич-сырец", "Raw brick", Gender.M)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.POTTERY)
            .lore("Отформован из промятой глины. Сырой сохнет на полке, сухой обжигают в горне — будет кирпич: им чинят горн.",
                    "Moulded from kneaded clay. A wet one dries on a shelf, a dry one is fired in a kiln for a brick — kilns are mended with bricks.")
            .stacksTo(16)
            .kg(2)
            .register();
    public static final DeferredItem<Item> BRICK_MOULD = item("brick_mould", "Форма для кирпича", "Brick mould", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("E по бочке с промятой глиной — отформовать кирпич-сырец.", "E at a barrel of kneaded clay moulds a raw brick.")
            .durability(120)
            .grams(800)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> RAW_LOOM_WEIGHT = item("raw_loom_weight", "Грузило-сырец", "Raw loom weight", Gender.N)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.POTTERY)
            .lore("Ком промятой глины, проткнутый палочкой. Сохнет на полке, обжигается в горне — будет грузило для станка.",
                    "A lump of kneaded clay pierced with a stick. Dry it on a shelf, fire it in a kiln — a weight for the loom.")
            .stacksTo(16)
            .grams(600)
            .register();
    public static final DeferredItem<GreenwareItem> GREENWARE = item("greenware", "Сырец", "Greenware", Gender.M)
            .traits(ModTraits.COMMON, ModTraits.RAW)
            .category(Category.POTTERY)
            .lore("Сосуд с гончарного круга. Сырой сохнет на полке, сухой обжигают в горне. Неудачный размочите в бочке.",
                    "A vessel from the wheel. A wet one dries on a shelf, a dry one is fired in a kiln. A failed one can be soaked back in the barrel.")
            .stacksTo(1)
            .kg(1)
            .register(GreenwareItem::new);
    // Обожжённая посуда: каждая вещь своей формы (вылеплена по вокселям), вид — из формы
    public static final DeferredItem<WareItem> CLAY_CUP = item("clay_cup", "Кружка", "Clay cup", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("Мера воды или питья: ПКМ по воде — набрать, ПКМ — пить. Налить можно из кувшина или корчаги: "
                            + "в инвентаре ПКМ ими по кружке.",
                    "One measure of water or a drink: right-click water to fill, right-click to drink. A jug or an amphora "
                            + "can fill it: right-click it onto the cup in your inventory.")
            .stacksTo(1)
            .grams(300)
            .register(properties -> new WareItem(properties, VesselForm.CUP));
    public static final DeferredItem<WareItem> CLAY_BOWL = item("clay_bowl", "Миска", "Clay bowl", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("E по горшку с готовым — зачерпнуть. После еды остаётся в руке.", "E at a pot of cooked food to ladle it. Stays in hand after eating.")
            .stacksTo(1)
            .grams(300)
            .register(properties -> new WareItem(properties, VesselForm.BOWL));
    public static final DeferredItem<WareItem> CLAY_JUG = item("clay_jug", "Кувшин", "Clay jug", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("Четыре меры воды или четыре склянки питья: ПКМ по воде — набрать, ПКМ — пить. Питьё — положите склянку "
                            + "на кувшин или налейте у куба, бочки, давильни или перелейте из корчаги (ПКМ корчагой по кувшину). "
                            + "Из кувшина наливают в кружку. Полный водой заменяет ведро в бочке, горшке и чане.",
                    "Four measures of water or four bottles of a drink: right-click water to fill, right-click to drink. "
                            + "Drop a bottle onto it or fill it at a still, barrel or press, or pour it from an amphora (right-click the "
                            + "amphora onto the jug). It fills a cup in turn. Full of water it does for a bucket.")
            .stacksTo(1)
            .grams(900)
            .register(properties -> new WareItem(properties, VesselForm.JUG));
    public static final DeferredItem<WareItem> CLAY_POT = item("clay_pot", "Горшок", "Clay pot", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("В инвентаре положите на него еду — внутри портится вдвое медленнее, под воском — вчетверо. ПКМ по нему — вынуть. "
                            + "Пустой ставят на очаг — в нём варят; изнашивается и трескается.",
                    "In the inventory drop food onto it — inside it spoils half as fast, under wax a quarter. Right-click to take out. "
                            + "An empty one goes on a hearth to cook in; it wears out and cracks.")
            .stacksTo(1)
            .kg(1.2)
            .register(properties -> new WareItem(properties, VesselForm.POT));
    public static final DeferredItem<WareItem> AMPHORA = item("amphora", "Корчага", "Amphora", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("Шестнадцать мер воды или восемь склянок питья одного рода: положите склянку на корчагу, пустой склянкой — "
                            + "наберите. В инвентаре ПКМ корчагой по кувшину или кружке — перелить в них питьё.",
                    "Sixteen measures of water or eight bottles of one drink: drop a bottle onto it, draw with an empty "
                            + "bottle. In your inventory, right-click the amphora onto a jug or cup to pour the drink into it.")
            .stacksTo(1)
            .kg(3)
            .register(properties -> new WareItem(properties, VesselForm.AMPHORA));
    public static final DeferredItem<WareItem> OIL_LAMP = item("oil_lamp", "Светильник", "Oil lamp", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("Заправьте салом или воском (положите на светильник), ПКМ — зажечь: нужно огниво или факел при себе.",
                    "Fill it with fat or wax (drop it onto the lamp), right-click to light: you need a flint and steel or a torch on you.")
            .stacksTo(1)
            .grams(250)
            .register(properties -> new WareItem(properties, VesselForm.LAMP));
    public static final DeferredItem<WareItem> CLAY_CRUCIBLE = item("clay_crucible", "Тигель", "Crucible", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.POTTERY)
            .lore("Толстостенный горшочек для расплава, до десяти слитков. Поставьте его в кузнечный горн, в окне горна "
                            + "положите шихту и плавьте; с жидким металлом (вынимают клещами) — к литейному столу. Опустев, может треснуть.",
                    "A thick-walled little pot for molten metal, up to ten ingots. Set it in the forge, charge it in the forge "
                            + "window and melt; take the molten metal (lifted with tongs) to the casting table. Emptied, it may crack.")
            .stacksTo(1)
            .grams(400)
            .register(properties -> new WareItem(properties, VesselForm.CRUCIBLE));
    // Станции гончара
    public static final DeferredItem<BlockItem> CLAY_BARREL = item("clay_barrel", "Бочка для замеса", "Clay barrel", Gender.F)
            .category(Category.TOOLS)
            .lore("Глина, ведро воды и песок или шамот (один к трём) — E пустой рукой месить. Shift + E — забрать гончарную глину.",
                    "Clay, a bucket of water and sand or grog (one to three) — E with an empty hand kneads. Shift + E takes the potter's clay.")
            .kg(25)
            .register(properties -> new BlockItem(ModBlocks.CLAY_BARREL.get(), properties));
    public static final DeferredItem<BlockItem> POTTERY_WHEEL = item("pottery_wheel", "Гончарный круг", "Potter's wheel", Gender.M)
            .category(Category.TOOLS)
            .lore("E гончарной глиной — положить, пустой рукой — сесть за круг и вылепить сосуд по вокселям.",
                    "E with potter's clay to put it on, an empty hand to sit at the wheel and shape a vessel voxel by voxel.")
            .kg(40)
            .register(properties -> new BlockItem(ModBlocks.POTTERY_WHEEL.get(), properties));
    public static final DeferredItem<BlockItem> DRYING_SHELF = item("drying_shelf", "Полка для сырца", "Drying shelf", Gender.F)
            .category(Category.TOOLS)
            .lore("E сырцом — поставить сохнуть. Под крышей — сутки, под небом — быстрее, но трескается; дождь размачивает.",
                    "E with greenware to set it drying. A day under a roof; faster under the sky but it cracks; rain soaks it.")
            .kg(6)
            .register(properties -> new BlockItem(ModBlocks.DRYING_SHELF.get(), properties));
    public static final DeferredItem<BlockItem> DOME_KILN = item("dome_kiln", "Купольный горн", "Dome kiln", Gender.M)
            .category(Category.TOOLS)
            .lore("Глиняный купол над топкой, 2×2×2 блока, топят через устье спереди. Десять сырцов. ПКМ — окно горна: сырец, "
                            + "дрова, огниво; жар — тягой: первую минуту тяга 1, потом 3–4 (600–850°). Слабая тяга в жару — дым: залощённое "
                            + "выйдет чернолощёным. Обжигает и руду, прокаливает свинец в пепел и кость в золу.",
                    "A clay dome over a firebox, 2×2×2 blocks, stoked through the mouth in front. Ten pieces. Right-click opens the "
                            + "kiln: greenware, firewood, a flint; heat is set by draft: draft 1 for a minute, then 3–4 (600–850°). Low draft "
                            + "while hot smokes burnished ware black. It also roasts ore and calcines lead to ash and bone to bone ash.")
            .kg(600)
            .register(properties -> new BlockItem(ModBlocks.DOME_KILN.get(), properties));

    // ----- Ткачество (weaving/*): лён и шерсть → чесалка → кудель → веретено → пряжа → станок → ткань → шитейный стол -----
    public static final DeferredItem<ItemNameBlockItem> FLAX_SEEDS = item("flax_seeds", "Льняное семя", "Flaxseed", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("Попадается в траве. ПКМ по пашне — посеять лён.", "Found in the grass. Right-click farmland to sow flax.")
            .grams(5)
            .register(properties -> new ItemNameBlockItem(ModBlocks.FLAX_CROP.get(), properties));
    public static final DeferredItem<Item> FLAX = item("flax", "Лён", "Flax", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("Стебли льна. Чесалкой — в кудель; с горсти падает и пакля.",
                    "Flax stalks. Comb them into fibre with the carding comb; tow falls out too.")
            .grams(100)
            .register();
    public static final DeferredItem<Item> FLAX_FIBER = item("flax_fiber", "Льняная кудель", "Flax fibre", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("Расчёсанный лён. Веретеном — в льняную пряжу.", "Combed flax. Spin it into linen yarn with the spindle.")
            .grams(80)
            .register();
    public static final DeferredItem<Item> WOOL_ROVING = item("wool_roving", "Шерстяная кудель", "Wool roving", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("Расчёсанная шерсть. Веретеном — в шерстяную пряжу.", "Combed wool. Spin it into woollen yarn with the spindle.")
            .grams(80)
            .register();
    public static final DeferredItem<Item> TOW = item("tow", "Пакля", "Tow", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("Короткое волокно, что остаётся при чесании льна. Набивка для акетонов и стёганых вещей.",
                    "The short fibre left from combing flax. Stuffing for aketons and quilted pieces.")
            .grams(50)
            .register();
    public static final DeferredItem<Item> LINEN_YARN = item("linen_yarn", "Льняная пряжа", "Linen yarn", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("На станок — основой и утком. Годится и на нитки.", "For the loom, as warp and weft. Makes thread too.")
            .grams(60)
            .register();
    public static final DeferredItem<Item> WOOL_YARN = item("wool_yarn", "Шерстяная пряжа", "Woollen yarn", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("На станок — основой и утком.", "For the loom, as warp and weft.")
            .grams(60)
            .register();
    public static final DeferredItem<Item> LINEN_CLOTH = item("linen_cloth", "Льняное полотно", "Linen cloth", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("Отрез с ткацкого станка. На шитейном столе — сюрко, акетоны, гамбезон, перчатки.",
                    "A length off the loom. At the sewing table it becomes surcoats, aketons, a gambeson, gloves.")
            .stacksTo(16)
            .grams(300)
            .register();
    public static final DeferredItem<Item> WOOLEN_CLOTH = item("woolen_cloth", "Шерстяное сукно", "Woollen cloth", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("Отрез с ткацкого станка. На шитейном столе — плащи, капюшоны, шапероны, варежки.",
                    "A length off the loom. At the sewing table it becomes cloaks, hoods, chaperons, mittens.")
            .stacksTo(16)
            .grams(400)
            .register();
    // ----- Лечение (downed/Healing): здоровье само не восстанавливается, только этим -----
    public static final DeferredItem<Item> BANDAGE = item("bandage", "Повязка", "Bandage", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("Чистое льняное полотно. В руке, E без цели — перевязаться самому (+15 здоровья). "
                            + "Поверженного — через меню E, «Вылечить»: встанет.",
                    "Clean linen. In hand, E with nothing targeted bandages yourself (+15 health). "
                            + "A downed player: E menu, \"Treat\", and they get up.")
            .stacksTo(16)
            .grams(30)
            .register();
    public static final DeferredItem<Item> HEALING_SALVE = item("healing_salve", "Лечебная мазь", "Healing salve", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.ALCHEMY)
            .lore("Баночка мази на травах. В руке, E без цели — намазать раны (+30 здоровья). "
                            + "Поверженного — через меню E, «Вылечить»: встанет.",
                    "A jar of herbal salve. In hand, E with nothing targeted dresses your wounds (+30 health). "
                            + "A downed player: E menu, \"Treat\", and they get up.")
            .stacksTo(8)
            .grams(150)
            .register();
    public static final DeferredItem<FiberToolItem> CARDING_COMB = item("carding_comb", "Чесалка", "Carding comb", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("Держите ПКМ — расчесать горсть льна или клок шерсти из инвентаря в кудель.",
                    "Hold right-click to comb a handful of flax or a tuft of wool from your inventory into fibre.")
            .durability(200)
            .grams(300)
            .draw(DrawType.TOOL)
            .register(properties -> new FiberToolItem(properties, FiberToolItem.Step.COMB));
    public static final DeferredItem<FiberToolItem> SPINDLE = item("spindle", "Веретено", "Spindle", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("Держите ПКМ — спрясть кудель из инвентаря в пряжу.", "Hold right-click to spin fibre from your inventory into yarn.")
            .durability(300)
            .grams(80)
            .draw(DrawType.TOOL)
            .register(properties -> new FiberToolItem(properties, FiberToolItem.Step.SPIN));
    public static final DeferredItem<Item> SHUTTLE = item("shuttle", "Челнок", "Shuttle", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("E по станку с натянутой основой — ткать: бросайте челнок, пока зев открыт, и прибивайте уток бёрдом "
                            + "в меру. Пряжа для утка берётся из инвентаря.",
                    "E at a loom with its warp set to weave: throw the shuttle while the shed is open and beat the weft in "
                            + "just right. Weft yarn is taken from your inventory.")
            .durability(400)
            .grams(150)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> LOOM_WEIGHT = item("loom_weight", "Грузило", "Loom weight", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.TEXTILES)
            .lore("Обожжённая глина с дырой. Восемь грузил оттягивают основу ткацкого станка.",
                    "Fired clay with a hole. Eight weights pull the warp of a loom taut.")
            .stacksTo(16)
            .grams(500)
            .register();
    public static final DeferredItem<BlockItem> LOOM = item("loom", "Ткацкий станок", "Warp-weighted loom", Gender.M)
            .category(Category.TOOLS)
            .lore("В два блока высотой. E грузилами — подвесить, E пряжей — натянуть основу, E челноком — ткать, "
                            + "пустой рукой — снять ткань.",
                    "Two blocks tall. E with weights hangs them, E with yarn sets the warp, E with the shuttle weaves, "
                            + "an empty hand takes the cloth off.")
            .kg(30)
            .register(properties -> new BlockItem(ModBlocks.LOOM.get(), properties));

    // ----- Плотничество (carpentry/*) -----
    public static final DeferredItem<Item> SAW = item("saw", "Пила", "Saw", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("E по козлам с бревном — распустить его на доски: ЛКМ — нажать на пилу на толчке, мышью вести "
                            + "пропил по разметке. Нажим на обратном ходу зажимает пилу.",
                    "E at a sawhorse with a log rips it into planks: left click presses on the push stroke, the mouse "
                            + "steers the cut along the line. Pressing on the return stroke jams the saw.")
            .durability(300)
            .grams(900)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> PLANE = item("plane", "Рубанок", "Plane", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Строгает заготовку на верстаке плотника. Ведите по волокну, не спешите на сучках. Берётся из инвентаря.",
                    "Planes the blank at a carpenter's bench. Go with the grain and slow down over knots. "
                            + "Taken from your inventory.")
            .durability(250)
            .grams(700)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> CHISEL = item("chisel", "Долото", "Chisel", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Выбирает гнёзда на верстаке плотника: ЛКМ — лёгкий удар, ПКМ — сильный. Сильный у края колет "
                            + "древесину вдоль волокна. Берётся из инвентаря.",
                    "Cuts mortises at a carpenter's bench: left click taps, right click strikes hard. A hard blow near "
                            + "the edge splits the wood along the grain. Taken from your inventory.")
            .durability(300)
            .grams(350)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> BRACE = item("brace", "Коловорот", "Brace and bit", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Сверлит на верстаке плотника: водите мышью кругами по часовой стрелке. Быстро — сверло уводит, "
                            + "к концу не сбавите — вырвет с изнанки. Берётся из инвентаря.",
                    "Drills at a carpenter's bench: circle the mouse clockwise. Too fast and the bit wanders; "
                            + "keep the pace near the end or it blows out the back. Taken from your inventory.")
            .durability(250)
            .grams(1200)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<Item> HAMMER = item("hammer", "Молоток", "Hammer", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Собирает изделия на верстаке плотника: бейте, когда гвоздь стоит прямо, и в меру. "
                            + "Берётся из инвентаря.",
                    "Assembles pieces at a carpenter's bench: strike when the nail stands straight, and not too hard. "
                            + "Taken from your inventory.")
            .durability(400)
            .grams(600)
            .draw(DrawType.TOOL)
            .register();
    // Задел для кузни: деревянные части с верстака плотника, на которые кузнец насадит железо
    public static final DeferredItem<Item> TOOL_HANDLE = item("tool_handle", "Рукоять", "Tool handle", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Короткая рукоять с пояском — для ножа, долота, молотка, напильника. Пойдёт в кузню.",
                    "A short handle with a collar for a knife, chisel, hammer or file. Goes to the smithy.")
            .stacksTo(16)
            .grams(120)
            .register();
    public static final DeferredItem<Item> AXE_HANDLE = item("axe_handle", "Топорище", "Axe handle", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Толстое под проушину, с тонкой шейкой и упором на конце — для топора, колуна, чекана. Пойдёт в кузню.",
                    "Thick at the eye, a slim neck and a knob at the end: for an axe, a maul, a war hammer. Goes to the smithy.")
            .stacksTo(16)
            .grams(450)
            .register();
    public static final DeferredItem<Item> HAFT = item("haft", "Черенок", "Haft", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Ровный, конец сведён под втулку — для лопаты, кирки, мотыги, вил, косы. Пойдёт в кузню.",
                    "Straight, its end tapered for a socket: for a shovel, pick, hoe, fork or scythe. Goes to the smithy.")
            .stacksTo(16)
            .grams(700)
            .register();
    public static final DeferredItem<Item> SPEAR_SHAFT = item("spear_shaft", "Древко", "Spear shaft", Gender.N)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Долгое, к наконечнику тоньше, с дырой под гвоздь втулки — для копья, рогатины, алебарды. Пойдёт в кузню.",
                    "Long, slimmer towards the head, with a hole for the socket nail: for a spear, boar spear, halberd. "
                            + "Goes to the smithy.")
            .stacksTo(8)
            .kg(1.5)
            .register();
    public static final DeferredItem<Item> SWORD_GRIP = item("sword_grip", "Рукоять меча", "Sword grip", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.MATERIALS)
            .lore("Деревянная основа рукояти с пазом под хвостовик клинка; её обтянут кожей. Пойдёт в кузню.",
                    "The wooden core of a hilt, slotted for the blade's tang; it gets wrapped in leather. Goes to the smithy.")
            .stacksTo(16)
            .grams(90)
            .register();
    public static final DeferredItem<BlockItem> CHOPPING_BLOCK = item("chopping_block", "Колода для колки", "Chopping block", Gender.F)
            .category(Category.TOOLS)
            .lore("E бревном — поставить на колоду, E топором — колоть на дрова (бейте с полного замаха), "
                            + "пустой рукой — снять.",
                    "E with a log sets it on the block, E with an axe splits it into firewood (strike with a full swing), "
                            + "an empty hand takes it off.")
            .kg(25)
            .register(properties -> new BlockItem(ModBlocks.CHOPPING_BLOCK.get(), properties));
    public static final DeferredItem<BlockItem> SAWHORSE = item("sawhorse", "Пильные козлы", "Sawhorse", Gender.PL)
            .category(Category.TOOLS)
            .lore("E бревном — положить на козлы, E пилой — распустить на доски, пустой рукой — снять нетронутое бревно.",
                    "E with a log lays it on, E with a saw rips it into planks, an empty hand takes an unsawn log off.")
            .kg(12)
            .register(properties -> new BlockItem(ModBlocks.SAWHORSE.get(), properties));
    public static final DeferredItem<BlockItem> CARPENTER_BENCH = item("carpenter_bench", "Верстак плотника", "Carpenter's bench", Gender.M)
            .category(Category.TOOLS)
            .lore("E — окно верстака: выбрать изделие, выложить доски и прочее, «Делать» — пройти этапы работы "
                            + "инструментами из инвентаря.",
                    "E opens the bench: pick a product, lay out planks and the rest, \"Make\" works through its steps "
                            + "with tools from your inventory.")
            .kg(40)
            .register(properties -> new BlockItem(ModBlocks.CARPENTER_BENCH.get(), properties));

    // ----- Драгоценности -----
    public static final DeferredItem<Item> COIN_GULDEN = item("coin_gulden", "Гульден", "Gulden", Gender.M)
            .traits(ModTraits.RARE)
            .lore("Большая золотая монета, привезенная с Тордесселя. Гульден чеканился в Лотангаре и был ходовой торговой монетой.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(99)
            .category(Category.JEWELRY)
            .grams(2.9)
            .register();
    public static final DeferredItem<Item> COIN_CRONA = item("coin_crona", "Крона", "Crona", Gender.F)
            .traits(ModTraits.RARE)
            .lore("Чеканится в Вессене Эберартом I.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(99)
            .category(Category.JEWELRY)

            .grams(1.1)
            .register();
    public static final DeferredItem<Item> COIN_TALER = item("coin_taler", "Талер", "Taler", Gender.M)
            .traits(ModTraits.RARE)
            .lore("Серебряная тордессельская монета, чеканившаяся в лотангарских землях.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(99)
            .category(Category.JEWELRY)
            .grams(2.5)
            .register();
    public static final DeferredItem<Item> COIN_MARKA = item("coin_marka", "Марка", "Marka", Gender.F)
            .traits(ModTraits.RARE)
            .lore("Серебряная вессенская монета.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(99)
            .category(Category.JEWELRY)
            .grams(1.3)
            .register();


    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        com.nrdhvnstd.halgventmod.smithing.SmithingItems.init(); // кузнечество — свои предметы в общем списке
        com.nrdhvnstd.halgventmod.alchemy.AlchemyItems.init(); // алхимия — тоже
        com.nrdhvnstd.halgventmod.glass.GlassItems.init(); // стекольное дело — тоже
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::setDefaultTraits);
    }

    /** Описание предмета: для подсказки, вкладок, моделей и переводов. */
    public record Definition(DeferredItem<? extends Item> item, String nameRu, String nameEn, Gender gender,
                             Category category, List<String> traits) {}

    public static List<Definition> definitions() {
        return Collections.unmodifiableList(DEFINITIONS);
    }

    /** Описание предмета мода или null для чужих предметов. */
    @Nullable
    public static Definition definition(Item item) {
        return BY_ID.get(BuiltInRegistries.ITEM.getKey(item));
    }

    public static Builder item(String id, String nameRu, String nameEn, Gender gender) {
        return new Builder(id, nameRu, nameEn, gender);
    }

    // Признаки из списка выше становятся признаками по умолчанию у каждого нового стака
    private static void setDefaultTraits(ModifyDefaultComponentsEvent event) {
        for (Definition def : DEFINITIONS) {
            if (!def.traits().isEmpty()) {
                event.modify(def.item(), builder -> builder.set(ModComponents.TRAITS.get(), def.traits()));
            }
        }
        // Шкура без сведений о звере (из творческого, /give, сундука) — воловья. Шкура со зверя ставит свою
        Hide unfleshed = new Hide("cow", 5, false, false);
        event.modify(RAW_HIDE, builder -> builder.set(ModComponents.HIDE.get(), unfleshed));
        event.modify(SALTED_HIDE, builder -> builder.set(ModComponents.HIDE.get(), unfleshed));
        event.modify(LIMED_HIDE, builder -> builder.set(ModComponents.HIDE.get(), unfleshed.withFleshed()));
    }

    public static final class Builder {
        private final String id;
        private final String nameRu;
        private final String nameEn;
        private final Gender gender;
        private Category category = Category.MISC;
        private List<String> traits = List.of();
        private String loreRu;
        private String loreEn;
        private int loreColor = ModLore.DEFAULT_COLOR;
        private int maxStack = 64;
        private int durability; // 0 — не изнашивается
        private double weightGrams = -1; // -1 — не задан, берётся вес по умолчанию
        private DrawType draw; // null — по умолчанию (ModDraw)

        // Еда и питьё
        private boolean edible;
        private int nutrition;
        private float saturation;
        private boolean drink;
        private int thirst;
        private int shelfLifeDays;
        private ItemLike returns;
        private int fedSeconds; // > 0 — «Сытость», < 0 — «Улучшенное насыщение»

        // Броня
        private ArmorSlot armorSlot;
        private ArmorKind armorKind;
        private int layerOrder = ModArmor.ArmorInfo.SLOT_ORDER;
        private ModArmor.Dye dye = ModArmor.Dye.NONE;
        private final List<ModArmor.Coating> coatings = new ArrayList<>();
        private List<String> armorModels = List.of();
        private boolean visor;
        private ModArmor.Protection protection = ModArmor.Protection.NONE;
        private ModArmor.Protection visorBonus = ModArmor.Protection.NONE;
        private boolean narrowView;
        private boolean hoodCompatible;
        private CloakType cloak;

        // Снаряжение и контейнеры
        private GearType gear;
        private ContainerType containerType;
        private int containerCapacity;
        private double carryBonusKg;

        private Builder(String id, String nameRu, String nameEn, Gender gender) {
            this.id = id;
            this.nameRu = nameRu;
            this.nameEn = nameEn;
            this.gender = gender;
        }

        public Builder category(Category category) {
            this.category = category;
            return this;
        }

        public Builder traits(ModTraits.Trait... traits) {
            this.traits = Arrays.stream(traits).map(ModTraits.Trait::id).toList();
            return this;
        }

        public Builder lore(String ru, String en) {
            return lore(ru, en, ModLore.DEFAULT_COLOR);
        }

        public Builder lore(String ru, String en, int color) {
            this.loreRu = ru;
            this.loreEn = en;
            this.loreColor = color;
            return this;
        }

        public Builder stacksTo(int maxStack) {
            if (maxStack < 1 || maxStack > 99) {
                throw new IllegalArgumentException(id + ": размер стака должен быть от 1 до 99");
            }
            this.maxStack = maxStack;
            return this;
        }

        public Builder grams(double grams) {
            this.weightGrams = grams;
            return this;
        }

        /** Прочность инструмента; стакается по 1. */
        public Builder durability(int durability) {
            if (durability < 1) {
                throw new IllegalArgumentException(id + ": прочность должна быть больше 0");
            }
            this.durability = durability;
            this.maxStack = 1;
            return this;
        }

        /** Как достаётся в руку: время и звук (ТЗ 5.3). */
        public Builder draw(DrawType type) {
            this.draw = type;
            return this;
        }

        public Builder kg(double kg) {
            this.weightGrams = kg * 1000;
            return this;
        }

        /** Сытость (шкала голода 0–100) и насыщение (запас, который тратится первым, не больше сытости игрока). */
        public Builder food(int nutrition, float saturation) {
            if (nutrition < 0 || saturation < 0) {
                throw new IllegalArgumentException(id + ": сытость и насыщение не могут быть отрицательными");
            }
            if (nutrition == 0 && saturation > 0) {
                throw new IllegalArgumentException(id + ": насыщение без сытости Minecraft не поддерживает");
            }
            this.edible = true;
            this.nutrition = nutrition;
            this.saturation = saturation;
            return this;
        }

        /** Питьё: анимация питья; можно выпить, если хочется пить или если напиток сытный. */
        public Builder drink() {
            this.drink = true;
            return this;
        }

        /** Жажда: + утоляет, − усиливает (шкала жажды 0–100). */
        public Builder thirst(int thirst) {
            this.thirst = thirst;
            return this;
        }

        /** Срок годности в игровых сутках. */
        public Builder shelfLife(int days) {
            this.shelfLifeDays = days;
            return this;
        }

        /** Что остаётся в руке после еды или питья: Items.BOWL, Items.GLASS_BOTTLE... */
        public Builder returns(ItemLike container) {
            this.returns = container;
            return this;
        }

        /** Эффект «Сытость» на столько секунд после еды (FoodEffects): жареное, каши. */
        public Builder satiety(int seconds) {
            this.fedSeconds = seconds;
            return this;
        }

        /** Эффект «Улучшенное насыщение» на столько секунд (FoodEffects): горячие сложные блюда. */
        public Builder wellFed(int seconds) {
            this.fedSeconds = -seconds;
            return this;
        }

        /** Вещь брони: слот задаёт ячейку, плоскость и порядок отрисовки. Стакается по 1. */
        public Builder armor(ArmorSlot slot) {
            this.armorSlot = slot;
            this.maxStack = 1;
            return this;
        }

        /** Материал для звука, если не такой, как по умолчанию у слота (ArmorKind.forSlot): латные перчатки, тканевые рукавицы. */
        public Builder armorKind(ArmorKind kind) {
            this.armorKind = kind;
            return this;
        }

        /** Свой порядок отрисовки вместо порядка слота: ботфорты поверх понож, жак поверх кольчуги. */
        public Builder layerOrder(int order) {
            if (order <= ModArmor.ArmorInfo.SLOT_ORDER) {
                throw new IllegalArgumentException(id + ": порядок отрисовки должен быть больше 0");
            }
            this.layerOrder = order;
            return this;
        }

        /** Красится как ткань: текстура в оттенках серого, неокрашиваемые детали — в <id>_overlay.png. */
        public Builder dyeable() {
            if (dye == ModArmor.Dye.COATED) {
                throw new IllegalArgumentException(id + ": .dyeable() и покрытие вместе не бывают");
            }
            this.dye = ModArmor.Dye.CLOTH;

            return this;
        }

        /** Красится эмалью: <id>.png в цвете, серый слой <id>_enamel.png, неокрашиваемое — в <id>_overlay.png. */
        public Builder enamel() {
            return coating(ModArmor.Coating.ENAMEL);
        }

        /** Красится росписью: <id>.png в цвете, серый слой <id>_painted.png, неокрашиваемое — в <id>_overlay.png. */
        public Builder painted() {
            return coating(ModArmor.Coating.PAINT);
        }

        // Покрытия копятся: .enamel().painted() — бывает и так, и так; первое — по умолчанию
        private Builder coating(ModArmor.Coating coating) {
            if (dye == ModArmor.Dye.CLOTH) {
                throw new IllegalArgumentException(id + ": .dyeable() и покрытие вместе не бывают");
            }
            this.dye = ModArmor.Dye.COATED;
            if (!coatings.contains(coating)) {
                coatings.add(coating);
            }
            return this;
        }

        /** Выступающие детали (забрало, рога, поля): models/armor/<папка слота>/<имя>. */
        public Builder armorModel(String... names) {
            this.armorModels = List.of(names);
            return this;
        }

        /** Подвижное забрало: поднимается клавишей, опущенное сужает обзор. Кость «visor» в модели детали (.armorModel(...)) поворачивается, если модель есть. */
        public Builder visor() {
            this.visor = true;
            return this;
        }

        /** Забрало, опущенное прибавляет к защите (броня, твёрдость брони); поднятое — защита как в .protection(...). */
        public Builder visor(double armorBonus, double toughnessBonus) {
            this.visorBonus = new ModArmor.Protection(armorBonus, toughnessBonus);
            return visor();
        }

        /** Защита: броня и твёрдость брони, как у ванильных атрибутов. Временно, до этапа 7 ТЗ брони. */
        public Builder protection(double armor, double toughness) {
            this.protection = new ModArmor.Protection(armor, toughness);
            return this;
        }

        /** Шлем без забрала с узким вырезом для лица: обзор сужен всегда, как опущенным забралом. */
        public Builder narrowView() {
            this.narrowView = true;
            return this;
        }

        /** Шлем, поверх которого можно надеть капюшон: небольшой, без выступающих деталей. */
        public Builder hoodCompatible() {
            this.hoodCompatible = true;
            return this;
        }

        /** Вид накидки — обязателен для слота накидки. */
        public Builder cloak(CloakType type) {
            this.cloak = type;
            return this;
        }

        /** Снаряжение: кольцо, подвеска, браслет, фонарь, пояс, колчан. Стакается по 1, надевается ПКМ. */
        public Builder gear(GearType type) {
            this.gear = type;
            this.maxStack = 1;
            return this;
        }

        /** Контейнер: рюкзак, сумка, подсумок, колчан (надеваются сами). Стакается по 1. */
        public Builder container(ContainerType type, int capacity) {
            if (capacity < 1 || capacity > type.maxCapacity()) {
                throw new IllegalArgumentException(id + ": вместимость " + type + " — от 1 до " + type.maxCapacity());
            }
            this.containerType = type;
            this.containerCapacity = capacity;
            this.gear = type.gear();
            this.maxStack = 1;
            return this;
        }

        /** Прибавка к грузоподъёмности, пока вещь надета на спину (только рюкзак и сумка). */
        public Builder carryBonus(double kg) {
            this.carryBonusKg = kg;
            return this;
        }

        /** Обычный предмет, еда, питьё или броня. */
        public DeferredItem<Item> register() {
            return add(ITEMS.<Item>register(id, () -> {
                if (armorSlot != null) {
                    return new ArmorPieceItem(properties());
                }
                if (gear != null) {
                    return new GearItem(properties());
                }
                return drink ? new DrinkItem(properties()) : new Item(properties());
            }));
        }

        /** Предмет своего класса (оружие, инструмент...): register(MyItem::new) */
        public <I extends Item> DeferredItem<I> register(Function<Item.Properties, ? extends I> factory) {
            return add(ITEMS.register(id, () -> factory.apply(properties())));
        }

        private Item.Properties properties() {
            Item.Properties properties = new Item.Properties().stacksTo(maxStack);
            if (durability > 0) {
                properties.durability(durability);
            }
            if (edible || drink) {
                properties.food(foodProperties());
            }
            return properties;
        }

        private FoodProperties foodProperties() {
            // Настоящие значения — в ModFoods (их читает Hunger). Здесь ванильные, ÷5, для чужих подсказок
            int vanillaNutrition = nutrition > 0 ? Math.max(1, Math.round((float) nutrition / Hunger.VANILLA_SCALE)) : 0;
            FoodProperties.Builder food = new FoodProperties.Builder().nutrition(vanillaNutrition);
            if (vanillaNutrition > 0) {
                // В Minecraft насыщение = сытость × множитель × 2; пересчитываем, чтобы задавать его напрямую
                food.saturationModifier(saturation / Hunger.VANILLA_SCALE / (vanillaNutrition * 2.0F));
            }
            if (drink) {
                food.alwaysEdible(); // когда пить можно, решает DrinkItem
            }
            if (returns != null) {
                food.usingConvertsTo(returns);
            }
            return food.build();
        }

        private <I extends Item> DeferredItem<I> add(DeferredItem<I> item) {
            Definition def = new Definition(item, nameRu, nameEn, gender, category, traits);
            DEFINITIONS.add(def);
            BY_ID.put(item.getId(), def);
            if (loreRu != null) {
                ModLore.put(item.getId(), loreRu, loreEn, loreColor);
            }
            if (draw != null) {
                ModDraw.put(item.getId(), draw);
            }
            if (weightGrams >= 0) {
                ModWeights.put(item.getId(), weightGrams);
            }
            if (edible || thirst != 0 || shelfLifeDays > 0) {
                ModFoods.put(item.getId(), new ModFoods.FoodInfo(nutrition, saturation, thirst, shelfLifeDays));
            }
            if (fedSeconds != 0) {
                FoodEffects.put(item.getId(), fedSeconds);
            }
            if (gear != null || containerType != null) {
                ModGear.put(item.getId(), gearInfo());
            } else if (carryBonusKg != 0) {
                throw new IllegalArgumentException(id + ": .carryBonus(...) без .container(...)");
            }
            if (armorSlot != null) {
                ModArmor.put(item.getId(), armorInfo());
            } else if (armorKind != null || layerOrder != ModArmor.ArmorInfo.SLOT_ORDER || dye != ModArmor.Dye.NONE || !armorModels.isEmpty()
                    || visor || !protection.isNone() || narrowView || hoodCompatible || cloak != null) {
                throw new IllegalArgumentException(id + ": свойства брони без .armor(...)");
            }
            return item;
        }

        private ModGear.GearInfo gearInfo() {
            if (armorSlot != null) {
                throw new IllegalArgumentException(id + ": вещь не может быть и бронёй, и снаряжением");
            }
            if (maxStack != 1) {
                throw new IllegalArgumentException(id + ": снаряжение стакается только по 1");
            }
            if (carryBonusKg != 0 && gear != GearType.BACK) {
                throw new IllegalArgumentException(id + ": .carryBonus(...) — только у рюкзака и сумки");
            }
            return new ModGear.GearInfo(gear, containerType, containerCapacity, carryBonusKg);
        }

        // Проверка сочетаний: что разрешено только определённым слотам
        private ModArmor.ArmorInfo armorInfo() {
            if (maxStack != 1) {
                throw new IllegalArgumentException(id + ": броня стакается только по 1");
            }
            if (narrowView && (armorSlot != ArmorSlot.HELMET || visor)) {
                throw new IllegalArgumentException(id + ": .narrowView() — только для шлема без забрала (у забрала обзор свой)");
            }
            if (hoodCompatible && armorSlot != ArmorSlot.HELMET) {
                throw new IllegalArgumentException(id + ": .hoodCompatible() — только для шлемов");
            }
            if ((cloak != null) != (armorSlot == ArmorSlot.CLOAK)) {
                throw new IllegalArgumentException(id + ": у накидки обязателен .cloak(...), у остальных его быть не может");
            }
            if (dye == ModArmor.Dye.COATED && armorSlot == ArmorSlot.CLOAK) {
                throw new IllegalArgumentException(id + ": накидка не покрывается эмалью и росписью — только .dyeable()");
            }
            if (protection.armor() < 0 || protection.toughness() < 0 || visorBonus.armor() < 0 || visorBonus.toughness() < 0) {
                throw new IllegalArgumentException(id + ": защита не бывает отрицательной");
            }
            return new ModArmor.ArmorInfo(armorSlot, armorKind != null ? armorKind : ArmorKind.forSlot(armorSlot), layerOrder, dye, List.copyOf(coatings), List.copyOf(armorModels),
                    visor, protection, visorBonus, narrowView, hoodCompatible, cloak);
        }
    }
}