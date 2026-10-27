package com.nrdhvnstd.halgventmod.client;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.social.SocialInput;
import com.nrdhvnstd.halgventmod.lock.Locks;
import com.nrdhvnstd.halgventmod.registry.ModItems;
import com.nrdhvnstd.halgventmod.cooking.HearthBlockEntity;
import com.nrdhvnstd.halgventmod.herbs.HerbPlantBlock;
import com.nrdhvnstd.halgventmod.leather.FleshingBeamBlockEntity;
import com.nrdhvnstd.halgventmod.leather.StationBlock;
import com.nrdhvnstd.halgventmod.leather.StationBlockEntity;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * открытые дверь, люк, калитка — «Закрыть», остальное (рычаг, кнопка, кровать) — «Использовать». Под станцией
 * с ожиданием (сушка, обжиг, брожение…) — ещё «Состояние: ПКМ», у двери и сундука с ключом в руке — «Ключ: Shift+E».
 * Блок ли это, решает Interaction: вещь и игрок перед блоком важнее.
 * Используемый блок — тот, чей класс переопределяет useWithoutItem (ПКМ пустой рукой что-то делает).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class UseHint {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "use_hint");
    private static final ResourceLocation CROSSHAIR = ResourceLocation.withDefaultNamespace("hud/crosshair");
    private static final int COLOR_NAME = 0xFFFFFF;
    private static final int COLOR_ACTION = 0xFFE8A0;
    private static final int COLOR_STATUS = 0xB8C8D8;

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

    // Ключ действия: open, close, use или cut_herbs
    private static String action(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        // Выросший кустик травы — срезать (herbs/HerbPlantBlock)
        if (block instanceof HerbPlantBlock && HerbPlantBlock.grown(state)) {
            return "cut_herbs";
        }
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
            return state.getValue(BlockStateProperties.OPEN) ? "close" : "open";
        }
        return state.getMenuProvider(level, pos) != null ? "open" : "use";
    }

    // Блок с действием — если E сейчас выбирает его (Interaction), а не вещь или игрока перед ним
    @Nullable
    private static BlockHitResult target(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        Interaction now = Interaction.current();
        if (player == null || minecraft.level == null || now.kind() != Interaction.Kind.BLOCK
                || SocialInput.menu() != null || player.isHandsBusy()) {
            return null;
        }
        return now.block();
    }

    // Вторая ветка — Shift+E ключом по двери или сундуку: запереть или отпереть (LockEvents)
    private static boolean keyBranch(LocalPlayer player, BlockState state) {
        ItemStack hand = player.getMainHandItem();
        return Locks.isLockable(state) && (hand.is(ModItems.KEY.get()) || hand.is(ModItems.KEY_RING.get()));
    }

    /**
     * Нужен ли прицел вместо надписи: на колоде со шкурой с инструментом кожевника в руке (бить по клочкам мездры)
     * и на очаге с кусками на углях (переворачивать нужный).
     */
    private static boolean aiming(Minecraft minecraft, BlockPos pos) {
        if (minecraft.level.getBlockEntity(pos) instanceof FleshingBeamBlockEntity beam) {
            return !beam.hide().isEmpty() && FleshingBeamBlockEntity.isLeatherTool(minecraft.player.getMainHandItem());
        }
        if (minecraft.level.getBlockEntity(pos) instanceof HearthBlockEntity hearth && !hearth.hasPot()) {
            for (int cell = 0; cell < HearthBlockEntity.CELLS; cell++) {
                if (!hearth.piece(cell).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Рудная жила или ил (кузнечество): прицел — бить по искре, под ним название и «Бить: ЛКМ» (с киркой или лопатой
     * в руке). true — нарисовано.
     */
    private static boolean renderVein(GuiGraphics graphics, Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null || player.isSpectator()
                || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof com.nrdhvnstd.halgventmod.smithing.OreVeinBlock vein)) {
            return false;
        }
        Font font = minecraft.font;
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2;
        boolean tool = player.getMainHandItem().is(vein.kind().tool());
        if (tool) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            graphics.blitSprite(CROSSHAIR, (graphics.guiWidth() - 15) / 2, (graphics.guiHeight() - 15) / 2, 15, 15);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
        int below = y + 10;
        graphics.drawCenteredString(font, state.getBlock().getName(), x, below, COLOR_NAME);
        int richness = state.getValue(com.nrdhvnstd.halgventmod.smithing.OreVeinBlock.RICHNESS);
        graphics.drawCenteredString(font, Component.translatable(richness == 0 ? "hud.halgventmod.use.vein_empty"
                : tool ? "hud.halgventmod.use.mine" : vein.kind().dug() ? "hud.halgventmod.use.need_shovel"
                : "hud.halgventmod.use.need_pickaxe", minecraft.options.keyAttack.getTranslatedKeyMessage()), x,
                below + font.lineHeight + 1, richness == 0 ? COLOR_STATUS : COLOR_ACTION);
        return true;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        if (renderVein(graphics, minecraft)) {
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
        // Колода со шкурой и инструмент кожевника в руке: вместо надписи — прицел, чтобы попадать по клочкам мездры
        if (aiming(minecraft, pos)) {
            // Как ванильный прицел: инверсия цвета под ним, без чёрного фона спрайта
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE,
                    GlStateManager.DestFactor.ZERO);
            graphics.blitSprite(CROSSHAIR, (graphics.guiWidth() - 15) / 2, (graphics.guiHeight() - 15) / 2, 15, 15);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            return;
        }
        // Очаг под перегонным кубом отдаёт всё кубу — и название тоже
        StationBlockEntity named = StationBlock.station(minecraft.level, pos);
        graphics.drawCenteredString(font, (named != null ? named.getBlockState() : state).getBlock().getName(), x, y - font.lineHeight, COLOR_NAME);
        graphics.drawCenteredString(font, Component.translatable("hud.halgventmod.use." + action(minecraft.level, pos, state),
                ModKeys.PICKUP.getTranslatedKeyMessage()), x, y + 1, COLOR_ACTION);
        // Станция с ожиданием: по ПКМ — как идёт дело (Pickup.onRightClickBlock)
        int line = y + 2 + font.lineHeight;
        StationBlockEntity station = StationBlock.station(minecraft.level, pos);
        if (station != null && station.hasReport()) {
            graphics.drawCenteredString(font, Component.translatable("hud.halgventmod.use.status",
                    minecraft.options.keyUse.getTranslatedKeyMessage()), x, line, COLOR_STATUS);
            line += font.lineHeight + 1;
        }
        if (keyBranch(minecraft.player, state)) {
            graphics.drawCenteredString(font, Component.translatable("hud.halgventmod.use.lock_key",
                    ModKeys.PICKUP.getTranslatedKeyMessage()), x, line, COLOR_ACTION);
        }
    }
}
