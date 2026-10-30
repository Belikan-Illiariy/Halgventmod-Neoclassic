package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.network.CorpsePayload;
import com.nrdhvnstd.halgventmod.npc.HalgNpc;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/**
 * Тела убитых (игроков и НПС), только у клиента: при смерти (combat/Bodies) снимается копия сущности — облик, броня,
 * раны, поза, — падает на землю и лежит {@link #LIFE} тиков, потом уходит в землю. Настоящее тело пока мертво не рисуется:
 * его заменяет копия. Копии нет в мире: с ней нельзя столкнуться, её не бьют, и вошедший позже её не увидит. Оружие из рук
 * выпадает, поэтому у тела руки пустые.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class Corpses {
    // Меняйте здесь ⚠️
    private static final int LIFE = 1200;             // минута
    private static final int SINK = 40;               // последние тики уходит в землю
    private static final int MAX = 32;
    private static final double RENDER_RANGE = 96;
    private static final double FALL = 0.25;          // блоков за тик, если умер в воздухе
    private static final int DEATH_TICKS = 20;        // ванильное падение тела
    private static final int HIDE_GRACE = 10;         // здоровье приходит позже пакета: столько не рисуем настоящее тело и живым

    private static final class Corpse {
        final LivingEntity body;
        final ClientLevel level;
        final int realId;
        boolean headless;
        Vec3 position;
        Vec3 previous;
        int age;

        Corpse(LivingEntity body, ClientLevel level, int realId, Vec3 position) {
            this.body = body;
            this.level = level;
            this.realId = realId;
            this.position = position;
            this.previous = position;
        }
    }

    private static final List<Corpse> CORPSES = new ArrayList<>();
    private static final Set<LivingEntity> BODIES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<LivingEntity> HEADLESS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Integer, Long> REPLACED = new HashMap<>(); // настоящее тело (id) → когда умер
    private static int nextId;

    private Corpses() {}

    /** Пакет с сервера: сущность умерла. */
    public static void onDeath(CorpsePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(payload.entityId()) instanceof LivingEntity entity)) {
            return;
        }
        LivingEntity body = copy(level, entity);
        if (body == null) {
            return;
        }
        if (CORPSES.size() >= MAX) {
            CORPSES.remove(0);
        }
        CORPSES.add(new Corpse(body, level, entity.getId(), entity.position()));
        BODIES.add(body);
        REPLACED.put(entity.getId(), level.getGameTime());
    }

    private static LivingEntity copy(ClientLevel level, LivingEntity entity) {
        LivingEntity body = entity instanceof Player player ? new RemotePlayer(level, player.getGameProfile())
                : entity instanceof HalgNpc && entity.getType().create(level) instanceof LivingEntity created ? created : null;
        if (body == null) {
            return null;
        }
        body.setId(-(++nextId));
        List<SynchedEntityData.DataValue<?>> values = entity.getEntityData().getNonDefaultValues();
        if (values != null) {
            body.getEntityData().assignValues(values);
        }
        body.setPose(Pose.STANDING);  // повержен лежал ничком: ложится как все
        body.setSwimming(false);
        body.setRemainingFireTicks(0);
        body.setPos(entity.position());
        body.setYRot(entity.getYRot());
        body.setXRot(0);
        body.yBodyRot = body.yBodyRotO = entity.yBodyRot;
        body.yHeadRot = body.yHeadRotO = entity.yBodyRot;
        body.setOldPosAndRot();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                body.setItemSlot(slot, entity.getItemBySlot(slot).copy());
            }
        }
        ArmorInventory armor = entity.getExistingDataOrNull(ModAttachments.ARMOR);
        if (armor != null) {
            body.setData(ModAttachments.ARMOR, armor.copy());
        }
        body.setData(ModAttachments.WOUNDS, entity.getData(ModAttachments.WOUNDS));
        return body;
    }

    /** Голова отрублена (SeveredHeads): у последнего тела этой сущности её нет. */
    static void sever(int realId) {
        for (int i = CORPSES.size() - 1; i >= 0; i--) {
            if (CORPSES.get(i).realId == realId) {
                HEADLESS.add(CORPSES.get(i).body);
                return;
            }
        }
    }

    /** Это копия мёртвого тела. */
    public static boolean isBody(LivingEntity entity) {
        return BODIES.contains(entity);
    }

    static boolean isHeadless(LivingEntity entity) {
        return HEADLESS.contains(entity);
    }

    // Настоящее тело мёртвого не рисуем — вместо него копия; ожившего (игрок возродился той же сущностью) — снова рисуем
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        Long at = REPLACED.get(entity.getId());
        if (at == null || isBody(entity)) {
            return;
        }
        if (entity.isAlive() && entity.level().getGameTime() - at > HIDE_GRACE) {
            REPLACED.remove(entity.getId());
            return;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && isBody(entity)) {
            event.setCanRender(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            CORPSES.clear();
            REPLACED.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        CORPSES.removeIf(corpse -> corpse.level != level || ++corpse.age >= LIFE);
        REPLACED.keySet().removeIf(id -> level.getEntity(id) == null);
        for (Corpse corpse : CORPSES) {
            corpse.previous = corpse.position;
            corpse.body.deathTime = Math.min(DEATH_TICKS, corpse.body.deathTime + 1);
            Vec3 at = corpse.position;
            if (level.noCollision(new AABB(at.x - 0.3, at.y - FALL, at.z - 0.3, at.x + 0.3, at.y, at.z + 0.3))) {
                corpse.position = at.add(0, -FALL, 0);
                corpse.body.setPos(corpse.position);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || CORPSES.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        for (Corpse corpse : CORPSES) {
            Vec3 at = corpse.previous.lerp(corpse.position, partial);
            if (at.distanceToSqr(camera) > RENDER_RANGE * RENDER_RANGE) {
                continue;
            }
            double sink = Math.max(0, corpse.age + partial - (LIFE - SINK)) / SINK * 0.5;
            dispatcher.render(corpse.body, at.x - camera.x, at.y - sink - camera.y, at.z - camera.z, 0, partial, pose, buffers,
                    dispatcher.getPackedLightCoords(corpse.body, partial));
        }
        buffers.endBatch();
    }
}
