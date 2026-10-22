package com.nrdhvnstd.halgventmod.pottery;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Сырец — посуда с гончарного круга до обжига. Форма — компонентом {@link com.nrdhvnstd.halgventmod.registry.ModComponents#VESSEL},
 * название — «Сырец: кувшин». Сохнет на полке, обжигается в горне; треснувший — размачивают в бочке для замеса.
 */
public class GreenwareItem extends Item {
    public GreenwareItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Component form = Component.translatable(Pottery.vessel(stack).vesselForm().lowerKey());
        String key = Pottery.has(stack, Pottery.CRACKED) ? "item.halgventmod.greenware.cracked"
                : Pottery.has(stack, Pottery.DRIED) ? "item.halgventmod.greenware.dried"
                : "item.halgventmod.greenware.named";
        return Component.translatable(key, form);
    }
}
