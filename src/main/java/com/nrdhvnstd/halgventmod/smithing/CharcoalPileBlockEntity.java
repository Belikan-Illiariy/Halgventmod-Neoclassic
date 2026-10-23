package com.nrdhvnstd.halgventmod.smithing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Угольная куча углежога. На E:
 * <pre>
 *   дрова, бревно (за {@link #LOG_FIREWOOD}) — сложить, до {@link #MAX_WOOD}
 *   лопата                                 — обсыпать землёй вокруг кучи: удар с полного замаха на каждые
 *                                            {@link #DIRT_PER} полена, тратит выносливость (в приключении землю не носят)
 *   огниво, факел                          — поджечь обсыпанную
 *   пустая рука                            — вскрыть тлеющую и забрать, что вышло; Shift — разобрать несожжённое
 * </pre>
 * Тлеет по календарю ({@link #DONE} суток в самый раз). Вскрыли раньше {@link #EARLY} — головешки (часть дров
 * вернётся), до {@link #DONE} — угля меньше; дольше {@link #LATE} — уголь прогорает в золу. ПКМ — как идёт дело.
 */
public class CharcoalPileBlockEntity extends StationBlockEntity implements SmithStationBlock.Ticking {
    // Меняйте здесь ⚠️
    public static final int MAX_WOOD = 16;
    public static final int LOG_FIREWOOD = 3;
    public static final int DIRT_PER = 4;
    public static final double EARLY = 0.6;
    public static final double DONE = 0.9;
    public static final double LATE = 1.5;
    public static final double BURNT = 2.2;
    public static final float YIELD = 0.5F;        // угля с полена в самый раз
    public static final float SHOVEL_STAMINA = 6;  // выносливости за удар лопатой

    private int wood;
    private int dirt;
    private long litAt;

    public CharcoalPileBlockEntity(BlockPos pos, BlockState state) {
        super(SmithingBlocks.CHARCOAL_PILE_ENTITY.get(), pos, state);
    }

    private boolean lit() {
        return getBlockState().getValue(SmithStationBlock.Lit.LIT);
    }

    private boolean covered() {
        return wood > 0 && dirt >= dirtNeeded();
    }

    private int dirtNeeded() {
        return (wood + DIRT_PER - 1) / DIRT_PER;
    }

    private void setPile(int pile, boolean lit) {
        level.setBlock(worldPosition, getBlockState().setValue(CharcoalPileBlock.PILE, pile)
                .setValue(SmithStationBlock.Lit.LIT, lit), 3);
    }

    @Override
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 != 0 || !state.getValue(SmithStationBlock.Lit.LIT)) {
            return;
        }
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    1, 0.2, 0.0, 0.2, 0.01);
        }
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (lit()) {
            if (stack.isEmpty()) {
                return open(player);
            }
            report(player);
            return true;
        }
        boolean firewood = stack.is(ModItems.FIREWOOD.get());
        if (firewood || stack.is(ItemTags.LOGS_THAT_BURN)) {
            int add = firewood ? 1 : LOG_FIREWOOD;
            if (dirt > 0 || wood + add > MAX_WOOD) {
                Smithing.say(player, dirt > 0 ? "pile_covered" : "pile_full", MAX_WOOD);
                return true;
            }
            wood += add;
            consumeOne(player, stack);
            sound(SoundEvents.WOOD_PLACE, 0.8F, 0.9F);
            setPile(1, false);
            report(player);
            changed();
            return true;
        }
        if (stack.is(ItemTags.SHOVELS)) {
            if (wood <= 0 || covered()) {
                Smithing.say(player, wood <= 0 ? "pile_no_wood" : "pile_covered_enough");
                return true;
            }
            if (com.nrdhvnstd.halgventmod.needs.Stamina.exhausted(player) || !strike(player, hand, stack)) {
                Smithing.say(player, "pile_rushed");
                return true;
            }
            com.nrdhvnstd.halgventmod.needs.Stamina.spend(player, SHOVEL_STAMINA);
            dirt++;
            crumbs(new ItemStack(Items.DIRT), 0.8, 8);
            sound(SoundEvents.ROOTED_DIRT_PLACE, 1.0F, 0.8F);
            if (covered()) {
                setPile(2, false);
            }
            report(player);
            changed();
            return true;
        }
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)) {
            if (!covered()) {
                Smithing.say(player, "pile_not_covered", dirtNeeded());
                return true;
            }
            if (stack.is(Items.FLINT_AND_STEEL)) {
                damage(player, hand, stack, 1);
            }
            sound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
            litAt = Smithing.now(level);
            setPile(2, true);
            Smithing.say(player, "pile_lit", Smithing.realMinutes(DONE));
            changed();
            return true;
        }
        if (stack.isEmpty()) {
            if (player.isShiftKeyDown() && (wood > 0 || dirt > 0)) {
                give(player, new ItemStack(ModItems.FIREWOOD.get(), wood));
                wood = dirt = 0;
                setPile(0, false);
                changed();
                return true;
            }
            if (getBlockState().getValue(CharcoalPileBlock.PILE) == 3) {
                setPile(0, false);
            }
            report(player);
            return true;
        }
        return false;
    }

    /** Вскрыть тлеющую кучу: что вышло — от того, сколько тлела. */
    private boolean open(Player player) {
        double days = Smithing.daysSince(level, litAt);
        int charcoal;
        int brands = 0;
        int ash = 0;
        String key;
        if (days < EARLY) {
            charcoal = Math.round(wood * YIELD * 0.3F);
            brands = Math.round(wood * 0.4F);
            key = "pile_early";
        } else if (days < DONE) {
            charcoal = Math.round(wood * YIELD * 0.7F);
            key = "pile_underdone";
        } else if (days <= LATE) {
            charcoal = Math.round(wood * YIELD);
            key = "pile_done";
        } else if (days <= BURNT) {
            charcoal = Math.round(wood * YIELD * 0.5F);
            ash = Math.max(1, wood / 4);
            key = "pile_overdone";
        } else {
            charcoal = 0;
            ash = Math.max(1, wood / 2);
            key = "pile_burnt";
        }
        if (charcoal > 0) {
            give(player, new ItemStack(Items.CHARCOAL, charcoal));
        }
        if (brands > 0) {
            give(player, new ItemStack(ModItems.FIREWOOD.get(), brands));
        }
        if (ash > 0) {
            give(player, new ItemStack(ModItems.ASH.get(), ash));
        }
        Smithing.tell(player, key, charcoal, brands, ash);
        sound(SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.8F);
        wood = dirt = 0;
        litAt = 0;
        setPile(charcoal > 0 ? 3 : 0, false);
        changed();
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    @Override
    public void report(Player player) {
        if (lit()) {
            double days = Smithing.daysSince(level, litAt);
            if (days < DONE) {
                Smithing.say(player, "pile_burning", Smithing.realMinutes(DONE - days));
            } else if (days <= LATE) {
                Smithing.say(player, "pile_ready", Smithing.realMinutes(LATE - days));
            } else {
                Smithing.say(player, "pile_too_long");
            }
        } else if (wood <= 0) {
            Smithing.say(player, "pile_empty", MAX_WOOD);
        } else {
            Smithing.say(player, "pile_report", wood, MAX_WOOD, dirt, dirtNeeded());
        }
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        if (wood > 0 && !lit()) {
            all.add(new ItemStack(ModItems.FIREWOOD.get(), wood));
        }
        return all;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Wood", wood);
        tag.putInt("Dirt", dirt);
        tag.putLong("LitAt", litAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        wood = tag.getInt("Wood");
        dirt = tag.getInt("Dirt");
        litAt = tag.getLong("LitAt");
    }
}
