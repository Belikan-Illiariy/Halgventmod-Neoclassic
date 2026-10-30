package com.nrdhvnstd.halgventmod.combat.anim;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.combat.StrikeSide;
import com.nrdhvnstd.halgventmod.combat.WeaponProfile;

/**
 * Какой анимацией KCC идёт удар — общее для сервера (клинок, BladePose) и клиента (рисунок, client/anim/CombatClips).
 * Вариант по классу оружия: одноручное — без приставки (заряженный — _heavy), двуручный меч — _longsword,
 * древковое, копьё, двуручные топор и молот — _longweapon (нет такой — _longsword). Кулак и щит — без анимаций.
 */
public final class CombatMoves {
    private CombatMoves() {}

    /** Приставка анимации по оружию; null — анимаций KCC нет (наши позы и дуги). */
    @Nullable
    public static String variant(@Nullable WeaponProfile profile) {
        if (profile == null) {
            return null;
        }
        return switch (profile.weaponClass()) {
            case KNIFE, DAGGER, SWORD, AXE, MACE -> "";
            case GREATSWORD -> "_longsword";
            case SPEAR, POLEARM, GREATAXE, GREATHAMMER -> "_longweapon";
            case SHIELD, FIST -> null;
        };
    }

    /**
     * Анимация удара; null — нет (или у неё не размечено окно удара). Рипост после парирования — мастер-удар
     * (master_strike_&lt;сторона&gt;).
     */
    @Nullable
    public static AnimClip attack(@Nullable WeaponProfile profile, StrikeSide side, boolean heavy, boolean master,
                                  String move) {
        String variant = variant(profile);
        if (variant == null) {
            return null;
        }
        if (!move.isEmpty()) {
            AnimClip clip = move(move);
            if (clip != null) {
                return clip;
            }
        }
        if (master) {
            AnimClip clip = AnimLibrary.get("master_strike_" + side(side));
            if (clip != null && clip.hitStart >= 0) {
                return clip;
            }
        }
        String base = "attack_" + side(side);
        AnimClip clip = heavy && variant.isEmpty()
                ? AnimLibrary.first(base + "_heavy", base)
                : AnimLibrary.first(base + variant, base + "_longsword", base);
        return clip != null && clip.hitStart >= 0 ? clip : null;
    }

    private static final Map<String, AnimClip> MOVES = new ConcurrentHashMap<>();

    /** Анимация приёма по строке «имя[@начало:конец][#жертва]» (ComboMoves.Combo.move); null — нет такой. */
    @Nullable
    public static AnimClip move(String move) {
        AnimClip cached = MOVES.get(move);
        if (cached != null) {
            return cached;
        }
        String spec = move.contains("#") ? move.substring(0, move.indexOf('#')) : move;
        String name = spec.contains("@") ? spec.substring(0, spec.indexOf('@')) : spec;
        AnimClip clip = AnimLibrary.get(name);
        if (clip == null) {
            return null;
        }
        if (spec.contains("@")) {
            String[] window = spec.substring(spec.indexOf('@') + 1).split(":");
            try {
                clip = clip.withWindow(Float.parseFloat(window[0]), Float.parseFloat(window[1]));
            } catch (RuntimeException e) {
                return null;
            }
        }
        if (clip.hitStart < 0) {
            return null;
        }
        MOVES.put(move, clip);
        return clip;
    }

    /** Анимация жертвы приёма («…#жертва»), или пусто. */
    public static String victim(String move) {
        return move.contains("#") ? move.substring(move.indexOf('#') + 1) : "";
    }

    /** Миг анимации удара (с) в доле фазы удара t ∈ [0, 1]: окно удара целиком. */
    public static float releaseTime(AnimClip clip, float t) {
        return clip.hitStart + Math.max(0, Math.min(1, t)) * (clip.hitEnd - clip.hitStart);
    }

    public static String side(StrikeSide side) {
        return switch (side) {
            case LEFT -> "left";
            case RIGHT -> "right";
            case UP -> "up";
            case DOWN -> "down";
        };
    }
}
