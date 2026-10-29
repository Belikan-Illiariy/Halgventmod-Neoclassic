package com.nrdhvnstd.halgventmod.npc;

import javax.annotation.Nullable;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.character.Appearance;
import com.nrdhvnstd.halgventmod.menu.NpcGearMenu;
import com.nrdhvnstd.halgventmod.name.RpNames;
import com.nrdhvnstd.halgventmod.network.NpcEditOpenPayload;
import com.nrdhvnstd.halgventmod.network.NpcEditPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Настройка НПС из окна (Shift+E по НПС, только администрация). Сервер сам проверяет права, дальность и каждое
 * поле: клиенту не верит. Окно — client/npc/NpcEditScreen. Место работы и дом задаются порознь; маршрут
 * ставится жезлом (NpcWandItem), из окна его можно только очистить. Снаряжение — своё окно-инвентарь (NpcGearMenu).
 */
public final class NpcEditing {
    private static final double EDIT_REACH = 12.0; // Меняйте здесь: блоков от администратора до НПС

    private NpcEditing() {}

    /** Открыть окно настройки администратору (все поля заменяются). */
    public static void open(ServerPlayer player, HalgNpc npc) {
        send(player, npc, true);
    }

    // reset = false: окно уже открыто — обновить привязки, а несохранённые правки личности не трогать
    private static void send(ServerPlayer player, HalgNpc npc, boolean reset) {
        PacketDistributor.sendToPlayer(player, new NpcEditOpenPayload(npc.getId(), reset,
                npc.hasCustomName() ? npc.getCustomName().getString() : "", npc.getRole().ordinal(), npc.getSkin(),
                npc.isSlim(), npc.getAppearance(), npc.getPost(), npc.getHome(), npc.getRoute().size(),
                npc.getTemplate(), npc.getMerchantName()));
    }

    public static void handle(ServerPlayer player, NpcEditPayload payload) {
        HalgNpc npc = find(player, payload.entityId());
        if (npc == null) {
            return;
        }
        switch (payload.action()) {
            case APPLY -> apply(player, npc, payload);
            case REROLL -> {
                NpcTemplate template = NpcTemplates.random(npc.getRole(), npc.getRandom());
                if (template == null) {
                    message(player, Component.translatable("npc.halgventmod.command.no_templates", npc.getRole().title()));
                    return;
                }
                npc.applyIdentity(template, template.generate(npc.getRandom()));
                send(player, npc, true); // окно показывает новую личность
            }
            case POST_HERE -> {
                npc.setPost(player.blockPosition());
                message(player, Component.translatable("npc.halgventmod.edit.post_set"));
                send(player, npc, false);
            }
            case HOME_HERE -> {
                npc.setHome(player.blockPosition());
                message(player, Component.translatable("npc.halgventmod.edit.home_set"));
                send(player, npc, false);
            }
            case CLEAR_POST -> {
                npc.setPost(BlockPos.ZERO);
                send(player, npc, false);
            }
            case CLEAR_HOME -> {
                npc.setHome(BlockPos.ZERO);
                send(player, npc, false);
            }
            case CLEAR_ROUTE -> {
                npc.clearRoute();
                send(player, npc, false);
            }
            case OPEN_GEAR -> player.openMenu(new SimpleMenuProvider(
                    (id, inventory, opener) -> new NpcGearMenu(id, inventory, npc), npc.getDisplayName()),
                    buf -> buf.writeVarInt(npc.getId()));
            case SET_MERCHANT -> {
                // Привязать к игроку-торговцу по нику (игрок должен быть в сети)
                ServerPlayer merchant = player.server.getPlayerList().getPlayerByName(payload.name().strip());
                if (merchant == null) {
                    message(player, Component.translatable("npc.halgventmod.edit.merchant_offline", payload.name().strip()));
                    return;
                }
                npc.setMerchant(merchant.getUUID(), merchant.getGameProfile().getName());
                message(player, Component.translatable("npc.halgventmod.edit.merchant_set", merchant.getGameProfile().getName()));
                send(player, npc, false);
            }
            case CLEAR_MERCHANT -> {
                npc.setMerchant(null, "");
                send(player, npc, false);
            }
            case OPEN_SETTINGS -> {
                player.closeContainer(); // из окна снаряжения — обратно в настройки
                open(player, npc);
            }
            case REMOVE -> {
                HalgventMod.LOGGER.info("{} убрал НПС «{}»", player.getGameProfile().getName(), npc.getDisplayName().getString());
                npc.discard();
            }
        }
    }

    private static void apply(ServerPlayer player, HalgNpc npc, NpcEditPayload payload) {
        String name = payload.name().strip();
        if (name.length() > RpNames.MAX_LENGTH) {
            name = name.substring(0, RpNames.MAX_LENGTH);
        }
        npc.setCustomName(name.isEmpty() ? null : Component.literal(name));
        npc.setRole(NpcRole.byOrdinal(payload.role()));
        String skin = payload.skin().strip();
        if (skin.isEmpty() || ResourceLocation.tryParse(skin) != null) {
            npc.setSkin(skin);
        }
        npc.setSlim(payload.slim());
        var appearance = Appearance.validated(payload.appearance());
        if (appearance != null) {
            npc.setAppearance(Appearance.matchSex(appearance));
        }
        message(player, Component.translatable("npc.halgventmod.edit.saved"));
    }

    /** НПС в зоне досягаемости администратора; нет прав, не НПС или далеко — null. */
    @Nullable
    private static HalgNpc find(ServerPlayer player, int entityId) {
        Entity entity = player.level().getEntity(entityId);
        if (!Permissions.isAdmin(player) || !(entity instanceof HalgNpc npc) || !npc.isAlive()
                || player.distanceToSqr(npc) > EDIT_REACH * EDIT_REACH) {
            return null;
        }
        return npc;
    }

    private static void message(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }
}
