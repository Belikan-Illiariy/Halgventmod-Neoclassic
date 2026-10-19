package com.nrdhvnstd.halgventmod.world;

import java.util.Locale;

import com.nrdhvnstd.halgventmod.HalgventMod;
import com.nrdhvnstd.halgventmod.Permissions;
import com.nrdhvnstd.halgventmod.network.NameTagsSyncPayload;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Ники над головами игроков: кому их видно. Хранится в мире, клиенты получают режим пакетом,
 * сами ники прячет клиент (NameTagsRender).
 *
 * /nametags                   — текущий режим
 * /nametags off|admins|all    — никому / только администраторам / всем (для операторов)
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public class NameTags extends SavedData {
    public enum Mode {
        OFF, ADMINS, ALL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Видит ли ники этот игрок. */
        public boolean visibleTo(Player viewer) {
            return switch (this) {
                case OFF -> false;
                case ADMINS -> Permissions.isAdmin(viewer);
                case ALL -> true;
            };
        }

        static Mode byId(String id) {
            for (Mode mode : values()) {
                if (mode.id().equals(id)) {
                    return mode;
                }
            }
            return ALL;
        }
    }

    /** Режим на клиенте — приходит с сервера при входе и после команды. */
    public static Mode clientMode = Mode.ALL;

    private static final String NAME = HalgventMod.MODID + "_name_tags";
    private static final Factory<NameTags> FACTORY = new Factory<>(NameTags::new, NameTags::load, null);

    private Mode mode = Mode.ALL; // по умолчанию — как в ванилле

    public static NameTags get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public Mode mode() {
        return mode;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("mode", mode.id());
        return tag;
    }

    private static NameTags load(CompoundTag tag, HolderLookup.Provider registries) {
        NameTags nameTags = new NameTags();
        nameTags.mode = Mode.byId(tag.getString("mode"));
        return nameTags;
    }

    // ----- Команда -----

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        var command = Commands.literal("nametags")
                .executes(context -> show(context.getSource()));
        for (Mode mode : Mode.values()) {
            command.then(Commands.literal(mode.id())
                    .requires(source -> source.hasPermission(Permissions.ADMIN_LEVEL))
                    .executes(context -> set(context.getSource(), mode)));
        }
        event.getDispatcher().register(command);
    }

    private static int show(CommandSourceStack source) {
        Mode mode = get(source.getServer()).mode;
        source.sendSuccess(() -> Component.translatable("command.halgventmod.nametags.current",
                Component.translatable(langKey(mode))), false);
        return 1;
    }

    private static int set(CommandSourceStack source, Mode mode) {
        MinecraftServer server = source.getServer();
        NameTags nameTags = get(server);
        nameTags.mode = mode;
        nameTags.setDirty();
        PacketDistributor.sendToAllPlayers(new NameTagsSyncPayload(mode));
        source.sendSuccess(() -> Component.translatable("command.halgventmod.nametags.set",
                Component.translatable(langKey(mode))), true);
        return 1;
    }

    public static String langKey(Mode mode) {
        return "command.halgventmod.nametags.mode." + mode.id();
    }

    // ----- Синхронизация -----

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new NameTagsSyncPayload(get(player.server).mode));
        }
    }
}
