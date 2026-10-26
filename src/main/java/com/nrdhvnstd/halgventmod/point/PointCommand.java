package com.nrdhvnstd.halgventmod.point;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Команды точек карты (только администрация).
 *
 * /point capture <id> — снять карту локации (LocationMaps); в подсказке — номера мест с их названиями
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class PointCommand {
    // Номер места и его название подсказкой; точки появления не снимаются
    private static final SuggestionProvider<CommandSourceStack> IDS = (context, builder) -> {
        for (MapPoint point : MapPoints.get(context.getSource().getServer()).all()) {
            if (!point.isSpawn()) {
                builder.suggest(point.id, Component.literal(point.name));
            }
        }
        return builder.buildFuture();
    };

    private PointCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("point")
                .requires(source -> source.hasPermission(Permissions.ADMIN_LEVEL))
                .then(Commands.literal("capture")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .suggests(IDS)
                                .executes(context -> {
                                    LocationMaps.start(context.getSource(), IntegerArgumentType.getInteger(context, "id"));
                                    return 1;
                                }))));
    }
}
