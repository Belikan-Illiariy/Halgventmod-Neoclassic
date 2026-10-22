package com.nrdhvnstd.halgventmod.leather;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.food.Freshness;
import com.nrdhvnstd.halgventmod.menu.SewingTableMenu;
import com.nrdhvnstd.halgventmod.network.CutOpenPayload;
import com.nrdhvnstd.halgventmod.network.SewingOpenPayload;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Шитейный стол — кожа и ткань (id блока остался от стола кожевника: так стоящие в мире столы не пропадут).
 * <ol>
 *   <li>E — окно стола ({@link SewingTableMenu}): выбрать выкройку, переложить нужное из инвентаря в ячейки стола
 *       (что лежит — видно на столе);</li>
 *   <li>«Раскроить» — мини-игра раскроя (CutScreen): ножом по коже, ножницами по ткани вести по меловому контуру.
 *       Кожа или ткань уходит, качество вещи — худший кусок с поправкой за раскрой ({@link #cutDelta});</li>
 *   <li>«Шить» — мини-игра шитья (SewingScreen): игла и нитки из инвентаря, фурнитура — из ячеек стола.
 *       Без промахов — качество +1, от {@link #SLOPPY_MISSES} промахов −1, от {@link #RUINED_MISSES} — −2.</li>
 * </ol>
 * После каждой мини-игры окно стола открывается снова. Кожаной вещью в руке (стол пуст) — смазать её салом или
 * воском из инвентаря: пересохшая снова защищает в полную силу.
 */
public class LeatherTableBlockEntity extends StationBlockEntity {
    // Меняйте здесь
    public static final int SLOTS = 9;
    public static final int DATA = 2;              // выкройка, раскроено ли — для окна
    public static final int SLOPPY_MISSES = 3;
    public static final int RUINED_MISSES = 6;
    public static final int CLEAN_WOBBLE = 25;     // неровность раскроя (×10 пикселей) — не больше: +1
    public static final int ROUGH_WOBBLE = 45;     // больше: −1
    public static final int ROUGH_SLIPS = 3;       // столько срывов ножа: −1
    public static final int RUINED_SLIPS = 6;      // а столько: −2
    public static final double REACH = 6.0;
    /** Чем кроят кожу: нож мода и охотничьи ножи Conquest. Ткань — ножницами. */
    public static final TagKey<Item> KNIVES = ItemTags.create(ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "knives"));

    private int pattern = -1;
    private final SimpleContainer materials = new SimpleContainer(SLOTS);
    private boolean cut;
    private int quality;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? pattern : cut ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    public LeatherTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LEATHER_TABLE.get(), pos, state);
        materials.addListener(container -> changed());
    }

    public SimpleContainer materials() {
        return materials;
    }

    public ContainerData data() {
        return data;
    }

    // Для отрисовки
    public int pattern() {
        return pattern;
    }

    public LeatherPatterns.Pattern current() {
        return LeatherPatterns.get(pattern);
    }

    public boolean isCut() {
        return cut;
    }

    public List<ItemStack> laid() {
        List<ItemStack> all = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (!materials.getItem(i).isEmpty()) {
                all.add(materials.getItem(i));
            }
        }
        return all;
    }

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>();
        laid().forEach(stack -> all.add(stack.copy()));
        return all;
    }

    private boolean idle() {
        return materials.isEmpty() && !cut;
    }

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (LeatherPatterns.isProduct(stack) && idle()) {
            return oil(player, stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            openMenu(serverPlayer);
        }
        return true;
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new SewingTableMenu(id, inventory, this),
                getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
    }

    private boolean near(Player player) {
        return player.distanceToSqr(worldPosition.getCenter()) <= REACH * REACH;
    }

    /** Выкройка выбрана в окне. Менять можно, пока раскроенное не дошито. */
    public void choosePattern(ServerPlayer player, int index) {
        if (index < 0 || index >= LeatherPatterns.all().size() || !near(player) || index == pattern) {
            return;
        }
        if (cut) {
            say(player, "table_cut");
            return;
        }
        pattern = index;
        sound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        changed();
    }

    // ---------------------------------------------------------------- раскрой

    private static Predicate<ItemStack> cutter(LeatherPatterns.Pattern pattern) {
        return pattern.material() == LeatherPatterns.Material.CLOTH ? stack -> stack.is(Items.SHEARS)
                : stack -> stack.is(KNIVES);
    }

    /** Почему кроить нельзя (ключ сообщения) или null — можно. */
    private String cutBlocker(Player player) {
        if (pattern < 0) {
            return "pick_pattern";
        }
        if (cut) {
            return "already_cut";
        }
        LeatherPatterns.Pattern current = current();
        for (LeatherPatterns.Need need : current.leather()) {
            if (count(need) < need.count()) {
                return "need_leather";
            }
        }
        for (ItemStack stack : laid()) {
            if (isCutPiece(current, stack) && Freshness.isSpoiled(stack, level)) {
                return "rotten";
            }
        }
        if (toolSlot(player, cutter(current)) < 0) {
            return current.material() == LeatherPatterns.Material.CLOTH ? "need_shears" : "need_knife";
        }
        return null;
    }

    /** Кнопка «Раскроить»: проверить и открыть мини-игру раскроя (окно стола закрывается, потом откроется снова). */
    public void requestCut(ServerPlayer player) {
        String blocker = cutBlocker(player);
        if (blocker != null) {
            say(player, blocker);
            if ("need_leather".equals(blocker)) {
                player.displayClientMessage(describe(current()), false);
            }
            return;
        }
        player.closeContainer();
        PacketDistributor.sendToPlayer(player, new CutOpenPayload(worldPosition, pattern));
    }

    /** Поправка качества за раскрой: срывы ножа и неровность линии (средний отход от контура, ×10 пикселей). */
    public static int cutDelta(int slips, int wobble) {
        if (slips >= RUINED_SLIPS) {
            return -2;
        }
        if (slips >= ROUGH_SLIPS || wobble > ROUGH_WOBBLE) {
            return -1;
        }
        return slips == 0 && wobble <= CLEAN_WOBBLE ? 1 : 0;
    }

    /** Итог мини-игры раскроя (CutResultPayload). Сервер заново проверяет стол и инструмент; клиенту доверяем итог. */
    public void cutDone(ServerPlayer player, int slips, int wobble) {
        if (!near(player) || cutBlocker(player) != null) {
            return;
        }
        LeatherPatterns.Pattern current = current();
        wear(player, player.getInventory().getItem(toolSlot(player, cutter(current))), 1 + Math.max(0, slips));
        quality = LeatherQuality.MAX;
        for (LeatherPatterns.Need need : current.leather()) {
            int left = need.count();
            for (int i = 0; i < SLOTS && left > 0; i++) {
                ItemStack stack = materials.getItem(i);
                if (need.matches(stack)) {
                    quality = Math.min(quality, LeatherQuality.get(stack));
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
        }
        int delta = cutDelta(slips, Math.max(0, wobble));
        quality += delta;
        cut = true;
        materials.setChanged();
        sound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
        if (current.stitches() == 0) {
            finish(player, current, 0);
        } else {
            if (current.material() == LeatherPatterns.Material.LEATHER) {
                give(player, new ItemStack(ModItems.LEATHER_SCRAPS.get()));
            }
            say(player, delta > 0 ? "cut_clean" : delta < 0 ? "cut_rough" : "cut_done", slips);
        }
        changed();
        openMenu(player);
    }

    // ---------------------------------------------------------------- шитьё

    /** Почему шить нельзя (ключ сообщения) или null — можно. */
    private String sewingBlocker(Player player) {
        if (pattern < 0) {
            return "pick_pattern";
        }
        if (!cut) {
            return "cut_first";
        }
        LeatherPatterns.Pattern current = current();
        for (LeatherPatterns.Need need : current.extras()) {
            if (count(need) < need.count()) {
                return "need_extras";
            }
        }
        if (threadIn(player.getInventory()) < current.thread()) {
            return "need_thread";
        }
        if (toolSlot(player, stack -> stack.is(ModItems.NEEDLE.get())) < 0) {
            return "need_needle";
        }
        return null;
    }

    /** Кнопка «Шить»: проверить и открыть мини-игру шитья. */
    public void requestSew(ServerPlayer player) {
        String blocker = sewingBlocker(player);
        if (blocker != null) {
            say(player, blocker, pattern < 0 ? 0 : current().thread());
            if ("need_extras".equals(blocker)) {
                player.displayClientMessage(describe(current()), false);
            }
            return;
        }
        player.closeContainer();
        LeatherPatterns.Pattern current = current();
        PacketDistributor.sendToPlayer(player, new SewingOpenPayload(worldPosition, current.result(), current.stitches()));
    }

    /**
     * Итог мини-игры шитья (SewingResultPayload). Сервер заново проверяет стол, иглу, нитки и фурнитуру;
     * клиенту доверяем только число промахов.
     */
    public void sewn(ServerPlayer player, int misses) {
        if (!near(player) || sewingBlocker(player) != null) {
            return;
        }
        LeatherPatterns.Pattern current = current();
        if (current.stitches() == 0) {
            return;
        }
        int needle = toolSlot(player, stack -> stack.is(ModItems.NEEDLE.get()));
        wear(player, player.getInventory().getItem(needle), current.stitches() + Math.max(0, misses));
        takeThread(player, current.thread());
        for (LeatherPatterns.Need need : current.extras()) {
            int left = need.count();
            for (int i = 0; i < SLOTS && left > 0; i++) {
                ItemStack stack = materials.getItem(i);
                if (need.matches(stack)) {
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
        }
        materials.setChanged();
        int delta = misses <= 0 ? 1 : misses >= RUINED_MISSES ? -2 : misses >= SLOPPY_MISSES ? -1 : 0;
        say(player, misses <= 0 ? "sewn_clean" : delta < 0 ? "sewn_sloppy" : "sewn", misses);
        finish(player, current, delta);
        changed();
        openMenu(player);
    }

    private void finish(Player player, LeatherPatterns.Pattern current, int qualityDelta) {
        // Броня со стола получает качество снаряжения (из качества материала, раскроя и шитья), прочее — качество материала
        ItemStack result = current.result();
        if (GearQuality.applies(result)) {
            GearQuality.set(result, GearQuality.fromLeather(quality + qualityDelta));
        } else {
            LeatherQuality.set(result, quality + qualityDelta);
        }
        if (current.stitches() > 0 && current.material() == LeatherPatterns.Material.LEATHER) {
            result.set(ModComponents.OILED_DAY.get(), Freshness.currentDay(level));
        }
        // Имя — до give: инвентарь забирает стак целиком, и после него он пуст («воздух»)
        Component name = result.getHoverName();
        give(player, result);
        player.displayClientMessage(Component.translatable("message.halgventmod.leather.made", name), false);
        sound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.2F);
        cut = false;
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

    // ---------------------------------------------------------------- помощники

    private int count(LeatherPatterns.Need need) {
        int n = 0;
        for (ItemStack stack : laid()) {
            if (need.matches(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    private static boolean isCutPiece(LeatherPatterns.Pattern pattern, ItemStack stack) {
        for (LeatherPatterns.Need need : pattern.leather()) {
            if (need.matches(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Ячейка инвентаря с инструментом: сначала руки, потом остальное; −1 — нет. */
    private static int toolSlot(Player player, Predicate<ItemStack> tool) {
        Inventory inventory = player.getInventory();
        if (tool.test(inventory.getSelected())) {
            return inventory.selected;
        }
        if (tool.test(inventory.getItem(Inventory.SLOT_OFFHAND))) {
            return Inventory.SLOT_OFFHAND;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (tool.test(inventory.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private static void wear(ServerPlayer player, ItemStack tool, int amount) {
        if (!player.getAbilities().instabuild && tool.isDamageableItem()) {
            tool.hurtAndBreak(amount, player.serverLevel(), player, item -> {});
        }
    }

    public static int threadIn(Inventory inventory) {
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
        ListTag list = new ListTag();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = materials.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag entry = (CompoundTag) stack.save(registries);
                entry.putByte("Slot", (byte) i);
                list.add(entry);
            }
        }
        tag.put("Materials", list);
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
        for (int i = 0; i < SLOTS; i++) {
            materials.setItem(i, ItemStack.EMPTY);
        }
        ListTag list = tag.getList("Materials", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte("Slot");
            if (slot >= 0 && slot < SLOTS) {
                materials.setItem(slot, ItemStack.parseOptional(registries, entry));
            }
        }
        // Столы из старых версий: выложенная кожа и фурнитура — в ячейки
        int next = 0;
        for (String old : new String[] {"Leather", "Extras"}) {
            ListTag legacy = tag.getList(old, Tag.TAG_COMPOUND);
            for (int i = 0; i < legacy.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(registries, legacy.getCompound(i));
                while (next < SLOTS && !materials.getItem(next).isEmpty()) {
                    next++;
                }
                if (!stack.isEmpty() && next < SLOTS) {
                    materials.setItem(next, stack);
                }
            }
        }
        cut = tag.getBoolean("Cut") && pattern >= 0;
        quality = tag.getInt("Quality");
    }
}
