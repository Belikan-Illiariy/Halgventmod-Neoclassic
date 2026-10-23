package com.nrdhvnstd.halgventmod.smithing;

import java.util.function.Supplier;

import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Что добывают из жилы или ила ударами ЛКМ (OreMining). Меняйте здесь ⚠️
 * <ul>
 *   <li>strikes — ударов на один кусок (удар по искре — за два);</li>
 *   <li>stamina — выносливости за удар (удар в бою — 6);</li>
 *   <li>deplete — шанс, что после куска жила обеднеет на ступень (богатая → средняя → бедная → пустая);</li>
 *   <li>regrow — за сколько суток по календарю мира в среднем возвращается ступень (VeinClock; у каждой ступени
 *       свой срок, ± половина): считается и пока чанк
 *       выгружен;</li>
 *   <li>barren — шанс, что вместо куска выйдет пустая порода.</li>
 * </ul>
 */
public enum OreKind implements StringRepresentable {
    //          id           руда                              ударов  выносл.  обеднеть  суток на ступ. пустая
    IRON      ("iron",      () -> SmithingItems.IRON_ORE.get(),    5, 6.0F, 0.25F, 1.0F,   0.00F, false),
    COPPER    ("copper",    () -> SmithingItems.COPPER_ORE.get(),  4, 6.0F, 0.25F, 1.0F,   0.00F, false),
    TIN       ("tin",       () -> SmithingItems.TIN_ORE.get(),     5, 6.0F, 0.35F, 2.0F,   0.05F, false),
    LEAD      ("lead",      () -> SmithingItems.LEAD_ORE.get(),    4, 6.0F, 0.25F, 1.0F,   0.00F, false),
    SILVER    ("silver",    () -> SmithingItems.SILVER_ORE.get(),  6, 7.0F, 0.40F, 2.0F,   0.15F, false),
    GOLD      ("gold",      () -> SmithingItems.GOLD_ORE.get(),    7, 8.0F, 0.45F, 4.0F,   0.35F, false),
    ZINC      ("zinc",      () -> SmithingItems.ZINC_ORE.get(),    5, 6.0F, 0.35F, 2.0F,   0.05F, false),
    CINNABAR  ("cinnabar",  () -> SmithingItems.CINNABAR.get(),    5, 6.0F, 0.40F, 2.5F,   0.10F, false),
    LIMESTONE ("limestone", () -> SmithingItems.FLUX.get(),        3, 5.0F, 0.15F, 0.6F,   0.00F, false),
    BOG       ("bog",       () -> SmithingItems.BOG_IRON_ORE.get(), 3, 4.0F, 0.25F, 0.8F,   0.00F, true);

    private final String id;
    private final Supplier<Item> ore;
    private final int strikes;
    private final float stamina;
    private final float deplete;
    private final float regrow;
    private final float barren;
    private final boolean dug;

    OreKind(String id, Supplier<Item> ore, int strikes, float stamina, float deplete, float regrow, float barren,
            boolean dug) {
        this.id = id;
        this.ore = ore;
        this.strikes = strikes;
        this.stamina = stamina;
        this.deplete = deplete;
        this.regrow = regrow;
        this.barren = barren;
        this.dug = dug;
    }

    public String id() {
        return id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Item ore() {
        return ore.get();
    }

    public int strikes() {
        return strikes;
    }

    public float stamina() {
        return stamina;
    }

    public float deplete() {
        return deplete;
    }

    public float regrow() {
        return regrow;
    }

    public float barren() {
        return barren;
    }

    /** Копают лопатой (ил), а не бьют киркой. */
    public boolean dug() {
        return dug;
    }

    /** Чем добывать: кирка или лопата. */
    public TagKey<Item> tool() {
        return dug ? ItemTags.SHOVELS : ItemTags.PICKAXES;
    }
}
