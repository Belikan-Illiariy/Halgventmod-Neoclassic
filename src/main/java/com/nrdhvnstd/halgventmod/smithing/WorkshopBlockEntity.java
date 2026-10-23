package com.nrdhvnstd.halgventmod.smithing;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.carpentry.Carpentry;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.WorkshopMenu;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Станция с изделиями — наковальня и литейный стол.
 * <ol>
 *   <li>E — окно ({@link WorkshopMenu}): выбрать изделие, выложить металл и прочее в ячейки (видно на станции);</li>
 *   <li>«Ковать» / «Лить» — этапы изделия по порядку (SmithWorkScreen), у каждого своя мини-игра; инструменты —
 *       из инвентаря, горн, чан (закалка) и точило — рядом ({@link SmithArea}).</li>
 * </ol>
 * Изделие выходит с качеством металла (и детали с верстака) плюс поправка за средний счёт этапов
 * ({@link Smithing#workDelta}); оружие и броня — с качеством снаряжения. Сервер не верит итогу раньше суммы
 * {@link ForgeOp#minTicks} и заново проверяет материалы, инструменты и место.
 */
public abstract class WorkshopBlockEntity extends StationBlockEntity implements Minigame.Host {
    public static final int SLOTS = 9;
    public static final int DATA = 1;
    public static final double REACH = 6.0;
    public static final float REFORGE_LOSS = 0.99F;   // ⚠️ перековка: прочность ×0,99 каждый раз
    public static final float CRUCIBLE_BREAK = 0.08F; // ⚠️ опустевший тигель трескается
    public static final int POUR_GRACE = 1200;        // ⚠️ тиков на заливку, пока расплав держится жидким

    private int product = -1;
    private final List<Integer> saved = new ArrayList<>();   // счёт уже сделанных этапов: бросили работу — не пропадает
    private final SimpleContainer materials = new SimpleContainer(SLOTS);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return product;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA;
        }
    };

    protected WorkshopBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        materials.addListener(container -> changed());
    }

    /** Изделия этой станции. */
    public abstract List<SmithProducts.Product> products();

    /** Вид мини-игры (Minigame.ANVIL / CASTING). */
    protected abstract int minigame();

    /** Литейный стол — для окна. */
    public boolean casting() {
        return minigame() == Minigame.CASTING;
    }

    public SimpleContainer materials() {
        return materials;
    }

    public ContainerData data() {
        return data;
    }

    public int product() {
        return product;
    }

    public SmithProducts.Product current() {
        return products().get(Math.floorMod(product, products().size()));
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

    @Override
    public boolean use(Player player, InteractionHand hand, ItemStack stack) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (stack.is(SmithingItems.SLEDGEHAMMER.get()) && minigame() == Minigame.ANVIL
                    && Striker.join(serverPlayer, worldPosition)) {
                return true;
            }
            openMenu(serverPlayer);
        }
        return true;
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new WorkshopMenu(id, inventory, this),
                getBlockState().getBlock().getName()), buf -> {
            buf.writeBlockPos(worldPosition);
            buf.writeBoolean(casting());
        });
    }

    private boolean near(Player player) {
        return player.distanceToSqr(worldPosition.getCenter()) <= REACH * REACH;
    }

    public void chooseProduct(ServerPlayer player, int index) {
        if (index < 0 || index >= products().size() || !near(player) || index == product) {
            return;
        }
        product = index;
        saved.clear();
        sound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        changed();
    }

    /** Почему работать нельзя (ключ сообщения) или null — можно. Не хватает вещи — она в {@code missing}. */
    private String blocker(Player player, ItemStack[] missing, boolean stations) {
        if (product < 0) {
            return "pick_product";
        }
        SmithProducts.Product current = current();
        if (!current.available()) {
            return "not_available";
        }
        for (SmithProducts.Need need : current.needs()) {
            if (need.role() == SmithProducts.Role.MELT) {
                String why = meltBlocker(player, need);
                if (why != null) {
                    missing[0] = need.icon().get();
                    return why;
                }
            } else if (count(need) < need.count()) {
                missing[0] = need.icon().get();
                return "need_materials";
            }
        }
        boolean hot = false;
        for (ForgeOp op : current.operations()) {
            hot |= op.hot();
            if (op.tool() != ForgeOp.Tool.NONE && toolSlot(player, op.tool()) < 0) {
                missing[0] = op.tool().icon();
                return "need_tool";
            }
        }
        if (hot && toolSlot(player, ForgeOp.Tool.TONGS) < 0) {
            missing[0] = ForgeOp.Tool.TONGS.icon();
            return "need_tool";
        }
        List<ForgeOp> left = new ArrayList<>();
        for (int i = Math.min(saved.size(), current.steps().size()); stations && i < current.steps().size(); i++) {
            left.add(current.steps().get(i).op());
        }
        for (ForgeOp op : left) {
            if (op.needsForge()) {
                ForgeBlockEntity forge = SmithArea.forge(level, worldPosition);
                if (forge == null) {
                    return "need_forge";
                }
                if (!forge.workable()) {
                    return "forge_cold";
                }
            }
            if (op.needsTub() && SmithArea.liquid(level, worldPosition) == 0) {
                return "need_tub";
            }
            if (op.needsWheel() && !SmithArea.wheel(level, worldPosition)) {
                return "need_wheel";
            }
        }
        return null;
    }

    /** Хватает ли жидкого расплава в тигле при себе: null — да, иначе ключ сообщения. */
    private String meltBlocker(Player player, SmithProducts.Need need) {
        int slot = Crucible.find(player, need.metal());
        if (slot < 0) {
            ItemStack any = Crucible.best(player, null);
            return any.isEmpty() ? "need_melt" : !Crucible.liquid(any, level) ? "melt_solid" : "melt_wrong";
        }
        int parts = Crucible.total(Crucible.melt(player.getInventory().getItem(slot)));
        return parts < need.count() * Crucible.PER_INGOT ? "melt_short" : null;
    }

    /** Тигель с расплавом для изделия; пусто — изделие не из тигля или тигля нет. */
    private ItemStack meltCrucible(Player player, SmithProducts.Product product) {
        SmithProducts.Need need = product.melt();
        if (need == null) {
            return ItemStack.EMPTY;
        }
        int slot = Crucible.find(player, need.metal());
        return slot < 0 ? ItemStack.EMPTY : player.getInventory().getItem(slot);
    }

    /** «Ковать» / «Лить»: проверить и начать этапы (окно станции закрывается, потом откроется снова). */
    public void requestWork(ServerPlayer player) {
        ItemStack[] missing = {ItemStack.EMPTY};
        String blocker = blocker(player, missing, true);
        if (blocker != null) {
            Smithing.say(player, blocker, missing[0].getHoverName());
            if ("need_materials".equals(blocker) || "melt_short".equals(blocker)) {
                player.displayClientMessage(describe(current()), false);
            }
            return;
        }
        player.closeContainer();
        if (saved.size() >= current().steps().size()) {
            saved.clear();
        }
        int savedTotal = 0;
        for (int score : saved) {
            savedTotal += score;
        }
        int savedAverage = saved.isEmpty() ? 0 : Math.round(savedTotal / (float) saved.size());
        // В окно: качество металла (биты 0–3), медь (4), сколько этапов уже сделано (8–15) и их средний счёт (16–22)
        int extra = Math.clamp(baseQuality(player), 0, Smithing.MAX_QUALITY) | (copperWork(current()) ? 1 << 4 : 0)
                | saved.size() << 8 | savedAverage << 16;
        int minTicks = 0;
        for (int i = saved.size(); i < current().steps().size(); i++) {
            minTicks += current().steps().get(i).op().minTicks();
        }
        // Пока льют, расплав не застынет
        ItemStack crucible = meltCrucible(player, current());
        if (!crucible.isEmpty()) {
            Crucible.keepLiquid(crucible, level.getGameTime() + minTicks + POUR_GRACE);
        }
        Minigame.open(player, worldPosition, minigame(), product, minTicks,
                SmithArea.info(level, worldPosition), extra);
    }

    /** Медь и драгметаллы куют холодными: наклёп вместо остывания (окно ковки). */
    private static boolean copperWork(SmithProducts.Product product) {
        for (SmithProducts.Need need : product.needs()) {
            ItemStack icon = need.icon().get();
            if (icon.is(SmithingItems.COPPER_INGOT.get()) || icon.is(SmithingItems.SILVER_INGOT.get())
                    || icon.is(SmithingItems.GOLD_INGOT.get())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void minigameResult(ServerPlayer player, Minigame.Session session, boolean done, List<Integer> scores) {
        if (session.kind() == Minigame.STRIKER) {
            Striker.leave(player, worldPosition);
            return;
        }
        if (!done) {
            // Бросили: сделанные этапы остаются за изделием, пока его не сменят
            if (session.product() == product && product >= 0
                    && saved.size() + scores.size() < current().steps().size()) {
                for (int score : scores) {
                    saved.add(Math.clamp(score, 0, 100));
                }
                changed();
                if (!saved.isEmpty()) {
                    Smithing.say(player, "work_saved", saved.size(), current().steps().size());
                }
            }
            openMenu(player);
            return;
        }
        List<Integer> all = new ArrayList<>(saved);
        all.addAll(scores);
        scores = all;
        // Горн, бочку и точило проверили в начале: за долгую работу горн мог и погаснуть
        ItemStack[] missing = {ItemStack.EMPTY};
        String blocker = session.product() != product ? "pick_product" : blocker(player, missing, false);
        if (blocker != null || scores.size() != current().steps().size()) {
            Smithing.say(player, blocker != null ? blocker : "pick_product", missing[0].getHoverName());
            openMenu(player);
            return;
        }
        saved.clear();
        SmithProducts.Product current = current();
        int total = 0;
        for (int i = 0; i < scores.size(); i++) {
            int score = Math.clamp(scores.get(i), 0, 100);
            total += score;
            ForgeOp op = current.steps().get(i).op();
            int wear = 1 + (100 - score) / 25;
            if (op.tool() != ForgeOp.Tool.NONE && op.tool() != ForgeOp.Tool.CRUCIBLE) {
                wearTool(player, op.tool(), wear);
            }
            if (op.hot()) {
                wearTool(player, ForgeOp.Tool.TONGS, 1);
            }
        }
        int average = Math.round(total / (float) scores.size());
        int base = baseQuality(player);
        // Расплав уходит из тигля: на слитки — все целые слитки
        ItemStack crucible = meltCrucible(player, current);
        SmithProducts.Need meltNeed = current.melt();
        int ingots = 0;
        if (meltNeed != null) {
            int parts = Crucible.total(Crucible.melt(crucible));
            ingots = current.kind() == SmithProducts.Result.INGOTS ? parts / Crucible.PER_INGOT : meltNeed.count();
            Crucible.take(crucible, ingots * Crucible.PER_INGOT);
        }
        // Материалы уходят; починяемая вещь — запоминается
        ItemStack repaired = ItemStack.EMPTY;
        for (SmithProducts.Need need : current.needs()) {
            if (need.role() == SmithProducts.Role.MELT) {
                continue;
            }
            int left = need.count();
            for (int i = 0; i < SLOTS && left > 0; i++) {
                ItemStack stack = materials.getItem(i);
                if (need.matches(stack)) {
                    int taken = Math.min(left, stack.getCount());
                    if (need.role() == SmithProducts.Role.REPAIRED) {
                        repaired = stack.copyWithCount(1);
                    }
                    stack.shrink(taken);
                    left -= taken;
                }
            }
        }
        materials.setChanged();
        int delta = Smithing.workDelta(average);
        int quality = Math.clamp(base + delta, 0, Smithing.MAX_QUALITY);
        ItemStack result = switch (current.kind()) {
            case PLAIN -> current.result();
            case QUALITY -> Smithing.setQuality(current.result(), quality);
            case GEAR -> {
                GearQuality.Grade grade = GearQuality.Grade.values()[quality];
                yield GearQuality.set(current.result(grade), grade);
            }
            case REPAIR -> reforged(repaired);
            case INGOTS -> Smithing.setQuality(meltNeed.metal().ingot().copyWithCount(Math.max(1, ingots)), quality);
        };
        if (!crucible.isEmpty() && Crucible.total(Crucible.melt(crucible)) <= 0) {
            crackCrucible(player, crucible);
        }
        Component name = result.getHoverName();
        give(player, result);
        Smithing.tell(player, "made", name);
        Smithing.say(player, current.kind() == SmithProducts.Result.PLAIN || current.kind() == SmithProducts.Result.REPAIR
                || delta == 0 ? "worked" : delta > 0 ? "worked_clean" : "worked_rough", average);
        sound(casting() ? SoundEvents.LAVA_EXTINGUISH : SoundEvents.ANVIL_USE, 0.8F, 1.0F);
        changed();
        openMenu(player);
    }

    /** Перекованная вещь: износа нет, но прочность чуть меньше прежней. */
    private static ItemStack reforged(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        int max = stack.getMaxDamage();
        stack.set(DataComponents.MAX_DAMAGE, Math.max(1, Math.round(max * REFORGE_LOSS)));
        stack.setDamageValue(0);
        return stack;
    }

    /** Опустевший тигель может треснуть. */
    private void crackCrucible(ServerPlayer player, ItemStack crucible) {
        if (!player.getAbilities().instabuild && player.getRandom().nextFloat() < CRUCIBLE_BREAK) {
            crucible.shrink(1);
            give(player, new ItemStack(ModItems.SHERDS.get()));
            Smithing.tell(player, "crucible_cracked");
            sound(SoundEvents.DECORATED_POT_SHATTER, 0.8F, 1.0F);
        }
    }

    /** Качество от материала: среднее по металлу (из тигля — качество расплава), деталь с верстака — четверть. */
    private int baseQuality(Player player) {
        if (product < 0) {
            return Smithing.BASE_QUALITY;
        }
        ItemStack crucible = meltCrucible(player, current());
        if (!crucible.isEmpty()) {
            return Crucible.quality(Crucible.melt(crucible));
        }
        List<ItemStack> metal = new ArrayList<>();
        int woodTotal = 0;
        int woodCount = 0;
        for (SmithProducts.Need need : current().needs()) {
            int left = need.count();
            for (ItemStack stack : laid()) {
                if (left <= 0 || !need.matches(stack)) {
                    continue;
                }
                int taken = Math.min(left, stack.getCount());
                left -= taken;
                if (need.role() == SmithProducts.Role.METAL) {
                    metal.add(stack.copyWithCount(taken));
                } else if (need.role() == SmithProducts.Role.WOOD) {
                    woodTotal += Carpentry.quality(stack) * taken;
                    woodCount += taken;
                }
            }
        }
        int metalQuality = Smithing.averageQuality(metal);
        if (woodCount == 0) {
            return metalQuality;
        }
        return Math.round((metalQuality * 3 + woodTotal / (float) woodCount) / 4);
    }

    // ---------------------------------------------------------------- помощники

    private int count(SmithProducts.Need need) {
        int n = 0;
        for (ItemStack stack : laid()) {
            if (need.matches(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Ячейка инвентаря с инструментом: сначала руки, потом остальное; −1 — нет. */
    public static int toolSlot(Player player, ForgeOp.Tool tool) {
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

    private static void wearTool(ServerPlayer player, ForgeOp.Tool tool, int amount) {
        int slot = toolSlot(player, tool);
        if (slot < 0 || player.getAbilities().instabuild) {
            return;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isDamageableItem()) {
            stack.hurtAndBreak(amount, player.serverLevel(), player, item -> {});
        }
    }

    /** «Изделие: Цепь — 2 × железный слиток». */
    private static MutableComponent describe(SmithProducts.Product product) {
        MutableComponent needs = Component.empty();
        boolean first = true;
        for (SmithProducts.Need need : product.needs()) {
            if (!first) {
                needs.append(", ");
            }
            needs.append(need.count() + " × ").append(need.icon().get().getHoverName());
            first = false;
        }
        return Component.translatable("message.halgventmod.smithing.product", product.result().getHoverName(), needs);
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Product", product);
        tag.putIntArray("Saved", saved);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        product = tag.contains("Product") ? tag.getInt("Product") : -1;
        saved.clear();
        for (int score : tag.getIntArray("Saved")) {
            saved.add(score);
        }
        if (product >= products().size()) {
            product = -1;
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
    }

    // ---------------------------------------------------------------- наковальня и литейный стол

    public static class Anvil extends WorkshopBlockEntity {
        public Anvil(BlockPos pos, BlockState state) {
            super(SmithingBlocks.ANVIL_ENTITY.get(), pos, state);
        }

        @Override
        public List<SmithProducts.Product> products() {
            return SmithProducts.anvil();
        }

        @Override
        protected int minigame() {
            return Minigame.ANVIL;
        }
    }

    public static class CastingTable extends WorkshopBlockEntity {
        public CastingTable(BlockPos pos, BlockState state) {
            super(SmithingBlocks.CASTING_ENTITY.get(), pos, state);
        }

        @Override
        public List<SmithProducts.Product> products() {
            return SmithProducts.casting();
        }

        @Override
        protected int minigame() {
            return Minigame.CASTING;
        }
    }
}
