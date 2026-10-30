package com.nrdhvnstd.halgventmod.client.npc;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.client.armor.NpcArmor;
import com.nrdhvnstd.halgventmod.client.character.AppearanceSkins;
import com.nrdhvnstd.halgventmod.npc.HalgNpc;
import com.nrdhvnstd.halgventmod.registry.ModEntities;

import com.nrdhvnstd.halgventmod.client.WoundLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * НПС с моделью игрока (толстые руки). Скин: заданный администрацией (textures/entity/npc/&lt;путь&gt;.png), иначе
 * собранный из облика. Предмет в руке и голова — слои HumanoidMobRenderer; броня НПС появится вместе с их экипировкой.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public class NpcRenderer extends HumanoidMobRenderer<HalgNpc, PlayerModel<HalgNpc>> {
    private static final float SHADOW_RADIUS = 0.5F;

    // «пространство:путь» → путь к текстуре; разбор строки не нужен каждый кадр
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    private final PlayerModel<HalgNpc> wideModel;
    private final PlayerModel<HalgNpc> slimModel;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), SHADOW_RADIUS);
        wideModel = model;
        slimModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new WoundLayer<>(this)); // раны на коже — под бронёй
        NpcArmor.addLayers(this, context); // броня — как у игрока
    }

    @Override
    public void render(HalgNpc npc, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
        model = npc.isSlim() ? slimModel : wideModel; // руки по НПС: слои берут модель отсюда же
        NpcArmor.applyCoverage(model, npc);
        super.render(npc, yaw, partialTick, pose, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HalgNpc npc) {
        String skin = npc.getSkin();
        if (!skin.isEmpty()) {
            ResourceLocation texture = TEXTURES.computeIfAbsent(skin, NpcRenderer::texture);
            if (texture != null) {
                return texture;
            }
        }
        PlayerSkin byAppearance = AppearanceSkins.skinOf(npc.getAppearance());
        return byAppearance != null ? byAppearance.texture() : ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    }

    private static ResourceLocation texture(String skin) {
        ResourceLocation id = ResourceLocation.tryParse(skin);
        return id == null ? null : id.withPath(path -> "textures/entity/npc/" + path + ".png");
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.NPC.get(), NpcRenderer::new);
        event.registerEntityRenderer(ModEntities.SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }
}
