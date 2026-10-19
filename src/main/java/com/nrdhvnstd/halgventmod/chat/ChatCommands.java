package com.nrdhvnstd.halgventmod.chat;

import org.slf4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.CommandTree;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Команды чата под RP:
 * - /msg, /tell, /w (личные сообщения на любом расстоянии) и /say (объявление от сервера) убраны совсем —
 *   их нет ни в подсказках, ни в /help;
 * - /me <действие> — своё: «* Имя действие», видно только в радиусе локального чата.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class ChatCommands {
    private static final String[] REMOVED = {"msg", "tell", "w", "say"};
    private static final Logger LOGGER = LogUtils.getLogger();

    private ChatCommands() {}

    // LOWEST — после ванильных команд и команд других модов
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandTree.remove(dispatcher, REMOVED);
        CommandTree.remove(dispatcher, "me");
        dispatcher.register(Commands.literal("me")
                .then(Commands.argument("action", StringArgumentType.greedyString())
                        .executes(ChatCommands::emote)));
    }

    private static int emote(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayerOrException();
        String action = StringArgumentType.getString(context, "action").trim();
        if (action.isEmpty()) {
            return 0;
        }
        ChatChannels.sendLocal(sender, Component.translatable("chat.halgventmod.emote", ChatChannels.name(sender), action)
                .withStyle(ChatFormatting.ITALIC));
        LOGGER.info("[Local] * {} {}", sender.getGameProfile().getName(), action);
        return 1;
    }
}
