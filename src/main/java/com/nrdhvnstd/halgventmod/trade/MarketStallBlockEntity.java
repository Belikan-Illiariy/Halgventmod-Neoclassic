package com.nrdhvnstd.halgventmod.trade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.quality.GearQuality;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.quality.QualityDisplay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Прилавок игрока-торговца: хозяин, товар (ячеек — по виду, StallKind), предложения по видам вещей — цена продажи и цена скупки
 * по каждому качеству (у вещей без качества — одна) и запросы покупателей на кончившийся
 * товар. Напрямую с прилавка не покупают: торгует НПС-торговец хозяина (Shop) с прилавками в 30 блоках от себя;
 * деньги — в его кошельке (Purse), не на прилавке.
 * Цена — набор монет (Price); все нули — по этому качеству не продаётся (не скупается).
 */
public class MarketStallBlockEntity extends BlockEntity {
    public static final int MAX_REQUESTS = 32;
    /** Сколько цен у вида с качеством: по одной на ступень (Ужасное … Шедевр). */
    public static final int GRADES = GearQuality.Grade.values().length;

    /**
     * Предложение по виду вещи. sell[ступень] и buy[ступень] — цена набором монет (Price), по ступени на качество
     * (или одна строка, если вид без качества). Все нули — по этому качеству не продаётся (не скупается).
     */
    public static final class Offer {
        public final ResourceLocation item;
        public final boolean graded;
        public final int[][] sell;
        public final int[][] buy;

        Offer(ResourceLocation item, boolean graded) {
            this.item = item;
            this.graded = graded;
            int rows = graded ? GRADES : 1;
            this.sell = new int[rows][Price.COINS];
            this.buy = new int[rows][Price.COINS];
        }

        public int rows() {
            return sell.length;
        }

        public int[] sell(int grade) {
            return grade >= 0 && grade < sell.length ? sell[grade] : Price.zero();
        }

        public int[] buy(int grade) {
            return grade >= 0 && grade < buy.length ? buy[grade] : Price.zero();
        }
    }

    /** Запрос покупателя: какой вещи (и какого качества; у вида без качества — 0) не хватило. */
    public record Request(ResourceLocation item, int grade, String buyer) {}

    // Вещи из ячеек, которых у вида прилавка стало меньше (старые сохранения): отдаются первому, кто откроет прилавок
    private final List<ItemStack> overflow = new ArrayList<>();
    private static final int OLD_SLOTS_MAX = 54;

    // Ячеек — по виду прилавка (StallKind.slots); у манекена каждая ячейка — свой слот брони
    private final SimpleContainer stock = new SimpleContainer(kind().slots()) {
        @Override
        public void setChanged() {
            super.setChanged();
            MarketStallBlockEntity.this.changed();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return kind().accepts(stack, slot);
        }

        @Override
        public int getMaxStackSize() {
            return kind().perSlot();
        }
    };
    @Nullable
    private UUID owner;
    private String ownerName = "";
    @Nullable
    private UUID merchant;          // НПС-торговец этого прилавка
    private String merchantName = "";
    private final Map<ResourceLocation, Offer> offers = new LinkedHashMap<>();
    private final List<Request> requests = new ArrayList<>();
    private int version; // растёт при любом изменении: окно хозяина по нему досылает данные

    public MarketStallBlockEntity(BlockPos pos, BlockState state) {
        super(TradeBlocks.MARKET_STALL_ENTITY.get(), pos, state);
    }

    public StallKind kind() {
        return getBlockState().getBlock() instanceof MarketStallBlock block ? block.kind() : StallKind.GOODS;
    }

    public SimpleContainer stock() {
        return stock;
    }

    public int version() {
        return version;
    }

