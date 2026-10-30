package com.nrdhvnstd.halgventmod.combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.quality.DamageKind;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Комбо по последовательности сторон (по образцу Kingdom Come Combat, MIT): data/&lt;мод&gt;/combat_combos/*.json.
 * Сервер помнит стороны последних ударов (CombatState.history); если хвост совпал с последовательностью комбо для
 * класса оружия — этот удар становится приёмом: своя анимация (и анимация жертвы), урон и тип, часть тела, куда он
 * приходится, выносливость сверху, выпад вперёд или подтягивание противника. Длинные последовательности — первыми.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class ComboMoves {
    private static final String DIR = "combat_combos";
    private static final Gson GSON = new Gson();
    private static List<Combo> combos = List.of();

    /** Приём. hitStart и hitEnd — окно удара в анимации (с), если в ней не размечено, иначе −1. */
    public record Combo(String id, List<StrikeSide> sequence, String animation, String victim, Set<WeaponClass> classes,
                        float damage, @Nullable DamageKind type, @Nullable HitZone zone, float stamina, boolean lunge,
                        boolean pull, float hitStart, float hitEnd) {
        /** Приём для клиента: «имя[@начало:конец][#жертва]» — окно удара, если в файле его нет, и анимация жертвы. */
        public String move() {
            return animation + (hitStart < 0 ? "" : "@" + hitStart + ":" + hitEnd) + (victim.isEmpty() ? "" : "#" + victim);
        }
    }

    private ComboMoves() {}

    /** Приём, которым стал удар с этой историей сторон (последняя — сам удар), или null. */
    @Nullable
    public static Combo match(Collection<StrikeSide> history, WeaponClass weapon) {
        List<StrikeSide> sides = new ArrayList<>(history);
        for (Combo combo : combos) {
            int n = combo.sequence().size();
            if (n > sides.size() || (!combo.classes().isEmpty() && !combo.classes().contains(weapon))) {
                continue;
            }
            if (sides.subList(sides.size() - n, sides.size()).equals(combo.sequence())) {
                return combo;
            }
        }
        return null;
    }

    /** Приёмы для класса оружия (ИИ боя выбирает план). */
    public static List<Combo> forWeapon(WeaponClass weapon) {
        return combos.stream().filter(combo -> combo.classes().isEmpty() || combo.classes().contains(weapon)).toList();
    }

    /** Самая длинная последовательность — столько сторон помнить. */
    public static int longest() {
        return combos.stream().mapToInt(combo -> combo.sequence().size()).max().orElse(0);
    }

    // ---------------------------------------------------------------- загрузка

    @SubscribeEvent
    public static void addReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(GSON, DIR);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<Combo> loaded = new ArrayList<>();
            files.forEach((file, json) -> {
                if (!json.isJsonObject() || !json.getAsJsonObject().has("combos")) {
                    return;
                }
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().getAsJsonObject("combos").entrySet()) {
                    try {
                        loaded.add(parse(entry.getKey(), entry.getValue().getAsJsonObject()));
                    } catch (RuntimeException e) {
                        HalgventMod.LOGGER.warn("Комбо {} в {} не прочитано: {}", entry.getKey(), file, e.getMessage());
                    }
                }
            });
            loaded.sort((a, b) -> Integer.compare(b.sequence().size(), a.sequence().size()));
            combos = List.copyOf(loaded);
            HalgventMod.LOGGER.info("Комбо: {}", combos.size());
        }
    }

    private static Combo parse(String id, JsonObject json) {
        List<StrikeSide> sequence = new ArrayList<>();
        for (char c : json.get("sequence").getAsString().toUpperCase().toCharArray()) {
            sequence.add(switch (c) {
                case 'L' -> StrikeSide.LEFT;
                case 'R' -> StrikeSide.RIGHT;
                case 'U' -> StrikeSide.UP;
                case 'D' -> StrikeSide.DOWN;
                default -> throw new IllegalArgumentException("сторона «" + c + "»");
            });
        }
        Set<WeaponClass> classes = EnumSet.noneOf(WeaponClass.class);
        if (json.has("classes")) {
            json.getAsJsonArray("classes").forEach(element -> {
                WeaponClass weapon = WeaponClass.byId(element.getAsString());
                if (weapon != null) {
                    classes.add(weapon);
                }
            });
        }
        HitZone zone = null;
        if (json.has("zone")) {
            zone = HitZone.valueOf(json.get("zone").getAsString().toUpperCase());
        }
        float hitStart = -1;
        float hitEnd = -1;
        if (json.has("hit")) {
            hitStart = json.getAsJsonArray("hit").get(0).getAsFloat();
            hitEnd = json.getAsJsonArray("hit").get(1).getAsFloat();
        }
        return new Combo(id, List.copyOf(sequence), json.get("animation").getAsString(),
                json.has("victim") ? json.get("victim").getAsString() : "", classes,
                json.has("damage") ? json.get("damage").getAsFloat() : 1.0F,
                json.has("type") ? DamageKind.byId(json.get("type").getAsString()) : null, zone,
                json.has("stamina") ? json.get("stamina").getAsFloat() : 0,
                json.has("lunge") && json.get("lunge").getAsBoolean(), json.has("pull") && json.get("pull").getAsBoolean(),
                hitStart, hitEnd);
    }
}
