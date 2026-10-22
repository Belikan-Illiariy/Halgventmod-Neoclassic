package com.nrdhvnstd.halgventmod.leather;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.network.PatternOpenPayload;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.network.SewingOpenPayload;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Стол кожевника, всё в мире, кроме самого шитья:
 * <ol>
 *   <li>E пустой рукой по свободному столу — окно выкроек (PatternScreen): что нужно и что есть в инвентаре;</li>
 *   <li>E кожей — выложить нужные куски на стол;</li>
 *   <li>E ножом (тег halgventmod:knives) — раскроить (кожа уходит, качество вещи — худший кусок; падают обрезки);</li>
 *   <li>E фурнитурой — железо, клей, ремешки;</li>
 *   <li>E иглой — окно шитья: мини-игра на точность (SewingScreen). Без промахов — качество +1,
 *       от {@link #SLOPPY_MISSES} промахов −1, от {@link #RUINED_MISSES} — −2. Нитки — из инвентаря.</li>
 * </ol>
 * Пустая рука до раскроя — забрать последнее выложенное. Кожаной вещью в руке (стол свободен) — смазать её
 * салом или воском из инвентаря: пересохшая снова защищает в полную силу.
 */
public class LeatherTableBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int SLOPPY_MISSES = 3;
    public static final int RUINED_MISSES = 6;
    public static final double REACH = 6.0;
    /** Чем кроят: нож мода и охотничьи ножи Conquest. */
    public static final TagKey<Item> KNIVES = ItemTags.create(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "knives"));

    private int pattern = -1;
    private final List<ItemStack> leather = new ArrayList<>();
    private final List<ItemStack> extras = new ArrayList<>();
    private boolean cut;
    private int quality;

    public LeatherTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LEATHER_TABLE.get(), pos, state);
    }

    // Для отрисовки
    public int pattern() {
        return pattern;
    }

    public boolean isCut() {
        return cut;
    }

    public List<ItemStack> laid() {
        List<ItemStack> all = new ArrayList<>(leather);
        all.addAll(extras);
        return all;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        laid().forEach(stack -> all.add(stack.copy()));
        return all;
    }

    private boolean idle() {
        return leather.isEmpty() && extras.isEmpty() && !cut;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        // Пустой рукой по свободному столу (или Shift + E всегда) — окно выбора выкройки
        if (stack.isEmpty() && (idle() || player.isShiftKeyDown())) {
            if (!idle()) {
                say(player, "table_busy");
                return true;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new PatternOpenPayload(worldPosition, pattern));
            }
            return true;
        }
        if (LeatherPatterns.isProduct(stack) && idle()) {
            return oil(player, stack);
        }
        if (pattern < 0) {
            if (stack.isEmpty() || isLeatherThing(stack)) {
                say(player, "pick_pattern");
                return true;
            }
            return false;
        }
        LeatherPatterns.Pattern current = LeatherPatterns.get(pattern);
        if (stack.isEmpty()) {
            return takeBack(player, current);
        }
        if (stack.is(KNIVES)) {
            return cut(player, hand, stack, current);
        }
        if (stack.is(ModItems.NEEDLE.get())) {
            return openSewing(player, current);
        }
        // Кожа до раскроя, фурнитура после
        List<LeatherPatterns.Need> needs = cut ? current.extras() : current.leather();
        List<ItemStack> laid = cut ? extras : leather;
        for (LeatherPatterns.Need need : needs) {
            if (!need.matches(stack)) {
                continue;
            }
            int missing = need.count() - count(laid, need);
            if (missing <= 0) {
                say(player, "enough_of", stack.getHoverName());
                return true;
            }
            if (!cut && Freshness.isSpoiled(stack, level)) {
                say(player, "rotten");
                return true;
            }
            int moved = Math.min(missing, stack.getCount());
            laid.add(stack.copyWithCount(moved));
            if (!player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
            sound(cut ? SoundEvents.CHAIN_PLACE : SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 1.0F);
            changed();
            return true;
        }
        if (isLeatherThing(stack) || stack.is(Items.IRON_NUGGET) || stack.is(Items.IRON_INGOT)) {
            say(player, cut ? "wrong_extra" : "wrong_leather");
            player.displayClientMessage(describe(current), false);
            return true;
        }
        return false;
    }

    /** Выкройка выбрана в окне (PatternChoosePayload). Менять можно, пока на столе ничего не лежит. */
    public void choosePattern(ServerPlayer player, int index) {
        if (index < 0 || index >= LeatherPatterns.all().size()
                || player.distanceToSqr(worldPosition.getCenter()) > REACH * REACH) {
            return;
        }
        if (!idle()) {
            say(player, "table_busy");
            return;
        }
        pattern = index;
        sound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        player.displayClientMessage(describe(LeatherPatterns.get(pattern)), true);
        changed();
    }

    private boolean cut(Player player, InteractionHand hand, ItemStack knife, LeatherPatterns.Pattern current) {
        if (cut) {
            say(player, "already_cut");
            return true;
        }
        for (LeatherPatterns.Need need : current.leather()) {
            if (count(leather, need) < need.count()) {
                say(player, "need_leather");
                player.displayClientMessage(describe(current), false);
                return true;
            }
        }
        strike(player, hand, knife);
        quality = LeatherQuality.MAX;
        for (ItemStack piece : leather) {
            quality = Math.min(quality, LeatherQuality.get(piece));
        }
        leather.clear();
        cut = true;
        sound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
        if (current.stitches() == 0) {
            finish(player, current, 0);
        } else {
            give(player, new ItemStack(ModItems.LEATHER_SCRAPS.get()));
            say(player, current.extras().isEmpty() ? "cut_sew" : "cut_extras");
        }
        changed();
        return true;
    }

    /** Почему шить нельзя (ключ сообщения) или null — можно. */
    private String sewingBlocker(Player player, LeatherPatterns.Pattern current) {
        if (!cut) {
            return "cut_first";
        }
        for (LeatherPatterns.Need need : current.extras()) {
            if (count(extras, need) < need.count()) {
                return "need_extras";
            }
        }
        if (threadIn(player.getInventory()) < current.thread()) {
            return "need_thread";
        }
        return null;
    }

    private boolean openSewing(Player player, LeatherPatterns.Pattern current) {
        String blocker = sewingBlocker(player, current);
        if (blocker != null) {
            say(player, blocker, current.thread());
            if ("need_extras".equals(blocker)) {
                player.displayClientMessage(describe(current), false);
            }
            return true;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new SewingOpenPayload(worldPosition, current.result(), current.stitches()));
        }
        return true;
    }

    /**
     * Итог мини-игры шитья (SewingResultPayload). Сервер заново проверяет стол, иглу и нитки;
     * клиенту доверяем только число промахов.
     */
    public void sewn(ServerPlayer player, int misses) {
        if (pattern < 0 || player.distanceToSqr(worldPosition.getCenter()) > REACH * REACH) {
            return;
        }
        LeatherPatterns.Pattern current = LeatherPatterns.get(pattern);
        if (current.stitches() == 0 || sewingBlocker(player, current) != null) {
            return;
        }
        InteractionHand hand = player.getMainHandItem().is(ModItems.NEEDLE.get()) ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().is(ModItems.NEEDLE.get()) ? InteractionHand.OFF_HAND : null;
        if (hand == null) {
            say(player, "need_needle");
            return;
        }
        damage(player, hand, player.getItemInHand(hand), current.stitches() + Math.max(0, misses));
        takeThread(player, current.thread());
        int delta = misses <= 0 ? 1 : misses >= RUINED_MISSES ? -2 : misses >= SLOPPY_MISSES ? -1 : 0;
        say(player, misses <= 0 ? "sewn_clean" : delta < 0 ? "sewn_sloppy" : "sewn", misses);
        finish(player, current, delta);
        changed();
    }

    private void finish(Player player, LeatherPatterns.Pattern current, int qualityDelta) {
        // Броня со стола получает качество снаряжения (из качества кожи и шитья), прочее — качество кожи
        ItemStack result = current.result();
        if (GearQuality.applies(result)) {
            GearQuality.set(result, GearQuality.fromLeather(quality + qualityDelta));
        } else {
            LeatherQuality.set(result, quality + qualityDelta);
        }
        if (current.stitches() > 0) {
            result.set(ModComponents.OILED_DAY.get(), Freshness.currentDay(level));
        }
        // Имя — до give: инвентарь забирает стак целиком, и после него он пуст («воздух»)
        Component name = result.getHoverName();
        give(player, result);
        player.displayClientMessage(Component.translatable("message.halgventmod.leather.made", name), false);
        sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.2F);
        extras.clear();
        leather.clear();
        cut = false;
    }

    private boolean takeBack(Player player, LeatherPatterns.Pattern current) {
        if (cut) {
            say(player, "table_cut");
            player.displayClientMessage(describe(current), false);
            return true;
        }
        if (leather.isEmpty()) {
            player.displayClientMessage(describe(current), true);
            return true;
        }
        give(player, leather.remove(leather.size() - 1));
        sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8F, 0.9F);
        changed();
        return true;
    }

    /** Смазать кожаную вещь салом или воском из инвентаря. */
    private boolean oil(Player player, ItemStack product) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack oil = inventory.getItem(i);
            if (oil.is(ModItems.SALO.get()) || oil.is(Items.HONEYCOMB) || oil.is(ModItems.BEESWAX.get())) {
                if (!player.getAbilities().instabuild) {
                    oil.shrink(1);
                }
                product.set(ModComponents.OILED_DAY.get(), Freshness.currentDay(level));
                product.remove(ModComponents.DRIED_OUT.get());
                sound(SoundEvents.HONEY_DRINK, 0.8F, 1.0F);
                say(player, "item_oiled", product.getHoverName());
                return true;
            }
        }
        say(player, "need_oil");
        return true;
    }

    private static int count(List<ItemStack> laid, LeatherPatterns.Need need) {
        int n = 0;
        for (ItemStack stack : laid) {
            if (need.matches(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static int threadIn(Inventory inventory) {
        return inventory.countItem(ModItems.THREADS.get()) + inventory.countItem(Items.STRING);
    }

    private static void takeThread(Player player, int amount) {
        if (player.getAbilities().instabuild) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.THREADS.get()) || stack.is(Items.STRING)) {
                int taken = Math.min(amount, stack.getCount());
                stack.shrink(taken);
                amount -= taken;
            }
        }
    }

    private static boolean isLeatherThing(ItemStack stack) {
        return stack.is(ModItems.TANNED_LEATHER.get()) || stack.is(ModItems.SOFT_LEATHER.get())
                || stack.is(ModItems.RAWHIDE.get()) || stack.is(ModItems.BOILED_LEATHER.get())
                || stack.is(ModItems.DRESSED_FUR.get()) || stack.is(ModItems.LEATHER_SCRAPS.get())
                || stack.is(ModItems.LEATHER_STRAP.get()) || stack.is(ModItems.HIDE_GLUE.get())
                || stack.is(KNIVES) || stack.is(ModItems.NEEDLE.get());
    }

    /** «Выкройка: Кожаный пояс — 2 × Дублёная кожа; затем 1 × Железный самородок; нитки 1, стежков 6». */
    private static MutableComponent describe(LeatherPatterns.Pattern pattern) {
        MutableComponent needs = Component.empty();
        boolean first = true;
        for (LeatherPatterns.Need need : pattern.leather()) {
            if (!first) {
                needs.append(", ");
            }
            needs.append(need.count() + " × ").append(new ItemStack(need.item().get()).getHoverName());
            first = false;
        }
        MutableComponent extras = Component.empty();
        first = true;
        for (LeatherPatterns.Need need : pattern.extras()) {
            if (!first) {
                extras.append(", ");
            }
            extras.append(need.count() + " × ").append(new ItemStack(need.item().get()).getHoverName());
            first = false;
        }
        ItemStack result = pattern.result();
        MutableComponent name = Component.empty().append(result.getHoverName());
        if (result.getCount() > 1) {
            name.append(" ×" + result.getCount());
        }
        String key = pattern.stitches() == 0 ? "pattern_cut_only"
                : pattern.extras().isEmpty() ? "pattern_simple" : "pattern";
        return Component.translatable("message.halgventmod.leather." + key, name, needs, extras, pattern.thread(),
                pattern.stitches());
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Pattern", pattern);
        tag.put("Leather", saveList(leather, registries));
        tag.put("Extras", saveList(extras, registries));
        tag.putBoolean("Cut", cut);
        tag.putInt("Quality", quality);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pattern = tag.contains("Pattern") ? tag.getInt("Pattern") : -1;
        if (pattern >= LeatherPatterns.all().size()) {
            pattern = -1;
        }
        loadList(tag.getList("Leather", Tag.TAG_COMPOUND), leather, registries);
        loadList(tag.getList("Extras", Tag.TAG_COMPOUND), extras, registries);
        cut = tag.getBoolean("Cut");
        quality = tag.getInt("Quality");
    }

    private static ListTag saveList(List<ItemStack> stacks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            list.add(stack.save(registries));
        }
        return list;
    }

    private static void loadList(ListTag list, List<ItemStack> into, HolderLookup.Provider registries) {
        into.clear();
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i));
            if (!stack.isEmpty()) {
                into.add(stack);
            }
        }
    }
}
