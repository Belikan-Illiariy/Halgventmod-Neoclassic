package com.nrdhvnstd.halgventmod.leather;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Шкура или мех со зверя: название по зверю («Волчья шкура», «Солёная волчья шкура», «Мех волка»), иконка — тоже
 * (свойство halgventmod:animal, client/leather/LeatherIcons). Без компонента {@link Hide} — общее название.
 */
public class HideItem extends Item {
    /** Свойство модели: номер зверя ({@link Animals#index}), по нему — иконка. */
    public static final ResourceLocation ANIMAL = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "animal");

    private final String form;

    /** @param form какое слово про зверя подставить: "" — «Волчья», ".lower" — «волчья», ".of" — «волка» */
    public HideItem(Properties properties, String form) {
        super(properties);
        this.form = form;
    }

    @Override
    public Component getName(ItemStack stack) {
        Hide hide = stack.get(ModComponents.HIDE.get());
        if (hide == null || Animals.byId(hide.animal()) == null) {
            return super.getName(stack);
        }
        return Component.translatable(getDescriptionId() + ".of", Component.translatable(Animals.langKey(hide.animal()) + form));
    }
}
