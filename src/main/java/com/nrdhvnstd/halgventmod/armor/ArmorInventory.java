package com.nrdhvnstd.halgventmod.armor;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Надетая броня: 12 ячеек, по одной на слот. Хранится на игроке (ModAttachments.ARMOR).
 * В сохранении ячейки записаны по имени слота, поэтому порядок в ArmorSlot можно менять.
 */
public class ArmorInventory extends ItemStackHandler {
    // Синхронизация: самому игроку и всем, кто его видит. Пишутся все 12 ячеек.
    public static final AttachmentSyncHandler<ArmorInventory> SYNC = new AttachmentSyncHandler<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, ArmorInventory armor, boolean initialSync) {
            for (int i = 0; i < ArmorSlot.COUNT; i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, armor.getStackInSlot(i));
            }
        }

        // Заполняем прежний объект, а не создаём новый: на него может ссылаться открытое окно инвентаря
        @Override
        public ArmorInventory read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable ArmorInventory previous) {
            ArmorInventory armor = previous != null ? previous : new ArmorInventory();
            for (int i = 0; i < ArmorSlot.COUNT; i++) {
                armor.stacks.set(i, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            }
            armor.version++;
            armor.layersCache = null;
            return armor;
        }
    };

    private boolean dirty; // изменилось с прошлой синхронизации
    private int version;   // растёт при каждом изменении: по нему клиент пересобирает вид брони
    private Object renderCache; // только на клиенте: собранный вид брони (ArmorLook)
    private int protectionVersion = -1; // только на сервере: version, при которой защита последний раз легла на игрока
    private Object layersCache;         // сводка слоёв брони для боя (ArmorLayers), сбрасывается при каждом изменении

    public ArmorInventory() {
        super(ArmorSlot.COUNT);
    }

    /** Копия вещей (тело убитого, отлетевшая голова: у живого броня потом меняется). */
    public ArmorInventory copy() {
        ArmorInventory copy = new ArmorInventory();
        for (int i = 0; i < getSlots(); i++) {
            copy.stacks.set(i, getStackInSlot(i).copy());
        }
        return copy;
    }

    public ItemStack get(ArmorSlot slot) {
        return getStackInSlot(slot.ordinal());
    }

    public void set(ArmorSlot slot, ItemStack stack) {
        setStackInSlot(slot.ordinal(), stack);
    }

    /** true — было изменение; флаг сбрасывается. */
    public boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    public int version() {
        return version;
    }

    /** true — броня изменилась с прошлого пересчёта защиты (или это первый); отметка ставится. */
    public boolean consumeProtectionChange() {
        boolean changed = protectionVersion != version;
        protectionVersion = version;
        return changed;
    }

    public Object layersCache() {
        return layersCache;
    }

    public void setLayersCache(Object layersCache) {
        this.layersCache = layersCache;
    }

    public Object renderCache() {
        return renderCache;
    }

    public void setRenderCache(Object renderCache) {
        this.renderCache = renderCache;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return ModArmor.slotOf(stack) == ArmorSlot.values()[slot];
    }

    @Override
    protected void onContentsChanged(int slot) {
        dirty = true;
        version++;
        layersCache = null;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack stack = get(slot);
            if (!stack.isEmpty()) {
                tag.put(slot.folder(), stack.save(provider));
            }
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack stack = tag.contains(slot.folder())
                    ? ItemStack.parseOptional(provider, tag.getCompound(slot.folder()))
                    : ItemStack.EMPTY;
            stacks.set(slot.ordinal(), stack);
        }
        version++;
        layersCache = null;
    }
}
