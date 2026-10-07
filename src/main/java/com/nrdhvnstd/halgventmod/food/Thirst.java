package com.nrdhvnstd.halgventmod.food;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.network.NeedsSyncPayload;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.world.DayLength;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Жажда: трата, урон при нуле, питьё из воды, синхронизация с клиентом. */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Thirst {
    // ===== Правила — меняйте здесь =====
    public static final int MAX = ThirstData.MAX;

    // Пассивная трата, даже когда игрок стоит: очков за игровые сутки (1 очко = пол-окорочка / полкапли).
    // Длина суток задаётся в DayLength.
    public static final float PASSIVE_HUNGER_PER_DAY = 8;
    public static final float PASSIVE_THIRST_PER_DAY = 8;

    public static final float EXHAUSTION_PER_POINT = 4.0F;  // как у голода: 4 единицы усталости = 1 очко
    public static final float THIRST_MULTIPLIER = 1.0F;     // жажда от действий: 1.5 — в полтора раза быстрее голода
    public static final int NO_SPRINT_AT = 6;               // при 6 и ниже (3 капли) бегать нельзя, как с голодом
    public static final int DAMAGE_INTERVAL = 80;           // при пустой жажде урон раз в 4 секунды

    public static final int WATER_SIP = 2;                  // глоток из воды: +1 капля
    public static final int WATER_SIP_COOLDOWN = 10;        // тиков между глотками

    private Thirst() {}

    public static int get(Player player) {
        return player.getData(ModAttachments.THIRST).level();
    }

    public static boolean needs(Player player) {
        return get(player) < MAX;
    }

    /** Изменить жажду: + утолить, − усилить. Только на сервере. */
    public static void add(Player player, int amount) {
        if (player.level().isClientSide) {
            return;
        }
        ThirstData data = player.getData(ModAttachments.THIRST);
        data.level = Mth.clamp(data.level + amount, 0, MAX);
        sync(player);
    }

    /** Вызывается из PlayerMixin на каждую трату голода. */
    public static void addExhaustion(Player player, float amount) {
        ThirstData data = player.getData(ModAttachments.THIRST);
        data.exhaustion = Math.min(data.exhaustion + amount * THIRST_MULTIPLIER, 40.0F);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ThirstData data = player.getData(ModAttachments.THIRST);

        // Пассивная трата раз в секунду. Голод и жажда считаются отдельно, перегруз увеличивает обе
        if (player.tickCount % 20 == 0 && !player.getAbilities().invulnerable) {
            float overweight = 1.0F + player.getData(ModAttachments.OVERWEIGHT_PENALTY);
            player.getFoodData().addExhaustion(perSecond(PASSIVE_HUNGER_PER_DAY) * overweight);
            data.exhaustion = Math.min(data.exhaustion + perSecond(PASSIVE_THIRST_PER_DAY) * overweight, 40.0F);
        }

        // Накопленная усталость превращается в потерю жажды
        if (data.exhaustion >= EXHAUSTION_PER_POINT) {
            data.exhaustion -= EXHAUSTION_PER_POINT;
            data.level = Math.max(data.level - 1, 0);
        }

        // На мирной сложности жажда восстанавливается сама, как голод
        if (player.level().getDifficulty() == Difficulty.PEACEFUL
                && player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)
                && player.tickCount % 10 == 0 && data.level < MAX) {
            data.level++;
        }

        // Пустая жажда ранит, по тем же правилам сложности, что и голод
        if (data.level == 0) {
            if (++data.damageTimer >= DAMAGE_INTERVAL) {
                data.damageTimer = 0;
                Difficulty difficulty = player.level().getDifficulty();
                if (player.getHealth() > 10.0F || difficulty == Difficulty.HARD
                        || (player.getHealth() > 1.0F && difficulty == Difficulty.NORMAL)) {
                    player.hurt(player.damageSources().starve(), 1.0F);
                }
            }
        } else {
            data.damageTimer = 0;
        }

        // Ванилла отправляет насыщение клиенту редко, поэтому шлём его сами вместе с жаждой
        float saturation = player.getFoodData().getSaturationLevel();
        if (data.level != data.lastSyncedLevel || saturation != data.lastSyncedSaturation) {
            sync(player);
        }
    }

    // При смене измерения клиент создаёт игрока заново — отправим всё ещё раз
    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        event.getEntity().getData(ModAttachments.THIRST).lastSyncedLevel = -1;
    }

    /** Глоток из блока воды. Вызывается по пакету от клиента, всё перепроверяем на сервере. */
    public static void drinkFromWater(ServerPlayer player) {
        ThirstData data = player.getData(ModAttachments.THIRST);
        if (!player.getMainHandItem().isEmpty() || !needs(player)
                || player.tickCount - data.lastDrinkTick < WATER_SIP_COOLDOWN) {
            return;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, true);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        var pos = ((BlockHitResult) hit).getBlockPos();
        if (!player.level().getBlockState(pos).is(Blocks.WATER)
                || !player.level().getFluidState(pos).is(FluidTags.WATER)) {
            return;
        }
        data.lastDrinkTick = player.tickCount;
        add(player, WATER_SIP);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.5F, 1.0F);
    }

    // Очки за сутки → усталость за секунду: 8 очков = 32 усталости на 10 800 секунд ≈ 0,003 в секунду
    private static float perSecond(float pointsPerDay) {
        return pointsPerDay * EXHAUSTION_PER_POINT / (DayLength.DAY_LENGTH_TICKS / 20.0F);
    }

    private static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            ThirstData data = serverPlayer.getData(ModAttachments.THIRST);
            float saturation = serverPlayer.getFoodData().getSaturationLevel();
            data.lastSyncedLevel = data.level;
            data.lastSyncedSaturation = saturation;
            PacketDistributor.sendToPlayer(serverPlayer, new NeedsSyncPayload(data.level, saturation));
        }
    }
}