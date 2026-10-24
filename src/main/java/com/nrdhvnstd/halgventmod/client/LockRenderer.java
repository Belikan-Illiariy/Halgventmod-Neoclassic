package com.nrdhvnstd.halgventmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.lock.LockData;
import com.nrdhvnstd.halgventmod.lock.LockSync;
import com.nrdhvnstd.halgventmod.lock.Locks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Замки на дверях и сундуках. Рисуются плоской картинкой поверх блока:
 * - навесной: запертый или с откинутой дужкой — видно издалека, запер ли хозяин;
 * - врезной: накладка со скважиной.
 * Только на закрытых дверях и сундуках: открытое всегда отперто, и замок на нём не нужен.
 * Картинки: assets/halgventmod/textures/block/lock/padlock_locked.png, padlock_unlocked.png, mortise.png.
 * Пока их нет — временные ванильные.
 */
@EventBusSubscriber(modid = HalgventMod.MODID, value = Dist.CLIENT)
public final class LockRenderer {
    private static final ResourceLocation PADLOCK_LOCKED = tex("textures/block/lock/padlock_locked.png");
    private static final ResourceLocation PADLOCK_UNLOCKED = tex("textures/block/lock/padlock_unlocked.png");
    private static final ResourceLocation MORTISE = tex("textures/block/lock/mortise.png");
    private static final ResourceLocation FALLBACK_PADLOCK = ResourceLocation.withDefaultNamespace("textures/item/iron_nugget.png");
    private static final ResourceLocation FALLBACK_MORTISE = ResourceLocation.withDefaultNamespace("textures/block/iron_block.png");

    // ===== Размеры и положение (в долях блока) =====
    private static final float PADLOCK_SIZE = 6 / 16F;
    private static final float MORTISE_SIZE = 4 / 16F;
    private static final float DOOR_HEIGHT = 15 / 16F;        // высота замка на нижней половине двери
    private static final float DOOR_FROM_EDGE = 2.5F / 16F;   // отступ от края со стороны ручки
    private static final float CHEST_HEIGHT = 6 / 16F;        // под защёлкой сундука
    private static final float SURFACE_GAP = 0.002F;          // чуть над поверхностью, чтобы не мерцало
    private static final double RENDER_DISTANCE = 48;

    private static final Map<ResourceLocation, ResourceLocation> RESOLVED = new HashMap<>();
    // Створка по модели: от и до вдоль оси двери. Ключ — модель: после перезагрузки ресурсов посчитается заново
    private static final Map<BakedModel, float[]> PLATES = new WeakHashMap<>();

