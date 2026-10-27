package com.nrdhvnstd.halgventmod.client;

import java.util.Optional;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.downed.DownedInput;
import com.nrdhvnstd.halgventmod.client.social.SocialInput;
import com.nrdhvnstd.halgventmod.downed.Downed;
import com.nrdhvnstd.halgventmod.network.PickupPayload;
import com.nrdhvnstd.halgventmod.network.UseKeyPayload;
import com.nrdhvnstd.halgventmod.pickup.Pickup;
import com.nrdhvnstd.halgventmod.weight.ModWeights;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Ручной подбор на клиенте (ТЗ 6.1): вещь или стрела под прицелом в пределах Pickup.REACH обведена
 * контуром (MinecraftMixin), над ней — «Хлеб ×3 · 0,6 кг», под прицелом — «[E] Подобрать».
 * E — подобрать, удержание E — подбирать по очереди с шагом Pickup.PICKUP_COOLDOWN, Shift+E — подобрать и надеть.
 * Вещи под прицелом нет — игрок в 2 блоках: удержание E открывает круговое меню (client/social/SocialInput).
 * Нет и игрока — E открывает и использует блок (дверь, сундук, рычаг), как ПКМ пустой рукой; нет и блока с действием,
 * а в руке повязка или мазь — перевязаться (DownedInput). Поверженный и несущий E только сдаются и бросают (DownedInput).
 * Решает сервер (Pickup.handle); здесь только выбор цели и подсказки.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class PickupInput {
    private static final double TARGET_INFLATE = 0.25; // мелкую вещь на земле легче навести
    private static final int HINT_COLOR = 0xFFFFFF;
    private static final int HINT_OFFSET = 12;         // под прицелом
    private static final ResourceLocation HINT_LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "pickup_hint");

    @Nullable
    private static Entity target;
    private static int heldTicks;

    private PickupInput() {}

    /** Вещь под прицелом, которую можно подобрать, или null. */
    @Nullable
    public static Entity target() {
        return target;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        target = player != null && minecraft.screen == null && !player.isSpectator() && !Downed.busy(player)
                ? findTarget(player) : null;
        SocialInput.update(minecraft, player, target != null); // игрок под прицелом — после вещи, до блока

        boolean down = ModKeys.PICKUP.isDown();
        while (ModKeys.PICKUP.consumeClick()) {
            // нажатия считаем по isDown: первое — сразу, дальше при удержании с шагом задержки
        }
        // Поверженный и несущий: E — сдаться или бросить (DownedInput), больше ничего
        if (player != null && DownedInput.handleKey(player, down)) {
            heldTicks = 0;
            return;
        }
        // E по игроку: удержание — круговое меню (SocialInput). Пока нажатие его — ни подбора, ни блока
        if (player != null && SocialInput.handleKey(player, down)) {
            heldTicks++;
            return;
        }
        if (!down || player == null) {
            heldTicks = 0;
            return;
        }
        if (heldTicks % Pickup.PICKUP_COOLDOWN == 0 && target != null) {
            PacketDistributor.sendToServer(new PickupPayload(target.getId(), Screen.hasShiftDown()));
            player.swing(InteractionHand.MAIN_HAND);
        } else if (heldTicks == 0 && target == null && DownedInput.selfHealReady(minecraft, player)) {
            DownedInput.healSelf(player);
        } else if (heldTicks == 0 && target == null && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            useBlock(minecraft, player, hit);
        }
        heldTicks++;
    }

    // E по блоку — как ПКМ (с замками и прочими проверками), но без предмета из руки; только по нажатию, не при удержании.
    // Shift+E — действия с замками (LockEvents)
    private static void useBlock(Minecraft minecraft, LocalPlayer player, BlockHitResult hit) {
        if (minecraft.gameMode == null || player.isHandsBusy()) {
            return;
        }
        PacketDistributor.sendToServer(UseKeyPayload.INSTANCE); // до ванильного пакета — сервер обработает их по порядку
        Pickup.markUseKey(player);
        InteractionResult result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        Pickup.clearUseKey(player);
        if (result.consumesAction() && result.shouldSwing()) {
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    // Ближайшая подбираемая вещь на луче взгляда до первого блока
    @Nullable
    private static Entity findTarget(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(Pickup.REACH));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        AABB area = player.getBoundingBox().expandTowards(look.scale(Pickup.REACH)).inflate(1.0);
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : player.level().getEntities(player, area,
                e -> (e instanceof ItemEntity || e instanceof AbstractArrow) && Pickup.canTarget(player, e))) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(TARGET_INFLATE).clip(eye, end);
            if (hit.isPresent()) {
                double distance = eye.distanceToSqr(hit.get());
                if (distance < bestDistance) {
                    best = entity;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /** Что подбирается: вещь на земле или стрела. */
    private static ItemStack stackOf(Entity entity) {
        if (entity instanceof ItemEntity item) {
            return item.getItem();
        }
        if (entity instanceof AbstractArrow arrow) {
            return arrow.getPickupItemStackOrigin();
        }
        return ItemStack.EMPTY;
    }

    // Надпись над вещью: «Хлеб ×3 · 0,6 кг»
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (event.getEntity() != target) {
            return;
        }
        ItemStack stack = stackOf(target);
        Component label = stack.getHoverName().copy();
        if (stack.getCount() > 1) {
            label = label.copy().append(" ×" + stack.getCount());
        }
        label = label.copy().append(" · ").append(WeightText.kg(ModWeights.stackGrams(stack) / 1000.0));
        event.setContent(label);
        event.setCanRender(TriState.TRUE);
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, HINT_LAYER, PickupInput::renderHint);
    }

    // «[E] Подобрать» и у надеваемой вещи «[Shift+E] Надеть» — под прицелом
    private static void renderHint(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (target == null || minecraft.options.hideGui) {
            return;
        }
        Component key = ModKeys.PICKUP.getTranslatedKeyMessage();
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + HINT_OFFSET;
        graphics.drawCenteredString(minecraft.font, Component.translatable("hud.halgventmod.pickup", key), x, y, HINT_COLOR);
        if (Pickup.equippable(stackOf(target))) {
            graphics.drawCenteredString(minecraft.font, Component.translatable("hud.halgventmod.pickup_equip", key),
                    x, y + minecraft.font.lineHeight + 1, HINT_COLOR);
        }
    }
}
