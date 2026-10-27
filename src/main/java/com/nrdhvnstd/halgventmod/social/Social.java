package com.nrdhvnstd.halgventmod.social;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.armor.ArmorInventory;
import com.nrdhvnstd.halgventmod.armor.ArmorRules;
import com.nrdhvnstd.halgventmod.armor.ArmorSlot;
import com.nrdhvnstd.halgventmod.downed.Downed;
import com.nrdhvnstd.halgventmod.downed.Healing;
import com.nrdhvnstd.halgventmod.menu.SocialSearchMenu;
import com.nrdhvnstd.halgventmod.name.RpNames;
import com.nrdhvnstd.halgventmod.network.SocialEndPayload;
import com.nrdhvnstd.halgventmod.network.SocialProgressPayload;
import com.nrdhvnstd.halgventmod.pickup.Pickup;
import com.nrdhvnstd.halgventmod.registry.ModAttachments;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Социальные взаимодействия игроков (docs/TZ_social_interactions.md): обыск, мешок, кандалы, наручники и их снятие.
 * Клиент только выбирает пункт кругового меню (client/social/SocialInput) и шлёт SocialActionPayload;
 * сервер проверяет цель, ведёт шкалу применения (3 с) и применяет действие.
 *
 * Шкала: оба в радиусе — заполняется, кто-то вышел — пустеет вчетверо быстрее, вернулся — заполняется дальше
 * с того же места, опустела — действие отменено. Урон тому, кто применяет, срывает действие сразу.
 * Жертва отменить не может ничем, кроме выхода из радиуса.
 *
 * Поверженного (downed/Downed) можно ещё вылечить повязкой или мазью и взять на плечи (docs/TZ_downed_and_hp.md, 4–6).
 * Себя — тем же HEAL с целью «сам»: предмет в руке, E без цели (client/downed/DownedInput); шкала та же.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class Social {
    public static final double INTERACT_REACH = 2.0;   // блоков от глаз до ближайшей точки хитбокса жертвы ⚠️
    private static final double REACH_MARGIN = 1.0;    // сервер прощает сдвиг за время пакета (только на старте)
    private static final double SEARCH_SLACK = 0.5;    // окно обыска не мигает на самой границе радиуса ⚠️
    public static final int APPLY_TICKS = 60;          // 3 с
    private static final int DRAIN_MULTIPLIER = 4;     // пустеет вчетверо быстрее, чем заполняется ⚠️
    private static final int COOLDOWN_TICKS = 20;      // 1 с между действиями одного игрока ⚠️
    private static final int PROGRESS_EVERY = 2;       // шкала клиентам — раз в 2 тика, между ними клиент сглаживает
    private static final int REFRESH_EVERY = 10;       // сверка маски ограничений с эффектами

    /** Шкала одного атакующего. */
    private static final class Apply {
        final UUID target;
        final SocialAction action;
        int ticks;

        Apply(UUID target, SocialAction action) {
            this.target = target;
            this.action = action;
        }
    }

    // Только в памяти: при выходе из игры и перезапуске шкалы сбрасываются (ТЗ 7.3)
    private static final Map<UUID, Apply> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> LAST_ACTION = new HashMap<>();

    private Social() {}

    /**
     * Можно ли вообще взаимодействовать с этим игроком (без дальности). Клиент зовёт для подсказки.
     * Поверженный и несущий сами не взаимодействуют; того, кого несут на плечах, не трогают.
     * Поверженный — цель для всего меню (docs/TZ_downed_and_hp.md, 7).
     */
    public static boolean canTarget(Player player, Entity entity) {
        return entity instanceof Player target && target != player && target.isAlive()
                && !target.isCreative() && !target.isSpectator() && !player.isSpectator()
                && !Downed.busy(player) && !(target.getVehicle() instanceof Player);
    }

    /** Перевязать себя: предмет в руке, есть что лечить, руки свободны. */
    public static boolean canHealSelf(Player player) {
        return player.isAlive() && !player.isSpectator() && !Downed.busy(player) && player.isHurt()
                && Healing.heals(player.getMainHandItem());
    }

    // Цель и действие всё ещё годятся — на старте и каждый тик шкалы
    private static boolean valid(ServerPlayer player, ServerPlayer target, SocialAction action) {
        if (target == player) {
            return action == SocialAction.HEAL && canHealSelf(player);
        }
        return canTarget(player, target) && action.fits(target);
    }

    /** Имя над подсказкой и в заголовке обыска: RP-имя, в хундсгугеле — «Неизвестный в шлеме» (ТЗ 2.2). */
    public static Component shownName(Player target) {
        ArmorInventory armor = target.getExistingDataOrNull(ModAttachments.ARMOR);
        if (armor != null && armor.get(ArmorSlot.HELMET).is(ModItems.HUNDSGUGEL_BASCINET.get())) {
            return Component.translatable("social.halgventmod.unknown");
        }
        String name = RpNames.get(target);
        return Component.literal(name.isEmpty() ? target.getGameProfile().getName() : name);
    }

    /** Пакет от клиента: начать действие над игроком targetId. */
    public static void handle(ServerPlayer player, int targetId, SocialAction action) {
        if (action == SocialAction.DROP) {
            Downed.drop(player); // бросить — сразу, без шкалы
            return;
        }
        if (!player.isAlive() || ACTIVE.containsKey(player.getUUID())) {
            return;
        }
        Integer last = LAST_ACTION.get(player.getUUID());
        int now = player.server.getTickCount();
        if (last != null && now - last < COOLDOWN_TICKS) {
            return;
        }
        Entity entity = player.level().getEntity(targetId);
        if (!(entity instanceof ServerPlayer target) || !valid(player, target, action) || (target != player
                && (!Pickup.inReach(player, target, INTERACT_REACH + REACH_MARGIN) || !player.hasLineOfSight(target)))) {
            return;
        }
        if (action == SocialAction.HEAL && Healing.find(player).isEmpty()) {
            ArmorRules.tell(player, Component.translatable("message.halgventmod.heal_no_item"));
            return;
        }
        LAST_ACTION.put(player.getUUID(), now);
        ACTIVE.put(player.getUUID(), new Apply(target.getUUID(), action));
        sendProgress(player, target, action, 0);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Apply>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Apply> entry = it.next();
            Apply apply = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ServerPlayer target = server.getPlayerList().getPlayer(apply.target);
            if (player == null || target == null || !player.isAlive() || player.level() != target.level()
                    || !valid(player, target, apply.action)) {
                it.remove();
                sendEnd(player, target);
                continue;
            }
            // Радиус — и для жертвы, и для атакующего: кто бы ни отошёл, шкала пустеет (ТЗ 4.3, 4.4)
            apply.ticks += Pickup.inReach(player, target, INTERACT_REACH) ? 1 : -DRAIN_MULTIPLIER;
            if (apply.ticks <= 0) {
                it.remove();
                sendEnd(player, target);
                continue;
            }
            if (apply.ticks >= APPLY_TICKS) {
                it.remove();
                sendEnd(player, target);
                complete(player, target, apply.action);
                continue;
            }
            if (server.getTickCount() % PROGRESS_EVERY == 0) {
                sendProgress(player, target, apply.action, apply.ticks / (float) APPLY_TICKS);
            }
        }
    }

    private static void complete(ServerPlayer player, ServerPlayer target, SocialAction action) {
        Restraint restraint = action.restraint();
        if (action == SocialAction.HEAL) {
            if (!heal(player, target)) {
                return;
            }
        } else if (action == SocialAction.CARRY) {
            Downed.carry(player, target);
        } else if (restraint == null) {
            player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new SocialSearchMenu(id, target, target.getInventory()),
                            Component.translatable("gui.halgventmod.search.title", shownName(target))),
                    buf -> buf.writeVarInt(target.getId()));
        } else if (action.removes()) {
            target.removeEffect(restraint.effect());
        } else {
            // Бесконечно, без частиц; значок — у жертвы на экране и над головой (client/social/SocialBars)
            target.addEffect(new MobEffectInstance(restraint.effect(), MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
            if (restraint == Restraint.CUFFS) {
                CuffsRules.cuffed(target);
            }
        }
        Restraint.refresh(target);
        if (target != player) {
            ArmorRules.tell(target, Component.translatable(action.doneKey()));
        }
    }

    // Повязка или мазь тратится; поверженный встаёт, потом получает здоровье (урезанное голодом и жаждой — Starvation)
    private static boolean heal(ServerPlayer player, ServerPlayer target) {
        ItemStack stack = target == player ? player.getMainHandItem() : Healing.find(player);
        float amount = Healing.amount(stack);
        if (amount <= 0) {
            ArmorRules.tell(player, Component.translatable("message.halgventmod.heal_no_item"));
            return false;
        }
        stack.consume(1, player);
        Downed.revive(target);
        target.heal(amount);
        return true;
    }

    /** Окно обыска открыто, пока жертва жива и рядом (SocialSearchMenu.stillValid). */
    public static boolean canStillSearch(Player player, Player target) {
        return target.isAlive() && !target.isRemoved() && player.level() == target.level()
                && Pickup.inReach(player, target, INTERACT_REACH + SEARCH_SLACK);
    }

    // Урон тому, кто применяет, снимает или обыскивает, срывает действие сразу, без пустения шкалы (ТЗ 4.3)
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) {
            return;
        }
        Apply apply = ACTIVE.remove(player.getUUID());
        if (apply != null) {
            sendEnd(player, player.server.getPlayerList().getPlayer(apply.target));
        }
        if (player.containerMenu instanceof SocialSearchMenu) {
            player.closeContainer();
        }
    }

    // Маска ограничений для клиентов — по эффектам: эффект могли снять командой, игрок мог умереть
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % REFRESH_EVERY == 0) {
            Restraint.refresh(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Restraint.refresh(player);
        }
    }

    // Вышел атакующий или жертва — шкала сброшена
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_ACTION.remove(id);
        MinecraftServer server = event.getEntity().getServer();
        Iterator<Map.Entry<UUID, Apply>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Apply> entry = it.next();
            if (entry.getKey().equals(id) || entry.getValue().target.equals(id)) {
                it.remove();
                if (server != null) {
                    sendEnd(server.getPlayerList().getPlayer(entry.getKey()),
                            server.getPlayerList().getPlayer(entry.getValue().target));
                }
            }
        }
    }

    private static void sendProgress(ServerPlayer player, ServerPlayer target, SocialAction action, float progress) {
        SocialProgressPayload payload = new SocialProgressPayload(player.getId(), target.getId(), action, progress);
        PacketDistributor.sendToPlayer(player, payload);
        if (target != player) {
            PacketDistributor.sendToPlayer(target, payload);
        }
    }

    // Атакующий мог уже выйти: тогда его номер сущности не узнать — жертва уберёт полоску сама (SocialBars, по таймауту)
    private static void sendEnd(@Nullable ServerPlayer player, @Nullable ServerPlayer target) {
        if (player == null) {
            return;
        }
        SocialEndPayload payload = new SocialEndPayload(player.getId());
        if (!player.hasDisconnected()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        if (target != null && target != player && !target.hasDisconnected()) {
            PacketDistributor.sendToPlayer(target, payload);
        }
    }
}
