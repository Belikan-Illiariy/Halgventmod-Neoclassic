package com.nrdhvnstd.halgventmod.client.armor;

import java.util.EnumMap;
import java.util.Map;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.client.armor.geo.GeoModelLoader;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/** Подключение отрисовки брони и снаряжения: слои на игроке, рука от первого лица, скрытие второго слоя скина, кэш. */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class ArmorRenderEvents {
    private static final int RELEASE_CHECK_INTERVAL = 200; // проверка неиспользуемых склеек раз в 10 секунд

    // Слой брони для каждого вида скина (обычный и «тонкий»)
    private static final Map<PlayerSkin.Model, ArmorLayer> LAYERS = new EnumMap<>(PlayerSkin.Model.class);
    private static int ticks;

    private ArmorRenderEvents() {}

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        EntityRendererProvider.Context context = event.getContext();
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) {
                continue;
            }
            boolean slim = skin == PlayerSkin.Model.SLIM;
            ArmorLayer layer = new ArmorLayer(renderer,
                    bake(context, slim ? ModelLayers.PLAYER_SLIM_INNER_ARMOR : ModelLayers.PLAYER_INNER_ARMOR),
                    bake(context, slim ? ModelLayers.PLAYER_SLIM_OUTER_ARMOR : ModelLayers.PLAYER_OUTER_ARMOR),
                    context.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER).getChild("cloak"));
            renderer.addLayer(layer);
            renderer.addLayer(new GearLayer(renderer)); // снаряжение — поверх брони
            LAYERS.put(skin, layer);
        }
    }

    private static HumanoidModel<AbstractClientPlayer> bake(EntityRendererProvider.Context context, ModelLayerLocation location) {
        return new HumanoidModel<>(context.bakeLayer(location));
    }

    // Второй слой скина (шапка, куртка, рукава, штанины) прячется на закрытых бронёй частях тела,
    // иначе он просвечивает сквозь броню
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        ArmorInventory armor = ArmorLayer.armorOf(player);
        if (armor == null) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        ArmorCompositor.Composite outer = ArmorCompositor.get(armor, ArmorSlot.Plane.OUTER);
        if (outer != null) {
            model.hat.visible &= !outer.covers(ArmorCompositor.COVERS_HEAD);
            model.jacket.visible &= !outer.covers(ArmorCompositor.COVERS_BODY);
            boolean arms = outer.covers(ArmorCompositor.COVERS_ARMS);
            model.rightSleeve.visible &= !arms;
            model.leftSleeve.visible &= !arms;
        }
        ArmorCompositor.Composite legs = ArmorCompositor.get(armor, ArmorSlot.Plane.LEGS);
        if (legs != null && legs.covers(ArmorCompositor.COVERS_LEGS)) {
            model.rightPants.visible = false;
            model.leftPants.visible = false;
        }
    }

    // Рукава и перчатки на руках от первого лица; саму руку игра рисует следом
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        ArmorLayer layer = LAYERS.get(player.getSkin().model());
        if (layer != null) {
            layer.renderFirstPersonArm(event.getPoseStack(), event.getMultiBufferSource(),
                    event.getPackedLight(), player, event.getArm());
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++ticks % RELEASE_CHECK_INTERVAL == 0) {
            ArmorCompositor.releaseUnused();
        }
    }

    // F3+T: текстуры вещей могли измениться — склеиваем заново; модели деталей перечитываются
    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> ArmorCompositor.clear());
        event.registerReloadListener(new GeoModelLoader());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ArmorCompositor.clear();
    }
}
