package com.nrdhvnstd.halgventmod.client;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Прицела нет: ванильный крест убран совсем. Взгляд на блок, который используется на E (PickupInput.useBlock), —
 * посередине экрана его название и действие: «Дверь» / «Открыть: E». Сундуки и прочие окна — «Открыть»,
 * открытые дверь, люк, калитка — «Закрыть», остальное (рычаг, кнопка, кровать) — «Использовать».
 * Используемый блок — тот, чей класс переопределяет useWithoutItem (ПКМ пустой рукой что-то делает).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class UseHint {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "use_hint");
    private static final int COLOR_NAME = 0xFFFFFF;
    private static final int COLOR_ACTION = 0xFFE8A0;

    // Переопределяет ли класс блока useWithoutItem — раз на класс
    private static final ClassValue<Boolean> USABLE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> each = type; each != null && each != BlockBehaviour.class; each = each.getSuperclass()) {
                try {
                    each.getDeclaredMethod("useWithoutItem", BlockState.class, Level.class, BlockPos.class,
                            Player.class, BlockHitResult.class);
                    return true;
                } catch (NoSuchMethodException e) {
                    // выше по иерархии
                }
            }
            return false;
        }
    };

    private UseHint() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.replaceLayer(VanillaGuiLayers.CROSSHAIR, (graphics, delta) -> {});
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, UseHint::render);
    }

    /** Можно ли использовать блок на E (без замков: запертое тоже подсвечивается — откажет сервер). */
    public static boolean usable(BlockState state) {
        Block block = state.getBlock();
        return USABLE.get(block.getClass()) && !(block instanceof RedStoneOreBlock) && !(block instanceof RedStoneWireBlock);
    }

    // Ключ действия: open, close или use
    private static String action(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
            return state.getValue(BlockStateProperties.OPEN) ? "close" : "open";
        }
        return state.getMenuProvider(level, pos) != null ? "open" : "use";
    }

    @Nullable
    private static BlockHitResult target(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null || player.isSpectator()
                || PickupInput.target() != null || player.isHandsBusy()) {
            return null;
        }
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && usable(minecraft.level.getBlockState(hit.getBlockPos()))) {
            return hit;
        }
        return null;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        BlockHitResult hit = target(minecraft);
        if (hit == null) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        Font font = minecraft.font;
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2;
        graphics.drawCenteredString(font, state.getBlock().getName(), x, y - font.lineHeight, COLOR_NAME);
        graphics.drawCenteredString(font, Component.translatable("hud.halgventmod.use." + action(minecraft.level, pos, state),
                ModKeys.PICKUP.getTranslatedKeyMessage()), x, y + 1, COLOR_ACTION);
    }
}
