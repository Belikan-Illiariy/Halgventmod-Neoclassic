package com.nrdhvnstd.halgventmod.glass;

import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.alchemy.AlchemyItems;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.smithing.SmithingItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Правила стекольного дела в одном месте. Меняйте здесь ⚠️
 *
 * <pre>
 * вода + зола (бродильная бочка)                      → поташ
 * песок ×2 + поташ (+ флюс — чище) в печи от 1100°    → порция стекломассы (бой стекла — порция сразу, быстрее)
 * стеклодувная трубка у печи от 1000° (мини-игра)     → склянки, бокал, бутыль, оконное стекло
 * отжиг в камере печи                                → крепкое; не отожжённое хрупко
 * добавка в печи (по одной на изделие) — цвет: ярь-медянка — зелёное, свинцовый пепел — хрусталь (+1 к качеству),
 * костяная зола — молочное, болотная руда — бурое, золотой песок — рубиновое; без добавки — лесное, зеленоватое
 * </pre>
 */
public final class Glassmaking {
    // Жар печи, °
    public static final float MAX_TEMP = 1250;      // к чему тянется горящая печь
    public static final float MELT_TEMP = 1100;     // с какого варится стекломасса
    public static final float WORK_TEMP = 1000;     // с какого можно набирать на трубку
    public static final float HEAT_UP = 0.6F;       // ° за тик, пока горит
    public static final float COOL_DOWN = 0.5F;     // ° за тик, когда погасла
    // Варка
    public static final int SAND_PER_PORTION = 2;
    public static final int POTASH_PER_PORTION = 1;
    public static final int MELT_TICKS = 600;       // порция из шихты; бой — вдвое быстрее
    public static final int MAX_BATCH = 16;         // песка, поташа, флюса, боя в печи — каждого
    public static final int MAX_MOLTEN = 8;         // порций стекломассы
    public static final int QUALITY_FLUX = 2;       // с флюсом — посредственное
    public static final int QUALITY_PLAIN = 1;      // без — мутное, плохое
    public static final int QUALITY_CULLET = 2;
    // Работа и отжиг
    public static final int WORK_MIN_TICKS = 300;   // мини-игра не короче
    public static final int FAIL_SCORE = 25;        // хуже — изделие лопнуло, в бой
    public static final int ANNEAL_TICKS = 1200;    // в камере отжига
    public static final int LEHR_SLOTS = 4;

    /** Свойство модели бокала и бутыли: номер цвета (Tint.ordinal) — своя иконка. */
    public static final net.minecraft.resources.ResourceLocation TINT =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.nrdhvnstd.halgventmod.HalgventMod.MODID, "tint");

    private Glassmaking() {}

    /** Что выдувают: сколько порций стекломассы и сколько выходит. */
    public enum Form {
        BOTTLES("bottles", 1),
        GOBLET("goblet", 1),
        FLASK("flask", 2),
        WINDOW("window", 2);

        private final String id;
        private final int cost;

        Form(String id, int cost) {
            this.id = id;
            this.cost = cost;
        }

        public String id() {
            return id;
        }

        public int cost() {
            return cost;
        }

        /** Красят ли: склянки — нет, остальное — добавкой. */
        public boolean tinted() {
            return this != BOTTLES;
        }

        /** Посуда с качеством (бокал, бутыль) — остальное как есть. */
        public boolean ware() {
            return this == GOBLET || this == FLASK;
        }

        public String langKey() {
            return "glass.halgventmod.form." + id;
        }

        public static Form byIndex(int index) {
            Form[] all = values();
            return all[Math.floorMod(index, all.length)];
        }
    }

    /** Цвет стекла: какая добавка его даёт и каким выйдет оконное стекло. */
    public enum Tint {
        FOREST("forest", stack -> false, Items.GLASS_PANE, 0),
        GREEN("green", stack -> stack.is(AlchemyItems.VERDIGRIS.get()), Items.GREEN_STAINED_GLASS_PANE, 0),
        CRYSTAL("crystal", stack -> stack.is(AlchemyItems.LEAD_ASH.get()), Items.GLASS_PANE, 1),
        MILK("milk", stack -> stack.is(AlchemyItems.BONE_ASH.get()), Items.WHITE_STAINED_GLASS_PANE, 0),
        AMBER("amber", stack -> stack.is(SmithingItems.BOG_IRON_ORE.get()), Items.BROWN_STAINED_GLASS_PANE, 0),
        RUBY("ruby", stack -> stack.is(SmithingItems.GOLD_DUST.get()), Items.RED_STAINED_GLASS_PANE, 1);

        private final String id;
        private final Predicate<ItemStack> colorant;
        private final Item pane;
        private final int bonus;

        Tint(String id, Predicate<ItemStack> colorant, Item pane, int bonus) {
            this.id = id;
            this.colorant = colorant;
            this.pane = pane;
            this.bonus = bonus;
        }

        public String id() {
            return id;
        }

        /** Прибавка к качеству (хрусталь, рубиновое). */
        public int bonus() {
            return bonus;
        }

        public String langKey() {
            return "glass.halgventmod.tint." + id;
        }

        /** Цвет от добавки; null — это не красит. */
        @Nullable
        public static Tint of(ItemStack colorant) {
            for (Tint tint : values()) {
                if (!colorant.isEmpty() && tint.colorant.test(colorant)) {
                    return tint;
                }
            }
            return null;
        }

        public static Tint byId(@Nullable String id) {
            for (Tint tint : values()) {
                if (tint.id.equals(id)) {
                    return tint;
                }
            }
            return FOREST;
        }
    }

    /** Песок для шихты. */
    public static boolean sand(ItemStack stack) {
        return stack.is(Items.SAND) || stack.is(Items.RED_SAND);
    }

    /** Что кладут в печь шихтой: песок, поташ, флюс, бой стекла. */
    public static boolean batch(ItemStack stack) {
        return sand(stack) || stack.is(GlassItems.POTASH.get()) || stack.is(SmithingItems.FLUX.get())
                || stack.is(GlassItems.CULLET.get());
    }

    /** Готовое изделие: склянки — 4, оконное стекло — 8 панелей цвета, бокал и бутыль — с качеством и цветом. */
    public static ItemStack make(Form form, Tint tint, int quality) {
        return switch (form) {
            case BOTTLES -> new ItemStack(Items.GLASS_BOTTLE, 4);
            case WINDOW -> new ItemStack(tint.pane, 8);
            case GOBLET, FLASK -> {
                ItemStack ware = new ItemStack(form == Form.GOBLET ? GlassItems.GLASS_GOBLET.get() : GlassItems.GLASS_FLASK.get());
                Pottery.setQuality(ware, quality);
                ware.set(ModComponents.GLASS_TINT.get(), tint.id());
                yield ware;
            }
        };
    }

    /**
     * Вынули из камеры раньше времени: посуда — «не отожжено» (хрупкая, как недожог), склянки и окна — половина
     * полопалась.
     */
    public static ItemStack unannealed(ItemStack stack) {
        if (Pottery.isWare(stack)) {
            Pottery.addFlags(stack, Pottery.UNDERFIRED);
            return stack;
        }
        return stack.copyWithCount(Math.max(1, (stack.getCount() + 1) / 2));
    }

    public static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.glass." + key, args), true);
    }
}
