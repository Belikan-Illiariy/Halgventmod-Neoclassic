package com.nrdhvnstd.halgventmod.npc;

import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

/**
 * Двери для НПС: ходят сквозь любые деревянные двери, в том числе запертые на замок мода (замок на них не действует) —
 * открывают на пути, проходят и закрывают за собой (OpenDoorGoal). Запертая дверь после прохода остаётся запертой:
 * закрытая, как была. Железные двери, открываемые только рычагом, НПС не открывают.
 */
final class NpcDoors {
    private NpcDoors() {}

    /** Навигация НПС: путь прокладывается сквозь двери. */
    static PathNavigation navigation(HalgNpc npc, Level level) {
        GroundPathNavigation navigation = new GroundPathNavigation(npc, level);
        navigation.setCanOpenDoors(true);
        navigation.setCanFloat(true);
        return navigation;
    }

    /** Цель «открыть дверь на пути, пройти и закрыть за собой». */
    static OpenDoorGoal openDoorGoal(HalgNpc npc) {
        return new OpenDoorGoal(npc, true);
    }
}
