package com.nrdhvnstd.halgventmod.smithing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Купель. На E: тигель с жидким свинцом с серебром (из сыродутного горна) — слить, или слиток — положить (до
 * {@link #CAPACITY}), древесный уголь — подложить, огниво —
 * разжечь: окно купели (CupelStep) — мехами держите жар, пока свинец выгорает в глёт, и сгоняйте глёт к краю чаши;
 * запёкшийся глёт и перегрев уносят серебро (хуже счёт). Свинец выгорел — серебро вспыхивает каплей.
 * Серебра в свинце мало ({@link #SILVER_PER_LEAD} слитка на кусок): что не набралось на слиток, остаётся в купели
 * каплей до следующего раза. Свинца остаётся много ({@link #LEAD_BACK}).
 */
public class CupelBlockEntity extends StationBlockEntity implements Minigame.Host {
    public static final int CAPACITY = 4;
    public static final float SILVER_PER_LEAD = 0.3F;  // ⚠️
    public static final float LEAD_BACK = 0.75F;       // ⚠️ свинца остаётся от свинца с серебром
    public static final float LATE_LOSS = 0.5F;        // поздно сняли — столько серебра пропало
    public static final float LOSS_PER_SLIP = 0.08F;   // ⚠️ серебра уходит с каждым запёкшимся глётом и брызгами
    public static final int MIN_TICKS = 400;
    // Итог окна (второе число): когда сняли
    public static final int ON_TIME = 0, EARLY = 1, LATE = 2;

    private ItemStack lead = ItemStack.EMPTY;
    private int fuel;
    private float bead;          // серебро, не набравшее на слиток

    public CupelBlockEntity(BlockPos pos, BlockState state) {
        super(SmithingBlocks.CUPEL_ENTITY.get(), pos, state);
    }

    private boolean lit() {
        return getBlockState().getValue(SmithStationBlock.Lit.LIT);
    }

    public ItemStack lead() {
        return lead;
    }

    /** Угля подложено (для отрисовки). */
    public int fuel() {
        return fuel;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (lit()) {
            Smithing.say(player, "cupel_busy");
            return true;
        }
        if (stack.is(SmithingItems.ARGENTIFEROUS_LEAD.get())) {
            if (!lead.isEmpty() && (lead.getCount() >= CAPACITY || !ItemStack.isSameItemSameComponents(lead, stack))) {
                Smithing.say(player, "cupel_full", CAPACITY);
                return true;
            }
            lead = lead.isEmpty() ? stack.copyWithCount(1) : lead.copyWithCount(lead.getCount() + 1);
            consumeOne(player, stack);
            sound(SoundEvents.METAL_PLACE, 0.8F, 0.8F);
            report(player);
            changed();
            return true;
        }
        if (Crucible.is(stack) && Crucible.total(Crucible.melt(stack)) > 0) {
            return pourIn(player, stack);
        }
        if (stack.is(Items.CHARCOAL)) {
            if (fuel >= CAPACITY) {
                Smithing.say(player, "cupel_full", CAPACITY);
                return true;
            }
            fuel++;
            consumeOne(player, stack);
            sound(SoundEvents.GRAVEL_PLACE, 0.6F, 1.2F);
            report(player);
            changed();
            return true;
        }
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)) {
            if (lead.isEmpty() || fuel < fuelNeeded()) {
                Smithing.say(player, "cupel_not_enough", fuelNeeded());
                return true;
            }
            if (stack.is(Items.FLINT_AND_STEEL)) {
                damage(player, hand, stack, 1);
            }
            sound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
            level.setBlock(worldPosition, getBlockState().setValue(SmithStationBlock.Lit.LIT, true), 3);
            if (player instanceof ServerPlayer serverPlayer) {
                Minigame.open(serverPlayer, worldPosition, Minigame.CUPEL, 0, MIN_TICKS, lead.getCount(), 0);
            }
            changed();
            return true;
        }
        if (stack.isEmpty()) {
            if (player.isShiftKeyDown() && !lead.isEmpty()) {
                give(player, lead);
                lead = ItemStack.EMPTY;
                changed();
                return true;
            }
            report(player);
            return true;
        }
        return false;
    }

    /** E тиглем: слить жидкий свинец с серебром — сколько целых слитков влезет; качество — среднее. */
    private boolean pourIn(Player player, ItemStack crucible) {
        if (Crucible.resolve(Crucible.melt(crucible)) == null
                || Crucible.resolve(Crucible.melt(crucible)).metal() != Metal.SILVER_LEAD) {
            Smithing.say(player, "cupel_wrong_metal");
            return true;
        }
        if (!Crucible.liquid(crucible, level)) {
            Smithing.say(player, "cupel_lead_solid");
            return true;
        }
        int room = CAPACITY - lead.getCount();
        int count = Math.min(room, Crucible.total(Crucible.melt(crucible)) / Crucible.PER_INGOT);
        if (count <= 0) {
            Smithing.say(player, room <= 0 ? "cupel_full" : "cupel_lead_little", CAPACITY);
            return true;
        }
        int quality = Crucible.quality(Crucible.melt(crucible));
        int have = lead.getCount();
        int mixed = have == 0 ? quality : Math.round((Smithing.quality(lead) * have + quality * count) / (float) (have + count));
        lead = Smithing.setQuality(new ItemStack(SmithingItems.ARGENTIFEROUS_LEAD.get(), have + count), mixed);
        Crucible.take(crucible, count * Crucible.PER_INGOT);
        sound(SoundEvents.LAVA_POP, 0.8F, 0.9F);
        Smithing.say(player, "cupel_poured", count);
        changed();
        return true;
    }

    private int fuelNeeded() {
        return Math.max(1, (lead.getCount() + 1) / 2);
    }

    @Override
    public void minigameResult(ServerPlayer player, Minigame.Session session, boolean done, List<Integer> data) {
        level.setBlock(worldPosition, getBlockState().setValue(SmithStationBlock.Lit.LIT, false), 3);
        if (!done || lead.isEmpty()) {
            Smithing.say(player, "cupel_quit");
            changed();
            return;
        }
        int count = Math.min(lead.getCount(), session.info());
        int score = Minigame.score(data, 0);
        int timing = Minigame.value(data, 1, ON_TIME);
        int quality = Smithing.quality(lead) + Smithing.workDelta(score) + (timing == EARLY ? -2 : 0);
        float silver = count * SILVER_PER_LEAD * (timing == LATE ? 1 - LATE_LOSS : 1)
                * (score < Smithing.SCORE_POOR ? 0.6F : 1)
                * Math.max(0.4F, 1 - LOSS_PER_SLIP * Minigame.value(data, 2, 0)); // запёкшийся глёт и брызги
        bead += silver;
        int ingots = (int) bead;
        bead -= ingots;
        int leadBack = Math.max(1, Math.round(count * LEAD_BACK));
        int burnt = fuelNeeded();
        lead.shrink(count);
        fuel = Math.max(0, fuel - burnt);
        give(player, new ItemStack(SmithingItems.LEAD_INGOT.get(), leadBack));
        if (ingots > 0) {
            give(player, Smithing.setQuality(new ItemStack(SmithingItems.SILVER_INGOT.get(), ingots), quality));
        }
        Smithing.tell(player, timing == EARLY ? "cupel_early" : timing == LATE ? "cupel_late" : "cupel_done",
                ingots, leadBack, Math.round(bead * 100));
        sound(SoundEvents.LAVA_EXTINGUISH, 0.8F, 1.3F);
        changed();
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    @Override
    public void report(Player player) {
        Smithing.say(player, "cupel_report", lead.getCount(), fuel, fuelNeeded(), Math.round(bead * 100));
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        if (!lead.isEmpty()) {
            all.add(lead.copy());
        }
        if (fuel > 0) {
            all.add(new ItemStack(Items.CHARCOAL, fuel));
        }
        return all;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!lead.isEmpty()) {
            tag.put("Lead", lead.save(registries));
        }
        tag.putInt("Fuel", fuel);
        tag.putFloat("Bead", bead);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lead = tag.contains("Lead") ? ItemStack.parseOptional(registries, tag.getCompound("Lead")) : ItemStack.EMPTY;
        fuel = tag.getInt("Fuel");
        bead = tag.getFloat("Bead");
    }
}
