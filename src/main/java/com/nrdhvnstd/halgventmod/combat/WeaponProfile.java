package com.nrdhvnstd.halgventmod.combat;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nrdhvnstd.halgventmod.quality.DamageKind;

import net.minecraft.util.GsonHelper;

/**
 * Профиль класса оружия (ТЗ боя, 11.1) — из data/halgventmod/stats/weapons/&lt;класс&gt;.json, правка по /reload.
 * Урон — на шкалу здоровья 0–100, тайминги — в тиках. Удар может взять всё у другого через "as" и переопределить
 * часть полей (особый удар и удар с разбега). "alt_grip" — второй набор ударов для хвата X, тем же видом.
 */
public record WeaponProfile(WeaponClass weaponClass, String segment, boolean twoHanded, float bladeLength,
                            float accuracy, float penetration, int blockRaise, float blockCost,
                            Map<AttackKind, Attack> attacks, Map<AttackKind, Attack> altGrip) {

    /**
     * Один удар. shape — траектория (взмах, сверху, укол): у особого и удара с разбега — того удара, на котором они
     * основаны. reachBonus — к длине клинка, accuracyBonus — к точности; blockCost — множитель цены блока (булава);
     * effect — особое действие (hook — подсечка, guard_break — как пинок по блоку), пусто — нет.
     */
    public record Attack(AttackKind shape, DamageKind type, float damage, int windup, int release, int recovery,
                         float stamina, float reachBonus, float accuracyBonus, float blockCost, String effect) {

        /** Сумма фаз в тиках — для отладки. */
        public int total() {
            return windup + release + recovery;
        }

        /** Копия с полями из JSON поверх (поля, которых в JSON нет, — как были). */
        Attack with(JsonObject json, AttackKind shape) {
            return new Attack(shape,
                    json.has("type") ? damageKind(json, type) : type,
                    GsonHelper.getAsFloat(json, "damage", damage),
                    GsonHelper.getAsInt(json, "windup", windup),
                    GsonHelper.getAsInt(json, "release", release),
                    GsonHelper.getAsInt(json, "recovery", recovery),
                    GsonHelper.getAsFloat(json, "stamina", stamina),
                    GsonHelper.getAsFloat(json, "reach_bonus", reachBonus),
                    GsonHelper.getAsFloat(json, "accuracy_bonus", accuracyBonus),
                    GsonHelper.getAsFloat(json, "block_cost", blockCost),
                    GsonHelper.getAsString(json, "effect", effect));
        }

        /** Урон и замах ×множители — для переопределений у предмета. */
        Attack scaled(float damageFactor, float windupFactor) {
            return new Attack(shape, type, damage * damageFactor, Math.max(1, Math.round(windup * windupFactor)),
                    release, recovery, stamina, reachBonus, accuracyBonus, blockCost, effect);
        }
    }

    private static final Attack EMPTY = new Attack(AttackKind.SLASH, DamageKind.BLUNT, 0, 1, 1, 1, 0, 0, 0, 1, "");

    /** Удар этого вида в обычном или втором хвате, или null — у оружия такого нет. */
    @Nullable
    public Attack attack(AttackKind kind, boolean alt) {
        if (alt && !altGrip.isEmpty()) {
            return altGrip.get(kind);
        }
        return attacks.get(kind);
    }

    // ---------------------------------------------------------------- чтение JSON

    /** Профиль класса из JSON. Ошибка — IllegalArgumentException с понятным текстом. */
    public static WeaponProfile parse(JsonObject json) {
        String classId = GsonHelper.getAsString(json, "class");
        WeaponClass weaponClass = WeaponClass.byId(classId);
        if (weaponClass == null) {
            throw new IllegalArgumentException("нет класса оружия «" + classId + "»");
        }
        JsonObject block = GsonHelper.getAsJsonObject(json, "block", new JsonObject());
        Map<AttackKind, Attack> attacks = attacks(GsonHelper.getAsJsonObject(json, "attacks", new JsonObject()));
        Map<AttackKind, Attack> alt = Map.of();
        JsonElement altJson = json.get("alt_grip");
        if (altJson != null && altJson.isJsonObject()) {
            // Второй хват — поверх основного: что не сказано, то как в основном
            alt = override(attacks, GsonHelper.getAsJsonObject(altJson.getAsJsonObject(), "attacks", new JsonObject()));
        }
        return new WeaponProfile(weaponClass,
                GsonHelper.getAsString(json, "segment", classId),
                GsonHelper.getAsBoolean(json, "two_handed", false),
                GsonHelper.getAsFloat(json, "blade_length", 1.0F),
                GsonHelper.getAsFloat(json, "accuracy", 0.05F),
                GsonHelper.getAsFloat(json, "penetration", 1.0F),
                GsonHelper.getAsInt(block, "raise", 3),
                GsonHelper.getAsFloat(block, "cost", 1.0F),
                attacks, alt);
    }

    /** Переопределения у предмета (ТЗ боя, 11.3): damage и windup — множители, остальное — значения. */
    public WeaponProfile forItem(JsonObject json) {
        float damage = GsonHelper.getAsFloat(json, "damage", 1.0F);
        float windup = GsonHelper.getAsFloat(json, "windup", 1.0F);
        Map<AttackKind, Attack> main = override(attacks, GsonHelper.getAsJsonObject(json, "attacks", new JsonObject()));
        Map<AttackKind, Attack> alt = altGrip.isEmpty() ? Map.of()
                : override(altGrip, GsonHelper.getAsJsonObject(json, "alt_attacks", new JsonObject()));
        return new WeaponProfile(weaponClass, segment, twoHanded,
                GsonHelper.getAsFloat(json, "blade_length", bladeLength),
                GsonHelper.getAsFloat(json, "accuracy", accuracy),
                GsonHelper.getAsFloat(json, "penetration", penetration),
                blockRaise, blockCost, scale(main, damage, windup), scale(alt, damage, windup));
    }

    private static Map<AttackKind, Attack> attacks(JsonObject json) {
        Map<AttackKind, Attack> result = new EnumMap<>(AttackKind.class);
        // Сначала три основных, потом те, что ссылаются на них через "as"
        for (boolean derived : new boolean[] {false, true}) {
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                AttackKind kind = kind(entry.getKey());
                JsonObject attack = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey());
                if (attack.has("as") != derived) {
                    continue;
                }
                if (derived) {
                    AttackKind base = kind(GsonHelper.getAsString(attack, "as"));
                    Attack from = result.get(base);
                    if (from == null) {
                        throw new IllegalArgumentException(entry.getKey() + ": нет удара «" + base.id() + "» для \"as\"");
                    }
                    result.put(kind, from.with(attack, from.shape()));
                } else {
                    AttackKind shape = attack.has("shape") ? kind(GsonHelper.getAsString(attack, "shape")) : kind;
                    result.put(kind, EMPTY.with(attack, shape));
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Map<AttackKind, Attack> override(Map<AttackKind, Attack> base, JsonObject json) {
        Map<AttackKind, Attack> result = new EnumMap<>(AttackKind.class);
        result.putAll(base);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            AttackKind kind = kind(entry.getKey());
            JsonObject attack = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey());
            Attack from = base.get(kind);
            if (attack.has("as")) {
                from = base.get(kind(GsonHelper.getAsString(attack, "as")));
            }
            if (attack.size() == 1 && attack.has("remove")) {
                result.remove(kind);
                continue;
            }
            result.put(kind, (from != null ? from : EMPTY).with(attack, from != null ? from.shape() : kind));
        }
        return Map.copyOf(result);
    }

    private static Map<AttackKind, Attack> scale(Map<AttackKind, Attack> attacks, float damage, float windup) {
        if (damage == 1.0F && windup == 1.0F) {
            return attacks;
        }
        Map<AttackKind, Attack> result = new EnumMap<>(AttackKind.class);
        attacks.forEach((kind, attack) -> result.put(kind, attack.scaled(damage, windup)));
        return Map.copyOf(result);
    }

    private static AttackKind kind(String id) {
        AttackKind kind = AttackKind.byId(id);
        if (kind == null) {
            throw new IllegalArgumentException("нет вида удара «" + id + "»");
        }
        return kind;
    }

    private static DamageKind damageKind(JsonObject json, DamageKind fallback) {
        String id = GsonHelper.getAsString(json, "type");
        DamageKind kind = DamageKind.byId(id);
        if (kind == null) {
            throw new IllegalArgumentException("нет типа урона «" + id + "»");
        }
        return kind;
    }
}
