package com.nrdhvnstd.halgventmod.gear;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.storage.LiveContents;

import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Надетое снаряжение: 9 ячеек (GearSlot). Хранится на игроке (ModAttachments.GEAR), как броня.
 * Сумка на спине и подсумки лежат здесь вместе со своим содержимым (компонент bag_contents);
 * окно работает с содержимым через живые списки contents(...).
 */
public class GearInventory extends ItemStackHandler {
    // Синхронизация: самому игроку и всем, кто его видит (модели на теле, свет фонаря)
    public static final AttachmentSyncHandler<GearInventory> SYNC = new AttachmentSyncHandler<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, GearInventory gear, boolean initialSync) {
            for (int i = 0; i < GearSlot.COUNT; i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, gear.getStackInSlot(i));
            }
        }

        // Заполняем прежний объект: на него ссылаются открытые окна
        @Override
        public GearInventory read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable GearInventory previous) {
            GearInventory gear = previous != null ? previous : new GearInventory();
            for (int i = 0; i < GearSlot.COUNT; i++) {
                gear.stacks.set(i, ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            }
            gear.version++;
            return gear;
        }
    };

    private final LiveContents[] contents = new LiveContents[GearSlot.COUNT];
    private boolean dirty; // изменилось с прошлой синхронизации
    private int version;   // растёт при каждом изменении

    public GearInventory() {
        super(GearSlot.COUNT);
        for (int i = 0; i < contents.length; i++) {
            contents[i] = new LiveContents();
        }
    }

    public ItemStack get(GearSlot slot) {
        return getStackInSlot(slot.ordinal());
    }

    public void set(GearSlot slot, ItemStack stack) {
        setStackInSlot(slot.ordinal(), stack);
    }

    /** Живые ячейки сумки в этой ячейке снаряжения (пустой список, если там не контейнер). */
    public NonNullList<ItemStack> items(GearSlot slot) {
        return contents[slot.ordinal()].of(get(slot));
    }

    /** Содержимое сумки поменяли на месте: записать в саму сумку и разослать. */
    public void contentsChanged(GearSlot slot) {
        contents[slot.ordinal()].flush();
        onContentsChanged(slot.ordinal());
    }

    /** Вещь в ячейке поменяли на месте — разослать. */
    public void markChanged(GearSlot slot) {
        onContentsChanged(slot.ordinal());
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

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return ModGear.typeOf(stack) == GearSlot.values()[slot].type();
    }

    @Override
    protected void onContentsChanged(int slot) {
        dirty = true;
        version++;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        for (GearSlot slot : GearSlot.values()) {
            ItemStack stack = get(slot);
            if (!stack.isEmpty()) {
                tag.put(slot.folder(), stack.save(provider));
            }
        }
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        for (GearSlot slot : GearSlot.values()) {
            ItemStack stack = tag.contains(slot.folder())
                    ? ItemStack.parseOptional(provider, tag.getCompound(slot.folder()))
                    : ItemStack.EMPTY;
            stacks.set(slot.ordinal(), stack);
        }
        version++;
    }
}
