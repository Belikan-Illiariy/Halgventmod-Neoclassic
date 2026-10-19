package com.nrdhvnstd.halgventmod.squad;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.chat.ChatChannels;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Команды отрядов (доступны всем игрокам); то же умеет окно «Отряд». Сами действия — в SquadService.
 *
 * /squad                         — свой отряд: название, лидер, участники (в игре — белым, нет — серым)
 * /squad create <название>       — создать отряд (знамя — белое, сменить его можно в окне «Отряд»)
 * /squad invite <игрок>          — пригласить (лидер); приглашение действует пять минут
 * /squad accept|decline [отряд]  — принять / отклонить приглашение (без названия — если оно одно)
 * /squad leave                   — уйти (лидер — только если он один: тогда отряд распускается)
 * /squad kick <игрок>            — исключить (лидер)
 * /squad leader <игрок>          — передать лидерство (лидер)
 * /squad disband                 — распустить отряд (лидер)
 * /squad chat <текст>, /sc <текст>, или «#» в начале сообщения — чат отряда
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class SquadCommands {
    public static final String CHAT_PREFIX = "#";

    private static final Logger LOGGER = LogUtils.getLogger();

    private SquadCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("squad")
                .executes(context -> info(context.getSource().getPlayerOrException()))
                .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> result(context, (player, ignored) -> SquadService.create(player,
                                        StringArgumentType.getString(context, "name"),
                                        DyeColor.WHITE, BannerPatternLayers.EMPTY)))))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SquadCommands::invite)))
                .then(Commands.literal("accept")
                        .executes(context -> answer(context, null, true))
                        .then(Commands.argument("squad", StringArgumentType.greedyString())
                                .suggests(INVITES)
                                .executes(context -> answer(context, StringArgumentType.getString(context, "squad"), true))))
                .then(Commands.literal("decline")
                        .executes(context -> answer(context, null, false))
                        .then(Commands.argument("squad", StringArgumentType.greedyString())
                                .suggests(INVITES)
                                .executes(context -> answer(context, StringArgumentType.getString(context, "squad"), false))))
                .then(Commands.literal("leave")
                        .executes(context -> result(context, (player, ignored) -> SquadService.leave(player))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("member", StringArgumentType.word())
                                .suggests(MEMBERS)
                                .executes(context -> withMember(context, SquadService::kick))))
                .then(Commands.literal("leader")
                        .then(Commands.argument("member", StringArgumentType.word())
                                .suggests(MEMBERS)
                                .executes(context -> withMember(context, SquadService::makeLeader))))
                .then(Commands.literal("disband")
                        .executes(context -> result(context, (player, ignored) -> SquadService.disband(player))))
                .then(Commands.literal("chat")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(SquadCommands::chatCommand))));
        event.getDispatcher().register(Commands.literal("sc")
                .then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(SquadCommands::chatCommand)));
    }

    // Ники участников своего отряда, кроме себя
    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        List<String> names = new ArrayList<>();
        if (player != null) {
            Squad squad = Squads.get(player.server).of(player.getUUID());
            if (squad != null) {
                for (UUID member : squad.members()) {
                    String name = SquadService.profileName(player.server, member);
                    if (!member.equals(player.getUUID()) && name != null) {
                        names.add(name);
                    }
                }
            }
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    // Названия отрядов, приславших приглашения
    private static final SuggestionProvider<CommandSourceStack> INVITES = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        List<String> names = new ArrayList<>();
        if (player != null) {
            Squads squads = Squads.get(player.server);
            for (Squads.Invite invite : squads.invites(player.getUUID(), player.server.getTickCount())) {
                Squad squad = squads.squad(invite.squad());
                if (squad != null) {
                    names.add(squad.name());
                }
            }
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    // ----- Просмотр -----

    private static int info(ServerPlayer player) {
        Squad squad = Squads.get(player.server).of(player.getUUID());
        if (squad == null) {
            player.sendSystemMessage(Component.translatable("squad.halgventmod.none"));
            return 0;
        }
        List<Component> names = new ArrayList<>();
        for (UUID member : squad.members()) {
            names.add(SquadService.memberName(player.server, member));
        }
        player.sendSystemMessage(Component.translatable("squad.halgventmod.info.header", squad.name())
                .withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.translatable("squad.halgventmod.info.leader",
                SquadService.memberName(player.server, squad.leader())));
        player.sendSystemMessage(Component.translatable("squad.halgventmod.info.members", names.size(),
                ComponentUtils.formatList(names, Component.literal(", "))));
        return 1;
    }

    // ----- Действия -----

    private static int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(context, "player");
        return result(context, (player, ignored) -> SquadService.invite(player, target));
    }

    // Приглашение по названию отряда; без названия — единственное
    private static int answer(CommandContext<CommandSourceStack> context, @Nullable String squadName, boolean accept)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Squads squads = Squads.get(player.server);
        List<Squads.Invite> invites = squads.invites(player.getUUID(), player.server.getTickCount());
        UUID squadId = null;
        if (squadName == null) {
            if (invites.size() == 1) {
                squadId = invites.get(0).squad();
            } else if (invites.size() > 1) {
                return fail(player, Component.translatable("squad.halgventmod.which_invite"));
            }
        } else {
            Squad squad = squads.byName(squadName.trim());
            squadId = squad != null ? squad.id() : null;
        }
        if (squadId == null) {
            return fail(player, Component.translatable("squad.halgventmod.no_invite"));
        }
        UUID id = squadId;
        return result(context, (p, ignored) -> accept ? SquadService.accept(p, id) : SquadService.decline(p, id));
    }

    private static int withMember(CommandContext<CommandSourceStack> context,
            BiFunction<ServerPlayer, UUID, Component> action) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(context, "member");
        Squad squad = Squads.get(player.server).of(player.getUUID());
        if (squad != null) {
            for (UUID member : squad.members()) {
                if (name.equalsIgnoreCase(SquadService.profileName(player.server, member))) {
                    return result(context, (p, ignored) -> action.apply(p, member));
                }
            }
        }
        return fail(player, Component.translatable("squad.halgventmod.not_member", name));
    }

    /** Выполнить действие от имени игрока; отказ — красным в чат. */
    private static int result(CommandContext<CommandSourceStack> context,
            BiFunction<ServerPlayer, Void, Component> action) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Component error = action.apply(player, null);
        return error == null ? 1 : fail(player, error);
    }

    // ----- Чат отряда -----

    private static int chatCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return chat(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "text")) ? 1 : 0;
    }

    /** Сообщение в чат отряда — всем участникам в игре, на любом расстоянии. */
    public static boolean chat(ServerPlayer sender, String text) {
        String body = text.trim();
        if (body.isEmpty()) {
            return false;
        }
        Squad squad = Squads.get(sender.server).of(sender.getUUID());
        if (squad == null) {
            fail(sender, Component.translatable("squad.halgventmod.chat_not_in"));
            return false;
        }
        Squads.tell(sender.server, squad, Component.translatable("squad.halgventmod.chat", ChatChannels.name(sender), body)
                .withStyle(ChatFormatting.AQUA));
        LOGGER.info("[Squad {}] <{}> {}", squad.name(), sender.getGameProfile().getName(), body);
        return true;
    }

    private static int fail(ServerPlayer player, Component message) {
        player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
        return 0;
    }
}
