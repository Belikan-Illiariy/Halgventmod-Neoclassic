package com.nrdhvnstd.halgventmod.smithing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.BloomeryMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Сыродутный горн. На E:
 * <pre>
 *   руда (одного рода)      — заложить, до {@link #MAX_ORE}
 *   древесный уголь          — заложить, до {@link #MAX_FUEL} (каменный тоже горит, но портит железо — сера)
 *   флюс                     — заложить, до {@link #MAX_FLUX}
 *   огниво, факел            — начать плавку: окно мехов (BellowsStep), держите жар в полосе
 *   Shift + E пустой рукой   — выгрести всё назад
 * </pre>
 * Одна закладка ({@link Smelt#ore} руды + {@link #FUEL_PER_BATCH} угля + флюс) — одна крица или слиток; за
 * плавку до {@link #MAX_BATCHES}. Качество: сорт руды, обжиг (болотная — лучше обожжённой, медная без обжига — хуже),
 * каменный уголь и нехватка флюса — хуже, плюс поправка за мехи. Совсем плохие мехи — половина ушла в шлак.
 */
public class BloomeryBlockEntity extends StationBlockEntity implements Minigame.Host {
    public static final int MAX_ORE = 12;
    public static final int MAX_FUEL = 12;
    public static final int MAX_FLUX = 4;
    public static final int MAX_BATCHES = 4;
    public static final int FUEL_PER_BATCH = 2;
    public static final int MIN_TICKS = 600;      // мехи — не меньше 30 секунд

    /** Что плавят и что выходит. Меняйте здесь ⚠️ */
    public enum Smelt {
        // Медь, олово, серебро и свинец с серебром (потом — в купель) стекают расплавом в тигель (metal); крица — штуками
        //       руда                           на закладку  выход                               качество  в тигель
        IRON    (SmithingItems.IRON_ORE,      2, SmithingItems.IRON_BLOOM,          2, null),
        BOG     (SmithingItems.BOG_IRON_ORE,  3, SmithingItems.IRON_BLOOM,          1, null),
        COPPER  (SmithingItems.COPPER_ORE,    2, SmithingItems.COPPER_INGOT,        2, Metal.COPPER),
        TIN     (SmithingItems.TIN_ORE,       2, SmithingItems.TIN_INGOT,           2, Metal.TIN),
        LEAD    (SmithingItems.LEAD_ORE,      1, SmithingItems.ARGENTIFEROUS_LEAD,  2, Metal.SILVER_LEAD),
        SILVER  (SmithingItems.SILVER_ORE,    3, SmithingItems.SILVER_INGOT,        2, Metal.SILVER);

        private final Supplier<? extends Item> ore;
        private final int perBatch;
        private final Supplier<? extends Item> result;
        private final int quality;
        @Nullable
        private final Metal metal;

        Smelt(Supplier<? extends Item> ore, int perBatch, Supplier<? extends Item> result, int quality, @Nullable Metal metal) {
            this.ore = ore;
            this.perBatch = perBatch;
            this.result = result;
            this.quality = quality;
            this.metal = metal;
        }

        /** Металл, что стекает в тигель; null — выходит штуками (крица, свинец с серебром). */
        @Nullable
        public Metal metal() {
            return metal;
        }

        public Item ore() {
            return ore.get();
        }

        public int perBatch() {
            return perBatch;
        }

        public Item result() {
            return result.get();
        }

        public boolean iron() {
            return this == IRON || this == BOG;
        }

        @Nullable
        public static Smelt of(ItemStack stack) {
            for (Smelt smelt : values()) {
                if (stack.is(smelt.ore())) {
                    return smelt;
                }
            }
            return null;
        }
    }

    @Nullable
    private Smelt smelt;
    private int ore;
    private int roasted;
    private int charcoal;
    private int coal;
    private int flux;

    // Окно: руда, уголь и флюс в ячейку сразу уходят в горн, что не влезло — остаётся в ячейке
    // Ячейка тигля — для меди, олова и серебра: расплав стекает в него
    public static final int IN_ORE = 0, IN_FUEL = 1, IN_FLUX = 2, IN_CRUCIBLE = 3;
    private final SimpleContainer inputs = new SimpleContainer(4) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return switch (slot) {
                case IN_ORE -> Smelt.of(stack) != null;
                case IN_CRUCIBLE -> Crucible.is(stack);
                case IN_FUEL -> stack.is(Items.CHARCOAL) || stack.is(Items.COAL);
                default -> stack.is(SmithingItems.FLUX.get());
            };
        }
    };
    public static final int D_ORE = 0, D_ROASTED = 1, D_CHARCOAL = 2, D_COAL = 3, D_FLUX = 4, D_BATCHES = 5, D_SMELT = 6,
            D_LIT = 7, DATA = 8;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case D_ORE -> ore;
                case D_ROASTED -> roasted;
                case D_CHARCOAL -> charcoal;
                case D_COAL -> coal;
                case D_FLUX -> flux;
                case D_BATCHES -> batches();
                case D_SMELT -> smelt == null ? -1 : smelt.ordinal();
                case D_LIT -> lit() ? 1 : 0;
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
    private boolean feeding;

    public BloomeryBlockEntity(BlockPos pos, BlockState state) {
        super(SmithingBlocks.BLOOMERY_ENTITY.get(), pos, state);
        inputs.addListener(container -> feed());
    }

    public SimpleContainer inputs() {
        return inputs;
    }

    public ContainerData data() {
        return data;
    }

    /** Из ячеек окна — в горн, по одной, пока принимает. */
    private void feed() {
        if (feeding || lit()) {
            return;
        }
        feeding = true;
        boolean fed = false;
        for (int slot = 0; slot < IN_CRUCIBLE; slot++) {
            ItemStack stack = inputs.getItem(slot);
            while (!stack.isEmpty() && accept(stack) == null) {
                stack.shrink(1);
                fed = true;
            }
        }
        feeding = false;
        if (fed) {
            sound(SoundEvents.GRAVEL_PLACE, 0.8F, 0.9F);
        }
        changed();
    }

    /** Тигель для меди, олова, серебра: есть ли, примет ли всё, что выйдет; null — да (или металл не в тигель). */
    @Nullable
    private String pourBlocker() {
        if (smelt == null || smelt.metal() == null) {
            return null;
        }
        return Crucible.canPour(inputs.getItem(IN_CRUCIBLE), smelt.metal(), batches() * Crucible.PER_INGOT);
    }

    public ItemStack crucible() {
        return inputs.getItem(IN_CRUCIBLE);
    }

    /** Принять одну штуку (руду, уголь, флюс): null — принято, иначе — ключ, почему нет. Стопку не уменьшает. */
    private String accept(ItemStack stack) {
        Smelt kind = Smelt.of(stack);
        if (kind != null) {
            if (smelt != null && smelt != kind && ore > 0) {
                return "bloomery_other_ore";
            }
            if (ore >= MAX_ORE) {
                return "bloomery_full";
            }
            smelt = kind;
            ore++;
            if (Smithing.roasted(stack)) {
                roasted++;
            }
            return null;
        }
        if (stack.is(Items.CHARCOAL) || stack.is(Items.COAL)) {
            if (fuel() >= MAX_FUEL) {
                return "bloomery_full";
            }
            if (stack.is(Items.COAL)) {
                coal++;
            } else {
                charcoal++;
            }
            return null;
        }
        if (stack.is(SmithingItems.FLUX.get())) {
            if (flux >= MAX_FLUX) {
                return "bloomery_full";
            }
            flux++;
            return null;
        }
        return "bloomery_not_this";
    }

    /** ПКМ и E пустой рукой — окно горна. */
    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new BloomeryMenu(id, inventory, this),
                getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
    }

    /** «Плавить» в окне: огниво, огненный заряд или факел из инвентаря. */
    public void startFromMenu(ServerPlayer player) {
        if (lit() || player.distanceToSqr(worldPosition.getCenter()) > 64) {
            return;
        }
        int slot = com.nrdhvnstd.halgventmod.cooking.Fire.lighterSlot(player);
        if (slot < 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.halgventmod.cooking.need_lighter"), true);
            return;
        }
        String pour = batches() > 0 ? pourBlocker() : null;
        if (pour != null) {
            Smithing.say(player, pour);
            return;
        }
        player.closeContainer();
        ItemStack lighter = player.getInventory().getItem(slot);
        if (batches() > 0 && !player.getAbilities().instabuild) {
            if (lighter.is(Items.FLINT_AND_STEEL)) {
                lighter.hurtAndBreak(1, player.serverLevel(), player, item -> {});
            } else if (lighter.is(Items.FIRE_CHARGE)) {
                lighter.shrink(1);
            }
        }
        start(player, null, ItemStack.EMPTY);
    }

    /** «Выгрести» в окне. */
    public void takeAllFromMenu(ServerPlayer player) {
        if (!lit() && player.distanceToSqr(worldPosition.getCenter()) <= 64) {
            takeAll(player);
        }
    }

    private boolean lit() {
        return getBlockState().getValue(SmithStationBlock.Lit.LIT);
    }

    // Для отрисовки
    public int ore() {
        return ore;
    }

    public int fuel() {
        return charcoal + coal;
    }

    /** Сколько закладок выйдет из того, что лежит. */
    public int batches() {
        if (smelt == null) {
            return 0;
        }
        return Math.min(MAX_BATCHES, Math.min(ore / smelt.perBatch(), fuel() / FUEL_PER_BATCH));
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (lit()) {
            Smithing.say(player, "bloomery_busy");
            return true;
        }
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)) {
            return start(player, hand, stack);
        }
        if (stack.isEmpty()) {
            if (player.isShiftKeyDown()) {
                return takeAll(player);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                openMenu(serverPlayer);
            }
            return true;
        }
        if (Smelt.of(stack) != null || stack.is(Items.CHARCOAL) || stack.is(Items.COAL) || stack.is(SmithingItems.FLUX.get())) {
            String refused = accept(stack);
            if (refused != null) {
                Smithing.say(player, refused);
                return true;
            }
            if (stack.is(Items.COAL)) {
                Smithing.say(player, "bloomery_coal_warning");
            } else {
                report(player);
            }
            consumeOne(player, stack);
            changed();
            return true;
        }
        if (com.nrdhvnstd.halgventmod.cooking.Fire.fuelValue(stack) > 0) {
            Smithing.say(player, "bloomery_wood");
            return true;
        }
        if (stack.is(SmithingItems.ZINC_ORE.get()) || stack.is(SmithingItems.GOLD_ORE.get())
                || stack.is(SmithingItems.CINNABAR.get()) || stack.is(SmithingItems.GOLD_DUST.get())) {
            Smithing.say(player, "bloomery_not_this");
            return true;
        }
        return false;
    }

    private boolean start(Player player, InteractionHand hand, ItemStack lighter) {
        int batches = batches();
        if (batches <= 0) {
            Smithing.say(player, "bloomery_not_enough", smelt == null ? 2 : smelt.perBatch(), FUEL_PER_BATCH);
            return true;
        }
        String pour = pourBlocker();
        if (pour != null) {
            Smithing.say(player, pour);
            return true;
        }
        if (lighter.is(Items.FLINT_AND_STEEL) && hand != null) {
            damage(player, hand, lighter, 1);
        } else if (lighter.is(Items.FIRE_CHARGE)) {
            consumeOne(player, lighter);
        }
        sound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
        level.setBlock(worldPosition, getBlockState().setValue(SmithStationBlock.Lit.LIT, true), 3);
        if (player instanceof ServerPlayer serverPlayer) {
            int flags = (coal > 0 ? 1 : 0) | (flux < batches ? 2 : 0);
            Minigame.open(serverPlayer, worldPosition, Minigame.BLOOMERY, smelt.ordinal(), MIN_TICKS, batches, flags);
        }
        changed();
        return true;
    }

    @Override
    public void minigameResult(ServerPlayer player, Minigame.Session session, boolean done, List<Integer> data) {
        level.setBlock(worldPosition, getBlockState().setValue(SmithStationBlock.Lit.LIT, false), 3);
        if (!done || smelt == null || smelt.ordinal() != session.product()) {
            Smithing.say(player, "bloomery_quit");
            changed();
            return;
        }
        int batches = Math.min(batches(), session.info());
        if (batches <= 0) {
            return;
        }
        int score = Minigame.score(data, 0);
        // Что ушло в плавку
        int oreUsed = batches * smelt.perBatch();
        int fuelUsed = batches * FUEL_PER_BATCH;
        int coalUsed = Math.min(coal, Math.max(0, fuelUsed - charcoal));
        int fluxUsed = Math.min(flux, batches);
        boolean allRoasted = roasted >= oreUsed;
        roasted = Math.max(0, roasted - oreUsed);
        ore -= oreUsed;
        int charcoalUsed = fuelUsed - coalUsed;
        charcoal -= charcoalUsed;
        coal -= coalUsed;
        flux -= fluxUsed;
        // Качество ⚠️
        int quality = smelt.quality + Smithing.workDelta(score);
        if (smelt == Smelt.BOG && allRoasted) {
            quality++;
        }
        if (smelt == Smelt.COPPER && !allRoasted) {
            quality--;
        }
        if (coalUsed > 0) {
            quality -= smelt.iron() ? 2 : 1;
        }
        if (fluxUsed < batches) {
            quality--;
        }
        int out = score < Smithing.SCORE_POOR ? batches / 2 : batches;
        ItemStack crucible = crucible();
        if (out > 0 && smelt.metal() != null && Crucible.canPour(crucible, smelt.metal(), out * Crucible.PER_INGOT) == null) {
            // Расплав стекает в тигель: жидкий минуту, вынимают клещами
            Crucible.pour(crucible, smelt.metal(), out * Crucible.PER_INGOT, Math.clamp(quality, 0, Smithing.MAX_QUALITY),
                    level.getGameTime() + Crucible.COOL_TICKS, level);
            inputs.setChanged();
            Smithing.tell(player, "smelted_crucible", out, net.minecraft.network.chat.Component.translatable(
                    smelt.metal().langKey()), score);
        } else if (out > 0) {
            ItemStack result = Smithing.setQuality(new ItemStack(smelt.result(), out), quality);
            give(player, result);
            Smithing.tell(player, "smelted", out, new ItemStack(smelt.result()).getHoverName(), score);
        } else {
            Smithing.tell(player, "smelt_failed", score);
        }
        if (out < batches) {
            Smithing.say(player, "smelt_slag", batches - out);
        }
        sound(SoundEvents.LAVA_EXTINGUISH, 1.0F, 0.8F);
        if (ore <= 0) {
            smelt = null;
        }
        changed();
    }

    private boolean takeAll(Player player) {
        if (smelt != null) {
            int plain = ore - roasted;
            if (plain > 0) {
                give(player, new ItemStack(smelt.ore(), plain));
            }
            if (roasted > 0) {
                ItemStack stack = new ItemStack(smelt.ore(), roasted);
                stack.set(com.nrdhvnstd.halgventmod.registry.ModComponents.ROASTED.get(), true);
                give(player, stack);
            }
        }
        if (charcoal > 0) {
            give(player, new ItemStack(Items.CHARCOAL, charcoal));
        }
        if (coal > 0) {
            give(player, new ItemStack(Items.COAL, coal));
        }
        if (flux > 0) {
            give(player, new ItemStack(SmithingItems.FLUX.get(), flux));
        }
        smelt = null;
        ore = roasted = charcoal = coal = flux = 0;
        sound(SoundEvents.GRAVEL_BREAK, 0.8F, 1.0F);
        changed();
        return true;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    @Override
    public void report(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            openMenu(serverPlayer);
            return;
        }
        Smithing.say(player, "bloomery_report", ore, smelt == null ? "-" : new ItemStack(smelt.ore()).getHoverName(),
                charcoal + coal, flux, batches());
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        if (smelt != null && ore > 0) {
            all.add(new ItemStack(smelt.ore(), ore));
        }
        if (charcoal > 0) {
            all.add(new ItemStack(Items.CHARCOAL, charcoal));
        }
        if (coal > 0) {
            all.add(new ItemStack(Items.COAL, coal));
        }
        if (flux > 0) {
            all.add(new ItemStack(SmithingItems.FLUX.get(), flux));
        }
        for (int i = 0; i < inputs.getContainerSize(); i++) {
            if (!inputs.getItem(i).isEmpty()) {
                all.add(inputs.getItem(i).copy());
            }
        }
        return all;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Smelt", smelt == null ? -1 : smelt.ordinal());
        tag.putInt("Ore", ore);
        tag.putInt("Roasted", roasted);
        tag.putInt("Charcoal", charcoal);
        tag.putInt("Coal", coal);
        tag.putInt("Flux", flux);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int index = tag.getInt("Smelt");
        smelt = index >= 0 && index < Smelt.values().length && tag.contains("Smelt") ? Smelt.values()[index] : null;
        ore = tag.getInt("Ore");
        roasted = tag.getInt("Roasted");
        charcoal = tag.getInt("Charcoal");
        coal = tag.getInt("Coal");
        flux = tag.getInt("Flux");
    }
}
