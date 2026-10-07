package com.nrdhvnstd.halgventmod.lock;

import java.util.List;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.registry.ModComponents;
import com.nrdhvnstd.halgventmod.registry.ModItems;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Команды замков (только администрация). Команды про конкретный замок работают с дверью или сундуком,
 * на который смотрит игрок.
 *
 * /lock info                     — секрет, вид и состояние замка
 * /lock remove                   — снять любой замок, даже запертый и врезной
 * /lock rekey                    — сменить секрет: старые ключи перестают подходить, выдаётся новый ключ
 * /lock key <секрет> [название]  — выдать ключ
 * /lock name <название>          — подписать ключ в руке
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class LockCommand {
    private LockCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lock")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info").executes(LockCommand::info))
                .then(Commands.literal("remove").executes(LockCommand::remove))
                .then(Commands.literal("rekey").executes(LockCommand::rekey))
                .then(Commands.literal("key")
                        .then(Commands.argument("secret", LongArgumentType.longArg(1))
                                .executes(context -> giveKey(context, null))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> giveKey(context, StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("name")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(LockCommand::name))));
    }

    /** Дверь или сундук под прицелом и замок на них. */
    private record Target(ServerLevel level, ServerPlayer player, BlockPos pos, List<BlockPos> place,
                          @Nullable LockData lock) {}

    @Nullable
    private static Target target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            context.getSource().sendFailure(Component.translatable("lock.halgventmod.command.no_target"));
            return null;
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (!Locks.isLockable(state)) {
            context.getSource().sendFailure(Component.translatable("lock.halgventmod.command.no_target"));
            return null;
        }
        List<BlockPos> place = Locks.place(level, pos, state);
        return new Target(level, player, pos, place, Locks.find(level, place));
    }

    @Nullable
    private static Target lockedTarget(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Target target = target(context);
        if (target != null && target.lock() == null) {
            context.getSource().sendFailure(Component.translatable("lock.halgventmod.no_lock"));
            return null;
        }
        return target;
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Target target = lockedTarget(context);
        if (target == null) {
            return 0;
        }
        context.getSource().sendSuccess(() -> LockActions.info(target.lock()), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Target target = lockedTarget(context);
        if (target == null) {
            return 0;
        }
        Locks.put(target.level(), target.place(), null);
        context.getSource().sendSuccess(() -> Component.translatable("lock.halgventmod.removed"), true);
        return 1;
    }

    private static int rekey(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Target target = lockedTarget(context);
        if (target == null) {
            return 0;
        }
        long secret = Locks.newId(target.level().getServer());
        Locks.put(target.level(), target.place(), target.lock().withSecret(secret));
        Locks.give(target.player(), Locks.makeKey(secret, null));
        context.getSource().sendSuccess(() -> Component.translatable("lock.halgventmod.command.rekeyed", secret), true);
        return 1;
    }

    private static int giveKey(CommandContext<CommandSourceStack> context, @Nullable String name)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        long secret = LongArgumentType.getLong(context, "secret");
        Locks.give(player, Locks.makeKey(secret, name));
        context.getSource().sendSuccess(() -> Component.translatable("lock.halgventmod.command.key_given", secret), true);
        return 1;
    }

    private static int name(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(ModItems.KEY.get())) {
            context.getSource().sendFailure(Component.translatable("lock.halgventmod.command.not_key"));
            return 0;
        }
        String name = StringArgumentType.getString(context, "name");
        stack.set(ModComponents.KEY_LABEL.get(), name);
        context.getSource().sendSuccess(() -> Component.translatable("lock.halgventmod.command.named", name), false);
        return 1;
    }
}