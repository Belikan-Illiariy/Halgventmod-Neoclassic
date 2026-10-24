package com.nrdhvnstd.halgventmod.glass;

import com.nrdhvnstd.halgventmod.draw.DrawType;
import com.nrdhvnstd.halgventmod.item.Category;
import com.nrdhvnstd.halgventmod.item.Gender;
import com.nrdhvnstd.halgventmod.item.ModTraits;
import com.nrdhvnstd.halgventmod.pottery.VesselForm;
import com.nrdhvnstd.halgventmod.pottery.WareItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

import static com.nrdhvnstd.halgventmod.registry.ModItems.item;

/**
 * Предметы стекольного дела: поташ, бой стекла, стеклянная посуда, стеклодувная трубка, печь. Регистрируются через
 * билдер ModItems ({@link #init()} зовёт ModItems.register). Что из чего — в {@link Glassmaking}.
 */
public final class GlassItems {
    public static final DeferredItem<Item> POTASH = item("potash", "Поташ", "Potash", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.GLASS)
            .lore("Белая соль, вываренная из древесной золы: зола с водой в бродильной бочке. С песком в стекловаренной "
                            + "печи — стекло: на два песка один поташ.",
                    "A white salt leached from wood ash: ash and water in a fermenting barrel. With sand in a glass furnace "
                            + "it makes glass: one potash to two sand.")
            .grams(100)
            .register();
    public static final DeferredItem<Item> CULLET = item("cullet", "Бой стекла", "Cullet", Gender.M)
            .traits(ModTraits.COMMON)
            .category(Category.GLASS)
            .lore("Осколки битого и лопнувшего стекла. В печи плавятся вдвое быстрее шихты — порция стекломассы с каждого.",
                    "Shards of broken and burst glass. In the furnace they melt twice as fast as a batch: a portion of glass each.")
            .grams(200)
            .register();
    public static final DeferredItem<WareItem> GLASS_GOBLET = item("glass_goblet", "Стеклянный бокал", "Glass goblet",
            Gender.M)
            .traits(ModTraits.UNCOMMON)
            .category(Category.GLASS)
            .lore("Выдувной бокал на ножке. Мера воды или питья: ПКМ по воде — набрать, ПКМ — пить; налить можно из "
                            + "кувшина, корчаги, бочки. Бьётся, как глиняный.",
                    "A blown goblet on a stem. One measure of water or a drink: right-click water to fill, right-click to "
                            + "drink; fill it from a jug, amphora or cask. It breaks like clay.")
            .stacksTo(1)
            .grams(200)
            .register(properties -> new WareItem(properties, VesselForm.CUP, WareItem.Material.GLASS));
    public static final DeferredItem<WareItem> GLASS_FLASK = item("glass_flask", "Бутыль", "Glass flask", Gender.F)
            .traits(ModTraits.UNCOMMON)
            .category(Category.GLASS)
            .lore("Выдувная бутыль с узким горлом. Четыре меры воды или четыре склянки питья, как кувшин. Бьётся.",
                    "A blown flask with a narrow neck. Four measures of water or four bottles of a drink, like a jug. It breaks.")
            .stacksTo(1)
            .grams(600)
            .register(properties -> new WareItem(properties, VesselForm.JUG, WareItem.Material.GLASS));
    public static final DeferredItem<Item> BLOWPIPE = item("blowpipe", "Стеклодувная трубка", "Blowpipe", Gender.F)
            .traits(ModTraits.UNCOMMON)
            .category(Category.TOOLS)
            .lore("Длинная железная трубка. E ею по горячей стекловаренной печи — набрать стекломассу и выдуть изделие, "
                            + "какое выбрано в окне печи.",
                    "A long iron tube. E with it at a hot glass furnace to gather glass and blow the piece chosen in the "
                            + "furnace window.")
            .durability(300)
            .kg(1.5)
            .draw(DrawType.TOOL)
            .register();
    public static final DeferredItem<BlockItem> GLASS_FURNACE = item("glass_furnace", "Стекловаренная печь", "Glass furnace",
            Gender.F)
            .category(Category.TOOLS)
            .lore("Кирпичная печь-колпак 2×2×2 с рабочими окнами. Дрова и огниво — жар до 1250°. Песок, поташ, флюс, бой "
                            + "стекла — шихта; от 1100° варится стекломасса. Трубкой у окна — выдувать; готовое отжигается в камере. "
                            + "ПКМ — окно печи.",
                    "A 2×2×2 brick hood furnace with working holes. Firewood and a flint bring it to 1250°. Sand, potash, flux "
                            + "and cullet are the batch; from 1100° it melts into glass. Blow at a hole with a pipe; the work is "
                            + "annealed in the chamber. Right-click opens the furnace.")
            .kg(800)
            .register(properties -> new BlockItem(GlassBlocks.GLASS_FURNACE.get(), properties));

    private GlassItems() {}

    /** Загрузить класс: предметы попадают в ModItems до регистрации. */
    public static void init() {
    }
}
