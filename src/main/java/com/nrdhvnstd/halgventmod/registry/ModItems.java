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
import com.nrdhvnstd.halgventmod.food.DrinkItem;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.nrdhvnstd.halgventmod.lock.KeyRingItem;

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
    //   Еда и питьё:
    //     .food(сытость, насыщение)                  — съедобно, в единицах шкалы голода 0–100
    //     .drink()                                   — пьётся, а не ешется
    //     .thirst(n)                                 — жажда: + утоляет, − усиливает (шкала 0–100)
    //     .shelfLife(суток)                          — срок годности, без него не портится
    //     .returns(Items.BOWL)                       — что остаётся в руке (миска, бутылка)
    //   Броня:
    //     .armor(ArmorSlot.HELMET)                   — вещь брони своего слота; стак 1, надевается ПКМ
    //     .layerOrder(n)                             — свой порядок отрисовки вместо порядка слота
    //     .dyeable()                                 — красится (текстура в сером + <id>_overlay.png)
    //     .armorModel("hounskull_visor")             — выступающие детали (модели), необязательно
    //     .hoodCompatible()                          — только шлем: поверх него можно надеть капюшон
    //     .cloak(CloakType.HOODED_CAPE)              — только накидка: капюшон, плащ или оба
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
            .lore("Для администрации.", "For administration.")
            .stacksTo(1)
            .register();
    public static final DeferredItem<KeyRingItem> KEY_RING = item("key_ring", "Связка ключей", "Key ring", Gender.F)
            .category(Category.TOOLS)
            .lore("Shift + ПКМ связкой по замку отпирает его, если на связке есть подходящий ключ.",
                    "Shift + right-click a lock with the ring to open it if any key on it fits.")
            .stacksTo(1)
            .grams(20)
            .register(KeyRingItem::new);



    // ----- Доспехи -----
    // Шлемы, которые надеваются под капюшон
    public static final DeferredItem<Item> CERVELLIERE = item("cervelliere", "Цервельер", "Cervelliere", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .lore("Простая железная шапка по форме головы. Лёгкая и неприметная: прячется под капюшоном.",
                    "A plain iron skullcap shaped to the head. Light and unobtrusive, it hides under a hood.")
            .armor(ArmorSlot.HELMET)
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
    // Проверочные шлемы: вес временный, описаний пока нет
    public static final DeferredItem<Item> KETTLE_HAT = item("kettle_hat", "Капеллина", "Kettle hat", Gender.F)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .kg(2)
            .register();
    // Проверочный набор для отрисовки: текстуры — временные заглушки, иконок пока нет (кроме хундсгугеля)
    public static final DeferredItem<Item> BASCINET_HOUNSKULL = item("bascinet_hounskull", "Хундсгугель", "Hounskull", Gender.M)
            .category(Category.ARMOR)
            .armor(ArmorSlot.HELMET)
            .armorModel("bascinet_hounskull_visor")
            .kg(3.2)
            .register();
    public static final DeferredItem<Item> HOODED_CLOAK = item("hooded_cloak", "Плащ с капюшоном", "Hooded cloak", Gender.M)
            .category(Category.ARMOR)
            .armor(ArmorSlot.CLOAK)
            .cloak(CloakType.HOODED_CAPE)
            .dyeable()
            .kg(1.8)
            .register();
    public static final DeferredItem<Item> QUILTED_AKETON = item("quilted_aketon", "Акетон", "Aketon", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.ARMOR)
            .lore("Стёганая куртка, набитая паклей или хлопком. Смягчает удары и не даёт кольчуге натирать тело.",
                    "A quilted jacket stuffed with tow or cotton. It softens blows and keeps mail from chafing.")
            .armor(ArmorSlot.PADDING)
            .dyeable()
            .kg(2.5)
            .register();
    public static final DeferredItem<Item> SHORT_MAIL = item("short_mail", "Короткая кольчуга", "Short mail", Gender.F)
            .category(Category.ARMOR)
            .armor(ArmorSlot.MAIL)
            .kg(8)
            .register();
    public static final DeferredItem<Item> BOOTS = item("boots", "Сапоги", "Boots", Gender.PL)
            .category(Category.ARMOR)
            .armor(ArmorSlot.BOOTS)
            .kg(1.5)
            .register();
    public static final DeferredItem<Item> GREAVES = item("greaves", "Поножи", "Greaves", Gender.PL)
            .category(Category.ARMOR)
            .armor(ArmorSlot.GREAVES)
            .kg(2)
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
        private double weightGrams = -1; // -1 — не задан, берётся вес по умолчанию

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
        private boolean dyeable;
        private List<String> armorModels = List.of();
        private boolean hoodCompatible;
        private CloakType cloak;

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

        /** Красится: текстура в оттенках серого, неокрашиваемые детали — в <id>_overlay.png. */
        public Builder dyeable() {
            this.dyeable = true;
            return this;
        }

        /** Выступающие детали (забрало, рога, поля): models/armor/<папка слота>/<имя>. */
        public Builder armorModel(String... names) {
            this.armorModels = List.of(names);
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

        /** Обычный предмет, еда, питьё или броня. */
        public DeferredItem<Item> register() {
            return add(ITEMS.<Item>register(id, () -> {
                if (armorSlot != null) {
                    return new ArmorPieceItem(properties());
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
            if (weightGrams >= 0) {
                ModWeights.put(item.getId(), weightGrams);
            }
            if (edible || thirst != 0 || shelfLifeDays > 0) {
                ModFoods.put(item.getId(), new ModFoods.FoodInfo(nutrition, saturation, thirst, shelfLifeDays));
            }
            if (armorSlot != null) {
                ModArmor.put(item.getId(), armorInfo());
            } else if (layerOrder != ModArmor.ArmorInfo.SLOT_ORDER || dyeable || !armorModels.isEmpty()
                    || hoodCompatible || cloak != null) {
                throw new IllegalArgumentException(id + ": свойства брони без .armor(...)");
            }
            return item;
        }

        // Проверка сочетаний: что разрешено только определённым слотам
        private ModArmor.ArmorInfo armorInfo() {
            if (maxStack != 1) {
                throw new IllegalArgumentException(id + ": броня стакается только по 1");
            }
            if (hoodCompatible && armorSlot != ArmorSlot.HELMET) {
                throw new IllegalArgumentException(id + ": .hoodCompatible() — только для шлемов");
            }
            if ((cloak != null) != (armorSlot == ArmorSlot.CLOAK)) {
                throw new IllegalArgumentException(id + ": у накидки обязателен .cloak(...), у остальных его быть не может");
            }
            return new ModArmor.ArmorInfo(armorSlot, layerOrder, dyeable, List.copyOf(armorModels), hoodCompatible, cloak);
        }
    }
}