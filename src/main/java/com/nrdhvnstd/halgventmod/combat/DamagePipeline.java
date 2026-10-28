package com.nrdhvnstd.halgventmod.combat;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorLayers;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModSounds;
import com.nrdhvnstd.halgventmod.sound.SoundPlay;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Удар сквозь броню (ТЗ боя, 7.3–7.4): снаружи внутрь — накидка → латы → кольчуга → поддоспех → тело. На каждом слое:
 * <ol>
 *   <li>попал ли в слой — шанс «покрытие − точность оружия», мимо — в щель к следующему;</li>
 *   <li>дробящий не пробивает — гасится сопротивлением дробящему;</li>
 *   <li>остальные: доля пробития p = 0.5 + (пробивание − твёрдость) / 2 (ткань — всегда 1); пробитое идёт дальше своим
 *       типом за вычетом сопротивления, непробитое × отдача типа — дальше дробящим;</li>
 *   <li>погашенное слоем × износ материала списывается с прочности вещей слоя по их доле КБ.</li>
 * </ol>
 * Тело = дошедшее своим типом × «урон телу» типа + дробящая отдача × 0.7. Разбор каждого слоя — для /combat debug.
 */
public final class DamagePipeline {
    private DamagePipeline() {}

    /**
     * Итог: урон по телу; из него дробящего (оглушение, выносливость); погашенное металлом (боль отдачей, этап 9);
     * боль; разбор по слоям.
     */
    public record Result(float body, float bluntBody, float metalAbsorbed, float pain, List<Component> lines) {}

    /** Прогнать удар через броню цели в зоне. penetration — пробивание удара, accuracy — точность. */
    public static Result apply(LivingEntity target, DamageKind kind, float amount, HitZone zone, float penetration,
                               float accuracy, RandomSource random) {
        float own = amount;   // урон своим типом
        float blunt = 0;      // дробящая отдача
        float metal = 0;
        List<Component> lines = new ArrayList<>();
        Map<ArmorSlot, Float> wear = new EnumMap<>(ArmorSlot.class);
        for (ArmorLayers.Summary layer : ArmorLayers.of(target, zone.bodyPart())) {
            Component name = Component.translatable(layer.layer().langKey());
            if (random.nextFloat() >= layer.coverage() - accuracy) {
                lines.add(Component.translatable("combat.halgventmod.debug.layer_gap", name));
                continue;
            }
            float before = own + blunt;
            blunt *= 1.0F - layer.resist(DamageKind.BLUNT);
            float pierced = 1.0F;
            if (kind == DamageKind.BLUNT) {
                own *= 1.0F - layer.resist(DamageKind.BLUNT);
            } else {
                pierced = layer.layer().cloth() ? 1.0F
                        : Mth.clamp(0.5F + (penetration - layer.hardness()) / 2.0F, 0.0F, 1.0F);
                float stopped = own * (1.0F - pierced);
                own = own * pierced * (1.0F - layer.resist(kind));
                blunt += stopped * kind.backlash();
            }
            float absorbed = Math.max(0, before - own - blunt);
            if (layer.layer().metal()) {
                metal += absorbed;
            }
            for (ArmorLayers.Piece piece : layer.pieces()) {
                wear.merge(piece.slot(), absorbed * piece.durabilityLoss() * piece.share(), Float::sum);
            }
            lines.add(Component.translatable("combat.halgventmod.debug.layer", name, Math.round(pierced * 100),
                    String.format("%.1f", own), String.format("%.1f", blunt), String.format("%.1f", absorbed)));
        }
        float bluntBody = (kind == DamageKind.BLUNT ? own * kind.body() : 0) + blunt * DamageKind.BLUNT.body();
        float body = (kind == DamageKind.BLUNT ? 0 : own * kind.body()) + bluntBody;
        float pain = (body - bluntBody) * kind.pain() + bluntBody * DamageKind.BLUNT.pain() + metal * Combat.METAL_PAIN;
        if (target instanceof Player player) {
            wear(player, wear, random);
        }
        return new Result(body, bluntBody, metal, pain, lines);
    }

    // Износ: дробные единицы — бросок (0.3 — с шансом 30 %). Вещь кладётся заново: сводка слоёв и защита пересчитаются,
    // броня уйдёт клиентам. Разбилась — звук, остаётся надетой без защиты
    private static void wear(Player player, Map<ArmorSlot, Float> wear, RandomSource random) {
        if (wear.isEmpty() || player.getAbilities().instabuild) {
            return;
        }
        ArmorInventory armor = player.getData(ModAttachments.ARMOR);
        wear.forEach((slot, amount) -> {
            int points = (int) Math.floor(amount) + (random.nextFloat() < amount - Math.floor(amount) ? 1 : 0);
            ItemStack stack = armor.get(slot);
            if (points <= 0 || !stack.isDamageableItem() || stack.getDamageValue() >= stack.getMaxDamage()) {
                return;
            }
            ItemStack worn = stack.copy();
            worn.setDamageValue(Math.min(worn.getMaxDamage(), worn.getDamageValue() + points));
            armor.set(slot, worn);
            if (worn.getDamageValue() >= worn.getMaxDamage()) {
                SoundPlay.atChest(player, ModSounds.ARMOR_BREAK.get(), SoundSource.PLAYERS, 1.0F, 0.05F);
            }
        });
    }
}
