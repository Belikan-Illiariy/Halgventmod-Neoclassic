package com.nrdhvnstd.halgventmod.combat.anim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Одна анимация Bedrock-формата (Blockbench, «format_version 1.8.0»): длина и ключевые кадры поворота и сдвига
 * по костям. Ключ кадра — либо вектор, либо {vector}, либо {pre, post} (pre — значение перед мигом, post — в миг),
 * сглаживание «catmullrom» или линейное. Разбор и выборка — по образцу Kingdom Come Combat (MIT,
 * GeckoLikeAnimationLibrary), код свой.
 * <p>Окно удара берётся из кости «hitbox»: пока её масштаб не ноль — клинок бьёт. По нему анимация сшивается с
 * фазами нашего боя: до окна — замах, в окне — удар, после — возврат (client/anim/CombatClips).
 */
public final class AnimClip {
    /** Ключевой кадр: время (с), значение, сглаженный ли путь к нему. */
    record Key(float time, Vector3f value, boolean smooth) {}

    /** Кость: кадры поворота (градусы) и сдвига (пиксели). */
    record Bone(List<Key> rotation, List<Key> position) {}

    public final String name;
    public final float length;
    public final boolean loop;
    private final Map<String, Bone> bones;
    /** Окно удара (с), или −1, если кости hitbox нет. */
    public final float hitStart;
    public final float hitEnd;
    // Окно удара мастер-удара (с), у которого его нет в файле ⚠️
    private static final float MASTER_HIT_START = 0.45F, MASTER_HIT_END = 0.7F;

    private AnimClip(String name, float length, boolean loop, Map<String, Bone> bones, float hitStart, float hitEnd) {
        this.name = name;
        this.length = length;
        this.loop = loop;
        this.bones = bones;
        this.hitStart = hitStart;
        this.hitEnd = hitEnd;
    }

    /** Та же анимация с другим окном удара (приём, у которого окно не размечено в файле). */
    public AnimClip withWindow(float start, float end) {
        return new AnimClip(name, length, loop, bones, start, end);
    }

    public boolean has(String bone) {
        return bones.containsKey(bone);
    }

    /** Поворот кости (градусы) в миг time или null, если кость им не двигает. */
    @Nullable
    public Vector3f rotation(String bone, float time) {
        Bone b = bones.get(bone);
        return b == null || b.rotation().isEmpty() ? null : sample(b.rotation(), time);
    }

    /** Сдвиг кости (пиксели) в миг time или null. */
    @Nullable
    public Vector3f position(String bone, float time) {
        Bone b = bones.get(bone);
        return b == null || b.position().isEmpty() ? null : sample(b.position(), time);
    }

    // ---------------------------------------------------------------- разбор

    static AnimClip parse(String name, JsonObject animation) {
        float length = animation.has("animation_length") ? animation.get("animation_length").getAsFloat() : 0;
        boolean loop = animation.has("loop") && animation.get("loop").isJsonPrimitive()
                && animation.get("loop").getAsJsonPrimitive().isBoolean() && animation.get("loop").getAsBoolean();
        Map<String, Bone> bones = new HashMap<>();
        if (animation.has("bones")) {
            for (Map.Entry<String, JsonElement> entry : animation.getAsJsonObject("bones").entrySet()) {
                JsonObject bone = entry.getValue().getAsJsonObject();
                bones.put(entry.getKey(), new Bone(
                        bone.has("rotation") ? keys(bone.get("rotation")) : List.of(),
                        bone.has("position") ? keys(bone.get("position")) : List.of()));
            }
        }
        // Окно удара: от последнего нулевого кадра масштаба hitbox перед ненулевым до первого нулевого после
        float start = -1;
        float end = -1;
        if (animation.has("bones") && animation.getAsJsonObject("bones").has("hitbox")) {
            JsonObject box = animation.getAsJsonObject("bones").getAsJsonObject("hitbox");
            if (box.has("scale")) {
                List<Key> scale = keys(box.get("scale"));
                float lastZero = 0;
                for (Key key : scale) {
                    boolean zero = key.value().lengthSquared() < 1.0E-6F;
                    if (start < 0) {
                        if (zero) {
                            lastZero = key.time();
                        } else {
                            start = lastZero;
                        }
                    } else if (zero) {
                        end = key.time();
                        break;
                    }
                }
                if (start >= 0 && end < 0) {
                    end = length;
                }
            }
        }
        // Мастер-удар KCC окна в hitbox не размечает (парный приём): удар — самая быстрая часть руки, ⚠️ на глаз
        if (start < 0 && name.startsWith("master_strike") && !name.contains("_hit")) {
            start = MASTER_HIT_START;
            end = MASTER_HIT_END;
        }
        return new AnimClip(name, length, loop, bones, start, end);
    }

