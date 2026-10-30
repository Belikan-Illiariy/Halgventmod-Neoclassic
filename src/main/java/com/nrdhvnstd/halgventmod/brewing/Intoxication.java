package com.nrdhvnstd.halgventmod.brewing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.cooking.FoodQuality;
import com.nrdhvnstd.halgventmod.food.Thirst;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Опьянение 0–100 (вложение halgventmod:intoxication). Каждый напиток прибавляет свою крепость (Brewing.strength),
 * хорошего качества — мягче. Хмель проходит сам: −{@link #SOBER_PER_MINUTE} в минуту.
 * <ul>
 *   <li><b>Навеселе</b> ({@link #TIPSY}+): выносливость восстанавливается быстрее (×{@link #TIPSY_STAMINA}), камеру
 *       чуть покачивает, изредка икота (её слышат все рядом);</li>
 *   <li><b>Пьян</b> ({@link #DRUNK}+): качает сильнее, походку уводит вбок (client/DrunkEffects), боли набирается меньше
 *       (×{@link #DRUNK_PAIN}, мертвецки — ×{@link #WASTED_PAIN}),
 *       икает чаще, на бегу может споткнуться; в местном чате путаются буквы (правило drunkChat);</li>
 *   <li><b>Мертвецки пьян</b> ({@link #WASTED}+): шатает, спотыкается часто, в глазах темнеет; от {@link #BLACKOUT_LEVEL}
 *       можно отключиться на {@link #BLACKOUT_SECONDS} с.</li>
 * </ul>
 * Выпил сверх 100 — <b>вырвало</b>: хмель падает до {@link #VOMIT_TO}, мутит и тянет есть.
 * Протрезвел после «Пьян» и выше — <b>похмелье</b> на {@link #HANGOVER_TICKS} тиков: усталость в руках, жажда вдвое.
 * Снимают кружкой пива («опохмелиться»), солониной, солёной рыбой или водой из горшка-отвара.
 * <p><b>Качество</b> (FoodQuality) выпитого:
 * <ul>
 *   <li>хмель ×{@link #strengthFactor}: каждая ступень выше Посредственного — на 10% мягче, ниже — крепче;</li>
 *   <li>плохое крепкое и настойка — <b>сивуха</b>: с шансом {@link #ROTGUT_CHANCE} за ступень ниже Посредственного
 *       мутит (Тошнота и Голод);</li>
 *   <li>похмелье считается по среднему качеству всего выпитого с последнего протрезвления: хорошее — с шансом
 *       {@link #HANGOVER_SKIP} за ступень обходится без него, плохое — дольше на {@link #HANGOVER_LONGER} за ступень.</li>
 * </ul>
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Intoxication {
    // Меняйте здесь ⚠️
    public static final float TIPSY = 15;
    public static final float DRUNK = 40;
    public static final float WASTED = 70;
    public static final float SOBER_PER_MINUTE = 4;
    public static final float TIPSY_STAMINA = 1.3F;
    public static final float DRUNK_PAIN = 0.8F;            // пьяный набирает боли меньше
    public static final float WASTED_PAIN = 0.65F;
    public static final int HANGOVER_TICKS = 20 * 60 * 20; // 20 минут
    public static final float STRENGTH_PER_STEP = 0.1F;     // хмель за ступень качества
    public static final float ROTGUT_CHANCE = 0.25F;        // сивуха: шанс, что замутит, за ступень ниже Посредственного
    public static final int ROTGUT_SECONDS = 15;
    public static final float HANGOVER_SKIP = 0.2F;         // хорошее: шанс обойтись без похмелья за ступень
    public static final float HANGOVER_LONGER = 0.25F;      // плохое: похмелье дольше за ступень
    // Что творится с выпившим (раз в секунду; шанс 1 из N)
    public static final int HICCUP_TIPSY = 60;              // икота навеселе
    public static final int HICCUP_DRUNK = 25;              // икота пьяного
    public static final int STUMBLE_DRUNK = 25;             // спотыкается на бегу пьяный
    public static final int STUMBLE_WASTED = 8;             // … мертвецки пьяный
    public static final double STUMBLE_PUSH = 0.35;         // толчок в сторону при спотыкании
    public static final float BLACKOUT_LEVEL = 90;          // с какого хмеля можно отключиться
    public static final int BLACKOUT_CHANCE = 40;
    public static final int BLACKOUT_SECONDS = 8;
    public static final float VOMIT_TO = 75;                // перебрал сверх 100 — вырвало, хмель падает до этого
    public static final int VOMIT_SECONDS = 30;             // голод после рвоты
    public static final GameRules.Key<GameRules.BooleanValue> DRUNK_CHAT =
            GameRules.register("drunkChat", GameRules.Category.CHAT, GameRules.BooleanValue.create(true));

    /**
     * Сохраняемое: уровень опьянения, был ли пьян (для похмелья), до какого тика похмелье; сколько хмеля выпито с
     * последнего протрезвления и его качество (сумма «хмель × качество») — для похмелья.
     */
    public record Data(float level, boolean wasDrunk, long hangoverUntil, float drunk, float qualitySum) {
        public static final Data NONE = new Data(0, false, 0, 0, 0);
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("level").forGetter(Data::level),
                Codec.BOOL.optionalFieldOf("was_drunk", false).forGetter(Data::wasDrunk),
                Codec.LONG.optionalFieldOf("hangover_until", 0L).forGetter(Data::hangoverUntil),
                Codec.FLOAT.optionalFieldOf("drunk", 0F).forGetter(Data::drunk),
                Codec.FLOAT.optionalFieldOf("quality_sum", 0F).forGetter(Data::qualitySum)
        ).apply(instance, Data::new));

        Data withHangover(long until) {
            return new Data(level, wasDrunk, until, drunk, qualitySum);
        }

        /** Среднее качество выпитого с последнего протрезвления (ничего — Посредственное). */
        float quality() {
            return drunk > 0 ? qualitySum / drunk : FoodQuality.NORMAL;
        }
    }

    /** Своему клиенту — только уровень хмеля: по нему качается камера и ведёт походку (client/DrunkEffects). */
    public static final AttachmentSyncHandler<Data> SYNC = new AttachmentSyncHandler<>() {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Data data, boolean initialSync) {
            buf.writeFloat(data.level());
        }

        @Override
        public Data read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, Data previousValue) {
            return new Data(buf.readFloat(), false, 0, 0, 0);
        }
    };

    public enum Stage { SOBER, TIPSY, DRUNK, WASTED }

    private Intoxication() {}

    /** Вызывается из конструктора мода: правило должно появиться до загрузки мира. */
    public static void init() {}

    public static Data data(Player player) {
        return player.getData(ModAttachments.INTOXICATION);
    }

    public static Stage stage(float level) {
        return level >= WASTED ? Stage.WASTED : level >= DRUNK ? Stage.DRUNK : level >= TIPSY ? Stage.TIPSY : Stage.SOBER;
    }

    public static Stage stage(Player player) {
        return stage(data(player).level());
    }

    /** Множитель восстановления выносливости (Stamina). */
    public static float staminaFactor(Player player) {
        return stage(player) == Stage.TIPSY ? TIPSY_STAMINA : 1.0F;
    }

    /** Множитель набираемой боли (needs/Pain): пьяному море по колено. */
    public static float painFactor(Player player) {
        Stage stage = stage(player);
        return stage == Stage.WASTED ? WASTED_PAIN : stage == Stage.DRUNK ? DRUNK_PAIN : 1.0F;
    }

    public static boolean hungover(Player player) {
        return data(player).hangoverUntil() > player.level().getGameTime();
    }

    // ---------------------------------------------------------------- качество

    /** Множитель хмеля: Шедевр — ×0,7, Ужасное — ×1,2. */
    public static float strengthFactor(int quality) {
        return Math.max(0.6F, 1.0F - STRENGTH_PER_STEP * (quality - FoodQuality.NORMAL));
    }

    /** Шанс, что замутит от сивухи (плохое крепкое или настойка), 0 — не мутит. */
    public static float rotgutChance(ItemStack drink) {
        boolean strong = drink.is(ModItems.SPIRIT.get()) || drink.is(ModItems.TINCTURE.get());
        return strong ? Math.min(1, ROTGUT_CHANCE * Math.max(0, FoodQuality.NORMAL - FoodQuality.get(drink))) : 0;
    }

    /** Шанс обойтись без похмелья при среднем качестве выпитого quality. */
    public static float hangoverSkip(float quality) {
        return Math.min(1, HANGOVER_SKIP * Math.max(0, quality - FoodQuality.NORMAL));
    }

    /** Во сколько раз дольше похмелье при среднем качестве quality. */
    public static float hangoverLength(float quality) {
        return 1 + HANGOVER_LONGER * Math.max(0, FoodQuality.NORMAL - quality);
    }

    // ---------------------------------------------------------------- выпил

    @SubscribeEvent
    public static void onDrink(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack drink = event.getItem();
        Data data = data(player);
        // Опохмелиться: пиво, солонина, солёная рыба снимают похмелье
        boolean cure = drink.is(ModItems.BEER.get()) || drink.is(ModItems.SALTED_MEAT.get()) || drink.is(ModItems.SALTED_FISH.get());
        if (cure && hungover(player)) {
            data = data.withHangover(0);
            player.removeEffect(MobEffects.DIG_SLOWDOWN);
            player.displayClientMessage(Component.translatable("message.halgventmod.drunk.cured"), true);
        }
        if (drink.is(ModItems.VINEGAR.get())) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 8 * 20, 0));
        }
        float strength = Brewing.strength(drink);
        if (strength <= 0) {
            player.setData(ModAttachments.INTOXICATION, data);
            return;
        }
        // Хорошее пьянит мягче: Шедевр — на 30% меньше
        int quality = FoodQuality.get(drink);
        strength *= strengthFactor(quality);
        // Сивуха мутит
        if (player.getRandom().nextFloat() < rotgutChance(drink)) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ROTGUT_SECONDS * 20, 0));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, ROTGUT_SECONDS * 20, 0));
            player.displayClientMessage(Component.translatable("message.halgventmod.drunk.rotgut"), true);
        }
        Stage before = stage(data.level());
        float level = data.level() + strength;
        if (level > 100) {
            vomit(player); // перебрал — лишнее вышло
            level = VOMIT_TO;
        }
        boolean wasDrunk = data.wasDrunk() || level >= DRUNK;
        set(player, new Data(level, wasDrunk, data.hangoverUntil(),
                data.drunk() + strength, data.qualitySum() + strength * quality));
        Stage after = stage(level);
        if (after != before) {
            player.displayClientMessage(Component.translatable("message.halgventmod.drunk." + after.name().toLowerCase()), true);
        }
    }

    // ---------------------------------------------------------------- время

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        Data data = data(player);
        long now = player.level().getGameTime();
        RandomSource random = player.getRandom();
        if (data.level() > 0) {
            float level = Math.max(0, data.level() - SOBER_PER_MINUTE / 60.0F);
            Stage stage = stage(level);
            // Камеру качает и походку ведёт клиент (DrunkEffects); здесь — то, что видят и слышат другие
            int hiccup = stage == Stage.TIPSY ? HICCUP_TIPSY : stage.ordinal() >= Stage.DRUNK.ordinal() ? HICCUP_DRUNK : 0;
            if (hiccup > 0 && random.nextInt(hiccup) == 0) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BURP,
                        SoundSource.PLAYERS, 0.5F, 1.7F + random.nextFloat() * 0.3F); // «ик!»
            }
            int stumble = stage == Stage.WASTED ? STUMBLE_WASTED : stage == Stage.DRUNK ? STUMBLE_DRUNK : 0;
            if (stumble > 0 && player.isSprinting() && player.onGround() && random.nextInt(stumble) == 0) {
                stumble(player, random);
            }
            if (stage == Stage.WASTED) {
                if (random.nextInt(30) == 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 4 * 20, 0, false, false)); // в глазах темнеет
                }
                if (level >= BLACKOUT_LEVEL && random.nextInt(BLACKOUT_CHANCE) == 0) {
                    blackout(player);
                }
            }
            long hangover = data.hangoverUntil();
            boolean wasDrunk = data.wasDrunk();
            if (level <= 0 && wasDrunk) {
                // Похмелье — по среднему качеству выпитого: хорошее может обойтись без него, сивуха — дольше
                float quality = data.quality();
                wasDrunk = false;
                if (random.nextFloat() < hangoverSkip(quality)) {
                    player.displayClientMessage(Component.translatable("message.halgventmod.drunk.clear_head"), true);
                } else {
                    hangover = now + Math.round(HANGOVER_TICKS * hangoverLength(quality));
                    player.displayClientMessage(Component.translatable("message.halgventmod.drunk.hangover"), true);
                }
            } else if (stage(data.level()) != stage && stage != Stage.SOBER) {
                player.displayClientMessage(Component.translatable("message.halgventmod.drunk." + stage.name().toLowerCase()), true);
            } else if (level <= 0) {
                player.displayClientMessage(Component.translatable("message.halgventmod.drunk.sober"), true);
            }
            // Протрезвел — счёт выпитого заново
            set(player, level <= 0 ? new Data(0, wasDrunk, hangover, 0, 0)
                    : new Data(level, wasDrunk, hangover, data.drunk(), data.qualitySum()));
        }
        if (data.hangoverUntil() > now) {
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 0, false, false));
            Thirst.addExhaustion(player, 0.05F); // жажда вдвое
        }
    }

    // Записать и отправить уровень своему клиенту
    private static void set(ServerPlayer player, Data data) {
        player.setData(ModAttachments.INTOXICATION, data);
        player.syncData(ModAttachments.INTOXICATION);
    }

    // Споткнулся на бегу: бег сбит, толкнуло вбок, пару секунд не разогнаться
    private static void stumble(ServerPlayer player, RandomSource random) {
        player.setSprinting(false);
        double angle = random.nextDouble() * Math.PI * 2;
        player.setDeltaMovement(player.getDeltaMovement().add(Math.cos(angle) * STUMBLE_PUSH, 0.1, Math.sin(angle) * STUMBLE_PUSH));
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2 * 20, 2, false, false));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SMALL_FALL,
                SoundSource.PLAYERS, 0.8F, 0.9F);
        player.displayClientMessage(Component.translatable("message.halgventmod.drunk.stumble"), true);
    }

    // Отключился: несколько секунд ни видеть, ни идти
    private static void blackout(ServerPlayer player) {
        int ticks = BLACKOUT_SECONDS * 20;
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, false));
        player.setSprinting(false);
        player.displayClientMessage(Component.translatable("message.halgventmod.drunk.blackout"), true);
    }

    // Перебрал: вырвало — мутит, тянет есть, лишний хмель вышел
    private static void vomit(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 6 * 20, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, VOMIT_SECONDS * 20, 1, false, false));
        if (player.level() instanceof ServerLevel level) {
            Vec3 look = player.getLookAngle();
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.ROTTEN_FLESH)),
                    player.getX() + look.x * 0.5, player.getEyeY() - 0.3, player.getZ() + look.z * 0.5, 16,
                    0.15, 0.1, 0.15, 0.08);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT_DROWN,
                SoundSource.PLAYERS, 0.8F, 0.6F);
        player.displayClientMessage(Component.translatable("message.halgventmod.drunk.vomit"), true);
    }

    /**
     * Привести в чувство (нашатырь): хмель −amount. Был пьян — похмелье решится, как обычно, когда хмель выйдет
     * (оставляем крупицу, чтобы до этого дошёл ежесекундный счёт).
     */
    public static void sober(Player player, float amount) {
        Data data = data(player);
        if (data.level() <= 0) {
            return;
        }
        float level = Math.max(data.wasDrunk() ? 0.01F : 0, data.level() - amount);
        player.setData(ModAttachments.INTOXICATION, new Data(level, data.wasDrunk(), data.hangoverUntil(), data.drunk(),
                data.qualitySum()));
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.syncData(ModAttachments.INTOXICATION);
        }
    }

    // ---------------------------------------------------------------- чат

    /** Пьяная речь в местном чате: путаются и тянутся буквы. Трезвому — как есть. */
    public static String slur(ServerPlayer player, String text) {
        Stage stage = stage(player);
        if (stage.ordinal() < Stage.DRUNK.ordinal() || !player.level().getGameRules().getBoolean(DRUNK_CHAT)) {
            return text;
        }
        float chance = stage == Stage.WASTED ? 0.22F : 0.1F;
        RandomSource random = player.getRandom();
        StringBuilder out = new StringBuilder();
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isLetter(c) && random.nextFloat() < chance) {
                switch (random.nextInt(3)) {
                    case 0 -> out.append(c).append(c);                                    // тянет: «ммёд»
                    case 1 -> {                                                           // путает соседние
                        if (i + 1 < chars.length && Character.isLetter(chars[i + 1])) {
                            out.append(chars[i + 1]).append(c);
                            i++;
                        } else {
                            out.append(c);
                        }
                    }
                    default -> out.append(c).append(random.nextBoolean() ? "-" : "");     // запинается
                }
            } else {
                out.append(c);
            }
        }
        if (stage == Stage.WASTED && random.nextBoolean()) {
            out.append(random.nextBoolean() ? " ...ик" : "... эээ");
        }
        return out.toString();
    }
}
