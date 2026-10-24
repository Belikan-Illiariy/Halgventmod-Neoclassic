package com.nrdhvnstd.halgventmod.alchemy;

import com.nrdhvnstd.halgventmod.item.Category;
import com.nrdhvnstd.halgventmod.item.Gender;
import com.nrdhvnstd.halgventmod.item.ModTraits;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;

import static com.nrdhvnstd.halgventmod.registry.ModItems.item;

/**
 * Предметы алхимии: вещества, снадобья, реторта. Регистрируются через билдер ModItems ({@link #init()} зовёт
 * ModItems.register). Что из чего — в {@link Alchemy}.
 */
public final class AlchemyItems {
    // ----- Вещества -----
    public static final DeferredItem<Item> SULFUR = item("sulfur", "Сера", "Sulphur", Gender.F)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ALCHEMY)
            .lore("Жёлтые комья горючей серы. Её выгоняют из медного колчедана в реторте; возогнанная ещё раз, "
                            + "она становится серным цветом.",
                    "Yellow lumps of burning sulphur. It is driven out of copper pyrite in a retort; sublimed once more, "
                            + "it becomes flowers of sulphur.")
            .grams(100)
            .register();
    public static final DeferredItem<Item> SULFUR_FLOWERS = item("sulfur_flowers", "Серный цвет", "Flowers of sulphur",
            Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ALCHEMY)
            .lore("Тонкий жёлтый налёт со стенок приёмника — «дух серы». Настоянный на крепком, даёт серный бальзам.",
                    "A fine yellow bloom from the walls of the receiver, the 'spirit of sulphur'. Steeped in spirit, it "
                            + "makes sulphur balm.")
            .grams(50)
            .register();
    public static final DeferredItem<Item> LEAD_ASH = item("lead_ash", "Свинцовый пепел", "Lead ash", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ALCHEMY)
            .lore("Свинец, прокалённый в гончарном горне до жёлтого глёта. Ядовит. В уксусе осаждается белой краской — "
                            + "свинцовыми белилами.",
                    "Lead calcined in a pottery kiln to yellow litharge. Poisonous. In vinegar it settles as white paint, "
                            + "white lead.")
            .grams(150)
            .register();
    public static final DeferredItem<Item> BONE_ASH = item("bone_ash", "Костяная зола", "Bone ash", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.ALCHEMY)
            .lore("Кость, прокалённая добела в горне или оставшаяся в реторте. С мёдом на крепком — териак, "
                            + "противоядие.",
                    "Bone calcined white in a kiln or left over in a retort. With honey in spirit it makes theriac, "
                            + "the antidote.")
            .grams(60)
            .register();
    public static final DeferredItem<SalAmmoniacItem> SAL_AMMONIAC = item("sal_ammoniac", "Нашатырь", "Sal ammoniac",
            Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ALCHEMY)
            .lore("Белые кристаллы, возогнанные из кости. ПКМ — понюхать: резкий дух приводит в чувство — трезвит, "
                            + "снимает тошноту и темноту в глазах.",
                    "White crystals sublimed from bone. Right-click to sniff: the sharp smell brings you round — it sobers, "
                            + "clears nausea and dark spots.")
            .stacksTo(16)
            .grams(30)
            .register(SalAmmoniacItem::new);
    public static final DeferredItem<Item> VERDIGRIS = item("verdigris", "Ярь-медянка", "Verdigris", Gender.F)
            .traits(ModTraits.COMMON)
            .category(Category.ALCHEMY)
            .lore("Зелёная медная ржавчина: медь, пролежавшая в уксусе. Краска для живописцев, а на крепком — "
                            + "ранозаживляющий бальзам.",
                    "Green copper rust: copper left in vinegar. A painter's pigment, and in spirit a wound balm.")
            .grams(50)
            .register();
    public static final DeferredItem<Item> VITRIOL = item("vitriol", "Купорос", "Blue vitriol", Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ALCHEMY)
            .lore("Синие кристаллы, выпавшие из воды, в которой мокла обожжённая медная руда. С ртутью в реторте — сулема.",
                    "Blue crystals settled out of water in which roasted copper ore was soaked. With quicksilver in a retort "
                            + "it makes corrosive sublimate.")
            .grams(50)
            .register();
    public static final DeferredItem<SublimateItem> SUBLIMATE = item("sublimate", "Сулема", "Corrosive sublimate", Gender.F)
            .traits(ModTraits.RARE)
            .category(Category.ALCHEMY)
            .lore("Едкий белый яд из ртути и купороса. В инвентаре ПКМ сулемой по клинку — намазать: четыре удара "
                            + "отравляют.",
                    "A caustic white poison from quicksilver and vitriol. In your inventory, right-click it onto a blade to "
                            + "coat it: four blows poison.")
            .stacksTo(16)
            .grams(20)
            .register(SublimateItem::new);
    public static final DeferredItem<Item> HERB_POWDER = item("herb_powder", "Травяной порошок", "Herb powder", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ALCHEMY)
            .lore("Сушёная трава, истолчённая в ступе. На крепком в бочке — эссенция: действует втрое дольше отвара.",
                    "Dried herb ground in a mortar. Steeped in spirit in a barrel it makes an essence that lasts three "
                            + "times as long as a tea.")
            .grams(10)
            .register();

    // ----- Снадобья -----
    public static final DeferredItem<RemedyItem> WOUND_BALM = item("wound_balm", "Ранозаживляющий бальзам", "Wound balm",
            Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ALCHEMY)
            .lore("Крепкое, настоянное на ярь-медянке. Затягивает раны: сразу +2 сердца и Регенерация I на 30 с.",
                    "Spirit steeped on verdigris. It closes wounds: +2 hearts at once and Regeneration I for 30 s.")
            .stacksTo(8).grams(300)
            .drink().food(0, 0.0F).thirst(0).returns(Items.GLASS_BOTTLE)
            .register(properties -> new RemedyItem(properties, Alchemy.Remedy.WOUND_BALM));
    public static final DeferredItem<RemedyItem> SULFUR_BALM = item("sulfur_balm", "Серный бальзам", "Sulphur balm",
            Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ALCHEMY)
            .lore("Крепкое на серном цвете. Кто его выпил, того огонь не берёт: Огнестойкость на 3 мин.",
                    "Spirit on flowers of sulphur. Fire won't take whoever drinks it: Fire Resistance for 3 min.")
            .stacksTo(8).grams(300)
            .drink().food(0, 0.0F).thirst(0).returns(Items.GLASS_BOTTLE)
            .register(properties -> new RemedyItem(properties, Alchemy.Remedy.SULFUR_BALM));
    public static final DeferredItem<RemedyItem> THERIAC = item("theriac", "Териак", "Theriac", Gender.M)
            .traits(ModTraits.RARE)
            .category(Category.ALCHEMY)
            .lore("Противоядие: крепкое с мёдом на костяной золе. Снимает яд, иссушение, отравление, тошноту и голод, "
                            + "Регенерация I на 10 с.",
                    "The antidote: spirit with honey on bone ash. Clears poison, wither, food poisoning, nausea and hunger, "
                            + "Regeneration I for 10 s.")
            .stacksTo(8).grams(300)
            .drink().food(0, 0.0F).thirst(0).returns(Items.GLASS_BOTTLE)
            .register(properties -> new RemedyItem(properties, Alchemy.Remedy.THERIAC));
    public static final DeferredItem<RemedyItem> HERB_ESSENCE = item("herb_essence", "Травяная эссенция", "Herb essence",
            Gender.F)
            .traits(ModTraits.UNCOMMON)
            .category(Category.ALCHEMY)
            .lore("Крепкое на травяном порошке. Действует, как отвар этой травы, но втрое дольше.",
                    "Spirit on herb powder. It works like a tea of that herb, but three times as long.")
            .stacksTo(8).grams(300)
            .drink().food(0, 0.0F).thirst(0).returns(Items.GLASS_BOTTLE)
            .register(properties -> new RemedyItem(properties, Alchemy.Remedy.HERB_ESSENCE));

    // ----- Станция -----
    public static final DeferredItem<BlockItem> RETORT = item("retort", "Реторта", "Retort", Gender.F)
            .category(Category.TOOLS)
            .lore("Глиняная колба с длинным носом и приёмником. Ставится над огнём (очаг, костёр). E — положить, пустой "
                            + "рукой — забрать готовое, Shift + E — вынуть положенное. ПКМ — окно.",
                    "A clay flask with a long beak and a receiver. Put it over a fire (hearth, campfire). E puts things in, "
                            + "an empty hand takes what is done, Shift + E takes back what you put in. Right-click opens it.")
            .kg(4).register(properties -> new BlockItem(AlchemyBlocks.RETORT.get(), properties));

    private AlchemyItems() {}

    /** Загрузить класс: предметы попадают в ModItems до регистрации. */
    public static void init() {
    }
}
