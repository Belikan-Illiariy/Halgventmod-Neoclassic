package com.nrdhvnstd.halgventmod.client.smithing;

import java.util.List;
import java.util.Random;

import com.nrdhvnstd.halgventmod.network.SmithOpenPayload;
import com.nrdhvnstd.halgventmod.smithing.BloomeryBlockEntity;
import com.nrdhvnstd.halgventmod.smithing.CupelBlockEntity;
import com.nrdhvnstd.halgventmod.smithing.GoldPlacerBlock;
import com.nrdhvnstd.halgventmod.smithing.Minigame;
import com.nrdhvnstd.halgventmod.smithing.MortarBlockEntity;
import com.nrdhvnstd.halgventmod.smithing.SmithArea;
import com.nrdhvnstd.halgventmod.smithing.SmithProducts;
import com.nrdhvnstd.halgventmod.smithing.SluiceBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Какое окно открыть по SmithOpenPayload и куда отдать удар молотобойца. Только клиент. */
public final class SmithScreens {
    private SmithScreens() {}

    public static void open(SmithOpenPayload p) {
        Minecraft minecraft = Minecraft.getInstance();
        Random random = new Random(p.seed());
        if (p.kind() == Minigame.STRIKER) {
            minecraft.setScreen(new StrikerScreen(p.pos()));
            return;
        }
        SmithWorkScreen screen;
        switch (p.kind()) {
            case Minigame.ANVIL, Minigame.CASTING -> {
                List<SmithProducts.Product> list = p.kind() == Minigame.ANVIL ? SmithProducts.anvil() : SmithProducts.casting();
                SmithProducts.Product product = list.get(Math.floorMod(p.product(), list.size()));
                boolean quality = product.kind() != SmithProducts.Result.PLAIN && product.kind() != SmithProducts.Result.REPAIR;
                // Этапы, сделанные раньше (работу бросали), — пропустить
                int done = Math.min((p.extra() >> 8) & 0xFF, product.steps().size() - 1);
                int minTicks = 0;
                for (SmithProducts.Step step : product.steps().subList(done, product.steps().size())) {
                    minTicks += step.op().minTicks();
                }
                screen = new SmithWorkScreen(p.pos(), p.kind(), Component.translatable("gui.halgventmod.smith_work.title_of",
                        product.result().getHoverName()), minTicks, p.info(), p.extra() & 0x1F, quality);
                int liquid = (p.info() >> SmithArea.LIQUID_SHIFT) & 3;
                screen.resume(done, (p.extra() >> 16) & 0x7F);
                for (SmithProducts.Step step : product.steps().subList(done, product.steps().size())) {
                    screen.add(step(screen, random, step, liquid)
                            .named(Component.translatable(step.op().langKey())));
                }
            }
            case Minigame.BLOOMERY -> {
                BloomeryBlockEntity.Smelt smelt = BloomeryBlockEntity.Smelt.values()[Math.floorMod(p.product(),
                        BloomeryBlockEntity.Smelt.values().length)];
                screen = new SmithWorkScreen(p.pos(), p.kind(), Component.translatable("gui.halgventmod.smith_work.bloomery_title",
                        new ItemStack(smelt.ore()).getHoverName()), BloomeryBlockEntity.MIN_TICKS, 0, 0, false);
                screen.add(new BellowsStep(screen, random, p.info(), p.extra())
                        .named(Component.translatable("gui.halgventmod.smith_work.op.bellows")));
            }
            case Minigame.CUPEL -> {
                screen = new SmithWorkScreen(p.pos(), p.kind(), Component.translatable("gui.halgventmod.smith_work.cupel_title"),
                        CupelBlockEntity.MIN_TICKS, 0, 0, false);
                screen.add(new CupelStep(screen, random, p.info())
                        .named(Component.translatable("gui.halgventmod.smith_work.op.cupel")));
            }
            case Minigame.MORTAR -> {
                screen = new SmithWorkScreen(p.pos(), p.kind(), Component.translatable("gui.halgventmod.smith_work.mortar_title"),
                        MortarBlockEntity.MIN_TICKS, 0, 0, false);
                screen.add(new StampStep(screen, random, p.product(), p.info())
                        .named(Component.translatable("gui.halgventmod.smith_work.op.stamp")));
            }
            case Minigame.PAN_PLACER, Minigame.PAN_SLUICE -> {
                boolean sluice = p.kind() == Minigame.PAN_SLUICE;
                screen = new SmithWorkScreen(p.pos(), p.kind(), Component.translatable("gui.halgventmod.smith_work.pan_title"),
                        sluice ? SluiceBlockEntity.MIN_TICKS : GoldPlacerBlock.MIN_TICKS, 0, 0, false);
                screen.add(new PanStep(screen, random, p.info(), sluice && (p.extra() & 1) != 0, (p.extra() & 2) != 0)
                        .named(Component.translatable("gui.halgventmod.smith_work.op.pan")));
            }
            case Minigame.FORGE_MELT -> {
                com.nrdhvnstd.halgventmod.smithing.Metal metal = com.nrdhvnstd.halgventmod.smithing.Metal.values()[Math.floorMod(
                        p.product(), com.nrdhvnstd.halgventmod.smithing.Metal.values().length)];
                screen = new SmithWorkScreen(p.pos(), p.kind(), Component.translatable("gui.halgventmod.smith_work.melt_title",
                        Component.translatable(metal.langKey())), com.nrdhvnstd.halgventmod.smithing.ForgeBlockEntity.MELT_MIN_TICKS,
                        p.info(), 0, false);
                screen.add(new MeltStep(screen, random, metal, p.extra())
                        .named(Component.translatable("gui.halgventmod.smith_work.op.melt")));
            }
            case Minigame.SHARPEN -> {
                screen = new SmithWorkScreen(p.pos(), p.kind(), Component.translatable("gui.halgventmod.smith_work.sharpen_title"),
                        60, 0, 0, false);
                screen.add(new GrindStep(screen, random, p.info())
                        .named(Component.translatable("gui.halgventmod.smith_work.op.grind")));
            }
            default -> {
                return;
            }
        }
        minecraft.setScreen(screen);
    }

