package com.nrdhvnstd.halgventmod.smithing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.alchemy.AlchemyItems;
import com.nrdhvnstd.halgventmod.herbs.Herb;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

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
 * Ступа с пестом. E золотой рудой, киноварью, сушёной травой или костью — положить (до {@link #CAPACITY}, одного рода),
 * пустой рукой — толочь (мини-игра StampStep, в ритм); Shift + E — вынуть. Золотая руда → толчёная руда (в промывочный
 * желоб), киноварь → {@link #DYE_PER_CINNABAR} красной краски, сушёная трава → травяной порошок (для эссенций),
 * кость → {@link #MEAL_PER_BONE} костной муки. Толкли вразнобой — часть просыпалась.
 */
public class MortarBlockEntity extends StationBlockEntity implements Minigame.Host {
    public static final int CAPACITY = 4;
    public static final int DYE_PER_CINNABAR = 3;
    public static final int MEAL_PER_BONE = 4;
    public static final int MIN_TICKS = 160;
    public static final int GOLD = 0, CINNABAR = 1, HERBS = 2, BONE = 3;

    private ItemStack inside = ItemStack.EMPTY;

    public MortarBlockEntity(BlockPos pos, BlockState state) {
        super(SmithingBlocks.MORTAR_ENTITY.get(), pos, state);
    }

    public ItemStack inside() {
        return inside;
    }

    private static boolean poundable(ItemStack stack) {
        return stack.is(SmithingItems.GOLD_ORE.get()) || stack.is(SmithingItems.CINNABAR.get())
                || stack.is(ModItems.DRIED_HERBS.get()) || stack.is(Items.BONE);
    }

    /** Одного рода: тот же предмет, у сушёной травы — та же трава (свежесть не в счёт). */
    private static boolean sameKind(ItemStack a, ItemStack b) {
        return a.is(b.getItem()) && java.util.Objects.equals(a.get(ModComponents.HERB.get()), b.get(ModComponents.HERB.get()));
    }

    /** Что толкут — для мини-игры (цвет в ступе). */
    private int what() {
        return inside.is(SmithingItems.GOLD_ORE.get()) ? GOLD : inside.is(SmithingItems.CINNABAR.get()) ? CINNABAR
                : inside.is(Items.BONE) ? BONE : HERBS;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (poundable(stack)) {
            if (!inside.isEmpty() && (!sameKind(inside, stack) || inside.getCount() >= CAPACITY)) {
                Smithing.say(player, "mortar_full", CAPACITY);
                return true;
            }
            inside = inside.isEmpty() ? stack.copyWithCount(1) : inside.copyWithCount(inside.getCount() + 1);
            consumeOne(player, stack);
            sound(SoundEvents.STONE_PLACE, 0.8F, 1.2F);
            changed();
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        if (inside.isEmpty()) {
            Smithing.say(player, "mortar_empty");
            return true;
        }
        if (player.isShiftKeyDown()) {
            give(player, inside);
            inside = ItemStack.EMPTY;
            changed();
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Minigame.open(serverPlayer, worldPosition, Minigame.MORTAR, what(), MIN_TICKS, inside.getCount(), 0);
        }
        return true;
    }

    @Override
    public void minigameResult(ServerPlayer player, Minigame.Session session, boolean done, List<Integer> data) {
        if (!done || inside.isEmpty()) {
            return;
        }
        int count = Math.min(inside.getCount(), session.info());
        int score = Minigame.score(data, 0);
        float spill = score < Smithing.SCORE_MEDIOCRE ? 0.3F : 0; // ⚠️
        int kept = 0;
        for (int i = 0; i < count; i++) {
            if (level.random.nextFloat() >= spill) {
                kept++;
            }
        }
        int what = what();
        ItemStack herbs = inside.copyWithCount(1);
        inside.shrink(count);
        if (kept > 0) {
            give(player, switch (what) {
                case GOLD -> new ItemStack(SmithingItems.CRUSHED_GOLD_ORE.get(), kept);
                case CINNABAR -> new ItemStack(Items.RED_DYE,
                        kept * (score < Smithing.SCORE_MEDIOCRE ? DYE_PER_CINNABAR - 1 : DYE_PER_CINNABAR));
                case BONE -> new ItemStack(Items.BONE_MEAL, kept * MEAL_PER_BONE);
                default -> {
                    // Порошок той же травы, что сушили
                    ItemStack powder = new ItemStack(AlchemyItems.HERB_POWDER.get(), kept);
                    Herb herb = Herb.of(herbs);
                    yield herb == null ? powder : herb.mark(powder);
                }
            });
        }
        Smithing.say(player, kept < count ? "mortar_spilled" : "mortar_done", kept, count - kept);
        sound(SoundEvents.GRAVEL_BREAK, 0.8F, 1.0F);
        changed();
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        if (!inside.isEmpty()) {
            all.add(inside.copy());
        }
        return all;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!inside.isEmpty()) {
            tag.put("Inside", inside.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inside = tag.contains("Inside") ? ItemStack.parseOptional(registries, tag.getCompound("Inside")) : ItemStack.EMPTY;
    }
}