    // Изменилось — сохранить и показать вещи на стойке, витрине, манекене всем вокруг
    private void changed() {
        version++;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ===== Хозяин =====

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        changed();
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    // ===== НПС-торговец, который этим прилавком торгует (привязывает жезл НПС, режим «Прилавок») =====

    @Nullable
    public UUID merchant() {
        return merchant;
    }

    public String merchantName() {
        return merchantName;
    }

    /** Привязать к НПС-торговцу (null — отвязать). */
    public void setMerchant(@Nullable UUID npc, String name) {
        merchant = npc;
        merchantName = npc == null ? "" : name;
        changed();
    }

    // ===== Качество =====

    /** Номер цены для вещи: ступень качества (без качества у вида с качеством — Посредственное), у вида без качества — 0. */
    public static int grade(ItemStack stack, boolean graded) {
        if (!graded) {
            return 0;
        }
        GearQuality.Grade grade = QualityDisplay.of(stack);
        return (grade != null ? grade : GearQuality.Grade.MEDIOCRE).ordinal();
    }

    /** Бывает ли у вещи качество. */
    public static boolean graded(ItemStack stack) {
        return QualityDisplay.of(stack) != null || GearQuality.applies(stack);
    }

    private static ResourceLocation id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    // ===== Товар и предложения =====

    @Nullable
    public Offer offer(ResourceLocation item) {
        return offers.get(item);
    }

    public List<Offer> offers() {
        return List.copyOf(offers.values());
    }

    /** Виды вещей для окна хозяина: с предложением и просто лежащие в товаре. */
    public List<ResourceLocation> kinds() {
        Set<ResourceLocation> kinds = new LinkedHashSet<>(offers.keySet());
        for (ItemStack stack : stock.getItems()) {
            if (!stack.isEmpty()) {
                kinds.add(id(stack));
            }
        }
        return new ArrayList<>(kinds);
    }

    /** Есть ли у вида качество: по предложению или по лежащим вещам. */
    public boolean gradedKind(ResourceLocation item) {
        Offer offer = offers.get(item);
        if (offer != null) {
            return offer.graded;
        }
        for (ItemStack stack : stock.getItems()) {
            if (!stack.isEmpty() && id(stack).equals(item) && graded(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Сколько вещей вида и качества лежит в товаре. */
    public int stockOf(ResourceLocation item, int grade) {
        boolean graded = gradedKind(item);
        int count = 0;
        for (ItemStack stack : stock.getItems()) {
            if (!stack.isEmpty() && id(stack).equals(item) && grade(stack, graded) == grade) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Образец вещи вида и качества из товара (для показа), или пусто. */
    public ItemStack sample(ResourceLocation item, int grade) {
        boolean graded = gradedKind(item);
        for (ItemStack stack : stock.getItems()) {
            if (!stack.isEmpty() && id(stack).equals(item) && grade(stack, graded) == grade) {
                return stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Взять из товара одну вещь вида и качества; пусто — нет. */
    public ItemStack takeOne(ResourceLocation item, int grade) {
        boolean graded = gradedKind(item);
        for (int i = 0; i < stock.getContainerSize(); i++) {
            ItemStack stack = stock.getItem(i);
            if (!stack.isEmpty() && id(stack).equals(item) && grade(stack, graded) == grade) {
                ItemStack one = stack.split(1);
                stock.setChanged();
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Первая ячейка товара с вещью под условие; -1 — нет (хранилище: найти, чем пополнить прилавок). */
    public int find(java.util.function.Predicate<ItemStack> test) {
        for (int i = 0; i < stock.getContainerSize(); i++) {
            ItemStack stack = stock.getItem(i);
            if (!stack.isEmpty() && test.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    /** Вынуть одну вещь из ячейки. */
    public ItemStack takeAt(int slot) {
        ItemStack one = stock.removeItem(slot, 1);
        stock.setChanged();
        return one;
    }

    /** Положить вещь в товар (скупка): дополнить такую же стопку или в свободную подходящую ячейку; false — некуда. */
    public boolean put(ItemStack stack) {
        int slot = room(stack);
        if (slot < 0) {
            return false;
        }
        ItemStack there = stock.getItem(slot);
        if (there.isEmpty()) {
            stock.setItem(slot, stack.split(Math.min(stack.getCount(), kind().perSlot())));
        } else {
            int moved = Math.min(stack.getCount(), Math.min(kind().perSlot(), there.getMaxStackSize()) - there.getCount());
            there.grow(moved);
            stack.shrink(moved);
            stock.setChanged();
        }
        return true;
    }

    /** Есть ли место под эту вещь. */
    public boolean hasRoom(ItemStack stack) {
        return room(stack) >= 0;
    }

    // Ячейка под вещь: сначала такая же неполная стопка, потом пустая подходящая; -1 — нет
    private int room(ItemStack stack) {
        int empty = -1;
        for (int i = 0; i < stock.getContainerSize(); i++) {
            if (!stock.canPlaceItem(i, stack)) {
                continue;
            }
            ItemStack there = stock.getItem(i);
            if (there.isEmpty()) {
                empty = empty < 0 ? i : empty;
            } else if (ItemStack.isSameItemSameComponents(there, stack)
                    && there.getCount() < Math.min(kind().perSlot(), there.getMaxStackSize())) {
                return i;
            }
        }
        return empty;
    }

    /**
     * Цены вида подряд: строка за строкой (ступень качества), в строке Price.COINS монет. Строк — GRADES у вида
     * с качеством, иначе одна; лишнее и отрицательное обрезается.
     */
    public void setPrices(ResourceLocation item, int[] sell, int[] buy) {
        Offer offer = new Offer(item, gradedKind(item));
        for (int row = 0; row < offer.rows(); row++) {
            offer.sell[row] = Price.clean(slice(sell, row));
            offer.buy[row] = Price.clean(slice(buy, row));
        }
        offers.put(item, offer);
        changed();
    }

    private static int[] slice(int[] flat, int row) {
        int from = row * Price.COINS;
        return from >= flat.length ? Price.zero() : Arrays.copyOfRange(flat, from, Math.min(flat.length, from + Price.COINS));
    }

    /** Цены вида одной строкой (для пакетов): ступень за ступенью по Price.COINS монет. */
    public static int[] flat(int[][] rows) {
        int[] flat = new int[rows.length * Price.COINS];
        for (int row = 0; row < rows.length; row++) {
            System.arraycopy(rows[row], 0, flat, row * Price.COINS, Price.COINS);
        }
        return flat;
    }

    public void removeOffer(ResourceLocation item) {
        if (offers.remove(item) != null) {
            changed();
        }
    }

    // ===== Розлив (бочонок с краном, StallKind.TAP) =====
    // Внутри — до TAP_CAPACITY мер одного питья. Долили — качество среднее по мерам, срок годности — у самого старого.
    // Цена за кружку и залог за кружку торговца (глиняную и пьютеровую) — наборы монет (Price).

    public static final int TAP_CAPACITY = 32;
    /** Строки цен бочонка в пакете: мера питья, залог за глиняную кружку, залог за пьютеровую. */
    public static final int TAP_ROWS = 1 + Mug.values().length;

    private ItemStack tapDrink = ItemStack.EMPTY; // одна мера, без качества и даты
    private int tapMeasures;
    private int tapQualityTotal;                  // сумма качеств всех мер: среднее — качество налитого
    private int tapCreated = -1;                  // день изготовления самого старого питья; -1 — без срока
    private int[] cupPrice = Price.zero();
    private final int[][] deposits = new int[Mug.values().length][Price.COINS];

    public int tapMeasures() {
        return tapMeasures;
    }

    /** Примет ли бочонок меру этого питья: то же самое (или пусто) и есть место. */
    public boolean tapAccepts(ItemStack drink) {
        return StallKind.isDrink(drink) && tapMeasures < TAP_CAPACITY
                && (tapMeasures == 0 || ItemStack.isSameItemSameComponents(plainDrink(drink), tapDrink));
    }

    /** Влить меру (сначала tapAccepts). */
    public void tapAdd(ItemStack drink) {
        if (tapMeasures == 0) {
            tapDrink = plainDrink(drink);
            tapQualityTotal = 0;
            tapCreated = -1;
        }
        tapMeasures++;
        tapQualityTotal += FoodQuality.get(drink);
        Integer created = drink.get(ModComponents.CREATED_DAY.get());
        if (created != null) {
            tapCreated = tapCreated < 0 ? created : Math.min(tapCreated, created);
        }
        changed();
    }

    /** Мера питья, какой она нальётся сейчас; пусто — бочонок пуст. */
    public ItemStack tapSample() {
        if (tapMeasures <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack one = tapDrink.copyWithCount(1);
        FoodQuality.set(one, Math.round(tapQualityTotal / (float) tapMeasures));
        if (tapCreated >= 0) {
            one.set(ModComponents.CREATED_DAY.get(), tapCreated);
        }
        return one;
    }

    /** Отлить меру: питьё, которое налилось; пусто — бочонок пуст. */
    public ItemStack tapTake() {
        ItemStack one = tapSample();
        if (one.isEmpty()) {
            return one;
        }
        tapQualityTotal -= FoodQuality.get(one);
        tapMeasures--;
        if (tapMeasures <= 0) {
            tapEmpty();
        } else {
            changed();
        }
        return one;
    }

    /** Вылить всё. */
    public void tapEmpty() {
        tapDrink = ItemStack.EMPTY;
        tapMeasures = 0;
        tapQualityTotal = 0;
        tapCreated = -1;
        changed();
    }

    public int[] cupPrice() {
        return cupPrice.clone();
    }

    public int[] deposit(Mug mug) {
        return deposits[mug.ordinal()].clone();
    }

    /** Цены бочонка подряд по строкам (TAP_ROWS × Price.COINS): мера, залог за кружку каждого вида. */
    public void setTapPrices(int[] flat) {
        cupPrice = Price.clean(slice(flat, 0));
        for (Mug mug : Mug.values()) {
            deposits[mug.ordinal()] = Price.clean(slice(flat, 1 + mug.ordinal()));
        }
        changed();
    }

    public int[] tapPricesFlat() {
        int[][] rows = new int[TAP_ROWS][];
        rows[0] = cupPrice;
        for (Mug mug : Mug.values()) {
            rows[1 + mug.ordinal()] = deposits[mug.ordinal()];
        }
        return flat(rows);
    }

    /** Ячейка с первой кружкой торговца (глиняные раньше пьютеровых); -1 — кружек нет. */
    public int mugSlot() {
        int found = -1;
        for (int i = 0; i < stock.getContainerSize(); i++) {
            Mug mug = Mug.of(stock.getItem(i));
            if (mug == Mug.CLAY) {
                return i;
            }
            if (mug != null && found < 0) {
                found = i;
            }
        }
        return found;
    }

    public int mugCount() {
        int count = 0;
        for (ItemStack stack : stock.getItems()) {
            if (Mug.of(stack) != null) {
                count++;
            }
        }
        return count;
    }

    // Питьё как «вид»: без качества и даты — по ним и сравниваем
    private static ItemStack plainDrink(ItemStack drink) {
        ItemStack copy = drink.copyWithCount(1);
        copy.remove(ModComponents.FOOD_QUALITY.get());
        copy.remove(ModComponents.CREATED_DAY.get());
        return copy;
    }

    // ===== Запросы =====

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    /** Оставить запрос; тот же покупатель на ту же вещь — один раз. */
    public void addRequest(ResourceLocation item, int grade, String buyer) {
        Request request = new Request(item, grade, buyer);
        if (requests.contains(request)) {
            return;
        }
        if (requests.size() >= MAX_REQUESTS) {
            requests.remove(0);
        }
        requests.add(request);
        changed();
    }

    /** Отдать игроку вещи из исчезнувших ячеек (не влезли — под ноги). */
    public void giveOverflow(Player player) {
        if (overflow.isEmpty()) {
            return;
        }
        for (ItemStack stack : overflow) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        overflow.clear();
        setChanged();
    }

    public void clearRequests() {
        requests.clear();
        changed();
    }

    // ===== Сохранение =====

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, stock.getItems(), registries);
        if (!overflow.isEmpty()) {
            NonNullList<ItemStack> spilled = NonNullList.withSize(Math.min(OLD_SLOTS_MAX, overflow.size()), ItemStack.EMPTY);
            for (int i = 0; i < spilled.size(); i++) {
                spilled.set(i, overflow.get(i));
            }
            tag.put("Overflow", ContainerHelper.saveAllItems(new CompoundTag(), spilled, registries));
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
        if (merchant != null) {
            tag.putUUID("Merchant", merchant);
        }
        tag.putString("MerchantName", merchantName);
        ListTag list = new ListTag();
        for (Offer offer : offers.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Item", offer.item.toString());
            entry.putBoolean("Graded", offer.graded);
            entry.putIntArray("SellCoins", flat(offer.sell));
            entry.putIntArray("BuyCoins", flat(offer.buy));
            list.add(entry);
        }
        tag.put("Offers", list);
        ListTag wanted = new ListTag();
        for (Request request : requests) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Item", request.item().toString());
            entry.putInt("Grade", request.grade());
            entry.putString("Buyer", request.buyer());
            wanted.add(entry);
        }
        tag.put("Requests", wanted);
        if (tapMeasures > 0) {
            tag.put("TapDrink", tapDrink.save(registries));
            tag.putInt("TapMeasures", tapMeasures);
            tag.putInt("TapQuality", tapQualityTotal);
            tag.putInt("TapCreated", tapCreated);
        }
        tag.putIntArray("TapPrices", tapPricesFlat());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        version++; // на клиенте — чтобы отрисовка манекена взяла новую броню
        stock.getItems().replaceAll(stack -> ItemStack.EMPTY);
        NonNullList<ItemStack> all = NonNullList.withSize(Math.max(OLD_SLOTS_MAX, stock.getContainerSize()), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, all, registries);
        overflow.clear();
        for (int i = 0; i < all.size(); i++) {
            if (i < stock.getContainerSize()) {
                stock.getItems().set(i, all.get(i));
            } else if (!all.get(i).isEmpty()) {
                overflow.add(all.get(i));
            }
        }
        if (tag.contains("Overflow")) {
            NonNullList<ItemStack> spilled = NonNullList.withSize(OLD_SLOTS_MAX, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound("Overflow"), spilled, registries);
            spilled.stream().filter(stack -> !stack.isEmpty()).forEach(overflow::add);
        }
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        merchant = tag.hasUUID("Merchant") ? tag.getUUID("Merchant") : null;
        merchantName = tag.getString("MerchantName");
        offers.clear();
        for (Tag raw : tag.getList("Offers", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            ResourceLocation item = ResourceLocation.tryParse(entry.getString("Item"));
            if (item == null) {
                continue;
            }
            Offer offer = new Offer(item, entry.getBoolean("Graded"));
            if (entry.contains("SellCoins")) {
                int[] sell = entry.getIntArray("SellCoins");
                int[] buy = entry.getIntArray("BuyCoins");
                for (int row = 0; row < offer.rows(); row++) {
                    offer.sell[row] = Price.clean(slice(sell, row));
                    offer.buy[row] = Price.clean(slice(buy, row));
                }
            } else {
                // Старое сохранение: одна монета на вид и по числу на ступень
                int coin = Coin.byOrdinal(entry.getInt("Coin")).ordinal();
                int[] sell = entry.getIntArray("Sell");
                int[] buy = entry.getIntArray("Buy");
                for (int row = 0; row < offer.rows(); row++) {
                    offer.sell[row][coin] = row < sell.length ? sell[row] : 0;
                    offer.buy[row][coin] = row < buy.length ? buy[row] : 0;
                }
            }
            offers.put(item, offer);
        }
        requests.clear();
        for (Tag raw : tag.getList("Requests", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            ResourceLocation item = ResourceLocation.tryParse(entry.getString("Item"));
            if (item != null) {
                requests.add(new Request(item, entry.getInt("Grade"), entry.getString("Buyer")));
            }
        }
        tapMeasures = tag.getInt("TapMeasures");
        tapDrink = tapMeasures > 0 ? ItemStack.parseOptional(registries, tag.getCompound("TapDrink")) : ItemStack.EMPTY;
        if (tapDrink.isEmpty()) {
            tapMeasures = 0;
        }
        tapQualityTotal = tag.getInt("TapQuality");
        tapCreated = tag.contains("TapCreated") ? tag.getInt("TapCreated") : -1;
        int[] tapPrices = tag.getIntArray("TapPrices");
        cupPrice = Price.clean(slice(tapPrices, 0));
        for (Mug mug : Mug.values()) {
            deposits[mug.ordinal()] = Price.clean(slice(tapPrices, 1 + mug.ordinal()));
        }
    }
}
