package com.nrdhvnstd.halgventmod.armorial;

import java.util.List;

import com.nrdhvnstd.halgventmod.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Гербовник — книга знамён. ПКМ — открыть: сервер присылает содержимое книги, клиент открывает окно.
 * Какая это книга — компонент armorial_book (id), нет компонента — общая книга MAIN.
 */
public class ArmorialItem extends Item {
    public ArmorialItem(Properties properties) {
        super(properties);
    }

    public static String bookId(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ARMORIAL_BOOK.get(), ArmorialBooks.MAIN);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            ArmorialBooks.open(serverPlayer, bookId(stack));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // Строка с названием книги (названия приходят всем игрокам — ArmorialTitlesPayload)
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String title = ArmorialBooks.clientTitles.get(bookId(stack));
        if (title != null && !title.isEmpty()) {
            tooltip.add(Component.literal(title).withStyle(ChatFormatting.GOLD));
        }
    }
}
