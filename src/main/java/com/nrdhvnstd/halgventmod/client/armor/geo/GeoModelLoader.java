package com.nrdhvnstd.halgventmod.client.armor.geo;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Загрузка моделей выступающих деталей: assets/<мод>/models/armor/**.geo.json
 * (папок может стать больше — FOLDERS).
 * Формат — Blockbench «Bedrock Entity» (format_version 1.12.0 и новее).
 *
 * В модели верхние кости с именами частей тела (head, body, right_arm, left_arm, right_leg, left_leg)
 * — точки крепления; всё внутри них — детали. Кости с именем, начинающимся с «_», — подсказки
 * в шаблоне (фигура игрока, плоскость брони) и не загружаются.
 *
 * Перевод координат Bedrock → Java: X и Z те же, Y переворачивается (в Java ось Y вниз),
 * углы те же, отсчёт — от точки вращения родителя.
 */
public final class GeoModelLoader extends SimplePreparableReloadListener<Map<ResourceLocation, GeoModel>> {
    private static final String[] FOLDERS = {"models/armor"}; // детали брони
    private static final String EXTENSION = ".geo.json";
    private static final String HIDDEN_PREFIX = "_";
    private static final int DEFAULT_TEXTURE_SIZE = 64;
    private static final String ROTATED_CUBE = ""; // имя кости повёрнутого кубика: своего имени в файле у него нет

    private static Map<ResourceLocation, GeoModel> models = Map.of();
    private static final Set<ResourceLocation> MISSING_REPORTED = new HashSet<>();

    /** Модель по пути или null — без предупреждения (у снаряжения модели необязательны). */
    @Nullable
    public static GeoModel find(ResourceLocation location) {
        return models.get(location);
    }

    /** Модель по пути (models/armor/<папка слота>/<имя>.geo.json) или null, если такой нет. */
    @Nullable
    public static GeoModel get(ResourceLocation location) {
        GeoModel model = models.get(location);
        if (model == null && MISSING_REPORTED.add(location)) {
            HalgventMod.LOGGER.warn("Нет модели детали брони {}", location);
        }
        return model;
    }

    @Override
    protected Map<ResourceLocation, GeoModel> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, GeoModel> loaded = new HashMap<>();
        Map<ResourceLocation, Resource> files = new HashMap<>();
        for (String folder : FOLDERS) {
            files.putAll(resourceManager.listResources(folder, location -> location.getPath().endsWith(EXTENSION)));
        }
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            try (Reader reader = file.getValue().openAsReader()) {
                loaded.put(file.getKey(), parse(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception e) {
                HalgventMod.LOGGER.error("Не удалось загрузить модель детали брони {}", file.getKey(), e);
            }
        }
        return loaded;
    }

    @Override
    protected void apply(Map<ResourceLocation, GeoModel> loaded, ResourceManager resourceManager, ProfilerFiller profiler) {
        models = loaded;
        MISSING_REPORTED.clear();
    }

    // ===== Разбор файла =====

    private static GeoModel parse(JsonObject root) {
        JsonObject geometry = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        float textureWidth = description.has("texture_width") ? description.get("texture_width").getAsFloat() : DEFAULT_TEXTURE_SIZE;
        float textureHeight = description.has("texture_height") ? description.get("texture_height").getAsFloat() : DEFAULT_TEXTURE_SIZE;

        // Кости по имени и дети каждой кости
        Map<String, JsonObject> byName = new HashMap<>();
        Map<String, List<JsonObject>> children = new HashMap<>();
        List<JsonObject> topLevel = new ArrayList<>();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            byName.put(bone.get("name").getAsString(), bone);
        }
        for (JsonObject bone : byName.values()) {
            if (bone.has("parent")) {
                children.computeIfAbsent(bone.get("parent").getAsString(), key -> new ArrayList<>()).add(bone);
            } else {
                topLevel.add(bone);
            }
        }

