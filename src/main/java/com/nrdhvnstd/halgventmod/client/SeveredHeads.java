package com.nrdhvnstd.halgventmod.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.client.armor.ArmorLayer;
import com.nrdhvnstd.halgventmod.network.SeveredHeadPayload;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Отрубленные головы (combat/Severing), только у клиента: голова с кожей убитого летит, кувыркается, катится и через
 * {@link #LIFE} тиков исчезает; у тела головы нет (CombatAnimation, ArmorLayer), из шеи бьёт кровь. Шлем летит вместе с
 * головой.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class SeveredHeads {
    // Меняйте здесь ⚠️
    private static final int LIFE = 600;              // 30 секунд
    private static final int SHRINK = 20;             // последние тики голова уменьшается
    private static final int MAX = 16;
    private static final int SPURT_TICKS = 30;        // сколько бьёт кровь из шеи
    private static final double GRAVITY = 0.04, DRAG = 0.98, BOUNCE = 0.35, FRICTION = 0.6;
    private static final double SIZE = 0.2;           // половина ребра головы для столкновений
    private static final float SPIN = 22;             // градусов за тик
    private static final int RESPAWN_GRACE = 40;      // столько тиков тело без головы, даже если ещё числится живым

    private static final class Head {
        final ResourceLocation texture;
        final ArmorInventory armor; // шлем летит вместе с головой
        Vec3 position;
        Vec3 previous;
        Vec3 velocity;
        float pitch, pitchBefore, yaw, yawBefore, spinPitch, spinYaw;
        int age;

        Head(ResourceLocation texture, ArmorInventory armor, Vec3 position, Vec3 velocity, float spinPitch, float spinYaw) {
            this.texture = texture;
            this.armor = armor;
            this.position = position;
            this.previous = position;
            this.velocity = velocity;
            this.spinPitch = spinPitch;
            this.spinYaw = spinYaw;
        }
    }

    private static final List<Head> HEADS = new ArrayList<>();
    private static final Map<Integer, Long> HEADLESS = new HashMap<>();
    private static ModelPart headModel;
    private static ModelPart hatModel;

    private SeveredHeads() {}

    /** Пакет с сервера. */
    public static void onSevered(SeveredHeadPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !(level.getEntity(payload.entityId()) instanceof LivingEntity entity)) {
            return;
        }
        HEADLESS.put(entity.getId(), level.getGameTime());
        Corpses.sever(entity.getId());
        if (!(minecraft.getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?> renderer)) {
            return;
        }
        ResourceLocation texture = texture(renderer, entity);
        if (HEADS.size() >= MAX) {
            HEADS.remove(0);
        }
        var random = entity.getRandom();
        ArmorInventory armor = entity.getExistingDataOrNull(ModAttachments.ARMOR);
        HEADS.add(new Head(texture, armor != null ? armor.copy() : null, neck(entity), new Vec3(payload.vx(), payload.vy(), payload.vz()),
                SPIN * (random.nextBoolean() ? 1 : -1), SPIN * 0.5F * (random.nextFloat() - 0.5F)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity> ResourceLocation texture(LivingEntityRenderer<?, ?> renderer, LivingEntity entity) {
        return ((LivingEntityRenderer<T, ?>) renderer).getTextureLocation((T) entity);
    }

    /** У тела этой сущности отрублена голова. Игрок после возрождения — та же сущность: живому голова возвращается. */
    public static boolean isSevered(LivingEntity entity) {
        if (Corpses.isHeadless(entity)) {
            return true;
        }
        Long at = HEADLESS.get(entity.getId());
        if (at == null) {
            return false;
        }
        if (entity.isAlive() && entity.level().getGameTime() - at > RESPAWN_GRACE) {
            HEADLESS.remove(entity.getId());
            return false;
        }
        return true;
    }

    // Откуда бьёт кровь и летит голова: у лежащего шея впереди
    private static Vec3 neck(LivingEntity entity) {
        if (entity.getBbHeight() >= 1) {
            return entity.position().add(0, entity.getEyeHeight() - 0.1, 0);
        }
        float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
        return entity.position().add(-Mth.sin(yaw) * 0.6, entity.getBbHeight() * 0.5, Mth.cos(yaw) * 0.6);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            HEADS.clear();
            HEADLESS.clear();
            return;
        }
        if (minecraft.isPaused()) {
            return;
        }
        BlockParticleOption blood = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.NETHER_WART_BLOCK.defaultBlockState());
        for (Iterator<Map.Entry<Integer, Long>> it = HEADLESS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> entry = it.next();
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity entity)) {
                it.remove(); // тело убрано из мира
                continue;
            }
            if (!isSevered(entity)) {
                it = HEADLESS.entrySet().iterator(); // запись удалена внутри проверки
                continue;
            }
            if (level.getGameTime() - entry.getValue() < SPURT_TICKS) {
                Vec3 neck = neck(entity);
                for (int i = 0; i < 3; i++) {
                    level.addParticle(blood, neck.x + (level.random.nextDouble() - 0.5) * 0.2, neck.y + level.random.nextDouble() * 0.2,
                            neck.z + (level.random.nextDouble() - 0.5) * 0.2, 0, 0, 0);
                }
            }
        }
        HEADS.removeIf(head -> ++head.age >= LIFE);
        for (Head head : HEADS) {
            move(level, head);
            if (head.velocity.lengthSqr() > 0.01 && level.random.nextInt(2) == 0) {
                level.addParticle(blood, head.position.x, head.position.y, head.position.z, 0, 0, 0);
            }
        }
    }

    private static void move(ClientLevel level, Head head) {
        head.previous = head.position;
        head.pitchBefore = head.pitch;
        head.yawBefore = head.yaw;
        Vec3 velocity = head.velocity.add(0, -GRAVITY, 0).scale(DRAG);
        double x = head.position.x, y = head.position.y, z = head.position.z;
        double vx = velocity.x, vy = velocity.y, vz = velocity.z;
        boolean grounded = false;
        if (blocked(level, x, y + vy, z)) {
            grounded = vy < 0;
            vy = Math.abs(vy) < GRAVITY * 3 ? 0 : -vy * BOUNCE;
            vx *= FRICTION;
            vz *= FRICTION;
            head.spinPitch *= 0.6F;
            head.spinYaw *= 0.6F;
        } else {
            y += vy;
        }
        if (blocked(level, x + vx, y, z)) {
            vx = -vx * BOUNCE;
        } else {
            x += vx;
        }
        if (blocked(level, x, y, z + vz)) {
            vz = -vz * BOUNCE;
        } else {
            z += vz;
        }
        head.position = new Vec3(x, y, z);
        head.velocity = new Vec3(vx, vy, vz);
        if (grounded && Math.abs(head.spinPitch) < 2) {
            head.spinPitch = 0;
            head.spinYaw = 0;
        }
        head.pitch += head.spinPitch;
        head.yaw += head.spinYaw;
    }

    private static boolean blocked(ClientLevel level, double x, double y, double z) {
        return !level.noCollision(new AABB(x - SIZE, y - SIZE, z - SIZE, x + SIZE, y + SIZE, z + SIZE));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || HEADS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (headModel == null) {
            ModelPart root = minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER);
            headModel = root.getChild("head");
            hatModel = root.getChild("hat");
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        for (Head head : HEADS) {
            Vec3 at = head.previous.lerp(head.position, partial);
            int light = LevelRenderer.getLightColor(minecraft.level, BlockPos.containing(at));
            float scale = Mth.clamp((LIFE - head.age - partial) / SHRINK, 0, 1);
            pose.pushPose();
            pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial, head.yawBefore, head.yaw)));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, head.pitchBefore, head.pitch)));
            pose.scale(-0.9375F * scale, -0.9375F * scale, 0.9375F * scale);
            pose.translate(0, 0.25, 0); // куб головы растёт от шеи вверх — вращаем вокруг его середины
            headModel.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(head.texture)), light, OverlayTexture.NO_OVERLAY);
            hatModel.render(pose, buffers.getBuffer(RenderType.entityTranslucent(head.texture)), light, OverlayTexture.NO_OVERLAY);
            if (head.armor != null) {
                ArmorLayer.renderHelmet(pose, buffers, light, head.armor);
            }
            pose.popPose();
        }
        buffers.endBatch();
    }
}
