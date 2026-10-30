package com.nrdhvnstd.halgventmod.client;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.Blood;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Кровь на снаряжении у клиента (combat/Blood): в подсказке — «Окровавлено» / «В крови», в ячейке — капля в углу,
 * с окровавленного оружия в руке капает (видно всем рядом). Пятна на броне — в склейке брони (ArmorCompositor).
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class BloodClient {
    // Меняйте здесь ⚠️
    private static final float DRIP_FROM = 0.3F;       // от стольки крови с оружия капает
    private static final float DRIP_CHANCE = 0.12F;    // за тик при полной крови
    private static final double DRIP_RANGE = 24;
    private static final int DROP_COLOR = 0xFF8A1010, DROP_DARK = 0xFF4A0808;

    private BloodClient() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        float blood = Blood.level(event.getItemStack());
        if (blood >= Blood.VISIBLE) {
            event.getToolTip().add(Component.translatable(blood >= 0.6F ? "tooltip.halgventmod.blood.soaked"
                    : "tooltip.halgventmod.blood.stained").withStyle(ChatFormatting.DARK_RED));
        }
    }

    // Капля крови в правом верхнем углу ячейки
    private static final IItemDecorator DROP = (graphics, font, stack, x, y) -> {
        if (Blood.level(stack) < Blood.VISIBLE) {
            return false;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        int cx = x + 13;
        int cy = y + 1;
        graphics.fill(cx, cy, cx + 1, cy + 1, DROP_COLOR);
        graphics.fill(cx - 1, cy + 1, cx + 2, cy + 3, DROP_COLOR);
        graphics.fill(cx, cy + 3, cx + 1, cy + 4, DROP_DARK);
        graphics.pose().popPose();
        return true;
    };

    @SubscribeEvent
    public static void registerDecorations(RegisterItemDecorationsEvent event) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.getDefaultMaxStackSize() == 1) { // кровь бывает только на оружии и броне
                event.register(item, DROP);
            }
        }
    }

    // С окровавленного оружия капает
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }
        for (Player player : minecraft.level.players()) {
            if (player.distanceToSqr(minecraft.player) > DRIP_RANGE * DRIP_RANGE) {
                continue;
            }
            float blood = Blood.level(player.getMainHandItem());
            if (blood < DRIP_FROM || player.getRandom().nextFloat() > DRIP_CHANCE * blood) {
                continue;
            }
            // У правой руки, чуть впереди и ниже груди
            float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
            Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 at = player.position().add(0, player.getBbHeight() * 0.45, 0).add(right.scale(0.4)).add(forward.scale(0.35));
            minecraft.level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.NETHER_WART_BLOCK.defaultBlockState()),
                    at.x, at.y, at.z, 0, 0, 0);
        }
    }
}
