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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Промывочный желоб. E толчёной золотой рудой — засыпать (до {@link #CAPACITY}), E лотком — промывать (PanStep).
 * Золота выходит от того, сколько его удержали в лотке и как отмыли песок; ртуть в инвентаре собирает и мелкие
 * крупинки (×{@link #MERCURY_BONUS}) — тратится склянка за промывку. Shift + E пустой рукой — выгрести руду.
 */
public class SluiceBlockEntity extends StationBlockEntity implements Minigame.Host {
    public static final int CAPACITY = 4;
    public static final float DUST_PER_ORE = 1.2F;     // ⚠️ золотого песка с куска при чистой промывке
    public static final float MERCURY_BONUS = 1.5F;
    public static final int MIN_TICKS = 160;
    public static final float NUGGET = 0.04F;   // ⚠️ шанс самородка за кусок руды

    private int ore;

    public SluiceBlockEntity(BlockPos pos, BlockState state) {
        super(SmithingBlocks.SLUICE_ENTITY.get(), pos, state);
    }

    public int ore() {
        return ore;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(SmithingItems.CRUSHED_GOLD_ORE.get())) {
            if (ore >= CAPACITY) {
                Smithing.say(player, "sluice_full", CAPACITY);
                return true;
            }
            ore++;
            consumeOne(player, stack);
            sound(SoundEvents.SAND_PLACE, 0.8F, 1.0F);
            changed();
            return true;
        }
        if (stack.is(SmithingItems.GOLD_PAN.get())) {
            if (ore <= 0) {
                Smithing.say(player, "sluice_empty");
            } else if (player instanceof ServerPlayer serverPlayer) {
                boolean nugget = level.random.nextFloat() < ore * NUGGET;
                Minigame.open(serverPlayer, worldPosition, Minigame.PAN_SLUICE, 0, MIN_TICKS, ore,
                        (mercurySlot(player) >= 0 ? 1 : 0) | (nugget ? 2 : 0));
            }
            return true;
        }
        if (stack.isEmpty()) {
            if (player.isShiftKeyDown() && ore > 0) {
                give(player, new ItemStack(SmithingItems.CRUSHED_GOLD_ORE.get(), ore));
                ore = 0;
                changed();
            } else {
                Smithing.say(player, "sluice_report", ore, CAPACITY);
            }
            return true;
        }
        return false;
    }

    private static int mercurySlot(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(SmithingItems.MERCURY.get())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void minigameResult(ServerPlayer player, Minigame.Session session, boolean done, List<Integer> data) {
        ItemStack pan = player.getMainHandItem();
        if (!done || ore <= 0 || !pan.is(SmithingItems.GOLD_PAN.get())) {
            return;
        }
        int count = Math.min(ore, session.info());
        int kept = Minigame.score(data, 0);
        int washed = Minigame.score(data, 1);
        int mercury = mercurySlot(player);
        float grains = count * DUST_PER_ORE * kept / 100F * (0.4F + 0.6F * washed / 100F)
                * (mercury >= 0 ? MERCURY_BONUS : 1);
        int dust = (int) grains + (level.random.nextFloat() < grains - (int) grains ? 1 : 0);
        if ((session.extra() & 2) != 0 && Minigame.value(data, 2, 0) == 1) {
            dust += GoldPlacerBlock.NUGGET_DUST;
            Smithing.tell(player, "panned_nugget");
        }
        if (mercury >= 0 && !player.getAbilities().instabuild) {
            player.getInventory().getItem(mercury).shrink(1);
        }
        if (!player.getAbilities().instabuild) {
            pan.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        ore -= count;
        if (dust > 0) {
            give(player, new ItemStack(SmithingItems.GOLD_DUST.get(), dust));
        }
        Smithing.say(player, mercury >= 0 ? "sluice_done_mercury" : "sluice_done", dust);
        sound(SoundEvents.GENERIC_SPLASH, 0.6F, 1.2F);
        changed();
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        if (ore > 0) {
            all.add(new ItemStack(SmithingItems.CRUSHED_GOLD_ORE.get(), ore));
        }
        return all;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Ore", ore);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ore = tag.getInt("Ore");
    }
}
