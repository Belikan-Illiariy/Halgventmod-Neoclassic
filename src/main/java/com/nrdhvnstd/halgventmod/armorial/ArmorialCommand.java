package com.nrdhvnstd.halgventmod.armorial;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.banner.BannerEntry;
import com.nrdhvnstd.halgventmod.banner.BannerRegistry;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Команды гербовников (только администрация). Сообщения — с префиксом «[Гербовник]».
 *
 * /armorial create <id> <название>   — новая пустая книга
 * /armorial rename <id> <название>   — новое название
 * /armorial give <id> [игроки]       — выдать книгу (по умолчанию себе)
 * /armorial remove <id>              — удалить содержимое; выданные книги станут пустыми ссылками
 * /armorial list                     — книги в чат
 * Страницы правятся в самой книге (администрация в творческом открывает её сразу в правке).
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class ArmorialCommand {
    private static final SuggestionProvider<CommandSourceStack> IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(ArmorialBooks.get(context.getSource().getServer()).ids(), builder);

    private ArmorialCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("armorial")
                .requires(source -> source.hasPermission(Permissions.ADMIN_LEVEL))
                .then(Commands.literal("create")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(ArmorialCommand::create))))
                .then(Commands.literal("rename")
                        .then(idArgument()
                                .then(Commands.argument("title", StringArgumentType.greedyString())
                                        .executes(ArmorialCommand::rename))))
                .then(Commands.literal("give")
                        .then(idArgument()
                                .executes(context -> give(context, List.of(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> give(context, EntityArgument.getPlayers(context, "targets"))))))
                .then(Commands.literal("remove")
                        .then(idArgument().executes(ArmorialCommand::remove)))
                .then(Commands.literal("list").executes(ArmorialCommand::list)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> idArgument() {
        return Commands.argument("id", StringArgumentType.word()).suggests(IDS);
    }

    /** Сообщение с префиксом «[Гербовник]». */
    public static MutableComponent message(String key, Object... args) {
        return Component.translatable("armorial.halgventmod.prefix").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("armorial.halgventmod." + key, args).withStyle(ChatFormatting.RESET));
    }

    private static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(message(key, args));
        return 0;
    }

    private static boolean titleTooLong(CommandContext<CommandSourceStack> context, String title) {
        if (title.length() > ArmorialBook.TITLE_MAX) {
            fail(context, "title_too_long", ArmorialBook.TITLE_MAX);
            return true;
        }
        return false;
    }

    private static int create(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        String title = StringArgumentType.getString(context, "title").strip();
        // id книги — по тем же правилам, что у знамён
        if (!BannerEntry.validId(id)) {
            return fail(context, "bad_id", BannerEntry.ID_MAX);
        }
        ArmorialBooks books = ArmorialBooks.get(context.getSource().getServer());
        if (books.exists(id)) {
            return fail(context, "taken", id);
        }
        if (titleTooLong(context, title)) {
            return 0;
        }
        books.put(id, new ArmorialBook(title, List.of()));
        context.getSource().sendSuccess(() -> message("created", title, id), true);
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        String title = StringArgumentType.getString(context, "title").strip();
        ArmorialBooks books = ArmorialBooks.get(context.getSource().getServer());
        ArmorialBook book = books.find(id);
        if (book == null) {
            return fail(context, "missing", id);
        }
        if (titleTooLong(context, title)) {
            return 0;
        }
        books.put(id, book.withTitle(title));
        context.getSource().sendSuccess(() -> message("renamed", id, title), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) {
        String id = StringArgumentType.getString(context, "id");
        if (ArmorialBooks.get(context.getSource().getServer()).find(id) == null) {
            return fail(context, "missing", id);
        }
        for (ServerPlayer player : targets) {
            ItemStack stack = new ItemStack(ModItems.ARMORIAL.get());
            stack.set(ModComponents.ARMORIAL_BOOK.get(), id);
            BannerRegistry.give(player, stack);
        }
        context.getSource().sendSuccess(() -> message("given", id, targets.size() == 1
                ? targets.iterator().next().getDisplayName() : Component.literal(Integer.toString(targets.size()))), true);
        return targets.size();
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        if (!ArmorialBooks.get(context.getSource().getServer()).remove(id)) {
            return fail(context, "missing", id);
        }
        context.getSource().sendSuccess(() -> message("removed", id), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Map<String, ArmorialBook> books = ArmorialBooks.get(context.getSource().getServer()).all();
        if (books.isEmpty()) {
            context.getSource().sendSuccess(() -> message("list_empty"), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> message("list_header", books.size()), false);
        books.forEach((id, book) -> context.getSource().sendSuccess(() -> Component.translatable(
                "armorial.halgventmod.list_entry", id, book.title(), book.pages().size()), false));
        return books.size();
    }
}
