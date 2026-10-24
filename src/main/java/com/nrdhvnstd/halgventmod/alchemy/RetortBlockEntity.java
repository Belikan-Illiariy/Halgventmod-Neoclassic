package com.nrdhvnstd.halgventmod.alchemy;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.brewing.StillBlockEntity;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.RetortMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Реторта: возгонка над огнём ({@link StillBlockEntity#heat} — очаг, костёр, огонь под ней). В колбу — то, что
 * возгоняют (Alchemy.sublimations), рядом — добавка, если нужна. Каждые {@link Alchemy#SUBLIME_TIME} единиц жара одна
 * штука возгоняется: летучее оседает в приёмнике (ячейка «приёмник»), остаток — в колбе (ячейка «остаток»).
 * Жар 3 — быстрее, но {@link Alchemy#OVERHEAT_LOSS} уходит дымом. Приёмник или остаток полны — стоит.
 * <p>E тем, что возгоняют, или добавкой — положить; пустой рукой — забрать готовое, Shift + E — вынуть положенное.
 * ПКМ — окно (RetortMenu).
 */
public class RetortBlockEntity extends StationBlockEntity {
    public static final int SLOT_IN = 0, SLOT_ADD = 1, SLOT_OUT = 2, SLOT_RESIDUE = 3, SLOTS = 4;
    public static final int D_PROGRESS = 0, D_HEAT = 1, D_STATE = 2, DATA = 3;
    public static final int STATE_EMPTY = 0, STATE_UNKNOWN = 1, STATE_NEEDS_ADDITIVE = 2, STATE_COLD = 3, STATE_FULL = 4,
            STATE_WORKING = 5;

    private int progress;
    private final SimpleContainer slots = new SimpleContainer(SLOTS) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_IN -> Alchemy.sublimation(stack) != null;
                case SLOT_ADD -> Alchemy.sublimationAdditive(stack);
                default -> false;
            };
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case D_PROGRESS -> progress;
                case D_HEAT -> level == null ? 0 : StillBlockEntity.heat(level, worldPosition);
                case D_STATE -> level == null ? STATE_EMPTY : state();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public RetortBlockEntity(BlockPos pos, BlockState state) {
        super(AlchemyBlocks.RETORT_ENTITY.get(), pos, state);
        slots.addListener(container -> setChanged());
    }

    public SimpleContainer slots() {
        return slots;
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (!slots.getItem(i).isEmpty()) {
                all.add(slots.getItem(i).copy());
            }
        }
        return all;
    }

    private int state() {
        ItemStack input = slots.getItem(SLOT_IN);
        if (input.isEmpty()) {
            return STATE_EMPTY;
        }
        Alchemy.Sublimation sublimation = Alchemy.sublimation(input);
        if (sublimation == null) {
            return STATE_UNKNOWN;
        }
        if (sublimation.needsAdditive() && !sublimation.additive().test(slots.getItem(SLOT_ADD))) {
            return STATE_NEEDS_ADDITIVE;
        }
        if (!room(sublimation, input)) {
            return STATE_FULL;
        }
        return StillBlockEntity.heat(level, worldPosition) > 0 ? STATE_WORKING : STATE_COLD;
    }

    /** Влезет ли в приёмник и в остаток ещё одна возгонка. */
    private boolean room(Alchemy.Sublimation sublimation, ItemStack input) {
        return fits(SLOT_OUT, sublimation.product().apply(input)) && fits(SLOT_RESIDUE, sublimation.residue().apply(input));
    }

    private boolean fits(int slot, ItemStack add) {
        ItemStack there = slots.getItem(slot);
        return add.isEmpty() || there.isEmpty()
                || ItemStack.isSameItemSameComponents(there, add) && there.getCount() + add.getCount() <= there.getMaxStackSize();
    }

    private void put(int slot, ItemStack add) {
        if (add.isEmpty()) {
            return;
        }
        ItemStack there = slots.getItem(slot);
        if (there.isEmpty()) {
            slots.setItem(slot, add.copy());
        } else {
            there.grow(add.getCount());
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RetortBlockEntity retort) {
        boolean full = !retort.slots.getItem(SLOT_IN).isEmpty();
        if (state.getValue(RetortBlock.FULL) != full) {
            level.setBlock(pos, state.setValue(RetortBlock.FULL, full), 3);
        }
        if (retort.state() != STATE_WORKING) {
            return;
        }
        int heat = StillBlockEntity.heat(level, pos);
        retort.progress += heat;
        if (retort.progress < Alchemy.SUBLIME_TIME) {
            if (level.getGameTime() % 100 == 0) {
                retort.setChanged();
            }
            return;
        }
        retort.progress = 0;
        ItemStack input = retort.slots.getItem(SLOT_IN);
        Alchemy.Sublimation sublimation = Alchemy.sublimation(input);
        ItemStack product = sublimation.product().apply(input);
        ItemStack residue = sublimation.residue().apply(input);
        input.shrink(1);
        if (sublimation.needsAdditive()) {
            retort.slots.getItem(SLOT_ADD).shrink(1);
        }
        // Перегрели — часть летучего ушла в дым
        if (heat < 3 || level.random.nextFloat() >= Alchemy.OVERHEAT_LOSS) {
            retort.put(SLOT_OUT, product);
        }
        retort.put(SLOT_RESIDUE, residue);
        retort.slots.setChanged();
        retort.sound(SoundEvents.BREWING_STAND_BREW, 0.5F, 1.4F);
        retort.changed();
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (Alchemy.sublimation(stack) != null || Alchemy.sublimationAdditive(stack)) {
            int slot = Alchemy.sublimation(stack) != null ? SLOT_IN : SLOT_ADD;
            ItemStack there = slots.getItem(slot);
            if (!there.isEmpty() && !ItemStack.isSameItemSameComponents(there, stack)) {
                Alchemy.say(player, "retort_other");
                return true;
            }
            int moved = Math.min(stack.getCount(), stack.getMaxStackSize() - there.getCount());
            if (moved <= 0) {
                Alchemy.say(player, "retort_full");
                return true;
            }
            if (there.isEmpty()) {
                slots.setItem(slot, stack.copyWithCount(moved));
                if (slot == SLOT_IN) {
                    progress = 0; // новое в пустой колбе — возгонка с начала
                }
            } else {
                there.grow(moved);
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
            slots.setChanged();
            sound(SoundEvents.DECORATED_POT_INSERT, 0.8F, 1.0F);
            status(player);
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        boolean done = !slots.getItem(SLOT_OUT).isEmpty() || !slots.getItem(SLOT_RESIDUE).isEmpty();
        if (done && !player.isShiftKeyDown()) {
            give(player, slots.removeItemNoUpdate(SLOT_OUT));
            give(player, slots.removeItemNoUpdate(SLOT_RESIDUE));
            slots.setChanged();
            sound(SoundEvents.DECORATED_POT_INSERT, 0.8F, 0.7F);
            return true;
        }
        if (player.isShiftKeyDown() && !slots.getItem(SLOT_IN).isEmpty()) {
            give(player, slots.removeItemNoUpdate(SLOT_IN));
            give(player, slots.removeItemNoUpdate(SLOT_ADD));
            progress = 0;
            slots.setChanged();
            return true;
        }
        status(player);
        return true;
    }

    private void status(Player player) {
        switch (state()) {
            case STATE_EMPTY -> Alchemy.say(player, "retort_empty");
            case STATE_NEEDS_ADDITIVE -> Alchemy.say(player, "retort_needs_additive");
            case STATE_FULL -> Alchemy.say(player, "retort_take");
            case STATE_COLD -> Alchemy.say(player, "retort_cold");
            case STATE_WORKING -> Alchemy.say(player, "retort_working", progress * 100 / Alchemy.SUBLIME_TIME);
            default -> Alchemy.say(player, "retort_unknown");
        }
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ — окно реторты. */
    @Override
    public void report(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new RetortMenu(id, inventory, this),
                    getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Progress", progress);
        for (int i = 0; i < SLOTS; i++) {
            if (!slots.getItem(i).isEmpty()) {
                tag.put("Slot" + i, slots.getItem(i).save(registries));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt("Progress");
        for (int i = 0; i < SLOTS; i++) {
            slots.setItem(i, tag.contains("Slot" + i) ? ItemStack.parseOptional(registries, tag.getCompound("Slot" + i))
                    : ItemStack.EMPTY);
        }
    }
}
