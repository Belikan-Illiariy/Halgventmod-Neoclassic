package com.nrdhvnstd.halgventmod.client;

import java.util.Optional;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.downed.DownedInput;
import com.nrdhvnstd.halgventmod.client.social.SocialInput;
import com.nrdhvnstd.halgventmod.network.InteractPayload;
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
 * Что под прицелом, решает Interaction (docs/INTERACTION_E_SPEC.md, 2), здесь — что E с этим делает:
 * игрок — удержание открывает круговое меню (client/social/SocialInput); НПС — окно «Информация» (InteractPayload);
 * блок — открыть и использовать, как ПКМ пустой рукой; повязка или мазь в руке без цели — перевязаться;
 * поверженный и несущий — сдаться и бросить (DownedInput). Между разными действиями — общий кулдаун 0,4 с.
 * Решает сервер (Pickup.handle и др.); здесь только выбор цели и подсказка подбора.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class PickupInput {
    private static final double TARGET_INFLATE = 0.25; // мелкую вещь на земле легче навести
    private static final int HINT_COLOR = 0xFFFFFF;
    private static final int HINT_OFFSET = 12;         // под прицелом
    private static final ResourceLocation HINT_LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "pickup_hint");

    private static int heldTicks;

    private PickupInput() {}

    /** Вещь под прицелом, которую можно подобрать, или null. */
    @Nullable
    public static Entity target() {
        Interaction now = Interaction.current();
        return now.kind() == Interaction.Kind.ITEM ? now.entity() : null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Interaction now = Interaction.resolve(minecraft, player);
        SocialInput.update(minecraft, player, now);

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
        // Удержание повторяет только подбор; остальное — по первому нажатию (ТЗ 3.6)
        boolean first = heldTicks == 0;
        if (Interaction.ready(player) && (now.kind() == Interaction.Kind.ITEM ? heldTicks % Pickup.PICKUP_COOLDOWN == 0 : first)) {
            switch (now.kind()) {
                case ITEM -> {
                    PacketDistributor.sendToServer(new PickupPayload(now.entity().getId(), Screen.hasShiftDown()));
                    player.swing(InteractionHand.MAIN_HAND);
                    Interaction.used(player);
                }
                case NPC -> {
                    PacketDistributor.sendToServer(new InteractPayload(now.entity().getId(), Screen.hasShiftDown()));
                    Interaction.used(player);
                }
                case BLOCK, PLAIN_BLOCK -> {
                    useBlock(minecraft, player, now.block());
                    Interaction.used(player);
                }
                case HEAL_SELF -> {
                    DownedInput.healSelf(player);
                    Interaction.used(player);
                }
                default -> {
                }
            }
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
    static Entity findTarget(LocalPlayer player) {
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
        Entity target = target();
        if (target == null || event.getEntity() != target) {
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
        Entity target = target();
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
