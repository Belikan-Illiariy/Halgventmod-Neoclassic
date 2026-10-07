package com.nrdhvnstd.halgventmod.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/** Картина с заранее заданным сюжетом: вешает на стену ровно свою картину. */
public class ModPaintingItem extends Item {
    private final ResourceKey<PaintingVariant> variant;

    public ModPaintingItem(ResourceKey<PaintingVariant> variant, Properties properties) {
        super(properties);
        this.variant = variant;
    }

    // Имя предмета берётся из названия картины, отдельный перевод для предмета не нужен
    @Override
    public String getDescriptionId(ItemStack stack) {
        return variant.location().toLanguageKey("painting", "title");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction direction = context.getClickedFace();
        if (direction.getAxis().isVertical()) {
            return InteractionResult.FAIL; // картины вешаются только на стены
        }

        BlockPos pos = context.getClickedPos().relative(direction);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player != null && !player.mayUseItemAt(pos, direction, stack)) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        var holder = level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getHolder(variant);
        if (holder.isEmpty()) {
            return InteractionResult.FAIL; // картина не описана
        }

        Painting painting = new Painting(level, pos, direction, holder.get());
        if (!painting.survives()) {
            return InteractionResult.CONSUME; // не хватает места на стене
        }

        if (!level.isClientSide) {
            painting.playPlacementSound();
            level.gameEvent(player, GameEvent.ENTITY_PLACE, painting.position());
            level.addFreshEntity(painting);
        }
        stack.consume(1, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Подсказка в общем формате мода: [2x3] [Автор]
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MutableComponent line = Component.empty();

        HolderLookup.Provider registries = context.registries();
        if (registries != null) {
            registries.lookup(Registries.PAINTING_VARIANT)
                    .flatMap(lookup -> lookup.get(variant))
                    .ifPresent(holder -> line.append("[")
                            .append(Component.translatable("painting.dimensions",
                                    holder.value().width(), holder.value().height()))
                            .append("] "));
        }

        line.append("[")
                .append(Component.translatable(variant.location().toLanguageKey("painting", "author")))
                .append("]");
        tooltip.add(line.withStyle(ChatFormatting.GRAY));
    }
}