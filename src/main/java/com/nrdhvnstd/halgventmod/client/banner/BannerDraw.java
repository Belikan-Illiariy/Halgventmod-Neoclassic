package com.nrdhvnstd.halgventmod.client.banner;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.Lighting;
import com.nrdhvnstd.halgventmod.banner.BannerEntry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;

/**
 * Лицевая сторона знамени в интерфейсе настоящими текстурами игры, как на ткацком станке (LoomScreen.renderBg):
 * деталь flag модели баннера, основа и слои узоров (BannerRenderer.renderPatterns).
 * Флаг — 20 × 40 текселей, поэтому ширина кратна 20, высота — вдвое больше ширины.
 */
public final class BannerDraw {
    /** Ширина флага модели баннера в текселях. */
    public static final int FLAG_TEXELS = 20;
    private static final float MODEL_UNITS = 16.0F; // текселей модели в блоке

    @Nullable
    private static ModelPart flag;

    private BannerDraw() {}

    /** Знамя шириной width и высотой 2 × width, левый верхний угол в (x; y). Нет записи — ничего. */
    public static void draw(GuiGraphics graphics, @Nullable BannerEntry entry, int x, int y, int width) {
        if (entry == null) {
            return;
        }
        if (flag == null) {
            flag = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.BANNER).getChild("flag");
        }
        float scale = MODEL_UNITS * width / FLAG_TEXELS;
        graphics.flush();
        Lighting.setupForFlatItems();
        graphics.pose().pushPose();
        graphics.pose().translate(x + width / 2.0F, y, 0.0F); // флаг по x от -10 до 10, по y от 0 до 40
        graphics.pose().scale(scale, scale, -scale);
        flag.xRot = 0.0F;
        flag.y = 0.0F;
        BannerRenderer.renderPatterns(graphics.pose(), graphics.bufferSource(), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, flag, ModelBakery.BANNER_BASE, true, entry.base(), entry.patterns());
        graphics.pose().popPose();
        graphics.flush();
        Lighting.setupFor3DItems();
    }
}
