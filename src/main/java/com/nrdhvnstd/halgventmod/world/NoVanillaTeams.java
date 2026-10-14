package com.nrdhvnstd.halgventmod.world;

import java.util.ArrayList;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.nrdhvnstd.halgventmod.CommandTree;
import com.nrdhvnstd.halgventmod.HalgventMod;

import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Ванильных команд табло (/team) в моде нет — вместо них будут свои отряды.
 * Убраны команды /team, /teammsg и /tm; команды, оставшиеся в мире с прошлых запусков, удаляются при старте сервера,
 * чтобы их цвет, приставки, видимость ников и запрет урона по своим больше ни на что не влияли.
 */
@EventBusSubscriber(modid = HalgventMod.MODID)
public final class NoVanillaTeams {
    private static final Logger LOGGER = LogUtils.getLogger();

    private NoVanillaTeams() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void removeCommands(RegisterCommandsEvent event) {
        CommandTree.remove(event.getDispatcher(), "team", "teammsg", "tm");
    }

    @SubscribeEvent
    public static void removeTeams(ServerStartedEvent event) {
        Scoreboard scoreboard = event.getServer().getScoreboard();
        for (PlayerTeam team : new ArrayList<>(scoreboard.getPlayerTeams())) {
            LOGGER.info("Removing vanilla team '{}' ({} members)", team.getName(), team.getPlayers().size());
            scoreboard.removePlayerTeam(team);
        }
    }
}
