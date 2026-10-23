package com.nrdhvnstd.halgventmod.carpentry;

import java.util.ArrayList;
import java.util.List;

import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;
import com.nrdhvnstd.halgventmod.menu.CarpenterBenchMenu;
import com.nrdhvnstd.halgventmod.network.BenchWorkOpenPayload;
import com.nrdhvnstd.halgventmod.registry.ModBlockEntities;

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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Верстак плотника.
 * <ol>
 *   <li>E — окно верстака ({@link CarpenterBenchMenu}): выбрать изделие, переложить доски и прочее из инвентаря
 *       в ячейки верстака (что лежит — видно на верстаке);</li>
 *   <li>«Делать» — этапы изделия по порядку (BenchWorkScreen): строгание, выборка долотом, сверление, обстругивание
 *       ножом, сборка молотком — у каждого своя мини-игра и свой инструмент из инвентаря. Материалы уходят,
 *       изделие выходит с качеством {@link Carpentry#BASE_QUALITY} + поправка за средний счёт этапов
 *       ({@link Carpentry#workDelta}); у инструментов от качества зависит прочность.</li>
 * </ol>
 * После работы окно верстака открывается снова. Сервер не верит итогу раньше, чем через сумму
 * {@link Operation#minTicks} этапов, и заново проверяет материалы и инструменты; каждый инструмент тупится
 * тем сильнее, чем хуже сделан его этап.
 */
public class CarpenterBenchBlockEntity extends StationBlockEntity {
    public static final int SLOTS = 9;
    public static final int DATA = 1;              // выбранное изделие — для окна
    public static final double REACH = 6.0;

    private int product = -1;
    private final SimpleContainer materials = new SimpleContainer(SLOTS);
    private long workSince;                        // время мира, когда начали работу; 0 — не работают
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

    public CarpenterBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CARPENTER_BENCH.get(), pos, state);
        materials.addListener(container -> changed());
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

    public BenchProducts.Product current() {
        return BenchProducts.get(product);
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
            openMenu(serverPlayer);
        }
        return true;
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new CarpenterBenchMenu(id, inventory, this),
                getBlockState().getBlock().getName()), buf -> buf.writeBlockPos(worldPosition));
    }

    private boolean near(Player player) {
        return player.distanceToSqr(worldPosition.getCenter()) <= REACH * REACH;
    }

    /** Изделие выбрано в окне. */
    public void chooseProduct(ServerPlayer player, int index) {
        if (index < 0 || index >= BenchProducts.all().size() || !near(player) || index == product) {
            return;
        }
        product = index;
        sound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        changed();
    }

    /** Почему работать нельзя (ключ сообщения) или null — можно. Не хватает инструмента — его в {@code missing}. */
    private String blocker(Player player, ItemStack[] missing) {
        if (product < 0) {
            return "pick_product";
        }
        for (BenchProducts.Need need : current().needs()) {
            if (count(need) < need.count()) {
                return "need_materials";
            }
        }
        for (Operation operation : current().operations()) {
            if (toolSlot(player, operation) < 0) {
                missing[0] = operation.icon();
                return "need_tool";
            }
        }
        return null;
    }

    /** Кнопка «Делать»: проверить и начать этапы (окно верстака закрывается, потом откроется снова). */
    public void requestWork(ServerPlayer player) {
        ItemStack[] missing = {ItemStack.EMPTY};
        String blocker = blocker(player, missing);
        if (blocker != null) {
            Carpentry.say(player, blocker, missing[0].getHoverName());
            if ("need_materials".equals(blocker)) {
                player.displayClientMessage(describe(current()), false);
            }
            return;
        }
        player.closeContainer();
        workSince = level == null ? 1 : Math.max(1, level.getGameTime());
        int seed = level == null ? 0 : level.random.nextInt();
        PacketDistributor.sendToPlayer(player, new BenchWorkOpenPayload(worldPosition, product, seed));
    }

    /**
     * Итог работы (BenchWorkResultPayload): счёт каждого этапа 0–100. Сервер заново проверяет верстак, материалы,
     * инструменты, число этапов и время; клиенту доверяем только счёт.
     */
    public void worked(ServerPlayer player, List<Integer> scores) {
        BenchProducts.Product current = product < 0 ? null : current();
        if (current == null || !near(player) || blocker(player, new ItemStack[1]) != null || level == null
                || workSince <= 0 || scores.size() != current.steps().size()
                || level.getGameTime() - workSince < current.minTicks()) {
            return;
        }
        workSince = 0;
        int total = 0;
        for (int i = 0; i < scores.size(); i++) {
            int score = Math.clamp(scores.get(i), 0, 100);
            total += score;
            // Инструмент этапа тупится: за сам этап и тем сильнее, чем хуже он сделан
            ItemStack tool = player.getInventory().getItem(toolSlot(player, current.steps().get(i).operation()));
            if (!player.getAbilities().instabuild && tool.isDamageableItem()) {
                tool.hurtAndBreak(1 + (100 - score) / 25, player.serverLevel(), player, item -> {});
            }
        }
        int average = Math.round(total / (float) scores.size());
        for (BenchProducts.Need need : current.needs()) {
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
        int delta = Carpentry.workDelta(average);
        ItemStack result = current.hasQuality() ? Carpentry.setQuality(current.result(), Carpentry.BASE_QUALITY + delta)
                : current.result();
        // Имя — до give: инвентарь забирает стак целиком, и после него он пуст
        Component name = result.getHoverName();
        give(player, result);
        player.displayClientMessage(Component.translatable("message.halgventmod.carpentry.made", name), false);
        Carpentry.say(player, !current.hasQuality() || delta == 0 ? "worked" : delta > 0 ? "worked_clean" : "worked_rough",
                average);
        sound(SoundEvents.WOOD_PLACE, 1.0F, 1.2F);
        changed();
        openMenu(player);
    }

    /** Работу бросили (Esc): материалы на месте, ждать итога больше не нужно. */
    public void workCancelled(ServerPlayer player) {
        if (near(player)) {
            workSince = 0;
        }
    }

    // ---------------------------------------------------------------- помощники

    private int count(BenchProducts.Need need) {
        int n = 0;
        for (ItemStack stack : laid()) {
            if (need.matches(stack)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** Ячейка инвентаря с инструментом операции: сначала руки, потом остальное; −1 — нет. */
    private static int toolSlot(Player player, Operation operation) {
        Inventory inventory = player.getInventory();
        if (operation.tool().test(inventory.getSelected())) {
            return inventory.selected;
        }
        if (operation.tool().test(inventory.getItem(Inventory.SLOT_OFFHAND))) {
            return Inventory.SLOT_OFFHAND;
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (operation.tool().test(inventory.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    /** «Изделие: Челнок — 1 × доски». */
    private static MutableComponent describe(BenchProducts.Product product) {
        MutableComponent needs = Component.empty();
        boolean first = true;
        for (BenchProducts.Need need : product.needs()) {
            if (!first) {
                needs.append(", ");
            }
            needs.append(need.count() + " × ").append(first ? Component.translatable("gui.halgventmod.carpenter_bench.planks")
                    : need.icon().get().getHoverName());
            first = false;
        }
        ItemStack result = product.result();
        return Component.translatable("message.halgventmod.carpentry.product", result.getHoverName(), needs);
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Product", product);
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
        if (product >= BenchProducts.all().size()) {
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
}
