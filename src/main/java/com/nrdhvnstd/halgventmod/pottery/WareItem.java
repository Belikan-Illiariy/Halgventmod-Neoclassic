package com.nrdhvnstd.halgventmod.pottery;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.food.DrinkItem;
import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.food.ModFoods;
import com.nrdhvnstd.halgventmod.food.Thirst;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.world.Calendar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Обожжённая посуда. Что умеет, зависит от формы:
 * <ul>
 *   <li><b>кружка, кувшин</b> — ПКМ по воде набрать, ПКМ — пить (мера — {@link Pottery#DRINK_THIRST} жажды),
 *       Shift + ПКМ — вылить;
 *       вода из кувшина идёт и в станции вместо ведра ({@link Pottery#takeWater});</li>
 *   <li><b>корчага</b> — {@link Pottery#AMPHORA_MEASURES} мер воды или до {@link Pottery#AMPHORA_DRINKS} склянок питья;</li>
 *   <li><b>горшок</b> — до {@link Pottery#POT_CAPACITY} еды одного рода: внутри портится медленнее; ставится на очаг
 *       вместо горшка, но изнашивается;</li>
 *   <li><b>миска</b> — в неё черпают из горшка, после еды остаётся в руке;</li>
 *   <li><b>светильник</b> — заправляется салом или воском, ПКМ — зажечь (нужно огниво или факел при себе).</li>
 * </ul>
 * В инвентаре, как с мешочком: положить на посудину еду, питьё, воск (запечатать), сало;
 * ПКМ по посудине пустым курсором — вынуть.
 * <p>От пользования (попить, вычерпать, вынуть, зажечь) посуда может треснуть ({@link Pottery#wear}): треснувшую
 * можно только опорожнить, Shift + ПКМ — разбить на черепки.
 */
public class WareItem extends Item {
    /**
     * Из чего посуда: глина (обжигают в горне), металл (литая: пьютер, серебро — не трескается), стекло (выдувное:
     * бьётся, как глина, разбитое — бой стекла; не отожжённое — хрупкое, как недожог).
     */
    public enum Material { CLAY, METAL, GLASS }

    private final VesselForm form;
    private final Material material;

    public WareItem(Properties properties, VesselForm form) {
        this(properties, form, Material.CLAY);
    }

    /** @param metal литая посуда (пьютер, серебро): служит так же, но не трескается и не обжигается. */
    public WareItem(Properties properties, VesselForm form, boolean metal) {
        this(properties, form, metal ? Material.METAL : Material.CLAY);
    }

    public WareItem(Properties properties, VesselForm form, Material material) {
        super(properties);
        this.form = form;
        this.material = material;
    }

    public VesselForm form() {
        return form;
    }

    public Material material() {
        return material;
    }

    public boolean metal() {
        return material == Material.METAL;
    }

    public boolean glass() {
        return material == Material.GLASS;
    }

    /** Корчага, кувшин и кружка держат питьё (склянками); кувшин и кружка — ещё и воду, но не вместе. */
    private boolean holdsDrinks() {
        return form == VesselForm.AMPHORA || form == VesselForm.JUG || form == VesselForm.CUP;
    }

    /** Из кувшина и кружки пьют прямо: и воду, и питьё. */
    private boolean sippable() {
        return form == VesselForm.JUG || form == VesselForm.CUP;
    }

    /** Питьё внутри кувшина или кружки (пусто — нет). */
    private static ItemStack drinkInside(ItemStack stack, Level level) {
        Stored stored = stack.get(ModComponents.STORED.get());
        return stored == null || stored.sealed() ? ItemStack.EMPTY : Pottery.unpack(stored, stack, level);
    }

    // ---------------------------------------------------------------- ПКМ: вода, питьё, светильник

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (Pottery.cracked(stack)) {
            // Треснувшей не пользуются: Shift + ПКМ — разбить на черепки (что внутри — в руки)
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    smash(stack, player, hand);
                } else {
                    Pottery.say(player, "ware_cracked_use");
                }
            }
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
        if (form == VesselForm.LAMP) {
            if (!level.isClientSide) {
                toggleLamp(stack, player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        int capacity = Pottery.waterCapacity(stack);
        if (capacity <= 0) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown() && Pottery.water(stack) > 0) {
            // Shift + ПКМ — вылить воду
            if (!level.isClientSide) {
                Pottery.setWater(stack, 0);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY,
                        SoundSource.NEUTRAL, 1.0F, 0.9F);
                Pottery.say(player, "water_poured");
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            if (level.mayInteract(player, pos) && level.getFluidState(pos).is(FluidTags.WATER)) {
                if (stack.has(ModComponents.STORED.get())) {
                    if (!level.isClientSide) {
                        Pottery.say(player, "amphora_has_drink");
                    }
                    return InteractionResultHolder.fail(stack);
                }
                if (Pottery.water(stack) < capacity) {
                    if (!level.isClientSide) {
                        Pottery.setWater(stack, capacity);
                        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL,
                                SoundSource.NEUTRAL, 1.0F, 0.8F);
                        level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
                    }
                    return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
                }
            }
        }
        // Кувшин или кружка с питьём (пиво, вино, морс…) — пить прямо из них
        if (sippable() && !drinkInside(stack, level).isEmpty()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (form != VesselForm.AMPHORA && Pottery.water(stack) > 0 && (Thirst.needs(player) || player.isCreative())) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        boolean drink = sippable() && stack.has(ModComponents.STORED.get());
        return (Pottery.water(stack) > 0 || drink) && form != VesselForm.AMPHORA ? UseAnim.DRINK : UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 24;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        ItemStack inside = sippable() ? drinkInside(stack, level) : ItemStack.EMPTY;
        if (entity instanceof Player player && !level.isClientSide && !inside.isEmpty()) {
            // Глоток — как выпить одну склянку: её действие и те же события (жажда, хмель, свежесть); склянки нет
            ItemStack sip = inside.copyWithCount(1);
            ItemStack before = sip.copy();
            ItemStack after = sip.finishUsingItem(level, player);
            net.neoforged.neoforge.event.EventHooks.onItemUseFinish(player, before, 0, after);
            if (!player.getAbilities().instabuild) {
                shrinkStored(stack, inside, 1, player);
            }
            Pottery.wear(stack, player);
            return stack;
        }
        if (entity instanceof Player player && !level.isClientSide && Pottery.water(stack) > 0) {
            Thirst.add(player, Pottery.DRINK_THIRST + coolBonus(stack, level));
            if (!player.getAbilities().instabuild) {
                Pottery.setWater(stack, Pottery.water(stack) - 1);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK,
                    SoundSource.PLAYERS, 0.5F, 1.0F);
            Pottery.wear(stack, player);
        }
        return stack;
    }

    /** Разбить треснувшую посудину: черепки, а что было внутри — игроку. */
    private static void smash(ItemStack stack, Player player, InteractionHand hand) {
        Stored stored = stack.get(ModComponents.STORED.get());
        if (stored != null) {
            giveOrDrop(player, Pottery.unpack(stored, stack, player.level()));
        }
        player.setItemInHand(hand, Pottery.sherds(stack));
        player.level().playSound(null, player.blockPosition(), SoundEvents.DECORATED_POT_SHATTER, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    /** Кувшин летом холодит воду: она сочится сквозь стенки и испаряется. */
    private int coolBonus(ItemStack stack, Level level) {
        boolean summer = Calendar.date(Calendar.today(level)).season() == Calendar.Season.SUMMER;
        return form == VesselForm.JUG && summer ? Pottery.COOL_WATER_BONUS : 0;
    }

    // ---------------------------------------------------------------- светильник

    private static void toggleLamp(ItemStack lamp, Player player) {
        if (Boolean.TRUE.equals(lamp.get(ModComponents.LAMP_LIT.get()))) {
            lamp.remove(ModComponents.LAMP_LIT.get());
            player.level().playSound(null, player.blockPosition(), SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }
        if (lampFuel(lamp) <= 0) {
            Pottery.say(player, "lamp_no_fuel");
            return;
        }
        if (!player.getAbilities().instabuild && !player.getInventory().hasAnyOf(java.util.Set.of(Items.FLINT_AND_STEEL, Items.TORCH))) {
            Pottery.say(player, "lamp_no_fire");
            return;
        }
        lamp.set(ModComponents.LAMP_LIT.get(), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
        Pottery.wear(lamp, player); // треснул — гаснет
    }

    public static int lampFuel(ItemStack lamp) {
        Integer fuel = lamp.get(ModComponents.LAMP_FUEL.get());
        return fuel == null ? 0 : fuel;
    }

    public static boolean lit(ItemStack stack) {
        return stack.getItem() instanceof WareItem ware && ware.form == VesselForm.LAMP
                && Boolean.TRUE.equals(stack.get(ModComponents.LAMP_LIT.get()));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (form != VesselForm.LAMP || level.isClientSide || !lit(stack) || level.getGameTime() % 20 != 0) {
            return;
        }
        int fuel = lampFuel(stack) - 20;
        if (fuel <= 0 || entity.isUnderWater()) {
            stack.remove(ModComponents.LAMP_LIT.get());
            if (fuel <= 0) {
                stack.remove(ModComponents.LAMP_FUEL.get());
            } else {
                stack.set(ModComponents.LAMP_FUEL.get(), fuel);
            }
            level.playSound(null, entity.blockPosition(), SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.0F);
            return;
        }
        stack.set(ModComponents.LAMP_FUEL.get(), fuel);
    }

    // ---------------------------------------------------------------- полоска: вода, масло

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return Pottery.water(stack) > 0 || (form == VesselForm.LAMP && lampFuel(stack) > 0)
                || (form == VesselForm.CRUCIBLE && com.nrdhvnstd.halgventmod.smithing.Crucible.total(
                        com.nrdhvnstd.halgventmod.smithing.Crucible.melt(stack)) > 0);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        if (form == VesselForm.CRUCIBLE) {
            int parts = com.nrdhvnstd.halgventmod.smithing.Crucible.total(com.nrdhvnstd.halgventmod.smithing.Crucible.melt(stack));
            return Math.min(13, Math.round(13.0F * parts / com.nrdhvnstd.halgventmod.smithing.Crucible.CAPACITY));
        }
        if (form == VesselForm.LAMP) {
            return Math.min(13, Math.round(13.0F * lampFuel(stack) / Pottery.LAMP_MAX_FUEL));
        }
        int capacity = Math.max(1, Pottery.waterCapacity(stack));
        return Math.min(13, Math.round(13.0F * Pottery.water(stack) / capacity));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if (form == VesselForm.CRUCIBLE) {
            com.nrdhvnstd.halgventmod.smithing.Melt melt = com.nrdhvnstd.halgventmod.smithing.Crucible.melt(stack);
            return melt != null && melt.molten() ? 0xF08A2A : 0x9A9AA0; // расплавлено — рыжая, шихта — серая
        }
        return form == VesselForm.LAMP ? 0xE8B040 : 0x3F76E4;
    }

    // ---------------------------------------------------------------- в инвентаре, как с мешочком

    /** На посудину в ячейке положили вещь с курсора (other) или щёлкнули по ней ПКМ пустым курсором. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack me, ItemStack other, Slot slot, ClickAction action, Player player,
                                            SlotAccess carried) {
        if (!slot.allowModification(player)) {
            return false;
        }
        if (other.isEmpty()) {
            return action == ClickAction.SECONDARY && takeOut(me, player, carried);
        }
        if (Pottery.cracked(me)) {
            // Треснувшую можно только опорожнить
            return holdsDrinks() && other.is(Items.GLASS_BOTTLE) && draw(me, other, player, carried);
        }
        if (other.is(ModItems.BEESWAX.get()) && (form == VesselForm.POT || form == VesselForm.AMPHORA)) {
            return seal(me, other, player);
        }
        if (form == VesselForm.LAMP) {
            return refuelLamp(me, other, player);
        }
        if (form == VesselForm.POT && storable(other)) {
            return store(me, other, other.getCount(), player, ItemStack.EMPTY);
        }
        if (holdsDrinks()) {
            if (other.getItem() instanceof DrinkItem) {
                return store(me, other, 1, player, new ItemStack(Items.GLASS_BOTTLE));
            }
            if (other.is(Items.GLASS_BOTTLE)) {
                return draw(me, other, player, carried);
            }
            // Перелить питьё из посудины на курсоре в эту (корчагу в кувшин или кружку, кувшин в кружку)
            if (Pottery.drinkCapacity(other) > 0 && other.has(ModComponents.STORED.get())) {
                return pourDrink(other, me, player);
            }
        }
        // Перелить воду из посудины на курсоре в эту (кувшин в кружку)
        if (Pottery.waterCapacity(other) > 0 && Pottery.water(other) > 0) {
            return pour(other, me, player);
        }
        return false;
    }

    /** Посудиной с курсора щёлкнули ПКМ по вещи в ячейке: налить в неё воды или питья. */
    @Override
    public boolean overrideStackedOnOther(ItemStack me, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        ItemStack other = slot.getItem();
        if (Pottery.water(me) > 0 && Pottery.waterCapacity(other) > 0 && other != me) {
            return pour(me, other, player);
        }
        if (me.has(ModComponents.STORED.get()) && holdsDrinks() && Pottery.drinkCapacity(other) > 0 && other != me) {
            return pourDrink(me, other, player);
        }
        return false;
    }

    /**
     * Перелить питьё из одной посудины в другую — сколько влезет: из корчаги в кувшин или кружку, из кувшина в кружку
     * (и обратно). Только одно и то же питьё; в посудину с водой, треснувшую или запечатанную — нельзя.
     */
    private static boolean pourDrink(ItemStack from, ItemStack to, Player player) {
        Level level = player.level();
        ItemStack drink = Pottery.storedDrink(from, level);
        if (drink.isEmpty()) {
            message(player, "sealed");
            return true;
        }
        if (Pottery.cracked(to)) {
            message(player, "ware_cracked_use");
            return true;
        }
        if (Pottery.water(to) > 0) {
            message(player, "has_water");
            return true;
        }
        Stored stored = to.get(ModComponents.STORED.get());
        if (stored != null && stored.sealed()) {
            message(player, "sealed");
            return true;
        }
        ItemStack inside = stored == null ? ItemStack.EMPTY : Pottery.unpack(stored, to, level);
        if (!inside.isEmpty() && !ItemStack.isSameItemSameComponents(inside, drink)) {
            message(player, "other_inside");
            return true;
        }
        int moved = Math.min(Pottery.drinkCapacity(to) - inside.getCount(), drink.getCount());
        if (moved <= 0) {
            message(player, "full");
            return true;
        }
        ItemStack next = inside.isEmpty() ? drink.copyWithCount(moved) : inside.copyWithCount(inside.getCount() + moved);
        to.set(ModComponents.STORED.get(), new Stored(next, Freshness.currentDay(level), false));
        shrinkStored(from, drink, moved, player);
        player.playSound(SoundEvents.BOTTLE_EMPTY, 0.7F, 1.0F);
        Pottery.wear(from, player); // от пользования посудина может треснуть
        return true;
    }

    private static boolean pour(ItemStack from, ItemStack to, Player player) {
        int room = Pottery.waterCapacity(to) - Pottery.water(to);
        if (room <= 0 || to.has(ModComponents.STORED.get())) {
            return false;
        }
        int moved = Math.min(room, Pottery.water(from));
        Pottery.setWater(from, Pottery.water(from) - moved);
        Pottery.setWater(to, Pottery.water(to) + moved);
        player.playSound(SoundEvents.BOTTLE_EMPTY, 0.6F, 1.1F);
        return true;
    }

    private boolean refuelLamp(ItemStack lamp, ItemStack fuel, Player player) {
        int add = fuel.is(ModItems.SALO.get()) ? Pottery.LAMP_SALO : fuel.is(ModItems.BEESWAX.get()) ? Pottery.LAMP_WAX : 0;
        if (add == 0) {
            return false;
        }
        if (lampFuel(lamp) + add > Pottery.LAMP_MAX_FUEL) {
            message(player, "lamp_full");
            return true;
        }
        lamp.set(ModComponents.LAMP_FUEL.get(), lampFuel(lamp) + add);
        if (!player.getAbilities().instabuild) {
            fuel.shrink(1);
        }
        player.playSound(SoundEvents.HONEY_BLOCK_PLACE, 0.5F, 1.0F);
        return true;
    }

    /** Что можно держать в горшке: еда, мука, соль — но не питьё и не вещи по одной. */
    private static boolean storable(ItemStack stack) {
        if (stack.getItem() instanceof DrinkItem || stack.getItem() instanceof WareItem || stack.getMaxStackSize() == 1) {
            return false;
        }
        return stack.has(DataComponents.FOOD) || ModFoods.perishable(stack) || stack.is(ModItems.SALT.get())
                || stack.is(ModItems.HONEY.get());
    }

    /**
     * Положить в горшок или корчагу. Внутри одна стопка одного и того же: другое — не кладётся.
     * @param leftover что отдать взамен каждой положенной штуки (пустая склянка от питья)
     */
    private boolean store(ItemStack me, ItemStack other, int want, Player player, ItemStack leftover) {
        if (Pottery.water(me) > 0) {
            message(player, "has_water");
            return true;
        }
        Stored stored = me.get(ModComponents.STORED.get());
        if (stored != null && stored.sealed()) {
            message(player, "sealed");
            return true;
        }
        int limit = holdsDrinks() ? Pottery.drinkCapacity(me) : Pottery.POT_CAPACITY;
        int today = Freshness.currentDay(player.level());
        ItemStack inside = ItemStack.EMPTY;
        if (stored != null) {
            inside = Pottery.unpack(stored, me, player.level());
            if (!ItemStack.isSameItemSameComponents(inside, other)) {
                message(player, "other_inside");
                return true;
            }
        }
        int moved = Math.min(want, limit - inside.getCount());
        if (moved <= 0) {
            message(player, "full");
            return true;
        }
        ItemStack next = inside.isEmpty() ? other.copyWithCount(moved) : inside.copyWithCount(inside.getCount() + moved);
        me.set(ModComponents.STORED.get(), new Stored(next, today, false));
        other.shrink(moved);
        if (!leftover.isEmpty()) {
            for (int i = 0; i < moved; i++) {
                giveOrDrop(player, leftover.copy());
            }
        }
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
        return true;
    }

    /** Набрать склянку питья из корчаги. */
    private boolean draw(ItemStack me, ItemStack bottle, Player player, SlotAccess carried) {
        Stored stored = me.get(ModComponents.STORED.get());
        if (stored == null) {
            return false;
        }
        ItemStack inside = Pottery.unpack(stored, me, player.level());
        ItemStack drink = inside.copyWithCount(1);
        shrinkStored(me, inside, 1, player);
        if (bottle.getCount() == 1) {
            carried.set(drink);
        } else {
            bottle.shrink(1);
            giveOrDrop(player, drink);
        }
        player.playSound(SoundEvents.BOTTLE_FILL, 0.8F, 1.0F);
        Pottery.wear(me, player);
        return true;
    }

    /** ПКМ по посудине пустым курсором: вынуть содержимое (печать воска ломается). */
    private boolean takeOut(ItemStack me, Player player, SlotAccess carried) {
        Stored stored = me.get(ModComponents.STORED.get());
        if (stored == null) {
            return false;
        }
        if (stored.sealed()) {
            // Печать ломается: дальше хранится как незапечатанное
            ItemStack inside = Pottery.unpack(stored, me, player.level());
            me.set(ModComponents.STORED.get(), new Stored(inside, Freshness.currentDay(player.level()), false));
            message(player, "seal_broken");
            player.playSound(SoundEvents.HONEYCOMB_WAX_ON, 0.6F, 0.7F);
            return true;
        }
        ItemStack inside = Pottery.unpack(stored, me, player.level());
        if (holdsDrinks()) {
            message(player, "need_bottle");
            return true;
        }
        int taken = Math.min(inside.getCount(), inside.getMaxStackSize());
        carried.set(inside.copyWithCount(taken));
        shrinkStored(me, inside, taken, player);
        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
        Pottery.wear(me, player);
        return true;
    }

    private static void shrinkStored(ItemStack me, ItemStack inside, int amount, Player player) {
        int left = inside.getCount() - amount;
        if (left <= 0) {
            me.remove(ModComponents.STORED.get());
        } else {
            me.set(ModComponents.STORED.get(), new Stored(inside.copyWithCount(left), Freshness.currentDay(player.level()), false));
        }
    }

    private boolean seal(ItemStack me, ItemStack wax, Player player) {
        Stored stored = me.get(ModComponents.STORED.get());
        if (stored == null) {
            message(player, "seal_empty");
            return true;
        }
        if (stored.sealed()) {
            message(player, "sealed");
            return true;
        }
        ItemStack inside = Pottery.unpack(stored, me, player.level());
        me.set(ModComponents.STORED.get(), new Stored(inside, Freshness.currentDay(player.level()), true));
        if (!player.getAbilities().instabuild) {
            wax.shrink(1);
        }
        player.playSound(SoundEvents.HONEYCOMB_WAX_ON, 0.8F, 1.0F);
        message(player, "sealed_now");
        return true;
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Сообщение только на сервере: щелчки в инвентаре идут и на клиенте (предсказание). */
    private static void message(Player player, String key) {
        if (!player.level().isClientSide) {
            Pottery.say(player, key);
        }
    }

    @Nullable
    public static WareItem of(ItemStack stack) {
        return stack.getItem() instanceof WareItem ware ? ware : null;
    }

    /** Насколько полна посудина водой — для отрисовки воды в ней (0–1). */
    public static float fill(ItemStack stack) {
        int capacity = Pottery.waterCapacity(stack);
        return capacity <= 0 ? 0 : Mth.clamp((float) Pottery.water(stack) / capacity, 0, 1);
    }
}