    private static List<Key> keys(JsonElement channel) {
        List<Key> keys = new ArrayList<>();
        if (channel.isJsonArray()) {
            keys.add(new Key(0, vector(channel), false));
            return keys;
        }
        JsonObject object = channel.getAsJsonObject();
        if (object.has("vector") || object.has("post")) {
            keys.add(new Key(0, vector(channel), smooth(channel)));
            return keys;
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            float time;
            try {
                time = Float.parseFloat(entry.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            JsonElement value = entry.getValue();
            if (value.isJsonObject() && !value.getAsJsonObject().has("vector")
                    && (value.getAsJsonObject().has("pre") || value.getAsJsonObject().has("post"))) {
                JsonObject frame = value.getAsJsonObject();
                if (frame.has("pre")) {
                    keys.add(new Key(time == 0 ? 0 : time - 0.001F, vector(frame.get("pre")), false));
                }
                if (frame.has("post")) {
                    keys.add(new Key(time, vector(frame.get("post")), smooth(value)));
                }
                continue;
            }
            keys.add(new Key(time, vector(value), smooth(value)));
        }
        keys.sort((a, b) -> Float.compare(a.time(), b.time()));
        return keys;
    }

    private static boolean smooth(JsonElement value) {
        return value.isJsonObject() && value.getAsJsonObject().has("lerp_mode")
                && "catmullrom".equals(value.getAsJsonObject().get("lerp_mode").getAsString());
    }

    private static Vector3f vector(JsonElement element) {
        JsonArray array = null;
        if (element.isJsonArray()) {
            array = element.getAsJsonArray();
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("vector")) {
                array = object.getAsJsonArray("vector");
            } else if (object.has("post")) {
                return vector(object.get("post"));
            }
        }
        if (array == null || array.size() < 3) {
            return new Vector3f();
        }
        return new Vector3f(number(array.get(0)), number(array.get(1)), number(array.get(2)));
    }

    // Числа бывают и строками-выражениями Molang — их не считаем (0)
    private static float number(JsonElement element) {
        try {
            return element.getAsFloat();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- выборка

    private static Vector3f sample(List<Key> keys, float time) {
        if (time <= keys.getFirst().time()) {
            return new Vector3f(keys.getFirst().value());
        }
        for (int i = 1; i < keys.size(); i++) {
            Key previous = keys.get(i - 1);
            Key next = keys.get(i);
            if (time < next.time()) {
                float t = (time - previous.time()) / Math.max(1.0E-4F, next.time() - previous.time());
                if (next.smooth()) {
                    Vector3f before = keys.get(Math.max(0, i - 2)).value();
                    Vector3f after = keys.get(Math.min(keys.size() - 1, i + 1)).value();
                    return catmullRom(before, previous.value(), next.value(), after, t);
                }
                return new Vector3f(previous.value()).lerp(next.value(), t);
            }
        }
        return new Vector3f(keys.getLast().value());
    }

    private static Vector3f catmullRom(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        return new Vector3f(cr(p0.x, p1.x, p2.x, p3.x, t), cr(p0.y, p1.y, p2.y, p3.y, t), cr(p0.z, p1.z, p2.z, p3.z, t));
    }

    private static float cr(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5F * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
    }
}