    private LockRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || LockSync.clientAll().isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        double maxDistanceSqr = RENDER_DISTANCE * RENDER_DISTANCE;
        Frustum frustum = event.getFrustum();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (Map<BlockPos, LockData> chunk : LockSync.clientAll().values()) {
            for (Map.Entry<BlockPos, LockData> entry : chunk.entrySet()) {
                BlockPos pos = entry.getKey();
                if (pos.distToCenterSqr(camera) > maxDistanceSqr
                        || !frustum.isVisible(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1))) {
                    continue; // далеко или вне поля зрения
                }
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof DoorBlock) {
                    renderDoor(poseStack, buffers, level, pos, state, entry.getValue());
                } else if (state.getBlock() instanceof ChestBlock) {
                    renderChest(poseStack, buffers, level, pos, state, entry.getValue());
                }
            }
        }
        poseStack.popPose();
        buffers.endBatch();
    }

    // Закрытая дверь — пластина поперёк оси двери; где она и какой толщины — по модели (у ванильной 3/16 у края,
    // у дверей Conquest бывает толще или посередине)
    private static void renderDoor(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level, BlockPos pos,
                                   BlockState state, LockData lock) {
        if (state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || state.getValue(DoorBlock.OPEN)) {
            return;
        }
        Direction facing = state.getValue(DoorBlock.FACING);
        boolean leftHinge = state.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT;
        Direction handleSide = leftHinge ? facing.getClockWise() : facing.getCounterClockWise();

        // У двойной двери один замок посередине, на стыке створок; рисуем его от левой створки
        boolean doubleDoor = Locks.place(level, pos, state).size() > 1;
        if (doubleDoor && !leftHinge) {
            return;
        }
        float along = doubleDoor ? 0.5F : 0.5F - DOOR_FROM_EDGE;

        float x = 0.5F + handleSide.getStepX() * along;
        float z = 0.5F + handleSide.getStepZ() * along;
        Direction.Axis axis = facing.getAxis();
        float[] plate = plate(state, axis);
        Direction toEnd = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);

        ResourceLocation texture = texture(lock);
        float size = lock.mortise() ? MORTISE_SIZE : PADLOCK_SIZE;
        // Замок виден с обеих сторон двери
        quad(poseStack, buffers, level, pos, texture, size,
                axis == Direction.Axis.X ? plate[0] : x, DOOR_HEIGHT,
                axis == Direction.Axis.Z ? plate[0] : z, toEnd.getOpposite());
        quad(poseStack, buffers, level, pos, texture, size,
                axis == Direction.Axis.X ? plate[1] : x, DOOR_HEIGHT,
                axis == Direction.Axis.Z ? plate[1] : z, toEnd);
    }

    // Где створка вдоль оси: самые дальние вершины модели закрытой нижней половины. Без модели — как у ванильной
    private static float[] plate(BlockState state, Direction.Axis axis) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        float[] bounds = PLATES.computeIfAbsent(model, key -> bounds(key, state));
        if (bounds.length == 0) {
            boolean positive = state.getValue(DoorBlock.FACING).getAxisDirection() == Direction.AxisDirection.POSITIVE;
            return positive ? new float[] {0F, 3F / 16F} : new float[] {13F / 16F, 1F};
        }
        return axis == Direction.Axis.X ? new float[] {bounds[0], bounds[1]} : new float[] {bounds[2], bounds[3]};
    }

    // Пределы вершин модели по x и по z: {от x, до x, от z, до z}; пусто — у модели нет граней
    private static float[] bounds(BakedModel model, BlockState state) {
        float[] bounds = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
        RandomSource random = RandomSource.create(42L);
        Direction[] sides = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction side : sides) {
            for (BakedQuad quad : model.getQuads(state, side, random, ModelData.EMPTY, null)) {
                int[] vertices = quad.getVertices();
                int stride = vertices.length / 4; // x, y, z — первые три числа вершины
                for (int i = 0; i < 4; i++) {
                    float x = Float.intBitsToFloat(vertices[i * stride]);
                    float z = Float.intBitsToFloat(vertices[i * stride + 2]);
                    bounds[0] = Math.min(bounds[0], x);
                    bounds[1] = Math.max(bounds[1], x);
                    bounds[2] = Math.min(bounds[2], z);
                    bounds[3] = Math.max(bounds[3], z);
                }
            }
        }
        return bounds[0] > bounds[1] ? new float[0] : bounds;
    }

    // Передняя стенка сундука — на 1/16 внутрь от края блока; у двойного сундука замок посередине
    private static void renderChest(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level, BlockPos pos,
                                    BlockState state, LockData lock) {
        ChestType type = state.getValue(ChestBlock.TYPE);
        BlockPos partner = type == ChestType.SINGLE ? null : pos.relative(ChestBlock.getConnectedDirection(state));
        if (type == ChestType.LEFT && partner != null && LockSync.clientGet(partner) != null) {
            return; // у двойного сундука рисуем один замок — от правой половины
        }
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.getOpenNess(1.0F) > 0.01F) {
            return; // крышка открыта
        }
        Direction facing = state.getValue(ChestBlock.FACING);
        float x = 0.5F;
        float z = 0.5F;
        if (partner != null) {
            Direction toPartner = ChestBlock.getConnectedDirection(state);
            x += toPartner.getStepX() * 0.5F;
            z += toPartner.getStepZ() * 0.5F;
        }
        float face = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15F / 16F : 1F / 16F;
        if (facing.getAxis() == Direction.Axis.Z) {
            z = face;
        } else {
            x = face;
        }
        quad(poseStack, buffers, level, pos, texture(lock), lock.mortise() ? MORTISE_SIZE : PADLOCK_SIZE,
                x, CHEST_HEIGHT, z, facing);
    }

    // Квадрат с картинкой: центр (x, y, z) внутри блока, смотрит в сторону outward
    private static void quad(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level, BlockPos pos,
                             ResourceLocation texture, float size, float x, float y, float z, Direction outward) {
        int light = LevelRenderer.getLightColor(level, pos.relative(outward));
        float half = size / 2;
        poseStack.pushPose();
        poseStack.translate(pos.getX() + x + outward.getStepX() * SURFACE_GAP, pos.getY() + y,
                pos.getZ() + z + outward.getStepZ() * SURFACE_GAP);
        poseStack.mulPose(Axis.YP.rotationDegrees(-outward.toYRot()));
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        vertex(consumer, pose, -half, -half, 0, 1, light);
        vertex(consumer, pose, half, -half, 1, 1, light);
        vertex(consumer, pose, half, half, 1, 0, light);
        vertex(consumer, pose, -half, half, 0, 0, light);
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int light) {
        consumer.addVertex(pose, x, y, 0)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 0, 1);
    }

    private static ResourceLocation texture(LockData lock) {
        if (lock.mortise()) {
            return resolve(MORTISE, FALLBACK_MORTISE);
        }
        return resolve(lock.locked() ? PADLOCK_LOCKED : PADLOCK_UNLOCKED, FALLBACK_PADLOCK);
    }

    // Своя картинка, если она есть в ресурсах; иначе временная ванильная
    private static ResourceLocation resolve(ResourceLocation wanted, ResourceLocation fallback) {
        return RESOLVED.computeIfAbsent(wanted, key ->
                Minecraft.getInstance().getResourceManager().getResource(key).isPresent() ? key : fallback);
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, path);
    }
}