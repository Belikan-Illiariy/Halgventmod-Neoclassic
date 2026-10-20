package com.nrdhvnstd.halgventmod.leather;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Во что высохнет мокрая кожа на раме: компонент halgventmod:tannage. */
public enum Tannage {
    TANNED("tanned", ModItems.TANNED_LEATHER),   // корьевой чан
    SOFT("soft", ModItems.SOFT_LEATHER),         // квасцовый чан
    FUR("fur", ModItems.DRESSED_FUR),            // жировой чан, мех остаётся
    RAWHIDE("rawhide", ModItems.RAWHIDE);        // голье прямо на раму, без дубления

    private final String id;
    private final Supplier<? extends Item> result;

    Tannage(String id, Supplier<? extends Item> result) {
        this.id = id;
        this.result = result;
    }

    public String id() {
        return id;
    }

    public Item result() {
        return result.get();
    }

    /** Ключ перевода «станет …»: tannage.halgventmod.tanned */
    public String langKey() {
        return "tannage.halgventmod." + id;
    }

    @Nullable
    public static Tannage byId(@Nullable String id) {
        for (Tannage tannage : values()) {
            if (tannage.id.equals(id)) {
                return tannage;
            }
        }
        return null;
    }

    @Nullable
    public static Tannage of(ItemStack stack) {
        return byId(stack.get(ModComponents.TANNAGE.get()));
    }
}