        UvSize uvSize = new UvSize(textureWidth, textureHeight);
        Map<GeoModel.Attachment, List<GeoModel.Bone>> result = new EnumMap<>(GeoModel.Attachment.class);
        for (JsonObject bone : topLevel) {
            String name = bone.get("name").getAsString();
            if (name.startsWith(HIDDEN_PREFIX)) {
                continue;
            }
            GeoModel.Attachment attachment = attachment(name);
            if (attachment == null) {
                HalgventMod.LOGGER.warn("Кость «{}» не прикреплена к части тела и пропущена", name);
                continue;
            }
            // Кость крепления — это сама часть тела: её кубики и дети считаются от стандартной точки вращения
            List<GeoModel.Bone> bones = result.computeIfAbsent(attachment, key -> new ArrayList<>());
            List<GeoModel.Quad> ownQuads = new ArrayList<>();
            List<GeoModel.Bone> rotatedCubes = new ArrayList<>();
            cubes(bone, attachment.pivot, uvSize, ownQuads, rotatedCubes);
            if (!ownQuads.isEmpty() || !rotatedCubes.isEmpty()) {
                bones.add(new GeoModel.Bone(name, new Vector3f(), new Vector3f(), ownQuads, rotatedCubes));
            }
            for (JsonObject child : children.getOrDefault(name, List.of())) {
                bones.add(bone(child, attachment.pivot, children, uvSize));
            }
        }
        return new GeoModel(result);
    }

    @Nullable
    private static GeoModel.Attachment attachment(String boneName) {
        String normalized = boneName.toLowerCase(Locale.ROOT).replace("_", "");
        for (GeoModel.Attachment attachment : GeoModel.Attachment.values()) {
            if (attachment.boneName.equals(normalized)) {
                return attachment;
            }
        }
        return null;
    }

    private static GeoModel.Bone bone(JsonObject json, Vector3f parentPivot, Map<String, List<JsonObject>> children, UvSize uvSize) {
        Vector3f pivot = vec(json, "pivot");
        List<GeoModel.Quad> quads = new ArrayList<>();
        List<GeoModel.Bone> nested = new ArrayList<>();
        cubes(json, pivot, uvSize, quads, nested);
        for (JsonObject child : children.getOrDefault(json.get("name").getAsString(), List.of())) {
            nested.add(bone(child, pivot, children, uvSize));
        }
        return new GeoModel.Bone(json.get("name").getAsString(), offset(pivot, parentPivot), radians(vec(json, "rotation")),
                quads, nested);
    }

    // Кубики кости: без поворота — грани прямо в кости, с поворотом — отдельной костью вокруг своей точки
    private static void cubes(JsonObject bone, Vector3f bonePivot, UvSize uvSize,
                              List<GeoModel.Quad> quads, List<GeoModel.Bone> rotated) {
        if (!bone.has("cubes")) {
            return;
        }
        boolean boneMirror = bone.has("mirror") && bone.get("mirror").getAsBoolean();
        float boneInflate = bone.has("inflate") ? bone.get("inflate").getAsFloat() : 0;
        for (JsonElement element : bone.getAsJsonArray("cubes")) {
            JsonObject cube = element.getAsJsonObject();
            Vector3f rotation = vec(cube, "rotation");
            if (rotation.lengthSquared() == 0) {
                quads.addAll(cube(cube, bonePivot, boneMirror, boneInflate, uvSize));
            } else {
                Vector3f cubePivot = vec(cube, "pivot");
                rotated.add(new GeoModel.Bone(ROTATED_CUBE, offset(cubePivot, bonePivot), radians(rotation),
                        cube(cube, cubePivot, boneMirror, boneInflate, uvSize), List.of()));
            }
        }
    }

    // Сдвиг точки вращения от родителя: Y в Java направлен вниз
    private static Vector3f offset(Vector3f pivot, Vector3f parentPivot) {
        return new Vector3f(pivot.x - parentPivot.x, parentPivot.y - pivot.y, pivot.z - parentPivot.z);
    }

    private static Vector3f radians(Vector3f degrees) {
        return new Vector3f((float) Math.toRadians(degrees.x), (float) Math.toRadians(degrees.y), (float) Math.toRadians(degrees.z));
    }

    // ===== Кубик → 6 граней =====

    private record UvSize(float width, float height) {}

    /** Прямоугольник развёртки грани: u1,v1 — u2,v2 в пикселях текстуры. */
    private record Rect(float u1, float v1, float u2, float v2) {}

    private static List<GeoModel.Quad> cube(JsonObject cube, Vector3f pivot, boolean boneMirror, float boneInflate, UvSize uvSize) {
        Vector3f origin = vec(cube, "origin");
        Vector3f size = vec(cube, "size");
        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : boneInflate;
        boolean mirror = cube.has("mirror") ? cube.get("mirror").getAsBoolean() : boneMirror;

        // Угол кубика в Java: отсчёт от точки вращения, Y вниз (верх кубика Bedrock — минимальный Y в Java)
        float x0 = origin.x - pivot.x - inflate;
        float y0 = pivot.y - origin.y - size.y - inflate;
        float z0 = origin.z - pivot.z - inflate;
        float x1 = origin.x - pivot.x + size.x + inflate;
        float y1 = pivot.y - origin.y + inflate;
        float z1 = origin.z - pivot.z + size.z + inflate;

        Map<Direction, Rect> rects;
        JsonElement uv = cube.get("uv");
        if (uv != null && uv.isJsonObject()) {
            rects = faceUv(uv.getAsJsonObject());
            mirror = false; // зеркало действует только на развёртку коробкой
        } else {
            JsonArray box = uv != null ? uv.getAsJsonArray() : new JsonArray();
            float u = box.isEmpty() ? 0 : box.get(0).getAsFloat();
            float v = box.isEmpty() ? 0 : box.get(1).getAsFloat();
            rects = boxUv(u, v, size);
        }
        if (mirror) {
            float swap = x1;
            x1 = x0;
            x0 = swap;
        }

        // Вершины и порядок граней — как в ванильном ModelPart.Cube
        Vector3f v7 = new Vector3f(x0, y0, z0);
        Vector3f v0 = new Vector3f(x1, y0, z0);
        Vector3f v1 = new Vector3f(x1, y1, z0);
        Vector3f v2 = new Vector3f(x0, y1, z0);
        Vector3f v3 = new Vector3f(x0, y0, z1);
        Vector3f v4 = new Vector3f(x1, y0, z1);
        Vector3f v5 = new Vector3f(x1, y1, z1);
        Vector3f v6 = new Vector3f(x0, y1, z1);

        List<GeoModel.Quad> quads = new ArrayList<>(6);
        face(quads, rects.get(Direction.DOWN), Direction.DOWN, mirror, uvSize, v4, v3, v7, v0);
        face(quads, rects.get(Direction.UP), Direction.UP, mirror, uvSize, v1, v2, v6, v5);
        face(quads, rects.get(Direction.WEST), Direction.WEST, mirror, uvSize, v7, v3, v6, v2);
        face(quads, rects.get(Direction.NORTH), Direction.NORTH, mirror, uvSize, v0, v7, v2, v1);
        face(quads, rects.get(Direction.EAST), Direction.EAST, mirror, uvSize, v4, v0, v1, v5);
        face(quads, rects.get(Direction.SOUTH), Direction.SOUTH, mirror, uvSize, v3, v4, v5, v6);
        return quads;
    }

    // Развёртка коробкой — формулы ванильного ModelPart.Cube
    private static Map<Direction, Rect> boxUv(float u, float v, Vector3f size) {
        float dx = size.x;
        float dy = size.y;
        float dz = size.z;
        Map<Direction, Rect> rects = new EnumMap<>(Direction.class);
        rects.put(Direction.DOWN, new Rect(u + dz, v, u + dz + dx, v + dz));
        rects.put(Direction.UP, new Rect(u + dz + dx, v + dz, u + dz + dx + dx, v));
        rects.put(Direction.WEST, new Rect(u, v + dz, u + dz, v + dz + dy));
        rects.put(Direction.NORTH, new Rect(u + dz, v + dz, u + dz + dx, v + dz + dy));
        rects.put(Direction.EAST, new Rect(u + dz + dx, v + dz, u + dz + dx + dz, v + dz + dy));
        rects.put(Direction.SOUTH, new Rect(u + dz + dx + dz, v + dz, u + dz + dx + dz + dx, v + dz + dy));
        return rects;
    }

    // Развёртка по граням. Имена граней Bedrock → грани Java-модели:
    // up — верх (в Java минимальный Y, DOWN), down — низ (UP), east и west меняются местами,
    // как и в развёртке коробкой, где первая полоса — east в Blockbench и WEST в Java
    private static Map<Direction, Rect> faceUv(JsonObject faces) {
        Map<Direction, Rect> rects = new EnumMap<>(Direction.class);
        putFace(rects, faces, "up", Direction.DOWN);
        putFace(rects, faces, "down", Direction.UP);
        putFace(rects, faces, "east", Direction.WEST);
        putFace(rects, faces, "west", Direction.EAST);
        putFace(rects, faces, "north", Direction.NORTH);
        putFace(rects, faces, "south", Direction.SOUTH);
        return rects;
    }

    private static void putFace(Map<Direction, Rect> rects, JsonObject faces, String name, Direction direction) {
        if (!faces.has(name)) {
            return; // грань без развёртки не рисуется
        }
        JsonObject face = faces.getAsJsonObject(name);
        JsonArray uv = face.getAsJsonArray("uv");
        JsonArray size = face.getAsJsonArray("uv_size");
        float u = uv.get(0).getAsFloat();
        float v = uv.get(1).getAsFloat();
        rects.put(direction, new Rect(u, v, u + size.get(0).getAsFloat(), v + size.get(1).getAsFloat()));
    }

    // Грань как ванильный ModelPart.Polygon: развёртка по углам, при зеркале — обратный порядок вершин
    private static void face(List<GeoModel.Quad> quads, @Nullable Rect rect, Direction direction, boolean mirror,
                             UvSize uvSize, Vector3f a, Vector3f b, Vector3f c, Vector3f d) {
        if (rect == null) {
            return;
        }
        Vector3f[] positions = {a, b, c, d};
        float[] u = {rect.u2, rect.u1, rect.u1, rect.u2};
        float[] v = {rect.v1, rect.v1, rect.v2, rect.v2};
        for (int i = 0; i < 4; i++) {
            u[i] /= uvSize.width;
            v[i] /= uvSize.height;
        }
        Vector3f normal = new Vector3f(direction.step());
        if (mirror) {
            reverse(positions);
            reverse(u);
            reverse(v);
            normal.mul(-1.0F, 1.0F, 1.0F);
        }
        quads.add(new GeoModel.Quad(positions, u, v, normal));
    }

    private static void reverse(Vector3f[] array) {
        Vector3f t = array[0]; array[0] = array[3]; array[3] = t;
        t = array[1]; array[1] = array[2]; array[2] = t;
    }

    private static void reverse(float[] array) {
        float t = array[0]; array[0] = array[3]; array[3] = t;
        t = array[1]; array[1] = array[2]; array[2] = t;
    }

    private static Vector3f vec(JsonObject json, String key) {
        if (!json.has(key)) {
            return new Vector3f();
        }
        JsonArray array = json.getAsJsonArray(key);
        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }
}
