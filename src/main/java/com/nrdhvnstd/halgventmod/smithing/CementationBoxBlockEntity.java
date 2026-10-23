package com.nrdhvnstd.halgventmod.smithing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.cooking.Fire;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.CementationMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Цементационная печь. Окно (E пустой рукой, ПКМ — {@link CementationMenu}): железные слитки — в ячейки камеры (до
 * {@link #CAPACITY}), древесный уголь — в свою ячейку ({@link #CHARCOAL_PER} на слиток: он и даёт углерод, и тлеет),
 * «Запечатать» — ком глины из инвентаря, «Разжечь» — огниво. Запечатанная тлеет по календарю; «Вскрыть» — что стало
 * сталью, ложится в ячейки камеры. На E можно и без окна: слиток, уголь, ком глины, огниво.
 * <p>Железо набирает углерод {@link #DONE} суток. Вскрыли раньше — часть слитков ещё железо, а сталь неровная (хуже);
 * в самый раз (до {@link #BEST}) — сталь лучше металла; передержали дольше {@link #OVER} — хрупкая (хуже).
 */
public class CementationBoxBlockEntity extends StationBlockEntity implements SmithStationBlock.Ticking {
    // Меняйте здесь ⚠️
    public static final int CAPACITY = 8;
    public static final int CHARCOAL_PER = 2;
    public static final double DONE = 1.5;
    public static final double BEST = 2.0;
    public static final double OVER = 3.0;
    public static final double COLD = 4.0;      // столько суток — и уголь прогорел, печь остыла
    public static final int MAX_CHARCOAL = CAPACITY * CHARCOAL_PER;

    public static final int D_CHARCOAL = 0, D_INGOTS = 1, D_SEALED = 2, D_LIT = 3, D_ELAPSED = 4, D_LEFT_MINUTES = 5, DATA = 6;

    private final SimpleContainer slots = new SimpleContainer(CAPACITY) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !sealed && stack.is(SmithingItems.IRON_INGOT.get());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };
    private final SimpleContainer fuelInput = new SimpleContainer(1) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !sealed && stack.is(Items.CHARCOAL);
        }
    };
    private int charcoal;
    private boolean sealed;
    private long litAt;
    private boolean feeding;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case D_CHARCOAL -> charcoal;
                case D_INGOTS -> ingots();
                case D_SEALED -> sealed ? 1 : 0;
                case D_LIT -> lit() ? 1 : 0;
                case D_ELAPSED -> litAt <= 0 || level == null ? 0 : (int) Math.round(Smithing.daysSince(level, litAt) * 100);
                case D_LEFT_MINUTES -> litAt <= 0 || level == null ? 0
                        : (int) Smithing.realMinutes(Math.max(0, DONE - Smithing.daysSince(level, litAt)));
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

    public CementationBoxBlockEntity(BlockPos pos, BlockState state) {
        super(SmithingBlocks.CEMENTATION_ENTITY.get(), pos, state);
        slots.addListener(container -> changed());
        fuelInput.addListener(container -> feedCharcoal());
    }

    public SimpleContainer slots() {
        return slots;
    }

    public SimpleContainer fuelInput() {
        return fuelInput;
    }

    public ContainerData data() {
        return data;
    }

    private boolean lit() {
        return getBlockState().getValue(SmithStationBlock.Lit.LIT);
    }

    private int ingots() {
        int n = 0;
        for (int i = 0; i < CAPACITY; i++) {
            if (slots.getItem(i).is(SmithingItems.IRON_INGOT.get())) {
                n++;
            }
        }
        return n;
    }

    /** Уголь из ячейки окна — в печь. */
    private void feedCharcoal() {
        if (feeding || sealed) {
            return;
        }
        feeding = true;
        ItemStack stack = fuelInput.getItem(0);
        boolean fed = false;
        while (!stack.isEmpty() && stack.is(Items.CHARCOAL) && charcoal < MAX_CHARCOAL) {
            stack.shrink(1);
            charcoal++;
            fed = true;
        }
        feeding = false;
        if (fed) {
            sound(SoundEvents.GRAVEL_PLACE, 0.6F, 1.2F);
            changed();
        }
    }

    @Override
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 100 != 0 || !state.getValue(SmithStationBlock.Lit.LIT)) {
            return;
        }
        if (Smithing.daysSince(level, litAt) > COLD) {
            level.setBlock(pos, state.setValue(SmithStationBlock.Lit.LIT, false), 3);
            return;
        }
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 2.05, pos.getZ() + 0.5, 3, 0.08, 0.0,
                    0.08, 0.01);
        }
    }

    // ---------------------------------------------------------------- E и окно

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new CementationMenu(id, inventory, this),
                getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(SmithingItems.IRON_INGOT.get()) && !sealed) {
            for (int i = 0; i < CAPACITY; i++) {
                if (slots.getItem(i).isEmpty()) {
                    slots.setItem(i, stack.copyWithCount(1));
                    consumeOne(player, stack);
                    sound(SoundEvents.METAL_PLACE, 0.8F, 1.0F);
                    return true;
                }
            }
            Smithing.say(player, "box_full", CAPACITY);
            return true;
        }
        if (stack.is(Items.CHARCOAL) && !sealed) {
            if (charcoal >= MAX_CHARCOAL) {
                Smithing.say(player, "box_full", CAPACITY);
                return true;
            }
            charcoal++;
            consumeOne(player, stack);
            sound(SoundEvents.GRAVEL_PLACE, 0.6F, 1.2F);
            changed();
            return true;
        }
        if (stack.is(Items.CLAY_BALL) && !sealed) {
            if (seal(player)) {
                consumeOne(player, stack);
            }
            return true;
        }
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)) {
            if (light(player) && stack.is(Items.FLINT_AND_STEEL)) {
                damage(player, hand, stack, 1);
            }
            return true;
        }
        if (stack.isEmpty() && player instanceof ServerPlayer serverPlayer) {
            openMenu(serverPlayer);
            return true;
        }
        return false;
    }

    /** Запечатать (ком глины тратит вызвавший). false — нельзя. */
    private boolean seal(Player player) {
        if (sealed) {
            return false;
        }
        if (ingots() == 0 || charcoal < ingots() * CHARCOAL_PER) {
            Smithing.say(player, "box_not_ready", ingots() * CHARCOAL_PER);
            return false;
        }
        sealed = true;
        sound(SoundEvents.MUD_PLACE, 1.0F, 1.0F);
        Smithing.say(player, "box_sealed");
        changed();
        return true;
    }

    /** Разжечь запечатанную. false — нельзя. */
    private boolean light(Player player) {
        if (!sealed || lit() || litAt > 0) {
            Smithing.say(player, sealed ? "box_lit_already" : "box_not_sealed");
            return false;
        }
        sound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
        litAt = Smithing.now(level);
        level.setBlock(worldPosition, getBlockState().setValue(SmithStationBlock.Lit.LIT, true), 3);
        Smithing.say(player, "box_lit", Smithing.realMinutes(DONE));
        changed();
        return true;
    }

    /** Окно: «Запечатать» — ком глины из инвентаря. */
    public void sealFromMenu(ServerPlayer player) {
        int slot = slotOf(player, Items.CLAY_BALL);
        if (slot < 0) {
            Smithing.say(player, "box_need_clay");
            return;
        }
        if (seal(player) && !player.getAbilities().instabuild) {
            player.getInventory().getItem(slot).shrink(1);
        }
    }

    /** Окно: «Разжечь» — огниво, огненный заряд или факел из инвентаря. */
    public void lightFromMenu(ServerPlayer player) {
        int slot = Fire.lighterSlot(player);
        if (slot < 0) {
            player.displayClientMessage(Component.translatable("message.halgventmod.cooking.need_lighter"), true);
            return;
        }
        ItemStack lighter = player.getInventory().getItem(slot);
        if (light(player) && !player.getAbilities().instabuild) {
            if (lighter.is(Items.FLINT_AND_STEEL)) {
                lighter.hurtAndBreak(1, player.serverLevel(), player, item -> {});
            } else if (lighter.is(Items.FIRE_CHARGE)) {
                lighter.shrink(1);
            }
        }
    }

    /** Окно: «Вскрыть» — запечатанную, но не разожжённую — разбить печать (глина пропала), тлеющую — достать. */
    public void openFromMenu(ServerPlayer player) {
        if (!sealed) {
            return;
        }
        if (litAt <= 0) {
            sealed = false;
            Smithing.say(player, "box_unsealed");
            changed();
            return;
        }
        double days = Smithing.daysSince(level, litAt);
        double share = Math.min(1, days / DONE);
        int steel = 0;
        int total = 0;
        for (int i = 0; i < CAPACITY; i++) {
            ItemStack ingot = slots.getItem(i);
            if (!ingot.is(SmithingItems.IRON_INGOT.get())) {
                continue;
            }
            total++;
            if (level.random.nextDouble() < share) {
                int quality = Smithing.quality(ingot) + (days < DONE ? -1 : days <= BEST ? 1 : days > OVER ? -1 : 0);
                slots.setItem(i, Smithing.setQuality(new ItemStack(SmithingItems.STEEL_INGOT.get()), quality));
                steel++;
            }
        }
        String key = days < DONE ? "box_early" : days <= BEST ? "box_best" : days > OVER ? "box_over" : "box_done";
        Smithing.tell(player, key, steel, total - steel);
        charcoal = 0;
        sealed = false;
        litAt = 0;
        level.setBlock(worldPosition, getBlockState().setValue(SmithStationBlock.Lit.LIT, false), 3);
        sound(SoundEvents.DECORATED_POT_SHATTER, 0.7F, 0.8F);
        changed();
    }

    private static int slotOf(Player player, net.minecraft.world.item.Item item) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    /** ПКМ — окно печи. */
    @Override
    public void report(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            openMenu(serverPlayer);
        }
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < slots.getContainerSize(); i++) {
            if (!slots.getItem(i).isEmpty()) {
                all.add(slots.getItem(i).copy());
            }
        }
        if (!fuelInput.getItem(0).isEmpty()) {
            all.add(fuelInput.getItem(0).copy());
        }
        if (charcoal > 0 && litAt <= 0) {
            all.add(new ItemStack(Items.CHARCOAL, charcoal));
        }
        return all;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (int i = 0; i < slots.getContainerSize(); i++) {
            ItemStack stack = slots.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = (CompoundTag) stack.save(registries);
                entry.putByte("Slot", (byte) i);
                list.add(entry);
            }
        }
        tag.put("Slots", list);
        tag.putInt("Charcoal", charcoal);
        tag.putBoolean("Sealed", sealed);
        tag.putLong("LitAt", litAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < slots.getContainerSize(); i++) {
            slots.setItem(i, ItemStack.EMPTY);
        }
        ListTag list = tag.getList("Slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot");
            if (slot >= 0 && slot < slots.getContainerSize()) {
                slots.setItem(slot, ItemStack.parseOptional(registries, entry));
            }
        }
        charcoal = tag.getInt("Charcoal");
        sealed = tag.getBoolean("Sealed");
        litAt = tag.getLong("LitAt");
    }
}
