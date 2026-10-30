package com.nrdhvnstd.halgventmod.combat;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.quality.DamageKind;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Видимые раны (attachment halgventmod:wounds): удар, дошедший до тела, оставляет на коже рану там, куда пришёлся, —
 * порез (рубящий, режущий), колотую (колющий, проникающий) или синяк (дробящий). Видна всем, пока не заживёт
 * (client/WoundLayer): лёгкая — {@link #LIGHT_TICKS}, средняя — вдвое, тяжёлая — вчетверо; повязка (Injuries.treat)
 * перевязывает все раны — их видно перевязанными, заживают вдвое быстрее. Под бронёй ран не видно — броня рисуется
 * поверх. После смерти ран нет. Скелеты, големы и прочие без крови ран не получают.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Wounds {
    // Меняйте здесь ⚠️
    public static final float MIN_DAMAGE = 5;           // урона по телу — рана
    public static final float MEDIUM_DAMAGE = 9;
    public static final float SEVERE_DAMAGE = 16;
    public static final long LIGHT_TICKS = 20 * 60 * 10; // 10 минут
    public static final int MAX = 12;                    // ран на теле, старые уходят
    private static final int CHECK_EVERY = 100;

    /** Вид раны. */
    public enum Kind { CUT, STAB, BRUISE }

    /** Рана: часть тела, вид, размер 1–3, зерно места, заживёт к тику, когда нанесена, перевязана. */
    public record Wound(HitZone zone, Kind kind, int size, int seed, long until, long since, boolean bandaged) {
        public static final Codec<Wound> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.xmap(HitZone::valueOf, HitZone::name).fieldOf("zone").forGetter(Wound::zone),
                Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(Wound::kind),
                Codec.INT.fieldOf("size").forGetter(Wound::size),
                Codec.INT.fieldOf("seed").forGetter(Wound::seed),
                Codec.LONG.fieldOf("until").forGetter(Wound::until),
                Codec.LONG.fieldOf("since").forGetter(Wound::since),
                Codec.BOOL.optionalFieldOf("bandaged", false).forGetter(Wound::bandaged)
        ).apply(instance, Wound::new));

        /** Доля заживления 0…1 в миг now. */
        public float healed(long now) {
            return until <= since ? 1 : Math.max(0, Math.min(1, (float) (now - since) / (until - since)));
        }
    }

    public static final Codec<List<Wound>> CODEC = Wound.CODEC.listOf();

    /** Всем, кто видит: раны на теле. */
    public static final AttachmentSyncHandler<List<Wound>> SYNC = new AttachmentSyncHandler<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, List<Wound> wounds, boolean initialSync) {
            buf.writeVarInt(wounds.size());
            for (Wound wound : wounds) {
                buf.writeByte(wound.zone().ordinal());
                buf.writeByte(wound.kind().ordinal());
                buf.writeByte(wound.size());
                buf.writeInt(wound.seed());
                buf.writeLong(wound.until());
                buf.writeLong(wound.since());
                buf.writeBoolean(wound.bandaged());
            }
        }

        @Override
        public List<Wound> read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, List<Wound> previous) {
            int count = Math.min(MAX, buf.readVarInt());
            List<Wound> wounds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                wounds.add(new Wound(HitZone.values()[buf.readByte()], Kind.values()[buf.readByte()], buf.readByte(),
                        buf.readInt(), buf.readLong(), buf.readLong(), buf.readBoolean()));
            }
            return List.copyOf(wounds);
        }
    };

    private Wounds() {}

    public static List<Wound> of(LivingEntity entity) {
        List<Wound> wounds = entity.getExistingDataOrNull(ModAttachments.WOUNDS);
        return wounds == null ? List.of() : wounds;
    }

    private static void set(LivingEntity entity, List<Wound> wounds) {
        entity.setData(ModAttachments.WOUNDS, List.copyOf(wounds));
        entity.syncData(ModAttachments.WOUNDS);
    }

    /** Удар дошёл до тела (Combat.landed): рана в части тела. */
    static void onWound(LivingEntity target, DamageKind kind, HitZone zone, float body) {
        if (body < MIN_DAMAGE || !Blood.bleeds(target)) {
            return;
        }
        int size = body >= SEVERE_DAMAGE ? 3 : body >= MEDIUM_DAMAGE ? 2 : 1;
        Kind look = switch (kind) {
            case BLUNT -> Kind.BRUISE;
            case PIERCING, PENETRATING -> Kind.STAB;
            default -> Kind.CUT;
        };
        long now = target.level().getGameTime();
        long life = LIGHT_TICKS << (size - 1); // 1 → ×1, 2 → ×2, 3 → ×4
        List<Wound> wounds = new ArrayList<>(of(target));
        wounds.add(new Wound(zone, look, size, target.getRandom().nextInt(), now + life, now, false));
        while (wounds.size() > MAX) {
            wounds.removeFirst();
        }
        set(target, wounds);
    }

    /** Повязка: все раны перевязаны, остаток заживления вдвое короче. */
    public static void bandage(LivingEntity entity) {
        List<Wound> wounds = of(entity);
        if (wounds.isEmpty()) {
            return;
        }
        long now = entity.level().getGameTime();
        List<Wound> bandaged = new ArrayList<>(wounds.size());
        for (Wound wound : wounds) {
            long left = Math.max(0, wound.until() - now);
            bandaged.add(new Wound(wound.zone(), wound.kind(), wound.size(), wound.seed(),
                    wound.bandaged() ? wound.until() : now + left / 2, wound.since(), true));
        }
        set(entity, bandaged);
    }

    // Зажившие уходят
    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide
                || entity.tickCount % CHECK_EVERY != 0) {
            return;
        }
        List<Wound> wounds = of(entity);
        if (wounds.isEmpty()) {
            return;
        }
        long now = entity.level().getGameTime();
        List<Wound> open = wounds.stream().filter(wound -> wound.until() > now).toList();
        if (open.size() != wounds.size()) {
            set(entity, open);
        }
    }
}