    private static SmithStep step(SmithWorkScreen screen, Random random, SmithProducts.Step step, int liquid) {
        return switch (step.op()) {
            case CONSOLIDATE -> new ConsolidateStep(screen, random);
            case DRAW -> new DrawStep(screen, random, step.shape());
            case UPSET -> new UpsetStep(screen, random);
            case BEND -> new BendStep(screen, random, step.shape());
            case PUNCH -> new PunchStep(screen, random, step.amount());
            case CUT -> new CutStep(screen, random, step.amount());
            case WELD -> new WeldStep(screen, random);
            case RAISE -> new RaiseStep(screen, random, step.shape());
            case QUENCH -> new QuenchStep(screen, random, liquid);
            case TEMPER -> new TemperStep(screen, random, step.amount());
            case GRIND -> new GrindStep(screen, random, -1);
            case WIRE -> new WireStep(screen, random, step.amount());
            case WEAVE -> new WeaveStep(screen, random, step.amount());
            case RIVET -> new RivetStep(screen, random, step.amount());
            case GILD -> new GildStep(screen, random);
            case MINT -> new MintStep(screen, random, step.amount());
            case MELT -> new MeltStep(screen, random, com.nrdhvnstd.halgventmod.smithing.Metal.COPPER, 10);
            case POUR -> new PourStep(screen, random);
        };
    }

    /** Удар молотобойца — в открытое окно ковки. */
    public static void strikerBlow(int accuracy) {
        if (Minecraft.getInstance().screen instanceof SmithWorkScreen screen) {
            screen.strikerBlow(accuracy);
        }
    }
}
