package com.nrdhvnstd.halgventmod.cooking;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Заготовка с кухонного стола — то, что потом запекают в печи. Что именно — компонент pie_filling ({@link Kind}),
 * название — «Заготовка: лосось с травами».
 */
public class RawDishItem extends Item {
    /** Какие бывают заготовки и что из них выходит в печи. */
    public enum Kind {
        SALMON_HERBS("salmon_herbs", ModItems.SALMON_WITH_HERBS),
        HARE_HERBS("hare_herbs", ModItems.HARE_WITH_HERBS),
        EXQUISITE_MUTTON("exquisite_mutton", ModItems.EXQUISITE_MUTTON);

        private final String id;
        private final Supplier<? extends Item> baked;

        Kind(String id, Supplier<? extends Item> baked) {
            this.id = id;
            this.baked = baked;
        }

        public String id() {
            return id;
        }

        public Item baked() {
            return baked.get();
        }

        @Nullable
        public static Kind byId(@Nullable String id) {
            for (Kind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }

    public RawDishItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Kind kind) {
        ItemStack stack = new ItemStack(ModItems.RAW_DISH.get());
        stack.set(ModComponents.PIE_FILLING.get(), kind.id());
        return stack;
    }

    @Nullable
    public static Kind kind(ItemStack stack) {
        return Kind.byId(stack.get(ModComponents.PIE_FILLING.get()));
    }

    @Override
    public Component getName(ItemStack stack) {
        Kind kind = kind(stack);
        return kind == null ? super.getName(stack)
                : Component.translatable("item.halgventmod.raw_dish.named", Component.translatable(kind.baked().getDescriptionId()));
    }
}
