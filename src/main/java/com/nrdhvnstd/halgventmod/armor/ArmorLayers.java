package com.nrdhvnstd.halgventmod.armor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Четыре слоя брони (ТЗ боя, 7.3): накидка → латы (3 слой) → кольчуга (2) → поддоспех (1). Слой вещи — по материалу:
 * слот накидки — накидка, PLATE — латы, MAIL — кольчуга, ткань и кожа — поддоспех. Удар по зоне видит только вещи
 * слотов этой части тела; накидка закрывает корпус, а с капюшоном — и голову.
 *
 * Сводка по каждой зоне и слою: покрытие c = min(ΣКБ / норма, потолок), твёрдость и сопротивления — среднее по КБ.
 * Поднятое забрало — сопротивления шлема ×0.75. Сводка кэшируется в ArmorInventory и пересчитывается при смене его
 * версии: смена брони, забрала и износ (износ кладёт вещь заново — DamagePipeline).
 */
public final class ArmorLayers {
    public static final float VISOR_RAISED_RESIST = 0.75F; // ⚠️

    /** Слой: норма КБ, потолок покрытия, ткань (пробивается всегда). Порядок — снаружи внутрь. */
    public enum Layer {
        CLOAK(7.5F, 1.00F, true), PLATE(110F, 0.95F, false), MAIL(50F, 0.95F, false), PADDING(45F, 1.00F, true);

        private final float norm;
        private final float cap;
        private final boolean cloth;

        Layer(float norm, float cap, boolean cloth) {
            this.norm = norm;
            this.cap = cap;
            this.cloth = cloth;
        }

        public boolean cloth() {
            return cloth;
        }

        /** Металл: погашенный им урон даёт боль отдачей (этап 9). */
        public boolean metal() {
            return this == PLATE || this == MAIL;
        }

        /** Ключ перевода: armor_layer.halgventmod.plate */
        public String langKey() {
            return "armor_layer.halgventmod." + name().toLowerCase();
        }

        static Layer of(ModArmor.ArmorInfo info) {
            if (info.slot() == ArmorSlot.CLOAK) {
                return CLOAK;
            }
            return switch (info.kind()) {
                case PLATE -> PLATE;
                case MAIL -> MAIL;
                case PADDED, LEATHER -> PADDING;
            };
        }
    }

    /** Вещь в слое: её доля КБ в слое — для износа. */
    public record Piece(ArmorSlot slot, float share, float durabilityLoss) {}

    /** Слой в зоне. */
    public record Summary(Layer layer, float coverage, float hardness, float[] resist, List<Piece> pieces) {
        public float resist(DamageKind kind) {
            return resist[kind.ordinal()];
        }
    }

    private static final Map<ArmorSlot.BodyPart, List<Summary>> NONE = Map.of(
            ArmorSlot.BodyPart.HEAD, List.of(), ArmorSlot.BodyPart.TORSO, List.of(), ArmorSlot.BodyPart.LEGS, List.of());

    private ArmorLayers() {}

    /** Слои зоны снаружи внутрь; пусто — брони нет (или это не игрок). */
    public static List<Summary> of(LivingEntity entity, ArmorSlot.BodyPart part) {
        ArmorInventory armor = entity.getExistingDataOrNull(ModAttachments.ARMOR);
        if (armor == null) {
            return List.of();
        }
        Cached cached = armor.layersCache() instanceof Cached existing ? existing : null;
        if (cached == null || cached.generation() != ArmorStats.generation()) {
            cached = new Cached(ArmorStats.generation(), compute(armor));
            armor.setLayersCache(cached);
        }
        return cached.layers().get(part);
    }

    // Сводка и поколение параметров брони, по которым она посчитана (/reload меняет поколение)
    private record Cached(int generation, Map<ArmorSlot.BodyPart, List<Summary>> layers) {}

    private static final class Sum {
        float coverage, hardness;
        final float[] resist = new float[DamageKind.values().length];
        final List<ArmorSlot> slots = new ArrayList<>();
        final List<Float> coverages = new ArrayList<>();
        final List<Float> losses = new ArrayList<>();

        void add(ArmorSlot slot, ArmorStats.Stats stats, float resistFactor) {
            coverage += stats.coverage();
            hardness += stats.coverage() * stats.hardness();
            for (int i = 0; i < resist.length; i++) {
                resist[i] += stats.coverage() * stats.resist()[i] * resistFactor;
            }
            slots.add(slot);
            coverages.add(stats.coverage());
            losses.add(stats.durabilityLoss());
        }
    }

    private static Map<ArmorSlot.BodyPart, List<Summary>> compute(ArmorInventory armor) {
        Map<ArmorSlot.BodyPart, Map<Layer, Sum>> sums = new EnumMap<>(ArmorSlot.BodyPart.class);
        for (ArmorSlot slot : ArmorSlot.values()) {
            ItemStack stack = armor.get(slot);
            ModArmor.ArmorInfo info = ModArmor.get(stack);
            ArmorStats.Stats stats = ArmorStats.of(stack);
            if (info == null || stats == null || stats.coverage() <= 0) {
                continue;
            }
            float resistFactor = slot == ArmorSlot.HELMET && ModArmor.visorRaised(stack) ? VISOR_RAISED_RESIST : 1.0F;
            Layer layer = Layer.of(info);
            List<ArmorSlot.BodyPart> parts = new ArrayList<>();
            if (slot == ArmorSlot.CLOAK) {
                parts.add(ArmorSlot.BodyPart.TORSO);
                if (info.cloak() != null && info.cloak().hasHood()) {
                    parts.add(ArmorSlot.BodyPart.HEAD);
                }
            } else {
                parts.add(slot.bodyPart());
            }
            for (ArmorSlot.BodyPart part : parts) {
                sums.computeIfAbsent(part, p -> new EnumMap<>(Layer.class))
                        .computeIfAbsent(layer, l -> new Sum()).add(slot, stats, resistFactor);
            }
        }
        Map<ArmorSlot.BodyPart, List<Summary>> result = new EnumMap<>(NONE);
        sums.forEach((part, layers) -> {
            List<Summary> list = new ArrayList<>();
            for (Layer layer : Layer.values()) {
                Sum sum = layers.get(layer);
                if (sum == null) {
                    continue;
                }
                float[] resist = new float[sum.resist.length];
                for (int i = 0; i < resist.length; i++) {
                    resist[i] = sum.resist[i] / sum.coverage;
                }
                List<Piece> pieces = new ArrayList<>();
                for (int i = 0; i < sum.slots.size(); i++) {
                    pieces.add(new Piece(sum.slots.get(i), sum.coverages.get(i) / sum.coverage, sum.losses.get(i)));
                }
                list.add(new Summary(layer, Math.min(sum.coverage / layer.norm, layer.cap), sum.hardness / sum.coverage,
                        resist, List.copyOf(pieces)));
            }
            result.put(part, List.copyOf(list));
        });
        return result;
    }
}
