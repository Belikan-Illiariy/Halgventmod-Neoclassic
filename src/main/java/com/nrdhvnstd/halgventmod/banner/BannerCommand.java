package com.nrdhvnstd.halgventmod.banner;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.network.BannerRegistryOpenPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Команды реестра знамён (только администрация). Сообщения — с префиксом «[Реестр]».
 *
 * /banner register <id> <название>      — внести баннер из основной руки
 * /banner update <id>                   — заменить цвет и узоры баннером из руки
 * /banner rename <id> <название>        — новое название
 * /banner describe <id> [описание]      — новое описание (без текста — убрать)
 * /banner give <id> [игроки] [кол-во]   — выдать копию баннера (по умолчанию себе, 1 шт.)
 * /banner remove <id>                   — убрать знамя из реестра
 * /banner list                          — окно реестра
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class BannerCommand {
    private static final int MAX_GIVE = 64;

    private static final SuggestionProvider<CommandSourceStack> IDS = (context, builder) ->
            SharedSuggestionProvider.suggest(BannerRegistry.get(context.getSource().getServer()).ids(), builder);

    private BannerCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("banner")
                .requires(source -> source.hasPermission(Permissions.ADMIN_LEVEL))
                .then(Commands.literal("register")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(BannerCommand::register))))
                .then(Commands.literal("update")
                        .then(idArgument().executes(BannerCommand::update)))
                .then(Commands.literal("rename")
                        .then(idArgument()
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(BannerCommand::rename))))
                .then(Commands.literal("describe")
                        .then(idArgument()
                                .executes(context -> describe(context, ""))
                                .then(Commands.argument("description", StringArgumentType.greedyString())
                                        .executes(context -> describe(context,
                                                StringArgumentType.getString(context, "description"))))))
                .then(Commands.literal("give")
                        .then(idArgument()
                                .executes(context -> give(context, List.of(context.getSource().getPlayerOrException()), 1))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> give(context, EntityArgument.getPlayers(context, "targets"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_GIVE))
                                                .executes(context -> give(context, EntityArgument.getPlayers(context, "targets"),
                                                        IntegerArgumentType.getInteger(context, "count")))))))
                .then(Commands.literal("remove")
                        .then(idArgument().executes(BannerCommand::remove)))
                .then(Commands.literal("list").executes(BannerCommand::list)));
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(), BannerRegistryOpenPayload.INSTANCE);
        return 1;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> idArgument() {
        return Commands.argument("id", StringArgumentType.word()).suggests(IDS);
    }

    /** Сообщение с префиксом «[Реестр]». */
    public static MutableComponent message(String key, Object... args) {
        return Component.translatable("banner.halgventmod.prefix").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("banner.halgventmod." + key, args).withStyle(ChatFormatting.RESET));
    }

    private static int fail(CommandContext<CommandSourceStack> context, String key, Object... args) {
        context.getSource().sendFailure(message(key, args));
        return 0;
    }

    /** Знамя по id из команды; нет — сообщение и null. */
    @Nullable
    private static BannerEntry entry(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "id");
        BannerEntry entry = BannerRegistry.get(context.getSource().getServer()).find(id);
        if (entry == null) {
            fail(context, "unknown", id);
        }
        return entry;
    }

    /** Баннер в основной руке; нет — сообщение и пустой стак. */
    private static ItemStack heldBanner(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ItemStack stack = context.getSource().getPlayerOrException().getMainHandItem();
        if (!(stack.getItem() instanceof BannerItem)) {
            fail(context, "no_banner");
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private static boolean nameTooLong(CommandContext<CommandSourceStack> context, String name) {
        if (name.length() > BannerEntry.NAME_MAX) {
            fail(context, "name_too_long", BannerEntry.NAME_MAX);
            return true;
        }
        return false;
    }

    private static int register(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = StringArgumentType.getString(context, "id");
        String name = StringArgumentType.getString(context, "name").strip();
        if (!BannerEntry.validId(id)) {
            return fail(context, "bad_id", BannerEntry.ID_MAX);
        }
        BannerRegistry registry = BannerRegistry.get(context.getSource().getServer());
        if (registry.find(id) != null) {
            return fail(context, "taken", id);
        }
        if (nameTooLong(context, name)) {
            return 0;
        }
        ItemStack stack = heldBanner(context);
        if (stack.isEmpty()) {
            return 0;
        }
        BannerEntry entry = new BannerEntry(id, name, "", ((BannerItem) stack.getItem()).getColor(),
                stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY),
                context.getSource().getTextName());
        registry.put(context.getSource().getServer(), entry);
        context.getSource().sendSuccess(() -> message("registered", name, id), true);
        return 1;
    }

    private static int update(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BannerEntry entry = entry(context);
        if (entry == null) {
            return 0;
        }
        ItemStack stack = heldBanner(context);
        if (stack.isEmpty()) {
            return 0;
        }
        BannerRegistry.get(context.getSource().getServer()).put(context.getSource().getServer(), entry.withDesign(
                ((BannerItem) stack.getItem()).getColor(),
                stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)));
        context.getSource().sendSuccess(() -> message("updated", entry.name(), entry.id()), true);
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> context) {
        BannerEntry entry = entry(context);
        String name = StringArgumentType.getString(context, "name").strip();
        if (entry == null || nameTooLong(context, name)) {
            return 0;
        }
        BannerRegistry.get(context.getSource().getServer()).put(context.getSource().getServer(), entry.withName(name));
        context.getSource().sendSuccess(() -> message("renamed", entry.id(), name), true);
        return 1;
    }

    private static int describe(CommandContext<CommandSourceStack> context, String description) {
        BannerEntry entry = entry(context);
        if (entry == null) {
            return 0;
        }
        description = description.strip();
        if (description.length() > BannerEntry.DESCRIPTION_MAX) {
            return fail(context, "description_too_long", BannerEntry.DESCRIPTION_MAX);
        }
        BannerRegistry.get(context.getSource().getServer())
                .put(context.getSource().getServer(), entry.withDescription(description));
        context.getSource().sendSuccess(() -> message("described", entry.name()), true);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets, int count) {
        BannerEntry entry = entry(context);
        if (entry == null) {
            return 0;
        }
        for (ServerPlayer player : targets) {
            BannerRegistry.give(player, entry.makeStack(count));
        }
        context.getSource().sendSuccess(() -> message("given", entry.name(), count, targets.size() == 1
                ? targets.iterator().next().getDisplayName() : Component.literal(Integer.toString(targets.size()))), true);
        return targets.size();
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        BannerEntry entry = entry(context);
        if (entry == null) {
            return 0;
        }
        BannerRegistry.get(context.getSource().getServer()).remove(context.getSource().getServer(), entry.id());
        context.getSource().sendSuccess(() -> message("removed", entry.name(), entry.id()), true);
        return 1;
    }
}
