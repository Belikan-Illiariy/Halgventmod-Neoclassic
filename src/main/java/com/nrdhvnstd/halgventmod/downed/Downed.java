package com.nrdhvnstd.halgventmod.downed;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.combat.Bleeding;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * «Повержен» (docs/TZ_downed_and_hp.md, 3): смертельный урон не убивает — игрок падает и ползёт (поза плавания,
 * как ванильный ползок) на 2 минуты, медленно, без ударов, предметов, инвентаря и хотбара. Удержание E — «Сдаться»
 * (SurrenderPayload), таймер истёк — 60 % встать самому с остановленной кровью, иначе смерть. Поднимает другой игрок: «Вылечить» повязкой или мазью, «Нести» на плече
 * (social/Social). Второе падение за 10 минут, лава, пустота, утопление и удушье убивают сразу (TZ_character_fx).
 *
 * Несущий (раздел 6) не бьёт и не использует предметы; поверженный едет у него на плечах пассажиром,
 * сам слезть не может. Бросить — E (SocialAction.DROP).
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Downed {
    public static final int DOWNED_TICKS = 2400;          // 2 мин
    private static final int REPEAT_WINDOW = 12000;       // второе падение за 10 мин — сразу смерть (TZ_character_fx) ⚠️
    private static final float DOWNED_HEALTH = 1.0F;      // здоровье, с которым лежит: любой удар добивает
    private static final float RISE_CHANCE = 0.6F;        // таймер истёк: столько шансов встать самому, иначе смерть ⚠️
    private static final float RISE_HEALTH = 10.0F;       // с таким здоровьем встаёт ⚠️
    private static final double CRAWL_SPEED = 0.01;       // −99 % от шага (решение Bynelog)
    private static final double VANILLA_CRAWL = 0.3;      // ванильный ползок и так даёт 0,3 шага (Attributes.SNEAKING_SPEED)

    private static final ResourceLocation CRAWL = id("downed_crawl");
    private static final ResourceLocation NO_JUMP = id("downed_no_jump");

    // Посадку и снятие с плеч делаем сами: слезть самому или сесть на кого-то поверженный не может
    private static boolean ours;
    private static final Set<ServerPlayer> CARRYING = Collections.newSetFromMap(new WeakHashMap<>());

    private Downed() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HalgventMod.MODID, path);
    }

    public static boolean isDowned(Player player) {
        DownedData data = player.getExistingDataOrNull(ModAttachments.DOWNED);
        return data != null && data.ticks > 0;
    }

    /** Сколько тиков осталось лежать; 0 — не повержен. */
    public static int ticksLeft(Player player) {
        DownedData data = player.getExistingDataOrNull(ModAttachments.DOWNED);
        return data == null ? 0 : data.ticks;
    }

    /** Кого игрок несёт на плечах, или null. */
    @Nullable
    public static Player carried(Player carrier) {
        for (Entity passenger : carrier.getPassengers()) {
            if (passenger instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /** Повержен или несёт кого-то: руки заняты, E — сдаться или бросить. */
    public static boolean busy(Player player) {
        return isDowned(player) || carried(player) != null;
    }

    // ===== Падение, подъём, смерть =====

    // Раньше остального: сюда приходит любая смерть игрока, кроме уже отменённых
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || instant(event.getSource()) || isDowned(player)) {
            return;
        }
        DownedData data = player.getData(ModAttachments.DOWNED);
        if (player.level().getGameTime() - data.lastDownedAt < REPEAT_WINDOW) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(DOWNED_HEALTH);
        down(player);
    }

    // Пустота и /kill, лава, утопление, удушье в блоке — смерть сразу. Добивание лежачего — тоже (isDowned)
    private static boolean instant(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.LAVA)
                || source.is(DamageTypes.DROWN) || source.is(DamageTypes.IN_WALL);
    }

    private static void down(ServerPlayer player) {
        DownedData data = player.getData(ModAttachments.DOWNED);
        data.ticks = DOWNED_TICKS;
        data.lastDownedAt = player.level().getGameTime();
        player.syncData(ModAttachments.DOWNED);
        drop(player);
        if (player.getVehicle() != null) {
            ours = true;
            player.stopRiding();
            ours = false;
        }
        if (player.isSleeping()) {
            player.stopSleeping();
        }
        player.stopUsingItem();
        player.setSprinting(false);
        player.closeContainer();
        applyModifiers(player, true);
    }

    /** Встал: вылечили (Social, «Вылечить»). */
    public static void revive(ServerPlayer player) {
        if (!isDowned(player)) {
            return;
        }
        player.getData(ModAttachments.DOWNED).ticks = 0;
        player.syncData(ModAttachments.DOWNED);
        applyModifiers(player, false);
    }

    // Таймер истёк, но выжил: встаёт сам с малым здоровьем, кровь запеклась
    private static void rise(ServerPlayer player) {
        player.syncData(ModAttachments.DOWNED);
        applyModifiers(player, false);
        if (player.getVehicle() != null) {
            ours = true;
            player.stopRiding();
            ours = false;
        }
        Bleeding.stop(player);
        player.setHealth(Math.max(player.getHealth(), RISE_HEALTH));
        player.displayClientMessage(Component.translatable("message.halgventmod.downed.rose"), true);
    }

    /** «Сдаться» — удержание E у поверженного (SurrenderPayload). */
    public static void surrender(ServerPlayer player) {
        if (isDowned(player)) {
            player.kill();
        }
    }

    // Поза — на обеих сторонах (у клиента своя проверка позы), таймер и ограничения — на сервере
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        boolean downed = isDowned(player);
        if (downed && player.getForcedPose() != Pose.SWIMMING) {
            player.setForcedPose(Pose.SWIMMING);
        } else if (!downed && player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
        if (!(player instanceof ServerPlayer server)) {
            // Между рассылками остаток считает клиент; до нуля не доходит — снимает сервер
            DownedData data = player.getExistingDataOrNull(ModAttachments.DOWNED);
            if (data != null && data.ticks > 1) {
                data.ticks--;
            }
            return;
        }
        syncCarrier(server);
        applyModifiers(server, downed);
        if (downed) {
            server.getData(ModAttachments.DOWNED).ticks--;
            if (!isDowned(server)) {
                if (server.getRandom().nextFloat() < RISE_CHANCE) {
                    rise(server); // отлежался: встал сам
                } else {
                    server.kill(); // таймер истёк
                }
                return;
            }
            if (server.containerMenu != server.inventoryMenu) {
                server.closeContainer();
            }
            if (server.isUsingItem()) {
                server.stopUsingItem();
            }
        }
        // Несущего повалили или убили — поверженный падает с плеч
        if (server.getVehicle() instanceof ServerPlayer carrier && (!carrier.isAlive() || isDowned(carrier))) {
            drop(carrier);
        }
    }

    // Ваниль рассылает пассажиров только тем, кто видит сущность со стороны, а самому несущему — нет: его клиент не знал,
    // что на плечах кто-то есть (не видел его на себе, не мог бросить). Шлём сами, когда это меняется — как бы ни менялось
    private static void syncCarrier(ServerPlayer player) {
        boolean carrying = carried(player) != null;
        if (carrying != CARRYING.contains(player)) {
            if (carrying) {
                CARRYING.add(player);
            } else {
                CARRYING.remove(player);
            }
            player.connection.send(new ClientboundSetPassengersPacket(player));
        }
    }

    // Ползок −99 % и без прыжков; модификаторы не сохраняются — после входа в игру ставятся заново
    private static void applyModifiers(ServerPlayer player, boolean downed) {
        modifier(player, Attributes.MOVEMENT_SPEED, CRAWL, CRAWL_SPEED / VANILLA_CRAWL - 1, downed);
        modifier(player, Attributes.JUMP_STRENGTH, NO_JUMP, -1, downed);
    }

    private static void modifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount, boolean on) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null || instance.hasModifier(id) == on) {
            return;
        }
        if (on) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            instance.removeModifier(id);
        }
    }

    // ===== Перенос =====

    /** «Нести»: поверженный — на плечи несущего. */
    public static void carry(ServerPlayer carrier, ServerPlayer target) {
        if (carried(carrier) != null || !isDowned(target)) {
            return;
        }
        ours = true;
        target.startRiding(carrier, true);
        ours = false;
    }

    /** Бросить: поверженный падает рядом с несущим. */
    public static void drop(ServerPlayer carrier) {
        Player target = carried(carrier);
        if (target == null) {
            return;
        }
        ours = true;
        target.stopRiding();
        ours = false;
    }

    // Вышел несущий или тот, кого несут, — сначала с плеч: иначе ваниль (PlayerList.remove) примет несущего
    // за ездовое животное вышедшего и уберёт его из мира вместе с ним
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            drop(player);
            if (player.getVehicle() instanceof ServerPlayer carrier) {
                drop(carrier);
            }
        }
    }

    // В портал с поверженным на плечах не уйти: пассажир-игрок в другой мир не переносится — бросает у портала
    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            drop(player);
        }
    }

    // Сам поверженный с плеч не слезает (Shift) и ни на кого не садится; на несущего никто больше не сядет
    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (ours || event.getLevel().isClientSide()) {
            return;
        }
        Entity rider = event.getEntityMounting();
        Entity vehicle = event.getEntityBeingMounted();
        if (event.isMounting()) {
            if ((rider instanceof Player player && isDowned(player)) || (vehicle instanceof Player carrier && carried(carrier) != null)) {
                event.setCanceled(true);
            }
        } else if (rider instanceof Player player && vehicle instanceof Player && isDowned(player)
                && player.isAlive() && !player.isRemoved() && vehicle.isAlive() && !vehicle.isRemoved()) {
            event.setCanceled(true);
        }
    }

    // ===== Руки заняты: поверженный и несущий не бьют и не используют предметы (разделы 3.1, 6) =====

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        cancelIfBusy(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfBusy(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfBusy(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfBusy(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfBusy(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelIfBusy(event.getEntity(), event);
    }

    private static void cancelIfBusy(Player player, ICancellableEvent event) {
        if (busy(player)) {
            event.setCanceled(true);
        }
    }
}
