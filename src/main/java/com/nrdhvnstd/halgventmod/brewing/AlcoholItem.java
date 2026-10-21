package com.nrdhvnstd.halgventmod.brewing;

import com.nrdhvnstd.halgventmod.food.DrinkItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Напиток (сусло, пиво, сидр, вино, медовуха, крепкое, настойка, уксус). Пьётся всегда — даже сытым и без жажды.
 * Название — из состава (Brew): drink.halgventmod.&lt;предмет&gt;.&lt;основа&gt; и приметы « на травах», « с мёдом».
 */
public class AlcoholItem extends DrinkItem {
    public AlcoholItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public Component getName(ItemStack stack) {
        Brew brew = Brewing.brew(stack);
        if (brew == null) {
            return super.getName(stack);
        }
        String item = BuiltInRegistries.ITEM.getKey(this).getPath();
        MutableComponent name = Component.translatable("drink.halgventmod." + item + "." + brew.kind());
        for (String flag : brew.flags()) {
            name.append(Component.translatable("drink.halgventmod.flag." + flag));
        }
        return name;
    }
}
