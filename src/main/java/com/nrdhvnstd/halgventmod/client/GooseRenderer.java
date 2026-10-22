package com.nrdhvnstd.halgventmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.farming.GooseEntity;
import com.nrdhvnstd.halgventmod.registry.ModEntities;

import net.minecraft.client.renderer.entity.ChickenRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Chicken;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Гусь — модель курицы с белым оперением и оранжевым клювом и лапами, крупнее. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public class GooseRenderer extends ChickenRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, "textures/entity/goose.png");
    private static final float SCALE = 1.3F;

    public GooseRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Chicken entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(Chicken entity, PoseStack pose, float partialTick) {
        pose.scale(SCALE, SCALE, SCALE);
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GOOSE.get(),
                context -> (EntityRenderer<GooseEntity>) (EntityRenderer<?>) new GooseRenderer(context));
    }
}
