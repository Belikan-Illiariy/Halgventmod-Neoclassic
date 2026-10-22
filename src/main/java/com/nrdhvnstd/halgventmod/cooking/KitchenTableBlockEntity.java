package com.nrdhvnstd.halgventmod.cooking;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.network.KitchenOpenPayload;
import com.nrdhvnstd.halgventmod.pottery.Pottery;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Кухонный стол: тесто и сырые пироги — в мире, без верстака. Всё на E:
 * <ol>
 *   <li>пустой рукой по свободному столу — окно рецептов (KitchenScreen); Shift + E — тоже;</li>
 *   <li>продукты по рецепту — выложить на доску; ведро воды — полить (у теста); соль — посолить (по желанию, +качество);</li>
 *   <li>пустой рукой — месить или лепить, {@link KitchenRecipes.Recipe#kneads()} раз. Ровно, дожидаясь замаха:
 *       от {@link #SLOPPY} спешных — качество −1;</li>
 *   <li>готовое лежит на доске — пустой рукой забрать. Shift + E до замеса — забрать назад выложенное.</li>
 * </ol>
 * Качество теста переходит в пирог, а в печи — в хлеб и пирог.
 */
public class KitchenTableBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int SLOPPY = 2;
    public static final double REACH = 6.0;

    private int recipe = -1;
    private final List<ItemStack> laid = new ArrayList<>();
    private final List<ItemStack> extras = new ArrayList<>(); // необязательные добавки
    private boolean water;
    private boolean salt;
    private int kneads;
    private int sloppy;
    private ItemStack result = ItemStack.EMPTY;

    public KitchenTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KITCHEN_TABLE.get(), pos, state);
    }

    // Для отрисовки: обязательное и добавки вместе
    public List<ItemStack> laid() {
        List<ItemStack> all = new ArrayList<>(laid);
        all.addAll(extras);
        return all;
    }

    public ItemStack result() {
        return result;
    }

    public boolean watered() {
        return water;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        laid.forEach(stack -> all.add(stack.copy()));
        extras.forEach(stack -> all.add(stack.copy()));
        if (salt) {
            all.add(new ItemStack(ModItems.SALT.get()));
        }
        if (!result.isEmpty()) {
            all.add(result.copy());
        }
        return all;
    }

    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.halgventmod.cooking." + key, args), true);
    }

    private boolean idle() {
        return laid.isEmpty() && extras.isEmpty() && !water && !salt && result.isEmpty();
    }

    private int count(KitchenRecipes.Need need) {
        int n = 0;
        for (ItemStack stack : laid) {
            if (need.matches().test(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private boolean complete(KitchenRecipes.Recipe current) {
        for (KitchenRecipes.Need need : current.needs()) {
            if (count(need) < need.count()) {
                return false;
            }
        }
        return !current.water() || water;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (!result.isEmpty()) {
            if (stack.isEmpty()) {
                give(player, result);
                result = ItemStack.EMPTY;
                sound(SoundEvents.WOOL_PLACE, 0.6F, 1.2F);
                changed();
                return true;
            }
            tell(player, "kitchen_take_first");
            return true;
        }
        if (stack.isEmpty()) {
            if (idle()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    PacketDistributor.sendToPlayer(serverPlayer, new KitchenOpenPayload(worldPosition, recipe));
                }
                return true;
            }
            if (player.isShiftKeyDown()) {
                return takeBack(player);
            }
            return knead(player, hand);
        }
        if (recipe < 0) {
            tell(player, "kitchen_pick");
            return Cookery.kind(stack) != null || stack.is(ModItems.WHEAT_FLOUR.get()) || stack.is(ModItems.DOUGH.get());
        }
        KitchenRecipes.Recipe current = KitchenRecipes.get(recipe);
        if (Pottery.isWater(stack, 1) && current.water()) {
            if (water) {
                tell(player, "kitchen_has_water");
                return true;
            }
            water = true;
            Pottery.takeWater(player, hand, stack, 1); // ведро или мера из кувшина
            sound(SoundEvents.BUCKET_EMPTY, 0.6F, 1.2F);
            status(player, current);
            changed();
            return true;
        }
        if (stack.is(ModItems.SALT.get()) && current.salted()) {
            if (salt) {
                tell(player, "kitchen_has_salt");
                return true;
            }
            salt = true;
            consumeOne(player, stack);
            sound(SoundEvents.SAND_PLACE, 0.6F, 1.4F);
            changed();
            return true;
        }
        boolean neededFull = false;
        for (KitchenRecipes.Need need : current.needs()) {
            if (!need.matches().test(stack)) {
                continue;
            }
            if (count(need) >= need.count()) {
                neededFull = true; // хватает — может, это ещё и добавка
                continue;
            }
            if (Freshness.isSpoiled(stack, level)) {
                tell(player, "kitchen_spoiled");
                return true;
            }
            laid.add(stack.copyWithCount(1));
            consumeOne(player, stack);
            sound(SoundEvents.WOOL_PLACE, 0.6F, 1.0F);
            status(player, current);
            changed();
            return true;
        }
        for (KitchenRecipes.Extra extra : current.extras()) {
            if (!extra.matches().test(stack)) {
                continue;
            }
            if (kneads > 0) {
                tell(player, "kitchen_kneaded");
                return true;
            }
            for (ItemStack added : extras) {
                if (extra.matches().test(added)) {
                    tell(player, "kitchen_extra_twice", stack.getHoverName());
                    return true;
                }
            }
            if (extras.size() >= KitchenRecipes.MAX_EXTRAS) {
                tell(player, "kitchen_extras_full", KitchenRecipes.MAX_EXTRAS);
                return true;
            }
            if (Freshness.isSpoiled(stack, level)) {
                tell(player, "kitchen_spoiled");
                return true;
            }
            extras.add(stack.copyWithCount(1));
            consumeOne(player, stack);
            sound(SoundEvents.WOOL_PLACE, 0.6F, 1.2F);
            tell(player, "kitchen_extra", stack.getHoverName(), Math.round(extra.saturation()));
            changed();
            return true;
        }
        if (neededFull) {
            tell(player, "kitchen_enough", stack.getHoverName());
            return true;
        }
        if (Cookery.kind(stack) != null || stack.is(ModItems.WHEAT_FLOUR.get()) || stack.is(ModItems.DOUGH.get())) {
            tell(player, "kitchen_wrong");
            return true;
        }
        return false;
    }

    /** Выбор рецепта в окне (KitchenChoosePayload): только пока доска пуста. */
    public void choose(ServerPlayer player, int index) {
        if (index < 0 || index >= KitchenRecipes.all().size()
                || player.distanceToSqr(worldPosition.getCenter()) > REACH * REACH) {
            return;
        }
        if (!idle()) {
            tell(player, "kitchen_busy");
            return;
        }
        recipe = index;
        sound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.0F);
        status(player, KitchenRecipes.get(recipe));
        changed();
    }

    private boolean knead(Player player, InteractionHand hand) {
        if (recipe < 0) {
            tell(player, "kitchen_pick");
            return true;
        }
        KitchenRecipes.Recipe current = KitchenRecipes.get(recipe);
        if (!complete(current)) {
            status(player, current);
            return true;
        }
        if (!strike(player, hand, ItemStack.EMPTY)) {
            sloppy++;
        }
        kneads++;
        sound(SoundEvents.SLIME_SQUISH_SMALL, 0.6F, 0.7F + 0.3F * level.random.nextFloat());
        crumbs(new ItemStack(ModItems.WHEAT_FLOUR.get()), 1.05, 3);
        if (kneads < current.kneads()) {
            tell(player, "kitchen_knead", kneads, current.kneads());
            changed();
            return true;
        }
        int quality = FoodQuality.NORMAL + (salt ? 1 : 0) - (sloppy >= SLOPPY ? 1 : 0);
        for (ItemStack stack : laid) {
            if (FoodQuality.has(stack)) {
                quality += FoodQuality.get(stack) - FoodQuality.NORMAL;
            }
        }
        result = FoodQuality.set(current.makeResult(), quality);
        if (!extras.isEmpty()) {
            float saturation = 0;
            List<String> ids = new ArrayList<>();
            for (ItemStack added : extras) {
                for (KitchenRecipes.Extra extra : current.extras()) {
                    if (extra.matches().test(added)) {
                        saturation += extra.saturation();
                        break;
                    }
                }
                ids.add(BuiltInRegistries.ITEM.getKey(added.getItem()).toString());
            }
            result.set(ModComponents.FOOD_EXTRAS.get(), new FoodExtras(List.copyOf(ids), saturation));
        }
        give(player, current.makeByproduct());
        laid.clear();
        extras.clear();
        water = false;
        salt = false;
        kneads = 0;
        sloppy = 0;
        sound(SoundEvents.WOOL_PLACE, 0.8F, 0.8F);
        tell(player, "kitchen_done", result.getHoverName());
        changed();
        return true;
    }

    private boolean takeBack(Player player) {
        if (kneads > 0) {
            tell(player, "kitchen_kneaded");
            return true;
        }
        if (!extras.isEmpty()) {
            give(player, extras.remove(extras.size() - 1));
        } else if (!laid.isEmpty()) {
            give(player, laid.remove(laid.size() - 1));
        } else if (salt) {
            salt = false;
            give(player, new ItemStack(ModItems.SALT.get()));
        } else if (water) {
            water = false; // вода впиталась в доску
        }
        changed();
        return true;
    }

    private void status(Player player, KitchenRecipes.Recipe current) {
        if (complete(current)) {
            tell(player, "kitchen_ready_to_knead");
            return;
        }
        for (KitchenRecipes.Need need : current.needs()) {
            int missing = need.count() - count(need);
            if (missing > 0) {
                tell(player, "kitchen_need", missing, new ItemStack(need.icon().get()).getHoverName());
                return;
            }
        }
        tell(player, "kitchen_need_water");
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Recipe", recipe);
        ListTag list = new ListTag();
        laid.forEach(stack -> list.add(stack.save(registries)));
        tag.put("Laid", list);
        ListTag extraList = new ListTag();
        extras.forEach(stack -> extraList.add(stack.save(registries)));
        tag.put("Extras", extraList);
        tag.putBoolean("Water", water);
        tag.putBoolean("Salt", salt);
        tag.putInt("Kneads", kneads);
        tag.putInt("Sloppy", sloppy);
        if (!result.isEmpty()) {
            tag.put("Result", result.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        recipe = tag.contains("Recipe") ? tag.getInt("Recipe") : -1;
        if (recipe >= KitchenRecipes.all().size()) {
            recipe = -1;
        }
        laid.clear();
        ListTag list = tag.getList("Laid", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty()) {
                laid.add(stack);
            }
        }
        extras.clear();
        ListTag extraList = tag.getList("Extras", Tag.TAG_COMPOUND);
        for (int i = 0; i < extraList.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, extraList.getCompound(i));
            if (!stack.isEmpty()) {
                extras.add(stack);
            }
        }
        water = tag.getBoolean("Water");
        salt = tag.getBoolean("Salt");
        kneads = tag.getInt("Kneads");
        sloppy = tag.getInt("Sloppy");
        result = tag.contains("Result") ? ItemStack.parseOptional(registries, tag.getCompound("Result")) : ItemStack.EMPTY;
    }
}
