package com.nrdhvnstd.halgventmod.chat;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.brewing.Intoxication;
import com.nrdhvnstd.halgventmod.squad.SquadCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;

/**
 * Каналы чата. Сообщение без префикса — в локальный чат, с «!» — в глобальный, с «#» — в чат отряда (SquadCommands).
 * Глобальный: видят все игроки сервера, писать могут только администраторы (Permissions).
 * Локальный: видят игроки в том же измерении в радиусе LOCAL_RADIUS блоков, включая самого автора.
 *
 * Ванильная рассылка отменяется, сообщения уходят игрокам системными (без подписи чата Mojang):
 * подписанное сообщение нельзя ни показать части игроков, ни убрать из него «!».
 * Команды чата — ChatCommands: /me в радиусе локального чата, /msg, /tell, /w, /say убраны.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class ChatChannels {
    public static final double LOCAL_RADIUS = 50.0;
    public static final String GLOBAL_PREFIX = "!";

    private static final Logger LOGGER = LogUtils.getLogger();

    private ChatChannels() {}

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        event.setCanceled(true);
        ServerPlayer sender = event.getPlayer();
        String text = event.getRawText();

        if (text.startsWith(SquadCommands.CHAT_PREFIX)) {
            SquadCommands.chat(sender, text.substring(SquadCommands.CHAT_PREFIX.length()));
            return;
        }
        if (text.startsWith(GLOBAL_PREFIX)) {
            if (!Permissions.isAdmin(sender)) {
                sender.sendSystemMessage(Component.translatable("chat.halgventmod.global_admins_only")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            String body = text.substring(GLOBAL_PREFIX.length()).trim();
            if (body.isEmpty()) {
                return;
            }
            Component message = Component.translatable("chat.halgventmod.global", name(sender), body)
                    .withStyle(ChatFormatting.GOLD);
            sender.server.getPlayerList().broadcastSystemMessage(message, false); // и в лог сервера
            return;
        }

        text = Intoxication.slur(sender, text); // пьяный путает буквы (правило drunkChat)
        sendLocal(sender, Component.translatable("chat.halgventmod.local", name(sender), text));
        LOGGER.info("[Local] <{}> {}", sender.getGameProfile().getName(), text);
    }

    /**
     * Имя автора для строки чата: RP-имя с подсказкой настоящего ника при наведении,
     * но без ванильного «щелчок — /tell»: личных сообщений на сервере нет.
     */
    public static Component name(ServerPlayer player) {
        return player.getDisplayName().copy().withStyle(style -> style.withClickEvent(null));
    }

    /** Отправить сообщение всем в радиусе локального чата; если не услышал никто, кроме автора, — сказать ему. */
    public static void sendLocal(ServerPlayer sender, Component message) {
        double radiusSqr = LOCAL_RADIUS * LOCAL_RADIUS;
        int heard = 0;
        for (ServerPlayer player : sender.serverLevel().players()) {
            if (player.distanceToSqr(sender) <= radiusSqr) {
                player.sendSystemMessage(message);
                if (player != sender) {
                    heard++;
                }
            }
        }
        if (heard == 0) {
            sender.sendSystemMessage(Component.translatable("chat.halgventmod.nobody_heard")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
    }
}
