package com.nrdhvnstd.halgventmod.client.npc;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.Interaction;
import com.nrdhvnstd.halgventmod.client.ModKeys;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** НПС под прицелом (Interaction.Kind.NPC): имя и «[E] Поговорить» посередине экрана, как у игрока. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class NpcHint {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "npc_hint");
    private static final int COLOR_NAME = 0xFFFFFFFF;
    private static final int COLOR_ACTION = 0xFFFFE8A0;   // как действие в UseHint

    private NpcHint() {}

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER, NpcHint::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Interaction now = Interaction.current();
        if (minecraft.options.hideGui || now.kind() != Interaction.Kind.NPC) {
            return;
        }
        Font font = minecraft.font;
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2;
        graphics.drawCenteredString(font, now.entity().getDisplayName(), x, y - font.lineHeight, COLOR_NAME);
        graphics.drawCenteredString(font, Component.translatable("hud.halgventmod.npc.talk",
                ModKeys.PICKUP.getTranslatedKeyMessage()), x, y + 1, COLOR_ACTION);
    }
}
