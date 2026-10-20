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
import com.nrdhvnstd.halgventmod.armor.ArmorPieceItem;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.armor.CloakType;
import com.nrdhvnstd.halgventmod.armor.ModArmor;
import com.nrdhvnstd.halgventmod.draw.DrawType;
import com.nrdhvnstd.halgventmod.draw.ModDraw;
import com.nrdhvnstd.halgventmod.food.DrinkItem;
import com.nrdhvnstd.halgventmod.gear.GearItem;
import com.nrdhvnstd.halgventmod.gear.GearType;
import com.nrdhvnstd.halgventmod.gear.ModGear;
import com.nrdhvnstd.halgventmod.storage.ContainerType;
import com.nrdhvnstd.halgventmod.food.Hunger;
import com.nrdhvnstd.halgventmod.food.ModFoods;
import com.nrdhvnstd.halgventmod.item.Category;
import com.nrdhvnstd.halgventmod.item.Gender;
import com.nrdhvnstd.halgventmod.item.ModLore;
import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.weight.ModWeights;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
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
    //   Броня:
    //     .armor(ArmorSlot.HELMET)                   — вещь брони своего слота; стак 1, надевается ПКМ
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
    public static final DeferredItem<HarvestToolItem> SCYTHE = item("scythe", "Коса", "Scythe", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.TOOLS)
            .lore("Срезает растения рядом по три поперёк взгляда. Каждый стебель тупит лезвие.",
                    "Cuts crops three in a row across your view. Every stalk dulls the blade.")
            .durability(250)
            .kg(2.5)
            .draw(DrawType.HEAVY)
            .register(HarvestToolItem::scythe);



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
            .grams(300)
            .register();
    public static final DeferredItem<Item> CLOTH_GAUNTLETS = item("cloth_gauntlets", "Тканевые перчатки", "Cloth gauntlets", Gender.PL)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
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
            .grams(700)
            .register();
    public static final DeferredItem<Item> MUFFLERS = item("mufflers", "Кольчужные рукавицы", "Mufflers", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .grams(600)
            .register();
    public static final DeferredItem<Item> VAMBRACES = item("vambraces", "Наручи", "Vambraces", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .grams(800)
            .register();
    public static final DeferredItem<Item> LAMELLAR_GAUNTLETS = item("lamellar_gauntlets", "Ламеллярные перчатки", "Lamellar gauntlets", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .grams(900)
            .register();
    public static final DeferredItem<Item> SPLINTED_GAUNTLETS = item("splinted_gauntlets", "Шинные перчатки", "Splinted gauntlets", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .grams(900)
            .register();
    public static final DeferredItem<Item> SIMPLE_PLATE_GAUNTLETS = item("simple_plate_gauntlets", "Простые латные перчатки", "Simple plate gauntlets", Gender.PL)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .kg(1)
            .register();
    public static final DeferredItem<Item> GWALDEN_GAUNTLETS = item("gwalden_gauntlets", "Гвальденские латные перчатки", "Gwalden gauntlets", Gender.PL)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .kg(1.2)
            .register();
    public static final DeferredItem<Item> MARINIKIAN_GAUNTLETS = item("marinikian_gauntlets", "Мариникийские латные перчатки", "Marinikian gauntlets", Gender.PL)
            .traits(ModTraits.RARE)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GLOVES)
            .kg(1.2)
            .register();



    // ----- Еда и питье -----
    public static final DeferredItem<Item> WHEAT_BREAD = item("wheat_bread", "Хлеб", "Bread", Gender.M)
            .traits(ModTraits.COMMON)
            .lore("Белый хлеб из пшеницы, подаваемый на стол к аристократам. Если много есть в детстве, то станешь пидаром.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(4)
            .category(Category.FOOD)
            .grams(400)
            .food(20, 10.0F)
            .shelfLife(8)
            .register();
    public static final DeferredItem<Item> RYE_BREAD = item("rye_bread", "Хлеб", "Bread", Gender.M)
            .traits(ModTraits.COMMON)
            .lore("Крестьянский черный хлеб для настоящих мужиков.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(4)
            .category(Category.FOOD)
            .grams(400)
            .food(20, 10.0F)
            .shelfLife(8)
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




    // ----- Материалы -----
    public static final DeferredItem<Item> BEAR_HIDE = item("bear_hide", "Шкура", "Hide", Gender.F)
            .traits(ModTraits.UNCOMMON, ModTraits.RAW)
            .lore("Большая шкура короля лесов. Лучше использовать ее как ковер: кошелек из медведя будет расточительным.",
                    "Hastily skinned and reeking of beast. Until it is dressed, it is good only for the flies.")
            .stacksTo(4)
            .category(Category.MATERIALS)
            .kg(14)
            .register();



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

        // Броня
        private ArmorSlot armorSlot;
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

        /** Вещь брони: слот задаёт ячейку, плоскость и порядок отрисовки. Стакается по 1. */
        public Builder armor(ArmorSlot slot) {
            this.armorSlot = slot;
            this.maxStack = 1;
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
            if (gear != null || containerType != null) {
                ModGear.put(item.getId(), gearInfo());
            } else if (carryBonusKg != 0) {
                throw new IllegalArgumentException(id + ": .carryBonus(...) без .container(...)");
            }
            if (armorSlot != null) {
                ModArmor.put(item.getId(), armorInfo());
            } else if (layerOrder != ModArmor.ArmorInfo.SLOT_ORDER || dye != ModArmor.Dye.NONE || !armorModels.isEmpty()
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
            return new ModArmor.ArmorInfo(armorSlot, layerOrder, dye, List.copyOf(coatings), List.copyOf(armorModels),
                    visor, protection, visorBonus, narrowView, hoodCompatible, cloak);
        }
    }
}