package com.nrdhvnstd.halgventmod.smithing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.ForgeMenu;
import com.nrdhvnstd.halgventmod.pottery.Pottery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Кузнечный горн. E:
 * <pre>
 *   древесный уголь, каменный уголь — подкинуть (запас до {@link #MAX_FUEL} тиков)
 *   огниво, огненный заряд, факел    — разжечь
 *   тигель                           — поставить в горн
 *   пустая рука                      — окно горна ({@link ForgeMenu}); Shift — качнуть мехи
 * </pre>
 * Жар 0–100 тянется к {@link #BASE_CHARCOAL} (на каменном угле — {@link #BASE_COAL}) плюс мехи. Горн нужен
 * наковальне рядом: в окно ковки уходит его жар — чем жарче горн, тем быстрее греется заготовка и тем выше её предел.
 * <p>В окне — тигель: в ячейку шихты кладут металл ({@link Crucible#accept}), «Плавить» — мини-игра плавки
 * (MeltStep), выходит расплав. Пока горн горит, расплав в тигле жидкий; вынуть тигель с жидким металлом — только
 * имея клещи. С ним — к литейному столу.
 */
public class ForgeBlockEntity extends StationBlockEntity implements SmithStationBlock.Ticking, Minigame.Host {
    // Меняйте здесь ⚠️
    public static final int MAX_FUEL = 12800;
    public static final int CHARCOAL_TICKS = 3200;
    public static final int COAL_TICKS = 4000;
    public static final float BASE_CHARCOAL = 62;
    public static final float BASE_COAL = 72;
    public static final float PUMP = 9;             // мехи: столько жара сверху за качок
    public static final float MAX_BOOST = 38;
    public static final float BOOST_DECAY = 0.12F;  // за тик
    public static final float RISE = 0.6F;          // жар к цели за тик
    public static final float FALL = 0.25F;
    public static final float WORKABLE = 40;        // ниже — наковальне горн не годится, расплав стынет
    public static final int MELT_MIN_TICKS = 80;
    private static final int STEP = 5;

    // Окно: тигель, шихта (уходит в тигель), уголь (уходит в горн)
    public static final int IN_CRUCIBLE = 0, IN_CHARGE = 1, IN_FUEL = 2;
    /** Почему шихта не легла в тигель — ключи сообщений по номеру (D_REJECT). */
    public static final String[] REJECTS = {"", "crucible_none", "crucible_broken", "crucible_lid_already", "crucible_iron",
            "crucible_not_metal", "crucible_full", "crucible_mix", "forge_full"};
    public static final int D_HEAT = 0, D_FUEL = 1, D_LIT = 2, D_COAL = 3, D_REJECT = 4, DATA = 5;

    private int fuel;
    private int coalFuel;          // сколько из запаса — каменный уголь (горит жарче)
    private float heat;
    private float boost;
    private int reject;
    private boolean feeding;
    private final SimpleContainer slots = new SimpleContainer(3) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return switch (slot) {
                case IN_CRUCIBLE -> Crucible.is(stack);
                case IN_FUEL -> stack.is(Items.CHARCOAL) || stack.is(Items.COAL);
                default -> charge(stack);
            };
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    };
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case D_HEAT -> Math.round(heat * 10);
                case D_FUEL -> fuel / 20;
                case D_LIT -> lit() ? 1 : 0;
                case D_COAL -> coal() ? 1 : 0;
                case D_REJECT -> reject;
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

    public ForgeBlockEntity(BlockPos pos, BlockState state) {
        super(SmithingBlocks.FORGE_ENTITY.get(), pos, state);
        slots.addListener(container -> feed());
    }

    /** Что берёт ячейка шихты: металл тигля, ком глины (крышка); железо — чтобы сказать, что его не плавят. */
    public static boolean charge(ItemStack stack) {
        return Metal.charge(stack) != null || Metal.iron(stack) || stack.is(Items.CLAY_BALL);
    }

    public SimpleContainer slots() {
        return slots;
    }

    public ContainerData data() {
        return data;
    }

    public ItemStack crucible() {
        return slots.getItem(IN_CRUCIBLE);
    }

    public boolean lit() {
        return getBlockState().getValue(SmithStationBlock.Lit.LIT);
    }

    public float heat() {
        return heat;
    }

    /** Горит каменный уголь — жарче. */
    public boolean coal() {
        return coalFuel > 0;
    }

    /** Годится ли горн для работы прямо сейчас. */
    public boolean workable() {
        return lit() && fuel > 0 && heat >= WORKABLE;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < slots.getContainerSize(); i++) {
            if (!slots.getItem(i).isEmpty()) {
                all.add(slots.getItem(i).copy());
            }
        }
        return all;
    }

    /** Из ячеек окна: уголь — в горн, шихта — в тигель, по одной, пока принимают. */
    private void feed() {
        if (feeding) {
            return;
        }
        feeding = true;
        boolean fed = false;
        ItemStack fuelStack = slots.getItem(IN_FUEL);
        while (!fuelStack.isEmpty()) {
            int value = fuelStack.is(Items.COAL) ? COAL_TICKS : CHARCOAL_TICKS;
            if (fuel + value > MAX_FUEL) {
                break;
            }
            fuel += value;
            if (fuelStack.is(Items.COAL)) {
                coalFuel += value;
            }
            fuelStack.shrink(1);
            fed = true;
        }
        ItemStack charge = slots.getItem(IN_CHARGE);
        int why = 0;
        while (!charge.isEmpty()) {
            String reason = Crucible.accept(crucible(), charge);
            if (reason != null) {
                why = indexOf(reason);
                break;
            }
            charge.shrink(1);
            fed = true;
        }
        if (why == 0 && !fuelStack.isEmpty()) {
            why = indexOf("forge_full");
        }
        reject = why;
        if (fed) {
            sound(SoundEvents.GRAVEL_PLACE, 0.8F, 1.2F);
            slots.setChanged();
        }
        feeding = false;
        changed(); // тигель виден в горне — вынули или поставили
    }

    /** Расплав в горне держится жидким (тик): без лишнего пересыла блока. */
    private void quietly(Runnable change) {
        feeding = true;
        change.run();
        slots.setChanged();
        feeding = false;
    }

    private static int indexOf(String reason) {
        for (int i = 1; i < REJECTS.length; i++) {
            if (REJECTS[i].equals(reason)) {
                return i;
            }
        }
        return 0;
    }

    @Override
    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % STEP != 0) {
            return;
        }
        boolean lit = state.getValue(SmithStationBlock.Lit.LIT);
        if (lit) {
            int burn = STEP + Math.round(boost / MAX_BOOST * STEP); // мехи — прожорливее
            fuel -= burn;
            coalFuel = Math.max(0, coalFuel - burn);
            if (fuel <= 0) {
                fuel = 0;
                coalFuel = 0;
                level.setBlock(pos, state.setValue(SmithStationBlock.Lit.LIT, false), 3);
                sound(SoundEvents.FIRE_EXTINGUISH, 0.6F, 0.9F);
                lit = false;
            }
        }
        boost = Math.max(0, boost - BOOST_DECAY * STEP);
        float target = lit ? (coal() ? BASE_COAL : BASE_CHARCOAL) + boost : 0;
        heat = heat < target ? Math.min(target, heat + RISE * STEP) : Math.max(target, heat - FALL * STEP);
        heat = Math.min(100, heat);
        // Расплав в горящем горне не стынет
        ItemStack crucible = crucible();
        if (lit && heat >= WORKABLE && Crucible.is(crucible)) {
            Melt melt = Crucible.melt(crucible);
            long now = level.getGameTime();
            if (melt != null && melt.molten() && melt.liquidUntil() >= now
                    && melt.liquidUntil() < now + Crucible.COOL_TICKS - 100) {
                quietly(() -> Crucible.keepLiquid(crucible, now + Crucible.COOL_TICKS));
            }
        }
        if (lit && level instanceof ServerLevel server && level.random.nextInt(3) == 0) {
            server.sendParticles(boost > 10 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1 + Math.round(boost / 12), 0.15, 0.02,
                    0.15, 0.01);
        }
        setChanged();
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.TORCH)) {
            return ignite(player, hand, stack);
        }
        if (stack.is(Items.CHARCOAL) || stack.is(Items.COAL)) {
            int value = stack.is(Items.COAL) ? COAL_TICKS : CHARCOAL_TICKS;
            if (fuel + value > MAX_FUEL) {
                Smithing.say(player, "forge_full");
                return true;
            }
            fuel += value;
            if (stack.is(Items.COAL)) {
                coalFuel += value;
            }
            consumeOne(player, stack);
            sound(SoundEvents.GRAVEL_PLACE, 0.8F, 1.2F);
            report(player);
            changed();
            return true;
        }
        if (Crucible.is(stack)) {
            if (!crucible().isEmpty()) {
                Smithing.say(player, "forge_has_crucible");
            } else {
                slots.setItem(IN_CRUCIBLE, stack.split(1));
                sound(SoundEvents.DECORATED_POT_INSERT, 0.8F, 1.0F);
                changed();
                if (player instanceof ServerPlayer serverPlayer) {
                    openMenu(serverPlayer);
                }
            }
            return true;
        }
        if (stack.isEmpty()) {
            if (player.isShiftKeyDown() && lit()) {
                pump(player);
            } else if (player instanceof ServerPlayer serverPlayer) {
                openMenu(serverPlayer);
            }
            return true;
        }
        if (com.nrdhvnstd.halgventmod.cooking.Fire.fuelValue(stack) > 0) {
            Smithing.say(player, "forge_wood");
            return true;
        }
        return false;
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new ForgeMenu(id, inventory, this),
                getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
    }

    /** Качнуть мехи (Shift + E пустой рукой, кнопка в окне). */
    public void pump(Player player) {
        if (!lit()) {
            Smithing.say(player, "forge_not_lit");
            return;
        }
        boost = Math.min(MAX_BOOST, boost + PUMP);
        sound(SoundEvents.FIRECHARGE_USE, 0.25F, 0.5F + boost / MAX_BOOST * 0.4F);
        player.swing(InteractionHand.MAIN_HAND, true);
        changed();
    }

    private boolean ignite(Player player, InteractionHand hand, ItemStack stack) {
        if (lit()) {
            Smithing.say(player, "forge_lit_already");
            return true;
        }
        if (fuel <= 0) {
            Smithing.say(player, "forge_no_fuel");
            return true;
        }
        if (stack.is(Items.FLINT_AND_STEEL)) {
            damage(player, hand, stack, 1);
            sound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
        } else if (stack.is(Items.FIRE_CHARGE)) {
            consumeOne(player, stack);
            sound(SoundEvents.FIRECHARGE_USE, 1.0F, 1.0F);
        } else {
            sound(SoundEvents.FIRE_AMBIENT, 1.0F, 1.0F);
        }
        level.setBlock(worldPosition, getBlockState().setValue(SmithStationBlock.Lit.LIT, true), 3);
        changed();
        return true;
    }

    // ---------------------------------------------------------------- плавка

    /** «Плавить» в окне: тигель с шихтой (или застывшим металлом), горн разгорелся — мини-игра плавки. */
    public void startMelt(ServerPlayer player) {
        if (player.distanceToSqr(worldPosition.getCenter()) > 64) {
            return;
        }
        ItemStack crucible = crucible();
        Melt melt = Crucible.melt(crucible);
        Crucible.Alloy alloy = Crucible.willGive(melt);
        String blocker = !Crucible.is(crucible) ? "forge_no_crucible"
                : Pottery.cracked(crucible) ? "crucible_broken"
                : Crucible.total(melt) <= 0 ? "crucible_empty"
                : Crucible.liquid(crucible, level) ? "crucible_liquid"
                : alloy == null ? "crucible_no_alloy"
                : !lit() ? "forge_not_lit"
                : heat < WORKABLE ? "forge_cold" : null;
        if (blocker != null) {
            Smithing.say(player, blocker);
            return;
        }
        player.closeContainer();
        int info = Math.clamp(Math.round(heat), 0, 100) | (coal() ? SmithArea.COAL_BIT : 0);
        Minigame.open(player, worldPosition, Minigame.FORGE_MELT, alloy.metal().ordinal(), MELT_MIN_TICKS, info,
                Crucible.total(melt));
    }

    @Override
    public void minigameResult(ServerPlayer player, Minigame.Session session, boolean done, List<Integer> data) {
        if (session.kind() != Minigame.FORGE_MELT) {
            return;
        }
        ItemStack crucible = crucible();
        Melt melt = Crucible.melt(crucible);
        Crucible.Alloy alloy = Crucible.willGive(melt);
        if (!done || alloy == null || alloy.metal().ordinal() != session.product() || Crucible.liquid(crucible, level)) {
            Smithing.say(player, "melt_quit");
            openMenu(player);
            return;
        }
        int score = Minigame.score(data, 0);
        int work = Smithing.workDelta(score);
        // Переплавка застывшего: лучше не станет, а состав уже учтён
        if (melt.molten()) {
            work = Math.min(0, work);
        }
        int quality = Math.clamp(Crucible.quality(melt) + (melt.molten() ? 0 : alloy.delta()) + work, 0,
                Smithing.MAX_QUALITY);
        boolean zincBurnt = !melt.lid() && Crucible.parts(melt).containsKey(Metal.ZINC);
        Crucible.molten(crucible, quality, level.getGameTime() + Crucible.COOL_TICKS);
        slots.setChanged();
        int total = Crucible.total(Crucible.melt(crucible));
        Smithing.tell(player, "melted", net.minecraft.network.chat.Component.translatable(alloy.metal().langKey()),
                ingots(total), score);
        if (zincBurnt) {
            Smithing.say(player, "zinc_burnt");
        }
        sound(SoundEvents.LAVA_POP, 0.8F, 1.0F);
        changed();
        openMenu(player);
    }

    /** «6» или «6,5» слитка из долей. */
    public static String ingots(int parts) {
        int whole = parts / Crucible.PER_INGOT;
        int rest = parts % Crucible.PER_INGOT;
        return rest == 0 ? String.valueOf(whole) : whole + "," + rest;
    }

    @Override
    public boolean hasReport() {
        return true;
    }

    @Override
    public void report(Player player) {
        int minutes = Math.max(0, Math.round(fuel / 1200.0F));
        if (!lit()) {
            Smithing.say(player, fuel > 0 ? "forge_out" : "forge_empty", minutes, Math.round(heat * 10));
        } else {
            Smithing.say(player, heat >= WORKABLE ? "forge_report" : "forge_warming", Math.round(heat * 10), minutes,
                    coal() ? 1 : 0);
        }
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Fuel", fuel);
        tag.putInt("CoalFuel", coalFuel);
        tag.putFloat("Heat", heat);
        tag.putFloat("Boost", boost);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuel = tag.getInt("Fuel");
        coalFuel = tag.getInt("CoalFuel");
        heat = tag.getFloat("Heat");
        boost = tag.getFloat("Boost");
        feeding = true;
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
        feeding = false;
    }
}
