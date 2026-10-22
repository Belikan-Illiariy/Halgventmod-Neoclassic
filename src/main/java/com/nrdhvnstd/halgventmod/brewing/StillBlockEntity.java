package com.nrdhvnstd.halgventmod.brewing;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.cooking.HearthBlock;
import com.nrdhvnstd.halgventmod.cooking.HearthBlockEntity;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.StillMenu;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Перегонный куб — котёл над огнём (горящий очаг, костёр или огонь прямо под ним). Пиво, вино, сидр или медовуху
 * заливают (до {@link #CAPACITY}, одно за раз): E склянкой или в окне — в ячейку «сырьё», пустые склянки выходят
 * в ячейку склянок. Каждые {@link #DISTILL_TIME} единиц жара перегоняется порция; из Brewing.DISTILL_RATIO порций —
 * одна крепкого: E пустой склянкой или в окне — склянки из своей ячейки наполняются сами. Жар 2 — в самый раз;
 * жар 3 (пышущий очаг, лава) — быстрее, но «первач»: качество −1; жар 1 — медленно.
 * Пиво — хлебное вино, вино — бренди, сидр — яблочный бренди, медовуха — крепкий мёд.
 * <p>ПКМ — окно (StillMenu); над очагом в нём и огонь очага: на очаг под кубом не щёлкнуть — всё идёт кубу
 * (HearthBlock.stationPos), дрова и огниво E по кубу уходят очагу. Shift + E пустой рукой или «Слить» — забрать
 * не перегнанное.
 */
public class StillBlockEntity extends StationBlockEntity {
    // Меняйте здесь ⚠️
    public static final int CAPACITY = 8;
    public static final int DISTILL_TIME = 1200;

    public static final int SLOT_IN = 0;      // сырьё: пиво, вино, сидр, медовуха
    public static final int SLOT_BOTTLES = 1; // пустые склянки: сюда — после заливки, отсюда — под крепкое
    public static final int SLOT_OUT = 2;     // крепкое
    public static final int D_INPUTS = 0, D_SPIRITS = 1, D_PROGRESS = 2, D_USED = 3, D_HEAT = 4, DATA = 5;

    private ItemStack input = ItemStack.EMPTY;
    private int inputs;
    private int qualitySum;
    private int used;
    private int harsh;
    private int progress;
    private int spirits;
    private int spiritQuality = FoodQuality.NORMAL;
    private String spiritKind = "";
    private final SimpleContainer slots = new SimpleContainer(3) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot == SLOT_IN ? Brewing.distillsInto(stack) != null || Pottery.drinkCapacity(stack) > 0
                    : slot == SLOT_BOTTLES && vessel(stack);
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case D_INPUTS -> inputs;
                case D_SPIRITS -> spirits;
                case D_PROGRESS -> progress;
                case D_USED -> used;
                case D_HEAT -> level == null ? 0 : heat(level, worldPosition);
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

    public StillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STILL.get(), pos, state);
        slots.addListener(container -> setChanged());
    }

    public boolean working() {
        return inputs > 0 && heat(level, worldPosition) > 0;
    }

    public SimpleContainer slots() {
        return slots;
    }

    public ContainerData data() {
        return data;
    }

    /** Что заливали (для окна): пусто — ничего. */
    public ItemStack input() {
        return input;
    }

    /** Очаг под кубом или null. */
    @Nullable
    public HearthBlockEntity hearth() {
        return level != null && level.getBlockEntity(worldPosition.below()) instanceof HearthBlockEntity hearth ? hearth : null;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        if (inputs > 0) {
            all.add(input.copyWithCount(inputs));
        }
        for (int i = 0; i < slots.getContainerSize(); i++) {
            if (!slots.getItem(i).isEmpty()) {
                all.add(slots.getItem(i).copy());
            }
        }
        return all;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.brewing." + key, args), true);
    }

    /** Жар снизу: очаг и костёр мода — свой (1–3), ванильный костёр и огонь — 2, лава и магма — 3. */
    public static int heat(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState state = level.getBlockState(below);
        if (state.getBlock() instanceof HearthBlock && level.getBlockEntity(below) instanceof HearthBlockEntity hearth) {
            return hearth.heat();
        }
        if (CampfireBlock.isLitCampfire(state) || state.is(BlockTags.FIRE)) {
            return 2;
        }
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.LAVA) ? 3 : 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StillBlockEntity still) {
        if (level.getGameTime() % 10 == 0) {
            still.pour();
            still.fill();
        }
        if (still.inputs <= 0) {
            return;
        }
        int heat = heat(level, pos);
        if (heat <= 0) {
            return;
        }
        still.progress += heat;
        if (heat >= 3) {
            still.harsh++;
        }
        if (still.progress < DISTILL_TIME) {
            if (level.getGameTime() % 100 == 0) {
                still.setChanged();
            }
            return;
        }
        still.progress = 0;
        still.inputs--;
        still.used++;
        if (still.used >= Brewing.DISTILL_RATIO) {
            int quality = Math.round((float) still.qualitySum / still.used) - (still.harsh > DISTILL_TIME ? 1 : 0);
            still.spiritQuality = still.spirits == 0 ? quality : Math.min(still.spiritQuality, quality);
            still.spirits++;
            still.used = 0;
            still.qualitySum = 0;
            still.harsh = 0;
            still.sound(SoundEvents.BREWING_STAND_BREW, 0.8F, 1.2F);
        }
        if (still.inputs == 0) {
            still.input = ItemStack.EMPTY;
            level.setBlock(pos, state.setValue(StillBlock.FULL, false), 3);
        }
        still.changed();
    }

    /** Почему это нельзя залить (ключ сообщения) или null — можно. */
    @Nullable
    private String pourBlocker(ItemStack stack) {
        String kind = Brewing.distillsInto(stack);
        if (kind == null) {
            return "";
        }
        if (inputs > 0 && (!stack.is(input.getItem()) || !kind.equals(Brewing.distillsInto(input)))) {
            return "still_mixed";
        }
        if (spirits > 0 && !kind.equals(spiritKind)) {
            return "still_take_first";
        }
        return inputs >= CAPACITY ? "still_full" : null;
    }

    /** Залить одну склянку. */
    private void pourOne(ItemStack stack) {
        input = stack.copyWithCount(1);
        inputs++;
        qualitySum += FoodQuality.get(stack);
        spiritKind = Brewing.distillsInto(stack);
        sound(SoundEvents.BOTTLE_EMPTY, 1.0F, 0.9F);
        if (!getBlockState().getValue(StillBlock.FULL)) {
            level.setBlock(worldPosition, getBlockState().setValue(StillBlock.FULL, true), 3);
        }
    }

    /** Окно: сырьё из ячейки — в куб, пустые склянки — в ячейку склянок (пока есть куда). */
    private void pour() {
        ItemStack stack = slots.getItem(SLOT_IN);
        // Из кувшина или корчаги — по склянке, пустых склянок нет
        ItemStack fromVessel = Pottery.storedDrink(stack, level);
        if (!fromVessel.isEmpty()) {
            if (Brewing.distillsInto(fromVessel) != null && pourBlocker(fromVessel) == null) {
                pourOne(fromVessel);
                Pottery.takeDrink(stack, level);
                slots.setChanged();
                changed();
            }
            return;
        }
        ItemStack bottles = slots.getItem(SLOT_BOTTLES);
        boolean room = bottles.isEmpty() || (bottles.is(Items.GLASS_BOTTLE) && bottles.getCount() < bottles.getMaxStackSize());
        if (stack.isEmpty() || !room || pourBlocker(stack) != null) {
            return;
        }
        pourOne(stack);
        stack.shrink(1);
        if (bottles.isEmpty()) {
            slots.setItem(SLOT_BOTTLES, new ItemStack(Items.GLASS_BOTTLE));
        } else {
            bottles.grow(1);
        }
        slots.setChanged();
        changed();
    }

    private ItemStack spirit() {
        return FoodQuality.set(Brewing.make(ModItems.SPIRIT.get(), Brew.of(spiritKind)), spiritQuality);
    }

    /** Во что наливают питьё станции: склянка, корчага или кувшин. */
    public static boolean vessel(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE) || Pottery.drinkCapacity(stack) > 0;
    }

    /** Окно: склянки из своей ячейки наполняются крепким, корчага — до полной. */
    private void fill() {
        ItemStack bottles = slots.getItem(SLOT_BOTTLES);
        if (spirits <= 0) {
            return;
        }
        if (Pottery.drinkCapacity(bottles) > 0) {
            if (Pottery.canPour(bottles, spirit(), level)) {
                Pottery.pour(bottles, spirit(), level);
                spirits--;
                sound(SoundEvents.BOTTLE_FILL, 1.0F, 0.8F);
                slots.setChanged();
                changed();
            }
            return;
        }
        if (!bottles.is(Items.GLASS_BOTTLE)) {
            return;
        }
        ItemStack spirit = spirit();
        ItemStack out = slots.getItem(SLOT_OUT);
        if (out.isEmpty()) {
            slots.setItem(SLOT_OUT, spirit);
        } else if (ItemStack.isSameItemSameComponents(out, spirit) && out.getCount() < out.getMaxStackSize()) {
            out.grow(1);
        } else {
            return;
        }
        bottles.shrink(1);
        spirits--;
        sound(SoundEvents.BOTTLE_FILL, 1.0F, 1.1F);
        slots.setChanged();
        changed();
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        // Дрова, огниво, лопата — огню очага под кубом
        HearthBlockEntity hearth = hearth();
        if (hearth != null && hearth.useFire(player, hand, stack)) {
            return true;
        }
        if (Brewing.distillsInto(stack) != null) {
            String blocker = pourBlocker(stack);
            if (blocker != null) {
                tell(player, blocker);
                return true;
            }
            pourOne(stack);
            consumeOne(player, stack);
            give(player, new ItemStack(Items.GLASS_BOTTLE));
            status(player);
            changed();
            return true;
        }
        // Из кувшина или корчаги с пивом, вином, сидром, медовухой — залить сколько влезет
        ItemStack fromVessel = stack.getCount() == 1 ? Pottery.storedDrink(stack, level) : ItemStack.EMPTY;
        if (Brewing.distillsInto(fromVessel) != null) {
            String blocker = pourBlocker(fromVessel);
            if (blocker != null) {
                tell(player, blocker);
                return true;
            }
            while (pourBlocker(fromVessel) == null) {
                pourOne(fromVessel);
                Pottery.takeDrink(stack, level);
                fromVessel = Pottery.storedDrink(stack, level);
                if (fromVessel.isEmpty()) {
                    break;
                }
            }
            status(player);
            changed();
            return true;
        }
        // Корчагой или кувшином — налить крепкого сколько влезет
        if (Pottery.drinkCapacity(stack) > 0 && stack.getCount() == 1) {
            int poured = 0;
            while (spirits > 0 && Pottery.canPour(stack, spirit(), level)) {
                Pottery.pour(stack, spirit(), level);
                spirits--;
                poured++;
            }
            if (poured > 0) {
                sound(SoundEvents.BOTTLE_FILL, 1.0F, 0.8F);
                tell(player, "still_poured_vessel", poured, stack.getHoverName());
                changed();
            } else {
                tell(player, spirits <= 0 ? "still_nothing_to_pour" : "still_vessel_no");
            }
            return true;
        }
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (spirits <= 0) {
                status(player);
                return true;
            }
            consumeOne(player, stack);
            give(player, spirit());
            spirits--;
            sound(SoundEvents.BOTTLE_FILL, 1.0F, 1.1F);
            changed();
            return true;
        }
        if (!stack.isEmpty()) {
            return false;
        }
        if (player.isShiftKeyDown() && inputs > 0) {
            drain(player);
            return true;
        }
        status(player);
        return true;
    }

    /** Забрать не перегнанное. */
    public void drain(Player player) {
        if (inputs <= 0 || player.distanceToSqr(worldPosition.getCenter()) > 64) {
            return;
        }
        for (int i = 0; i < inputs; i++) {
            give(player, input.copyWithCount(1));
        }
        inputs = 0;
        used = 0;
        qualitySum = 0;
        progress = 0;
        input = ItemStack.EMPTY;
        level.setBlock(worldPosition, getBlockState().setValue(StillBlock.FULL, false), 3);
        changed();
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ — окно куба (над очагом — и с огнём очага). */
    @Override
    public void report(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            HearthBlockEntity hearth = hearth();
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new StillMenu(id, inventory, this, hearth),
                    getBlockState().getBlock().getName()), buf -> {
                buf.writeBlockPos(worldPosition);
                buf.writeBoolean(hearth != null);
            });
        }
    }

    private void status(Player player) {
        int heat = heat(level, worldPosition);
        tell(player, heat == 0 ? "still_cold" : heat >= 3 ? "still_hot" : "still_status",
                inputs, spirits, Brewing.DISTILL_RATIO);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!input.isEmpty()) {
            tag.put("Input", input.save(registries));
        }
        tag.putInt("Inputs", inputs);
        tag.putInt("QualitySum", qualitySum);
        tag.putInt("Used", used);
        tag.putInt("Harsh", harsh);
        tag.putInt("Progress", progress);
        tag.putInt("Spirits", spirits);
        tag.putInt("SpiritQuality", spiritQuality);
        tag.putString("SpiritKind", spiritKind);
        for (int i = 0; i < slots.getContainerSize(); i++) {
            if (!slots.getItem(i).isEmpty()) {
                tag.put("Slot" + i, slots.getItem(i).save(registries));
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains("Input") ? ItemStack.parseOptional(registries, tag.getCompound("Input")) : ItemStack.EMPTY;
        inputs = tag.getInt("Inputs");
        qualitySum = tag.getInt("QualitySum");
        used = tag.getInt("Used");
        harsh = tag.getInt("Harsh");
        progress = tag.getInt("Progress");
        spirits = tag.getInt("Spirits");
        spiritQuality = tag.contains("SpiritQuality") ? tag.getInt("SpiritQuality") : FoodQuality.NORMAL;
        spiritKind = tag.getString("SpiritKind");
        for (int i = 0; i < slots.getContainerSize(); i++) {
            slots.setItem(i, tag.contains("Slot" + i) ? ItemStack.parseOptional(registries, tag.getCompound("Slot" + i))
                    : ItemStack.EMPTY);
        }
    }
}
